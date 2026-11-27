package io.github.nexalloy.morphe.youtube.interaction.continuewatching

import app.morphe.extension.youtube.patches.DisableContinueWatchingPromptPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import java.lang.reflect.Member

/**
 * The autoplay overlay method that compares the time since the last user interaction
 * against the inactivity limit.
 */
internal val autoplayInactivityLimitMethod = findMethodDirect {
    AutoplayInactivityLimitFingerprint.run()
}

/**
 * The playback progress method that compares the time since the last user interaction
 * against the inactivity limit.
 */
internal val inactivityLimitMethod = findMethodDirect {
    InactivityLimitFingerprint.run()
}

/**
 * The method that returns whether something holds an autoplay block when the end screen
 * decides between the countdown and the suggested video without countdown.
 */
internal val autoplayBlockedCheckMethod = findMethodDirect {
    AutoplayInactivityLimitFingerprint.instructionMatches[4].instruction.methodRef!!
}

/**
 * An inactivity limit that is practically never reached, used when the prompt is
 * disabled to keep autoplay going.
 */
private const val NEVER_REACHED_INACTIVITY_LIMIT_MS = 100L * 365 * 24 * 60 * 60 * 1000 // 100 years

private fun Member.matchesStackFrame(element: StackTraceElement): Boolean =
    declaringClass.name == element.className && name == element.methodName

/**
 * True while the autoplay overlay method runs. Used to scope the hook of the
 * autoplay block check to that method, matching the upstream instruction-level transform.
 */
private val inAutoplayInactivityLimitMethod = ThreadLocal.withInitial { false }

val DisableContinueWatchingPrompt = patch(
    name = "Disable continue watching prompt",
    description = "Adds an option to keep autoplay going instead of pausing with a " +
            "\"Continue watching\" prompt after a period of inactivity, " +
            "or to always show the autoplay countdown at the end of the video.",
) {
    dependsOn(VersionCheck)

    PreferenceScreen.PLAYER.addPreferences(
        ListPreference("morphe_continue_watching_prompt")
    )

    val autoplayMethod = ::autoplayInactivityLimitMethod.method
    val inactivityMethod = ::inactivityLimitMethod.method

    autoplayMethod.hookMethod {
        before { inAutoplayInactivityLimitMethod.set(true) }
        after { inAutoplayInactivityLimitMethod.set(false) }
    }

    // Upstream transforms the result of the autoplay block check where it is used.
    // At runtime the check method has its result transformed while the
    // autoplay overlay method runs.
    ::autoplayBlockedCheckMethod.hookMethod {
        after {
            if (inAutoplayInactivityLimitMethod.get() == true) {
                it.result = DisableContinueWatchingPromptPatch.adjustAutoplayBlocked(it.result as Boolean)
            }
        }
    }

    // Upstream transforms the inactivity comparison results where they are compared with
    // the branch instructions. At runtime the equivalent is achieved by making the server
    // provided inactivity limit practically unreachable, but only for the two
    // comparison methods: the transform forces the comparison to "limit not reached",
    // and a comparison against an unreachable limit never reaches the limit either.
    java.util.concurrent.TimeUnit::class.java
        .getDeclaredMethod("toMillis", Long::class.javaPrimitiveType).hookMethod {
            before {
                if (!DisableContinueWatchingPromptPatch.disableConfirmDialog()) return@before
                if (Thread.currentThread().stackTrace.any { frame ->
                        autoplayMethod.matchesStackFrame(frame) ||
                                inactivityMethod.matchesStackFrame(frame)
                    }
                ) {
                    it.result = NEVER_REACHED_INACTIVITY_LIMIT_MS
                }
            }
        }

    // The confirm dialog is never shown when the prompt is disabled.
    // Upstream injects an early return of false at the start of the method.
    (if (is_21_30_or_greater) AutoplayConfirmDialogEnabledFingerprint
    else AutoplayConfirmDialogEnabledLegacyFingerprint).hookMethod {
        before {
            if (DisableContinueWatchingPromptPatch.disableConfirmDialog()) {
                it.result = false
            }
        }
    }
}
