/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Spoof video streams"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/misc/spoof/SpoofVideoStreamsPatch.kt
 * and morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/misc/spoof/SpoofVideoStreamsPatch.kt).
 *
 * The patcher injects extension calls into the smali of the app; the runtime port installs the
 * equivalent Xposed hooks. The host protobuf and media request classes are reached through
 * reflection and bridged with the extension interfaces.
 */

package io.github.nexalloy.morphe.music.misc.spoof

import android.app.Activity
import android.net.Uri
import app.morphe.extension.music.patches.spoof.SpoofVideoStreamsPatch as MusicSpoofVideoStreamsPatch
import app.morphe.extension.music.settings.preference.SpoofVideoStreamsSignInPreference
import app.morphe.extension.music.settings.preference.SpoofVideoStreamsWebsiteSignInPreference
import app.morphe.extension.shared.patches.MediaHttpRequest
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch as SharedSpoofVideoStreamsPatch
import app.morphe.extension.shared.spoof.WebViewCheck
import app.morphe.extension.shared.spoof.WebsiteStream
import com.google.android.libraries.youtube.media.interfaces.HttpHeader
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.spoof.plainStreamRequestsPatch
import io.github.nexalloy.patch
import java.io.OutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

private const val HLS_CURRENT_TIME_FEATURE_FLAG_ID = 45355374L

/**
 * The host request of a [MediaHttpRequest] bridge.
 */
private class HostMediaHttpRequest(private val host: Any) : MediaHttpRequest {

    override fun patch_getUrl(): String? =
        MediaRequestBridge.urlField.get(host) as String?

    override fun patch_getBody(): ByteArray? =
        MediaRequestBridge.bodyField.get(host) as ByteArray?

    @Suppress("UNCHECKED_CAST")
    override fun patch_getHeaders(): ArrayList<HttpHeader>? =
        MediaRequestBridge.headersField.get(host) as? ArrayList<HttpHeader>

    override fun patch_withBodyAndHeaders(body: ByteArray?, headers: ArrayList<HttpHeader>?): MediaHttpRequest {
        val args = MediaRequestBridge.constructor.parameterTypes.map { parameterType ->
            when (parameterType) {
                MediaRequestBridge.bodyField.type -> body
                MediaRequestBridge.headersField.type -> headers
                else -> {
                    val field = MediaRequestBridge.fieldsByType.getValue(parameterType)
                    if (parameterType == Boolean::class.javaPrimitiveType) field.getBoolean(host)
                    else field.get(host)
                }
            }
        }.toTypedArray()
        return HostMediaHttpRequest(MediaRequestBridge.constructor.newInstance(*args))
    }

    companion object {
        /** The request of a bridge, the host request itself, or null if neither. */
        fun unwrap(request: Any?): Any? = when {
            request is HostMediaHttpRequest -> request.host
            MediaRequestBridge.requestClass.isInstance(request) -> request
            else -> null
        }
    }
}

/**
 * Reflection of the media request class of the app, whose fields are obfuscated.
 * Filled by the patch before any bridge is used.
 */
private object MediaRequestBridge {
    lateinit var requestClass: Class<*>
    lateinit var constructor: Constructor<*>
    lateinit var urlField: Field
    lateinit var bodyField: Field
    lateinit var headersField: Field
    lateinit var fieldsByType: Map<Class<*>, Field>
}

/**
 * The host player response proto, as the [MessageLite] the extension expects.
 * The extension only serializes it, so only [toByteArray] is implemented.
 */
private class HostProtoShim(hostProto: Any) : MessageLite {

    private val hostRef = WeakReference(hostProto)

    private fun host(): Any = hostRef.get() ?: throw UnsupportedOperationException("Host proto is gone")

    override fun toByteArray(): ByteArray {
        val proto = host()
        return hostToByteArrayMethods.getOrPut(proto.javaClass) {
            proto.javaClass.getMethod("toByteArray")
        }.invoke(proto) as ByteArray
    }

