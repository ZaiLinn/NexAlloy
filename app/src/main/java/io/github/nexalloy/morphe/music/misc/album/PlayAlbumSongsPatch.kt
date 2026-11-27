package io.github.nexalloy.morphe.music.misc.album

import app.morphe.extension.music.patches.album.PlayAlbumSongsPatch
import app.morphe.extension.shared.Logger
import io.github.nexalloy.callMethod
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import java.lang.reflect.Method

/**
 * Runtime port of the upstream "Play album songs" patch. The upstream patch injects smali that
 * fills the protobuf bridge stubs of the extension and rewrites two host entry points:
 *
 *  - The watch next model constructor (`Lwhm;-><init>(Lamkl;)V`), which every watch next response
 *    the app shows is built into, changes the album queue before the app builds anything from it.
 *  - The command resolver (`Lvxv;->c(Lakgm;Ljava/util/Map;)V`), which routes every command a tap
 *    sends, changes a tapped album track before the app starts playing it.
 *
 * The bridge stubs are private static methods of the extension that only throw. This port hooks
 * them (the module classes run inside the host process) and provides the same bodies the patcher
 * generates, using the host's own protobuf runtime resolved by the fingerprints of this package.
 */
val playAlbumSongsPatch = patch(
    name = "Play album songs",
    description = "Adds an option to play the songs of albums instead of their music videos.",
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_music_play_album_songs", summary = true)
    )

    // region Fail-closed helper.

    fun hookRegion(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            Logger.printException({ "Play album songs hook failed: $name" }, it)
        }
    }

    // endregion

    // region Protobuf bridge stubs.

    // The module and the host use separate protobuf implementations, so the extension reads and
    // writes the messages of the host, parsed with the host's own extension registry so the watch
    // endpoint is not parsed as an unknown field.
    hookRegion("protobuf bridge") {
        val parser = ::albumMessageParser.method
        val registry = AlbumGeneratedRegistryFingerprint.method.invoke(null)
        val watchNextResponseDefault = ::albumWatchNextResponseDefaultInstanceField.field.get(null)
        val commandDefault = ::albumCommandDefaultInstanceField.field.get(null)

        extensionMethod("serializeMessage", Any::class.java).hookMethod {
            // MessageLite.toByteArray() is not obfuscated.
            before { it.result = it.args[0].callMethod("toByteArray") }
        }
        extensionMethod("parseMessage", Any::class.java, ByteArray::class.java, Any::class.java).hookMethod {
            before { it.result = parser.invoke(null, it.args[0], it.args[1], it.args[2]) }
        }
        extensionMethod("extensionRegistry").hookMethod {
            before { it.result = registry }
        }
        extensionMethod("watchNextResponseDefaultInstance").hookMethod {
            before { it.result = watchNextResponseDefault }
        }
        extensionMethod("commandDefaultInstance").hookMethod {
            before { it.result = commandDefault }
        }
    }

    // endregion

    // region Watch next responses.

    // The album queue is changed before the app builds anything from the response.
    // Anchor (9.46.51): `Lwhm;-><init>(Lamkl;)V`, the only parameter is the WatchNextResponse.
    hookRegion("watch next response") {
        WatchNextResponseModelConstructorFingerprint.hookMethod {
            before { it.args[0] = PlayAlbumSongsPatch.rewriteWatchNextResponse(it.args[0]) }
        }
    }

    // endregion

    // region Album track commands.

    // A tapped album track is changed before the app starts playing it.
    // Anchor (9.46.51): `Lvxv;->c(Lakgm;Ljava/util/Map;)V` logs "Unknown command not resolved %s".
    hookRegion("album track command") {
        CommandResolverFingerprint.hookMethod {
            before { it.args[0] = PlayAlbumSongsPatch.overrideCommand(it.args[0]) }
        }
    }

    // endregion
}

/**
 * A private static stub of the album extension the patcher fills with smali. The stubs only
 * throw, so replacing their result skips the original body.
 */
private fun extensionMethod(name: String, vararg parameterTypes: Class<*>): Method =
    PlayAlbumSongsPatch::class.java.getDeclaredMethod(name, *parameterTypes).apply {
        isAccessible = true
    }
