package io.github.nexalloy.morphe.youtube.layout.hide.ambientmode

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.ambientlight.AmbientLightController
import app.morphe.extension.youtube.patches.AmbientModePatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.shared.layout.theme.lithoColorHookPatch
import io.github.nexalloy.morphe.shared.layout.theme.lithoDrawableColorHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.information.videoTimeHook
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Field

/**
 * CinematicContainerRenderer field 3 (presentation style) DYNAMIC_BLURRED:
 * the background takes the colors of the video.
 */
private const val PRESENTATION_STYLE_DYNAMIC_BLURRED = 5

/**
 * CinematicContainerRenderer used when the /next response has none.
 * Serialized from a server response, with field 3 (presentation style) = 5 (DYNAMIC_BLURRED)
 * and field 4.11 (ambient mode on by default) = true.
 */
private const val DEFAULT_CINEMATIC_CONTAINER_RENDERER =
    "EgsQ94CqmwMdAAAAABIGEPeAqpsDEgsQgICAmAMdAACAPxgFIpEBCICAgPgPEICAgPgPGg0IkE4QiCcdAACAPyAUJQAAIEAoATUA" +
            "AIA/PQAAgkNFmpmZP00AAMA/VQAAgD9YAWpSDQAAIEAVAAAgQB0AACBAJQAAIEAyPBIREPeAqpsDHQAAAAAI94CqmwMSERCA" +
            "gICYAx0AAIA/CICAgJgDCAIdAAAAPyUAAAA/LQAAgD81AACAPw=="

