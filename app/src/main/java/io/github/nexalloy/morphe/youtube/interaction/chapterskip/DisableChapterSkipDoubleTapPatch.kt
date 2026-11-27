package io.github.nexalloy.morphe.youtube.interaction.chapterskip

import app.morphe.extension.youtube.patches.DisableDoubleTapActionsPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_11_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * The seek source enum class, such as SEEK_TO_NEXT_CHAPTER.
 */
internal val seekTypeEnumMethod = findMethodDirect {
    SeekTypeEnumFingerprint.run()
}

/**
 * The two-finger double tap info method that returns the seek source.
 */
internal val doubleTapInfoGetSeekSourceMethod = findMethodDirect {
    doubleTapInfoGetSeekSourceFingerprint(
        seekTypeEnumMethod().declaredClass!!.descriptor
    ).run()
}

/**
 * The method that reads the server config value that turns on the two-finger
 * double tap chapter skip.
 */
internal val doubleTapChapterSkipConfigMethod = findMethodDirect {
    DoubleTapChapterSkipConfigFingerprint.matchAll()
        .single { it.method.paramTypes.getOrNull(2)?.descriptor == "Landroid/os/Handler;" }
        .method
}

/**
 * The field that stores the server config value that turns on the two-finger
 * double tap chapter skip.
 */
internal val chapterSkipConfigField = findFieldDirect {
    DoubleTapChapterSkipConfigFingerprint.matchAll()
        .single { it.method.paramTypes.getOrNull(2)?.descriptor == "Landroid/os/Handler;" }
        .instructionMatches.first().instruction.fieldRef!!
}

private fun Field.setBooleanOf(instance: Any?, value: Boolean) {
    if (Modifier.isStatic(modifiers)) setBoolean(null, value) else setBoolean(instance, value)
}

private fun Field.getBooleanOf(instance: Any?): Boolean =
    if (Modifier.isStatic(modifiers)) getBoolean(null) else getBoolean(instance)

val DisableChapterSkipDoubleTap = patch(
    name = "Disable double tap actions",
    description = "Adds an option to disable player double tap gestures.",
) {
    dependsOn(VersionCheck)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_disable_chapter_skip_double_tap", summary = true),
    )

    // Force isChapterSeek flag to false.
    ::doubleTapInfoGetSeekSourceMethod.hookMethod {
        before {
            it.args[0] = DisableDoubleTapActionsPatch.disableDoubleTapChapters(it.args[0] as Boolean)
        }
    }

    DoubleTapInfoCtorFingerprint.constructor.hookMethod {
        before {
            it.args[2] = DisableDoubleTapActionsPatch.disableDoubleTapChapters(it.args[2] as Boolean)
        }
    }

    // Some accounts get the chapter skip turned off by the server config.
    // Upstream transforms the config value where it is read. At runtime the stored
    // config field is transformed before the method reads it.
    val configField = ::chapterSkipConfigField.field

    ::doubleTapChapterSkipConfigMethod.method.hookMethod {
        before {
            val configHolder = listOf(it.thisObject, *it.args)
                .firstOrNull { it != null && configField.declaringClass.isInstance(it) }
                ?: return@before

            configField.setBooleanOf(
                configHolder,
                DisableDoubleTapActionsPatch.enableDoubleTapChaptersConfig(
                    configField.getBooleanOf(configHolder)
                )
            )
        }
    }

    // A/B flag that turns off the two-finger double tap chapter skip (21.11+).
    if (is_21_11_or_greater) {
        insertLiteralOverride(
            45767151L,
            DisableDoubleTapActionsPatch::disableDoubleTapChaptersFeatureFlag
        )
    }
}
