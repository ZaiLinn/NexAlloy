package io.github.nexalloy.morphe.music.interaction.crossfade

import io.github.nexalloy.ApkContext
import io.github.nexalloy.FilePathArgumentsProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.luckypray.dexkit.DexKitBridge
import java.nio.file.Path

/**
 * Temporary validation of the runtime discovery chain against the host APK.
 * Not part of the fingerprint report.
 */
@ParameterizedClass
@ArgumentsSource(FilePathArgumentsProvider::class)
class CrossfadeDiscoveryTest(val apkPath: Path) {
    val context = ApkContext(apkPath.toString())
    val dexkit: DexKitBridge = context.dexkit

    companion object {
        @AfterAll
        @JvmStatic
        fun close() = Unit
    }

    private fun crossfadeApk(): Boolean =
        apkPath.toString().contains("com.google.android.apps.youtube.music")

    @Test
    fun `discovery chain resolves`() {
        if (!crossfadeApk()) return
        context.setupCurrentThread()
        val bridge = dexkit
        fun <T> run(name: String, block: DexKitBridge.() -> T) {
            try {
                val result = bridge.block()
                println("DISCOVERY OK  $name -> $result")
            } catch (e: Throwable) {
                throw AssertionError("DISCOVERY FAIL $name: $e", e)
            }
        }

        run("coordinatorClass") { crossfadeCoordinatorClass(bridge).descriptor }
        run("medialibPlayerClass") { crossfadeMedialibPlayerClass(bridge).descriptor }
        run("videoToggleClass") { crossfadeVideoToggleClass(bridge).descriptor }
        run("exoPlayerImplClass") { crossfadeExoPlayerImplClass(bridge).descriptor }
        run("exoPlayerField") { crossfadeExoPlayerField(bridge).descriptor }
        run("sessionField") { crossfadeSessionField(bridge).descriptor }
        run("factoryField") { crossfadeFactoryField(bridge).descriptor }
        run("factoryMethod") { crossfadeFactoryMethod(bridge).descriptor }
        run("loadControlField") { crossfadeLoadControlField(bridge).descriptor }
        run("sharedStateField") { crossfadeSharedStateField(bridge).descriptor }
        run("sharedCallbackField") { crossfadeSharedCallbackField(bridge).descriptor }
        run("guardField") { crossfadeGuardField(bridge).descriptor }
        run("timelineField") { crossfadeTimelineField(bridge).descriptor }
        run("videoSurfaceField") { crossfadeVideoSurfaceField(bridge).descriptor }
        run("videoSurfaceExoField") { crossfadeVideoSurfaceExoField(bridge).descriptor }
        run("playerVolumeField") { crossfadePlayerVolumeField(bridge).descriptor }
        run("playerInterfaceClass") { crossfadePlayerInterfaceClass(bridge).descriptor }
        run("listenerWrapperField") { crossfadeListenerWrapperField(bridge).descriptor }
        run("listenerSetAdd") { crossfadeListenerSetAdd(bridge).descriptor }
        run("listenerSetInWrapper") { crossfadeListenerSetInWrapper(bridge).descriptor }
        run("listenerSetRemove") { crossfadeListenerSetRemove(bridge).descriptor }
        run("directListenerSetField") { crossfadeDirectListenerSetField(bridge).descriptor }
        run("directListenerType") { crossfadeDirectListenerType(bridge).descriptor }
        run("coordinatorListenerField") { crossfadeCoordinatorListenerField(bridge).descriptor }
        run("cwhAddListener") { crossfadeCwhAddListener(bridge).descriptor }
        run("coordinatorCwhListenerField") { crossfadeCoordinatorCwhListenerField(bridge).descriptor }
        run("exoCwhField") { crossfadeExoCwhField(bridge).descriptor }
        run("cqbField") { crossfadeCqbField(bridge).descriptor }
        run("dltFieldOnShared") { crossfadeDltFieldOnShared(bridge).descriptor }
        run("dltFieldOnExo") { crossfadeDltFieldOnExo(bridge).descriptor }
        run("playbackInfoClass") { crossfadePlaybackInfoClass(bridge).descriptor }
        run("playbackInfoField") { crossfadePlaybackInfoField(bridge).descriptor }
        run("playbackStateField") { crossfadePlaybackStateField(bridge).descriptor }
        run("currentPositionMethod") { crossfadeCurrentPositionMethod(bridge).descriptor }
        run("durationMethod") { crossfadeDurationMethod(bridge).descriptor }
        run("playerChainField") { crossfadePlayerChainField(bridge).descriptor }
        run("delegateFields") { crossfadeDelegateFields(bridge).map { it.descriptor } }
        run("medialibPlayNextInQueue") { crossfadeMedialibPlayNextInQueue(bridge).descriptor }
        run("stateProviderField") { crossfadeStateProviderField(bridge).descriptor }
        run("stateGetter") { crossfadeStateGetter(bridge).descriptor }
        run("isAudioModeMethod") { crossfadeIsAudioModeMethod(bridge).descriptor }
        run("setStateMethod") { crossfadeSetStateMethod(bridge).descriptor }
        run("broadcasterField") { crossfadeBroadcasterField(bridge).descriptor }
        run("broadcastMethod") { crossfadeBroadcastMethod(bridge).descriptor }
        run("silentSetMethod") { crossfadeSilentSetMethod(bridge).descriptor }
        run("releaseRunnableClass") { crossfadeReleaseRunnableClass(bridge).descriptor }
        context.close()
    }
}