val AmbientMode = patch(
    name = "Ambient mode",
    description = "Adds Enhanced Ambient mode and options to force Ambient mode on, " +
        "bypass its power saving restrictions, and disable it entirely or in fullscreen.",
) {
    dependsOn(
        lithoColorHookPatch { is_21_30_or_greater },
        VideoInformationPatch,
        PlayerTypeHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_ambient_mode_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_enhanced_ambient_mode", summary = true),
                NonInteractivePreference(
                    key = "morphe_enhanced_ambient_mode_strength",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_enhanced_ambient_mode_smoothing",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_enhanced_ambient_mode_blur",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                SwitchPreference("morphe_enhanced_ambient_mode_readable_text", summary = true),
                SwitchPreference("morphe_enhanced_ambient_mode_dim_while_scrolling", summary = true),
                NonInteractivePreference(
                    key = "morphe_enhanced_ambient_mode_dim_duration",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                SwitchPreference("morphe_bypass_ambient_mode_restrictions", summary = true),
                SwitchPreference("morphe_disable_ambient_mode"),
                SwitchPreference("morphe_disable_fullscreen_ambient_mode"),
            )
        )
    )

    //
    // Enhanced Ambient mode.
    //

    // The Litho background colors of the watch page are blended with the video.
    lithoDrawableColorHook { drawable, color ->
        AmbientLightController.getLithoColor(drawable, color)
    }

    // The video time drives the captures of the video surface.
    videoTimeHook { videoTime ->
        AmbientLightController.setVideoTime(videoTime)
    }

    run {
        val touchFeedbackClass = classLoader.loadClass(
            "com.google.android.libraries.youtube.rendering.ui.spec.motion.TouchFeedbackDrawable"
        )

        // The background drawable that the original draw method draws first.
        val backgroundField = touchFeedbackClass.declaredFields
            .first { it.type == Drawable::class.java }
            .apply { isAccessible = true }

        // The touch feedback (ripple) drawable that the original draw method draws last.
        val rippleField = touchFeedbackClass.declaredFields
            .first { it.type != Drawable::class.java && Drawable::class.java.isAssignableFrom(it.type) }
            .apply { isAccessible = true }
        val rippleDrawMethod = rippleField.type.getMethod("draw", Canvas::class.java)

        // Upstream replaces the background draw call of the original method with the
        // extension draw call. At runtime the original method is replaced entirely:
        // the extension draws the background and the touch feedback is drawn directly.
        TouchFeedbackDrawableDrawFingerprint.hookMethod {
            before {
                // Keep the original draw when the enhanced ambient mode is off.
                if (!AmbientLightController.replacesAmbientMode()) return@before

                val touchFeedback = it.thisObject
                val canvas = it.args[0]
                val background = backgroundField.get(touchFeedback)
                if (background != null) {
                    AmbientLightController.drawTouchFeedbackBackground(
                        background as Drawable,
                        canvas as Canvas,
                        touchFeedback as Drawable,
                    )
                }
                rippleDrawMethod.invoke(rippleField.get(touchFeedback), canvas)

                // The background and the touch feedback are both drawn, so the original
                // method body is skipped by setting the result of the void method.
                it.result = null
            }
        }
    }

    // Receives the Ambient mode toggle of the player flyout menu when the app resumes and
    // whenever it changes. Upstream inserts the extension call before the menu item state
    // setter call, passing the new toggle. At runtime the setter is hooked instead.
    ::ambientModeToggleStateMethod.hookMethod {
        before {
            if (it.args[0] == "menu_item_cinematic_lighting") {
                AmbientLightController.setAmbientModeToggle(it.args[1] as? Boolean)
            }
        }
    }

    run {
        // Without this the rows of the playlist panel keep their plain background.
        val controlsOverlayLayout = ResourceUtils.getLayoutIdentifier("size_adjustable_youtube_controls_overlay")
        val insetOverlayViewLayout = inset_overlay_view_layout

        // Upstream inserts the extension call after the controls overlay view of the
        // overlay class is cast to a FrameLayout. At runtime the inflate of the overlay
        // layout is scoped-hooked and the inflated overlay view is passed instead.
        ::ambientControlsOverlayFingerprint.hookMethod(
            scopedHook(
                DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;")
                    .toMember()
            ) {
                after {
                    if (it.args[0] != controlsOverlayLayout) return@after
                    val layout = it.result as ViewGroup
                    val overlayView = layout.findViewById<ViewGroup>(insetOverlayViewLayout) ?: layout
                    AmbientLightController.initialize(overlayView)
                }
            }
        )
    }

    //
    // Bypass ambient mode restrictions.
    //

    // Upstream replaces the calls to PowerManager.isPowerSaveMode() inside the power save
    // mode readers of the Ambient mode wiring with the extension bypass call. At runtime
    // the call is scoped-hooked: the result is transformed only while a reader runs.
    ::powerSaveModeReaderMethods.dexMethodList.forEach { reader ->
        reader.toMethod().hookMethod(
            scopedHook(PowerManager::class.java.getDeclaredMethod("isPowerSaveMode")) {
                after {
                    // Only the call of the reader itself is replaced, not the nested call
                    // of the extension that reads the original power save mode.
                    if (innerDepth == 0) {
                        it.result = AmbientModePatch.bypassAmbientModeRestrictions(
                            it.thisObject as PowerManager
                        )
                    }
                }
            }
        )
    }

    //
    // Force ambient mode, unless it is disabled in the Morphe settings.
    // The flyout menu toggle of the player still turns it on and off.
    //

    run {
        val rendererClass = CinematicContainerRendererFingerprint.declaredClass

        // These values are extracted only on an uncached query. Persist them too,
        // so a release restart does not rely on process-local query side effects.
        val styleMetadata = cache.getString("ambientModeStyleMetadata", null) ?: run {
            ::cinematicPresentationStyleGetter.method
            val style = cinematicContainerStyle
            "${style.hasBitsField.name}|${style.styleHasBitMask}|${style.styleField.name}".also {
                cache.putString("ambientModeStyleMetadata", it)
            }
        }
        val (hasBitsName, hasBitMask, styleName) = styleMetadata.split('|')
        val styleMask = hasBitMask.toInt()
        val rendererHasBitsField = rendererClass.getDeclaredField(hasBitsName)
            .apply { isAccessible = true }
        val rendererStyleField = rendererClass.getDeclaredField(styleName)
            .apply { isAccessible = true }
        val rendererConfigField: Field = ::cinematicContainerConfigField.field
        val configEnabledByDefaultField: Field = ::cinematicContainerEnabledByDefaultField.field

        // The parser of the generated proto class of the host, used to deserialize the
        // default renderer when the /next response has none. Upstream passes the default
        // instance of the renderer to a parse helper of the extension instead.
        val getParserMethod = rendererClass.superclass
            .getDeclaredMethod("getParserForClass", Class::class.java)
        val rendererParser by lazy { getParserMethod.invoke(null, rendererClass)!! }
        val rendererParseMethod by lazy {
            rendererParser.javaClass.methods.first {
                it.parameterCount == 1 && it.parameterTypes[0] == ByteArray::class.java
            }
        }

        val rendererStateConstructor: Constructor<*> = ::rendererWithStateClass.clazz
            .declaredConstructors.first { it.parameterCount == 2 }
        val stateType = rendererStateConstructor.parameterTypes[0]
        val optionalType = rendererStateConstructor.parameterTypes[1]

        val stateValues = stateType.getDeclaredMethod("values").invoke(null) as Array<*>
        val waitingState = stateValues.first { (it as Enum<*>).name == "WAITING" }
        val loadedState = stateValues.first { (it as Enum<*>).name == "LOADED" }

        val optionalIsPresentMethod = optionalType.getMethod("isPresent")
        val optionalGetMethod = optionalType.getMethod("get")
        val optionalEmptyMethod = optionalType.getMethod("empty")
        val optionalOfMethod = optionalType.getMethod("of", Any::class.java)

        fun isPresent(optional: Any) = optionalIsPresentMethod.invoke(optional) == true

        // Upstream generates two helper methods in the RendererWithState class and calls
        // them at the start of its constructor: the first forces the renderer from the
        // loading state, the second sets the loading state from the forced renderer.
        // At runtime the constructor arguments are transformed the same way.
        rendererStateConstructor.hookMethod {
            before {
                var optional = it.args[1]

                optional = if (!AmbientModePatch.disableAmbientMode(true)) {
                    // Ambient mode is disabled, so the renderer of the server is not used either.
                    optionalEmptyMethod.invoke(null)
                } else if (it.args[0] != waitingState) {
                    var renderer = if (isPresent(optional)) {
                        optionalGetMethod.invoke(optional)
                    } else {
                        // The /next response can have no renderer: use the default renderer.
                        rendererParseMethod.invoke(
                            rendererParser,
                            Base64.decode(DEFAULT_CINEMATIC_CONTAINER_RENDERER, 0)
                        )
                    }

                    // The /next response can also have a static color style (the background
                    // does not change with the video) or ambient mode off by default.
                    // Override what the server decides.
                    rendererHasBitsField.setInt(
                        renderer,
                        rendererHasBitsField.getInt(renderer) or styleMask
                    )
                    rendererStyleField.setInt(renderer, PRESENTATION_STYLE_DYNAMIC_BLURRED)

                    // Only the default: the flyout menu toggle is saved separately and still applies.
                    val config = rendererConfigField.get(renderer)
                    if (config != null) {
                        configEnabledByDefaultField.setBoolean(config, true)
                    }

                    optionalOfMethod.invoke(null, renderer)
                } else {
                    // No /next response yet.
                    optional
                }

                it.args[1] = optional

                // The state is LOADED when the forced renderer is present.
                it.args[0] = if (isPresent(optional)) loadedState else it.args[0]
            }
        }
    }

    //
    // Black background experiment: ambient mode is always inactive, whatever the
    // CinematicContainerRenderer, power save mode or flyout menu toggle say.
    // Turn it off, so the watch page uses the same ambient mode and layout as
    // outside the experiment.
    //

    insertLiteralOverride(AMBIENT_MODE_BLACK_BACKGROUND_FLAG, false)

    //
    // Disable fullscreen ambient mode.
    //

    // Upstream transforms the color argument of the background color call that follows
    // the ambient fullscreen color. At runtime the call is scoped-hooked instead.
    SetFullScreenBackgroundColorFingerprint.hookMethod(
        scopedHook(View::class.java.getDeclaredMethod("setBackgroundColor", Int::class.javaPrimitiveType)) {
            before {
                if (innerDepth == 0 && it.thisObject === outerParam.thisObject) {
                    it.args[0] = AmbientModePatch.getFullScreenBackgroundColor(it.args[0] as Int)
                }
            }
        }
    )
}
