package io.github.nexalloy.morphe.youtube.misc.fix.mediasessionstop

import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.patches.FixMediaSessionStopPatch
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.nexalloy.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field

/**
 * Fixes https://github.com/MorpheApp/morphe-patches/issues/4762.
 *
 * All players share one media session and one background playback. A player that did not start
 * the session can stop them, for example the player of the comment replies panel when the panel
 * is closed. The media notification goes away, and the background player service is stopped.
 * When the app goes to the background, the service is started again from the background, and
 * Android mutes the audio of a service started from the background, while the video keeps playing.
 *
 * The player that starts the session is its owner, and a session stop or a background playback
 * stop from another player is ignored. 21.42+ has an A/B flag that does the same for the
 * session stop only.
 *
 * Upstream inserts an invoke-static with the player register right before the player reads the
 * session object from its field at each call site. Instruction insertion is not possible at
 * runtime, so the call sites (the caller methods) are hooked instead: while a caller runs, the
 * player is resolved as the object that holds the called session or background playback object
 * in one of the player fields, which is the same relationship the bytecode reads express.
 */
internal val fixMediaSessionStopPatch = patch(
    description = "Prevents a player that did not start the shared media session or the background playback from stopping it."
) {
    dependsOn(VersionCheck)

    val playerFields = ::mediaSessionPlayerFields.fieldList
    if (playerFields.isEmpty()) {
        Logger.printException { "FixMediaSessionStop: no player fields found" }
        return@patch
    }

    /**
     * The frames of the hooked caller methods that are currently running on this thread,
     * outermost first.
     */
    val callerFrames = ThreadLocal<ArrayDeque<MethodHookParam>?>()

    fun pushFrame(param: MethodHookParam) {
        val frames = callerFrames.get()
            ?: ArrayDeque<MethodHookParam>().also { callerFrames.set(it) }
        frames.addLast(param)
    }

    fun popFrame() {
        val frames = callerFrames.get() ?: return
        frames.removeLastOrNull()
        if (frames.isEmpty()) callerFrames.remove()
    }

    /**
     * The player that calls the method with [receiver], resolved from the objects of the
     * running caller frames, or null if the call is not made by a player.
     */
    fun resolvePlayer(receiver: Any?): Any? {
        if (receiver == null) return null
        val frames = callerFrames.get() ?: return null
        for (index in frames.indices.reversed()) {
            val frame = frames[index]
            for (candidate in listOf(frame.thisObject, *frame.args)) {
                if (candidate != null && holdsPlayerField(candidate, receiver, playerFields)) {
                    return candidate
                }
            }
        }
        return null
    }

    fun hookCallers(callers: List<DexMethod>) {
        callers.forEach { caller ->
            caller.toMethod().hookMethod {
                before { pushFrame(it) }
                after { popFrame() }
            }
        }
    }

    // The player that starts the session is its owner.
    hookCallers(::sessionStartCallers.dexMethodList)
    MediaSessionStartFingerprint.hookMethod {
        before { param ->
            resolvePlayer(param.thisObject)?.let { FixMediaSessionStopPatch.setMediaSessionOwner(it) }
        }
    }

    // A background playback stop from a player that is not the owner is ignored.
    hookCallers(::backgroundPlaybackStopCallers.dexMethodList)
    BackgroundPlaybackStopFingerprint.hookMethod {
        before { param ->
            resolvePlayer(param.thisObject)
                ?.let { FixMediaSessionStopPatch.setBackgroundPlaybackStoppingPlayer(it) }
            if (!FixMediaSessionStopPatch.isBackgroundPlaybackStopAllowed()) {
                param.result = null
            }
        }
    }

    if (is_21_42_or_greater) {
        // The A/B flag does the same for the session stop.
        insertLiteralOverride(MEDIA_SESSION_OWNER_FEATURE_FLAG, true)
        return@patch
    }

    // A session stop from a player that is not the owner is ignored.
    hookCallers(::sessionStopCallers.dexMethodList)
    MediaSessionStopFingerprint.hookMethod {
        before { param ->
            resolvePlayer(param.thisObject)
                ?.let { FixMediaSessionStopPatch.setMediaSessionStoppingPlayer(it) }
            if (!FixMediaSessionStopPatch.isMediaSessionStopAllowed(param.args[0] as Boolean)) {
                param.result = null
            }
        }
    }
}

private fun holdsPlayerField(candidate: Any, receiver: Any?, fields: List<Field>): Boolean =
    fields.any { field -> runCatching { field.get(candidate) }.getOrNull() === receiver }
