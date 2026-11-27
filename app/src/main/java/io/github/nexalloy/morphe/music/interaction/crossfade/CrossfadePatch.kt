package io.github.nexalloy.morphe.music.interaction.crossfade

import android.content.Context
import android.content.Intent
import android.view.Surface
import app.morphe.extension.music.settings.Settings
import app.morphe.extension.music.settings.preference.CrossfadeCurvePreference
import app.morphe.extension.music.settings.preference.CrossfadeEnabledPreference
import app.morphe.extension.shared.Logger
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.findClass
import io.github.nexalloy.patch
import io.github.nexalloy.morphe.music.misc.playservice.is_9_19_or_greater
import io.github.nexalloy.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.shared.YOUTUBE_MUSIC_MAIN_ACTIVITY_CLASS_TYPE
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

private typealias CrossfadeExtension = app.morphe.extension.music.patches.CrossfadePatch

val Crossfade = patch(
    name = "Crossfade",
    description = "Adds a true dual-player crossfade between consecutive tracks.",
) {
    dependsOn(versionCheckPatch)

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_crossfade_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference(
                    key = "morphe_music_crossfade_enabled",
                    tag = CrossfadeEnabledPreference::class.java
                ),
                ListPreference("morphe_music_crossfade_curve"),
                NonInteractivePreference(
                    key = "morphe_music_crossfade_curve_preview",
                    summaryKey = null,
                    tag = CrossfadeCurvePreference::class.java,
                ),
                ListPreference("morphe_music_crossfade_duration"),
                SwitchPreference("morphe_music_crossfade_on_skip", summary = true),
                SwitchPreference("morphe_music_crossfade_on_auto_advance", summary = true),
                SwitchPreference("morphe_music_crossfade_session_control", summary = true),
                screenInfoPreferenceCategory(
                    key = "morphe_music_crossfade_about",
                    preferences = setOf(
                        NonInteractivePreference("morphe_music_crossfade_about_how"),
                        NonInteractivePreference("morphe_music_crossfade_about_quirks"),
                        NonInteractivePreference("morphe_music_crossfade_about_unsupported"),
                        NonInteractivePreference("morphe_music_crossfade_about_credit")
                    )
                )
            )
        )
    )

    // The extension reads its enabled flag once at class load, and changing the
    // setting restarts the app, so match that snapshot and install nothing when
    // the crossfade is off or unavailable. The player lifecycle hooks below run
    // per track change and a no-op hook still pays the dispatch cost.
    val crossfadeEnabled = runCatching {
        Settings.CROSSFADE_ENABLED.get() && Settings.CROSSFADE_ENABLED.isAvailable()
    }.getOrDefault(false)

    // The extension requires a spoofed stream client without SABR, so without
    // the stream spoofing feature of the app the crossfade stays off.
    if (!crossfadeEnabled || !is_9_19_or_greater) return@patch

    val runtime = runCatching { buildCrossfadeRuntime() }.getOrElse { error ->
        Logger.printException({ "Crossfade player structure not found, hooks skipped" }, error)
        return@patch
    }

    // Fail-closed helper for the optional hooks.
    fun hookRegion(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            Logger.printException({ "Crossfade hook failed: $name" }, it)
        }
    }

    // region Player lifecycle of the MedialibPlayer.

    // Returns true to block the native call, which is then re-invoked
    // internally so the next track loads onto the new player.
    MusicStopVideoFingerprint.hookMethod {
        before {
            if (CrossfadeExtension.onBeforeStopVideo(
                    runtime.wrapMedialibPlayer(it.thisObject), it.args[0] as Int
                )
            ) it.result = null
        }
    }

    MusicPauseVideoFingerprint.hookMethod {
        before { CrossfadeExtension.onPauseVideo() }
    }

    MusicPlayVideoFingerprint.hookMethod {
        before { CrossfadeExtension.onPlayVideo(runtime.wrapMedialibPlayer(it.thisObject)) }
    }

    // The descriptor is cached so repeat-one can reload the same track.
    MusicLoadVideoFingerprint.hookMethod {
        before {
            CrossfadeExtension.onBeforeLoadVideo(runtime.wrapMedialibPlayer(it.thisObject), it.args[0])
        }
    }

    // endregion

    // region Player swap of the coordinator.

    MusicPlayNextInQueueFingerprint.hookMethod {
        before {
            if (CrossfadeExtension.onBeforePlayNext(runtime.wrapCoordinator(it.thisObject))) {
                it.result = null
            }
        }
    }

    // endregion

    // region Audio/video mode.

    MusicAudioVideoToggleFingerprint.hookMethod {
        before {
            if (CrossfadeExtension.shouldBlockVideoToggle(runtime.wrapVideoToggle(it.thisObject))) {
                it.result = null
            }
        }
    }

    // The toggle hook only runs when the user taps the toggle, so the toggle
    // instance is captured when it is created as well.
    hookRegion("video toggle capture") {
        val toggleClass = MusicAudioVideoToggleFingerprint.declaredClass
        toggleClass.declaredConstructors.maxByOrNull { it.parameterTypes.size }?.hookMethod {
            after { CrossfadeExtension.onNbaCreated(runtime.wrapVideoToggle(it.thisObject)) }
        }
    }

    // endregion

    // region Repeat state, sleep timer and queue dismissal.

    // Optional: without it repeat-one is not detected and the queue advances as usual.
    hookRegion("loop state") {
        MusicLoopStateAdapterFingerprint.hookMethod {
            before { CrossfadeExtension.onLoopStateChanged(it.args[0]) }
        }
    }

    // Optional: without it the end of song sleep timer keeps being skipped (#2017).
    hookRegion("sleep timer state") {
        MusicSleepTimerStateChangedFingerprint.hookMethod {
            before { CrossfadeExtension.onSleepTimerStateChanged(it.args[0]) }
        }
    }

    // Optional: without it the dismissal falls back to the slower poll-STATE_IDLE recovery (#1671).
    hookRegion("queue dismissal") {
        MusicHandleDismissWatchEventFingerprint.hookMethod {
            before { CrossfadeExtension.onQueueDismissed() }
        }
    }

    // endregion

    // region Activity lifecycle.

    // Playback goes on in the foreground service, so neither the monitor nor a
    // running crossfade is stopped here (#1311, #1442).
    hookRegion("activity lifecycle") {
        val activityClass = YOUTUBE_MUSIC_MAIN_ACTIVITY_CLASS_TYPE.findClass(classLoader)
        for ((methodName, extensionMethod) in listOf(
            "onStart" to { CrossfadeExtension.onActivityStart() },
            "onStop" to { CrossfadeExtension.onActivityStop() },
            // The process can outlive the activity through the foreground service,
            // so static player references must not leak into the next activity.
            "onDestroy" to { CrossfadeExtension.onActivityDestroy() },
        )) {
            activityClass.getDeclaredMethods().single {
                it.name == methodName && it.parameterTypes.isEmpty()
            }.hookMethod {
                before { extensionMethod() }
            }
        }
    }

    // endregion

    // region Audio effect session closes.

    // The app closes the audio effect session of the old player as soon as the
    // new player reports its own, which detaches equalizers from a track that
    // still fades out. The close broadcast of the session helper is rerouted
    // through the extension, which holds it back while a crossfade runs.
    hookRegion("audio effect session close") {
        val sendBroadcast = XposedHelpers.findMethodExact(
            "android.app.ContextImpl", null, "sendBroadcast", Intent::class.java
        )
        // The broadcasts the extension replays run the original, so the
        // reentrant call is recognized by its intent.
        val replayed = ThreadLocal<Intent?>()
        MusicCloseAudioEffectSessionFingerprint.hookMethod(
            scopedHook(sendBroadcast to {
                before {
                    val intent = it.args[0] as? Intent ?: return@before
                    if (replayed.get() === intent) return@before
                    replayed.set(intent)
                    try {
                        CrossfadeExtension.closeAudioEffectSession(it.thisObject as Context, intent)
                    } finally {
                        replayed.set(null)
                    }
                    it.result = null
                }
            })
        )
    }

    // endregion

    // region Release of an outgoing player.

    // Releasing a player also releases its analytics collector listener set,
    // which the incoming player shares, and the MediaSession would stop getting
    // events. 9.28+ inlines the collector release into a Runnable the release
    // posts on a handler, so the runnable created while the extension holds
    // suppressCwhU around the release is suppressed. The flag is read when the
    // runnable is created, like upstream's filterAnalyticsRelease, because the
    // handler runs it after the release has returned and the flag is back to
    // false (9.46.51: `bhu`, its case 8 captures the `cbe`).
    hookRegion("cwh release suppression") {
        val runnableClass = runtime.releaseRunnableClass
        val receiverField = runtime.releaseRunnableReceiverField
        if (runnableClass != null && receiverField != null) {
            val suppressed = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
            for (constructor in runnableClass.declaredConstructors) {
                constructor.hookMethod {
                    after {
                        if (!CrossfadeExtension.suppressCwhU) return@after
                        val receiver = runCatching { receiverField.get(it.thisObject) }.getOrNull()
                            ?: return@after
                        if (runtime.sharedCallbackClass.isInstance(receiver)) {
                            synchronized(suppressed) { suppressed.add(it.thisObject) }
                        }
                    }
                }
            }
            runnableClass.getDeclaredMethod("run").hookMethod {
                before {
                    if (synchronized(suppressed) { suppressed.remove(it.thisObject) }) {
                        it.result = null
                    }
                }
            }
        } else {
            Logger.printInfo { "Crossfade: analytics release runnable not found" }
        }
    }

    // endregion
}