    // The extension calls no other method of the message.
    override fun getDefaultInstanceForType(): MessageLite =
        throw UnsupportedOperationException()

    override fun isInitialized(): Boolean = throw UnsupportedOperationException()

    override fun writeTo(output: CodedOutputStream) {
        throw UnsupportedOperationException()
    }

    override fun getSerializedSize(): Int = throw UnsupportedOperationException()

    override fun getParserForType(): Parser<out MessageLite> = throw UnsupportedOperationException()

    override fun toByteString(): ByteString = throw UnsupportedOperationException()

    override fun writeTo(output: OutputStream) {
        throw UnsupportedOperationException()
    }

    override fun writeDelimitedTo(output: OutputStream) {
        throw UnsupportedOperationException()
    }

    override fun newBuilderForType(): MessageLite.Builder =
        throw UnsupportedOperationException()

    override fun toBuilder(): MessageLite.Builder = throw UnsupportedOperationException()

    companion object {
        private val hostToByteArrayMethods = ConcurrentHashMap<Class<*>, Method>()
        private val shims = ArrayDeque<HostProtoShim>()

        // Preserve identity for the extension's offline-response cache. Serializing a response
        // again after its player config changed can fail because protobuf caches its size.
        @Synchronized
        fun of(proto: Any): HostProtoShim {
            shims.removeAll { it.hostRef.get() == null }
            shims.firstOrNull { it.hostRef.get() === proto }?.let { return it }
            if (shims.size >= 16) shims.removeLast()
            return HostProtoShim(proto).also { shims.addFirst(it) }
        }
    }
}

/**
 * Runtime port of "Spoof video streams".
 */
