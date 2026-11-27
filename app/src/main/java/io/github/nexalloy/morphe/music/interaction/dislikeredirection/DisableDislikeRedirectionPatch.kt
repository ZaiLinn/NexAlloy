package io.github.nexalloy.morphe.music.interaction.dislikeredirection

import app.morphe.extension.music.patches.DisableDislikeRedirectionPatch
import io.github.nexalloy.morphe.music.misc.playservice.is_9_35_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Field

/**
 * Runtime port of "Disable dislike redirection".
 *
 * Upstream inserts a guard right before the queue navigation of each dislike handler,
 * which skips the navigation when the setting is on. The runtime module cannot skip a
 * single inner call from a hook of the outer method, so instead the concrete
 * implementations of the queue navigation method are hooked with scoped hooks installed
 * on the dislike handlers: the navigation is only skipped while a dislike handler runs,
 * and only for the NEXT command it navigates with. Navigating elsewhere (the next
 * button, autoplay at the track end) is untouched.
 */
val disableDislikeRedirectionPatch = patch(
    name = "Disable dislike redirection",
    description = "Adds an option to prevent skipping to the next track when the dislike " +
            "button is pressed.",
) {
    dependsOn(versionCheckPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_disable_dislike_redirection", summary = true),
    )

    // The dislike command handlers and the navigation implementations they call are
    // only resolved for 9.35+; older versions are left untouched (fail-closed).
    if (!is_9_35_or_greater) return@patch

    // The notification like button listener and the dislike command handlers,
    // each with a single queue navigation reached only on the dislike path.
    val handlerMethods = buildList {
        add(NotificationLikeButtonOnClickListenerFingerprint.method)
        addAll(::dislikeSkipMethods.dexMethodList.map { it.toMethod() })
    }
    val navigateMethods = ::dislikeNavigateMethods.dexMethodList.map { it.toMethod() }

    // Check the navigation argument against the NEXT command type, so only the
    // skip-to-next of the dislike path is skipped.
    val commandFactory = ::dislikeCommandFactoryMethod.method
    val commandTypeClass = commandFactory.parameterTypes[0]
    val nextType = commandTypeClass.enumConstants
        .filterIsInstance<Enum<*>>()
        .first { it.name == "NEXT" }
    val commandTypeField: Field = navigateMethods.first().parameterTypes[0].declaredFields
        .first { it.type == commandTypeClass }
        .apply { isAccessible = true }

    handlerMethods.forEach { handler ->
        navigateMethods.forEach { navigate ->
            handler.hookMethod(scopedHook(navigate) {
                before {
                    if (it.args[0] != null &&
                        commandTypeField.get(it.args[0]) == nextType &&
                        DisableDislikeRedirectionPatch.disableDislikeRedirection()
                    ) {
                        it.result = null
                    }
                }
            })
        }
    }
}