/**
 * Builds the runtime from the discovered player structure. Every member was
 * anchor-verified against the host, but a structural change of a future
 * version must fail closed instead of hooking the wrong member.
 */
private fun io.github.nexalloy.PatchExecutor.buildCrossfadeRuntime(): CrossfadeRuntime {
    // The release runnable and the coordinator's MediaSession listener are
    // optional parts of the player swap; everything else is required.
    val releaseRunnableClass = runCatching { ::crossfadeReleaseRunnableClass.clazz }.getOrNull()
    // The runnable also captures its switch case id in an int field, so the
    // receiver is its only non-primitive field.
    val releaseRunnableReceiverField = releaseRunnableClass
        ?.declaredFields?.firstOrNull {
            !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.type.isPrimitive
        }
    val coordinatorCwhListenerField = runCatching {
        ::crossfadeCoordinatorCwhListenerField.field
    }.getOrNull()
    val cwhAddListenerMethod = runCatching { ::crossfadeCwhAddListener.method }.getOrNull()
    val exoCwhField = runCatching { ::crossfadeExoCwhField.field }.getOrNull()
    val guardField = runCatching { ::crossfadeGuardField.field }.getOrNull()
    val directListenerSetField = runCatching { ::crossfadeDirectListenerSetField.field }.getOrNull()
    val silentSetMethod = runCatching { ::crossfadeSilentSetMethod.method }.getOrNull()

    val exoPlayerImplClass = ::crossfadeExoPlayerImplClass.clazz
    val playerInterface = ::crossfadePlayerInterfaceClass.clazz

    fun methodInHierarchy(start: Class<*>, name: String, vararg parameters: Class<*>): Method {
        var current: Class<*>? = start
        while (current != null) {
            XposedHelpers.findMethodExactIfExists(current, name, *parameters)?.let { return it }
            current = current.superclass
        }
        error("Method $name(${parameters.joinToString()}) not found in ${start.name}")
    }

    // The player interface declares one void method of each of these signatures.
    val setVolumeName = playerInterface.declaredMethods.single {
        it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(Float::class.java))
    }.name
    val setPlayWhenReadyName = playerInterface.declaredMethods.single {
        it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(Boolean::class.java))
    }.name
    val setVideoSurfaceName = playerInterface.declaredMethods.single {
        it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(Surface::class.java))
    }.name
    val releaseName = MusicExoPlayerReleaseFingerprint.method.name

    val stateType = ::crossfadeStateGetter.method.returnType
    val stateConstants = stateType.enumConstants!!.toList()

    return CrossfadeRuntime(
        coordinatorClass = ::crossfadeCoordinatorClass.clazz,
        sharedCallbackClass = ::crossfadeSharedCallbackField.field.type,
        exoPlayerField = ::crossfadeExoPlayerField.field,
        sessionField = ::crossfadeSessionField.field,
        loadControlField = ::crossfadeLoadControlField.field,
        sharedStateField = ::crossfadeSharedStateField.field,
        sharedCallbackField = ::crossfadeSharedCallbackField.field,
        videoSurfaceField = ::crossfadeVideoSurfaceField.field,
        playerVolumeField = ::crossfadePlayerVolumeField.field,
        coordinatorListenerField = ::crossfadeCoordinatorListenerField.field,
        coordinatorCwhListenerField = coordinatorCwhListenerField,
        factoryField = ::crossfadeFactoryField.field,
        factoryMethod = ::crossfadeFactoryMethod.method,
        guardField = guardField,
        timelineField = ::crossfadeTimelineField.field,
        cqbField = ::crossfadeCqbField.field,
        dltFieldOnShared = ::crossfadeDltFieldOnShared.field,
        exoCwhField = exoCwhField,
        cwhAddListenerMethod = cwhAddListenerMethod,
        listenerWrapperField = ::crossfadeListenerWrapperField.field,
        listenerSetInWrapper = ::crossfadeListenerSetInWrapper.field,
        listenerSetRemoveMethod = ::crossfadeListenerSetRemove.method,
        directListenerSetField = directListenerSetField,
        dltFieldOnExo = ::crossfadeDltFieldOnExo.field,
        playbackInfoField = ::crossfadePlaybackInfoField.field,
        playbackStateField = ::crossfadePlaybackStateField.field,
        currentPositionMethod = ::crossfadeCurrentPositionMethod.method,
        durationMethod = ::crossfadeDurationMethod.method,
        setVolumeMethod = methodInHierarchy(exoPlayerImplClass, setVolumeName, Float::class.java),
        setPlayWhenReadyMethod = methodInHierarchy(exoPlayerImplClass, setPlayWhenReadyName, Boolean::class.java),
        setVideoSurfaceMethod = methodInHierarchy(exoPlayerImplClass, setVideoSurfaceName, Surface::class.java),
        releaseMethod = methodInHierarchy(exoPlayerImplClass, releaseName),
        releaseRunnableClass = releaseRunnableClass,
        releaseRunnableReceiverField = releaseRunnableReceiverField,
        videoSurfaceExoField = ::crossfadeVideoSurfaceExoField.field,
        playerChainField = ::crossfadePlayerChainField.field,
        delegateFields = ::crossfadeDelegateFields.fieldList.map { it.declaringClass to it },
        playNextInQueueMethod = ::crossfadeMedialibPlayNextInQueue.method,
        loadVideoMethod = MusicLoadVideoFingerprint.method,
        stateProviderField = ::crossfadeStateProviderField.field,
        getStateMethod = ::crossfadeStateGetter.method,
        isAudioModeMethod = ::crossfadeIsAudioModeMethod.method,
        setStateMethod = ::crossfadeSetStateMethod.method,
        broadcasterField = ::crossfadeBroadcasterField.field,
        silentSetMethod = silentSetMethod,
        audioPreferredState = stateConstants[0],
        videoPreferredState = stateConstants[1],
    )
}