val spoofVideoStreamsPatch = patch(
    name = "Spoof video streams",
    description = "Adds options to spoof the client video streams to fix playback.",
) {
    // The extension checks this in its static initializer, so the hook must be installed
    // before anything first touches the class.
    setExtensionIsPatchIncluded(SharedSpoofVideoStreamsPatch::class.java)

    dependsOn(plainStreamRequestsPatch)

    // region Settings

    PreferenceScreen.MISC.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_spoof_video_streams_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference(
                    key = "morphe_spoof_video_streams",
                    titleKey = "morphe_spoof_video_streams_screen_title",
                    summary = true
                ),
                ListPreference(
                    "morphe_spoof_video_streams_client_type",
                    entriesKey = "morphe_spoof_video_streams_client_type_entries_music",
                    entryValuesKey = "morphe_spoof_video_streams_client_type_entry_values_music"
                ),
                NonInteractivePreference(
                    key = "morphe_spoof_video_streams_sign_in_android_vr_about",
                    tag = SpoofVideoStreamsSignInPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_spoof_video_streams_sign_in_mweb_about",
                    tag = SpoofVideoStreamsWebsiteSignInPreference::class.java,
                    selectable = true
                ),
                SwitchPreference("morphe_spoof_video_streams_player_js_quack", summary = true)
            )
        )
    )

    // endregion

    // region Application and activity hooks

    MusicActivityOnCreateFingerprint.hookMethod {
        before {
            WebViewCheck.showDialogIfNeeded(it.thisObject as Activity)
            MusicSpoofVideoStreamsPatch.setClientOrderToUse()
        }
    }

    YouTubeMusicApplicationInitFingerprint.hookMethod {
        before { MusicSpoofVideoStreamsPatch.setClientOrderToUse() }
    }
    // Patches are installed from Application.onCreate, whose current invocation has already
    // entered the hook dispatcher. Initialize now as well, before background requests start.
    MusicSpoofVideoStreamsPatch.setClientOrderToUse()

    // endregion

    // region Fetch streaming data

    // The requests of the app are not changed. The streams are fetched when the app requests a
    // video, and also when the app builds the streams of a video it loaded with a request that
    // was not seen.
    // The runtime port retains Music's original package name, so upstream's user-agent
    // package-name replacement is already satisfied.
    val requestUrlMethod = ::musicRequestUrlMethod.method
    val requestParameters = MusicBuildRequestFingerprint.method.parameterTypes
    val headersIndex = requestParameters.indexOf(Map::class.java)
    val bodyIndex = requestParameters.indexOf(ByteArray::class.java)
    check(headersIndex >= 0 && bodyIndex >= 0)
    MusicBuildRequestFingerprint.hookMethod {
        before {
            SharedSpoofVideoStreamsPatch.fetchStreams(
                requestUrlMethod.invoke(it.args[0]) as String?,
                it.args[headersIndex] as Map<String, String>?,
                it.args[bodyIndex] as ByteArray?
            )
        }
    }

    // When a media response asks for the player response again, the app loads the video again,
    // which can be with requests that are not seen.
    ::reloadPlayerResponseMethod.hookMethod {
        before { SharedSpoofVideoStreamsPatch.onReloadPlayerResponse() }
    }

    // The Onesie response of the app cannot be used with replaced streams. YouTube Music
    // does not have the check, and the fingerprint resolves to nothing then.
    OnesieResponseCompatibilityFingerprint.memberOrNull?.hookMethod {
        before {
            if (SharedSpoofVideoStreamsPatch.skipOnesieCompatibilityCheck(it.args[0] as? String)) {
                it.result = false
            }
        }
    }

    // endregion

    // region Make the media requests of the streams of TV clients name the TV client

    val requestClass = HttpRequestConstructorFingerprint.declaredClass
    MediaRequestBridge.requestClass = requestClass
    MediaRequestBridge.constructor = HttpRequestConstructorFingerprint.constructor
    MediaRequestBridge.urlField = requestClass.declaredFields.single { it.type == String::class.java }
    MediaRequestBridge.bodyField = requestClass.declaredFields.single { it.type == ByteArray::class.java }
    MediaRequestBridge.headersField =
        requestClass.declaredFields.single { it.type == ArrayList::class.java }
    MediaRequestBridge.fieldsByType = requestClass.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) }
        .associateBy { it.type }
    MediaRequestBridge.urlField.isAccessible = true
    MediaRequestBridge.bodyField.isAccessible = true
    MediaRequestBridge.headersField.isAccessible = true
    MediaRequestBridge.fieldsByType.values.forEach { it.isAccessible = true }

    val headerNameField = ::mediaHttpHeaderNameField.field.apply { isAccessible = true }
    WebsiteStream.setHeaderNameResolver { header -> headerNameField.get(header) as String? }

    ::netFetchStartFetchTaskMethod.hookMethod {
        before {
            val request = it.args[0] ?: return@before
            val replacedRequest = SharedSpoofVideoStreamsPatch.onMediaRequest(
                HostMediaHttpRequest(request),
                it.args[1]
            )
            it.args[0] = HostMediaHttpRequest.unwrap(replacedRequest) ?: request
        }
    }

    NetFetchResponseChunkFingerprint.hookMethod {
        before {
            SharedSpoofVideoStreamsPatch.onMediaResponseChunk(it.thisObject, it.args[0] as ByteBuffer?)
        }
    }

    // endregion

    // region Replace the streaming data with the replacement streams

    val streamingDataField = ::playerProtoStreamingDataField.field
    val videoDetailsField = ::playerProtoVideoDetailsField.field
    val playerConfigField = ::playerProtoPlayerConfigField.field
    val mediaCommonConfigField = ::mediaCommonConfigField.field
    val mediaUstreamerRequestConfigField = ::mediaUstreamerRequestConfigField.field

    val playerProtoClass = CreateStreamingDataFingerprint.constructor.parameterTypes.first()
    val playerProtoConstructor = playerProtoClass.getDeclaredConstructor().apply { isAccessible = true }
    val playerProtoFields = playerProtoClass.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }
    val playerProtoDefaultInstance = playerProtoClass.declaredFields
        .single { Modifier.isStatic(it.modifiers) && it.type == playerProtoClass }
        .apply { isAccessible = true }
    val videoDetailsClass = videoDetailsField.type
    // The upstream patch hardcodes the video id field of the video details.
    val videoIdField = videoDetailsClass.getDeclaredField("c").apply { isAccessible = true }
    val videoDetailsDefaultInstance = videoDetailsClass.declaredFields
        .single { Modifier.isStatic(it.modifiers) && it.type == videoDetailsClass }
        .apply { isAccessible = true }
    val videoLengthField = videoDetailsClass.declaredFields
        .singleOrNull { it.type == Long::class.javaPrimitiveType }?.apply { isAccessible = true }

    val parseStreamingData = ::playerResponseParseFromMethod.method
    val playerConfigCreateBuilder = ::playerConfigCreateBuilderMethod.method
    val playerConfigMergeFrom = ::playerConfigMergeFromMethod.method
    val playerConfigBuild = ::playerConfigBuildMethod.method
    val playerConfigClass = playerConfigField.type
    val playerConfigDefaultInstance = playerConfigClass.declaredFields
        .single { Modifier.isStatic(it.modifiers) && it.type == playerConfigClass }
        .apply { isAccessible = true }
    val playerConfigToByteArray = playerConfigClass.getMethod("toByteArray")
    // The extension parses the config with the registry of the host protobuf library.
    val hostGeneratedRegistry = classLoader
        .loadClass("com.google.protobuf.ExtensionRegistryLite")
        .getDeclaredMethod("getGeneratedRegistry")

    val configParser = HostPlayerConfigParser(
        playerConfigDefaultInstance,
        playerConfigCreateBuilder,
        playerConfigMergeFrom,
        playerConfigBuild,
        hostGeneratedRegistry
    )

    CreateStreamingDataFingerprint.hookMethod {
        before {
            val proto = it.args[0] ?: return@before
            if (!SharedSpoofVideoStreamsPatch.isSpoofingEnabled()) return@before

            // Get the video id. A default video details has no video id.
            val videoDetails = videoDetailsField.get(proto) ?: videoDetailsDefaultInstance.get(null)
            val videoId = videoIdField.get(videoDetails) as String? ?: return@before

            // Get the replacement streaming data.
            val streamingDataBytes = SharedSpoofVideoStreamsPatch.getStreamingData(
                videoId,
                HostProtoShim.of(proto)
            ) ?: return@before

            // The patcher replaces a field of the object being constructed, without changing
            // the shared response. At runtime give this constructor a shallow response copy;
            // changing the original even temporarily races with other response readers.
            val newProto = parseStreamingData.invoke(null, playerProtoDefaultInstance.get(null), streamingDataBytes)
            val newStreamingData = streamingDataField.get(newProto) ?: return@before
            val constructorProto = playerProtoConstructor.newInstance()
            playerProtoFields.forEach { field -> field.set(constructorProto, field.get(proto)) }
            streamingDataField.set(constructorProto, newStreamingData)
            it.args[0] = constructorProto

            // The replacement streams can be of another video, such as an album track played
            // as its song version, and then the app keeps the timeline of the music video.
            if (videoLengthField != null) {
                val videoLength = SharedSpoofVideoStreamsPatch.getVideoLengthSeconds(videoId)
                if (videoLength > 0) videoLengthField.setLong(videoDetails, videoLength)
            }

            // Get the replacement player config. The app player config tells how the video is
            // played inline.
            val appPlayerConfig = playerConfigField.get(proto)
            val appPlayerConfigBytes =
                if (appPlayerConfig != null) playerConfigToByteArray.invoke(appPlayerConfig) as ByteArray
                else null
            val playerConfigBytes = SharedSpoofVideoStreamsPatch.getPlayerConfig(
                videoId,
                appPlayerConfigBytes
            ) ?: return@before
            val newPlayerConfig = configParser.parse(playerConfigBytes)

            if (!SharedSpoofVideoStreamsPatch.hasAndroidMedia(videoId)) {
                val newMediaCommonConfig = mediaCommonConfigField.get(newPlayerConfig) ?: return@before
                val newMediaUstreamerConfig =
                    mediaUstreamerRequestConfigField.get(newMediaCommonConfig) ?: return@before

                // Set the media uStreamer request config in a copy of the app player config.
                // The app config may already be serialized, which caches its size, and
                // changing it then breaks serializing it again.
                val appPlayerConfigBytesCopy = appPlayerConfigBytes ?: return@before
                val appPlayerConfigCopy = configParser.parse(appPlayerConfigBytesCopy)
                val appMediaCommonConfig = mediaCommonConfigField.get(appPlayerConfigCopy)
                    ?: return@before
                mediaUstreamerRequestConfigField.set(appMediaCommonConfig, newMediaUstreamerConfig)
                playerConfigField.set(proto, appPlayerConfigCopy)
                playerConfigField.set(constructorProto, appPlayerConfigCopy)
            } else {
                playerConfigField.set(proto, newPlayerConfig)
                playerConfigField.set(constructorProto, newPlayerConfig)
            }
        }
    }

    // endregion

    // region Remove the video playback request body to fix playback

    val dataSourceUrlField = ::mediaDataSourceUrlField.field
    val dataSourceHttpMethodField = ::mediaDataSourceHttpMethodField.field
    val dataSourceHttpBodyField = ::mediaDataSourceHttpBodyField.field

    BuildMediaDataSourceFingerprint.hookMethod {
        after {
            if (it.hasThrowable()) return@after
            val newBody = SharedSpoofVideoStreamsPatch.removeVideoPlaybackPostBody(
                dataSourceUrlField.get(it.thisObject) as Uri?,
                dataSourceHttpMethodField.getInt(it.thisObject),
                dataSourceHttpBodyField.get(it.thisObject) as ByteArray?
            )
            dataSourceHttpBodyField.set(it.thisObject, newBody)
        }
    }

    // endregion

    // region Append spoof info

    ::nerdsStatsVideoFormatBuilderMethod.hookMethod {
        after {
            it.result = SharedSpoofVideoStreamsPatch.appendSpoofedClient(it.result as String)
        }
    }

    // endregion

    // region Fix iOS live stream current time

    // The upstream patch wraps every read of the flag in the method it appears in. The runtime
    // port wraps the flag getter itself, which is equivalent for every reader of the flag.
    ::hlsCurrentTimeFlagMethod.hookMethod {
        after {
            if (it.args[1] as Long == HLS_CURRENT_TIME_FEATURE_FLAG_ID) {
                it.result = SharedSpoofVideoStreamsPatch.fixHLSCurrentTime(it.result as Boolean)
            }
        }
    }

    // endregion

    // region Disable SABR playback

    // Several videos are set up at once, such as the next track of the queue, so the choice
    // is made from the streams of the video the decision is for.
    val videoStreamingDataVideoIdField = ::videoStreamingDataVideoIdField.field
    val mediaFetchSabrField = ::mediaFetchEnumSabrField.field

    ::mediaFetchSabrDecisionMethod.hookMethod {
        before {
            val videoId = videoStreamingDataVideoIdField.get(it.args[1]) as String?
            if (SharedSpoofVideoStreamsPatch.disableSABR(videoId)) {
                it.result = mediaFetchSabrField.get(null)
            }
        }
    }

    // endregion

    // region Turn off feature flags that interfere with spoofing

    // The media session feature flag method only reads the flag, so overriding the result of
    // the method overrides the flag. The playback start descriptor and reel item watch
    // response flags are not read by YouTube Music 9.46.51.
    MediaSessionFeatureFlagFingerprint.hookMethod {
        after {
            it.result = SharedSpoofVideoStreamsPatch.useMediaSessionFeatureFlag(it.result as Boolean)
        }
    }

    // endregion
}

