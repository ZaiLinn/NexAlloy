package io.github.nexalloy.morphe.youtube.layout.captions.core

import app.morphe.extension.youtube.patches.AutoTranslateCaptionsPatch
import app.morphe.extension.youtube.patches.CaptionTracks
import app.morphe.extension.youtube.patches.CaptionTracks.CaptionTrackHandlerInterface
import app.morphe.extension.youtube.patches.CaptionTracks.UploadedCaptionCallback
import app.morphe.extension.youtube.patches.StableCaptionBlocks as StableCaptionBlocksExtension
import app.morphe.extension.youtube.patches.TranslatedCaptionPensPatch
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.videoid.VideoId
import io.github.nexalloy.morphe.youtube.video.videoid.hookVideoId
import io.github.nexalloy.patch
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

private const val HANDLER_EXTRA = "morphe.caption.handler"
private const val BLOCK_STATE_EXTRA = "morphe.caption.blockState"

/**
 * Members of the caption track classes, resolved once per process.
 * The upstream patch adds interface methods to the host classes during patching; at runtime
 * the extension interfaces are implemented by the proxy classes below instead.
 */
internal object CaptionTracksRuntime {
    // Caption track request.
    lateinit var requestTrackField: Field

    // Caption track accessors.
    lateinit var languageCodeField: Field
    lateinit var vssIdField: Field
    lateinit var urlField: Field

    // Caption track builder, for track copies.
    lateinit var builderCopyConstructor: Constructor<*>
    lateinit var builderBuild: Method
    lateinit var builderLabelField: Field
    lateinit var builderSetLanguageCode: Method
    lateinit var builderSetUrl: Method
    lateinit var builderSetVssId: Method

    // Loaded caption track (timeline).
    lateinit var timelineClass: Class<*>
    lateinit var timelineWindowsField: Field
    lateinit var timelineTimesField: Field
    lateinit var timelineConstructor: Constructor<*>
    lateinit var timelineSampler: Method

    /** Timeline proxies by host object, so the same host always has the same proxy. */
    val timelines: MutableMap<Any, TimelineProxy> =
        Collections.synchronizedMap(WeakHashMap<Any, TimelineProxy>())

    fun wrapTimeline(host: Any?): TimelineProxy? {
        if (host == null || !timelineClass.isInstance(host)) return null
        return synchronized(timelines) { timelines.getOrPut(host) { TimelineProxy(host) } }
    }
}

/**
 * The caption track of the host, implementing the extension caption track interface.
 */
internal class CaptionTrackProxy(val host: Any) : AutoTranslateCaptionsPatch.CaptionTrackInterface {

    override fun patch_getLanguageCode(): String? =
        CaptionTracksRuntime.languageCodeField.get(host) as? String

    override fun patch_getVssId(): String? =
        CaptionTracksRuntime.vssIdField.get(host) as? String

    override fun patch_getUrl(): String? =
        CaptionTracksRuntime.urlField.get(host) as? String

    override fun patch_copyTranslation(
        languageCode: String,
        url: String,
        vssId: String,
        label: CharSequence
    ): AutoTranslateCaptionsPatch.CaptionTrackInterface {
        val runtime = CaptionTracksRuntime
        val builder = runtime.builderCopyConstructor.newInstance(host)
        runtime.builderSetLanguageCode(builder, languageCode)
        runtime.builderSetUrl(builder, url)
        runtime.builderSetVssId(builder, vssId)
        runtime.builderLabelField.set(builder, label)
        return CaptionTrackProxy(runtime.builderBuild(builder)!!)
    }

    override fun toString(): String = "CaptionTrackProxy($host)"
    override fun equals(other: Any?): Boolean = other is CaptionTrackProxy && other.host === host
    override fun hashCode(): Int = System.identityHashCode(host)
}

/**
 * The caption track request of the host, implementing the extension caption request interface.
 */
internal class CaptionRequestProxy(val host: Any) : TranslatedCaptionPensPatch.CaptionRequestInterface {

    override fun patch_getCaptionTrack(): Any? =
        CaptionTracksRuntime.requestTrackField.get(host)?.let(::CaptionTrackProxy)

    override fun toString(): String = "CaptionRequestProxy($host)"
}

/**
 * The loaded caption track of the host, implementing the extension timeline interface.
 * The block state is stored as an additional instance field of the host, instead of the
 * field that the upstream patcher adds to the host class.
 */
internal class TimelineProxy internal constructor(val host: Any) : StableCaptionBlocksExtension.Timeline {

    override fun patch_getOriginalCaptions(time: Long): List<*> =
        CaptionTracksRuntime.timelineSampler(host, time) as List<*>

    override fun patch_getBlockState(): StableCaptionBlocksExtension.State? =
        XposedHelpers.getAdditionalInstanceField(host, BLOCK_STATE_EXTRA)
            as? StableCaptionBlocksExtension.State

    override fun patch_setBlockState(state: StableCaptionBlocksExtension.State?) {
        XposedHelpers.setAdditionalInstanceField(host, BLOCK_STATE_EXTRA, state)
    }

    override fun patch_mergeWith(other: StableCaptionBlocksExtension.Timeline): StableCaptionBlocksExtension.Timeline {
        val runtime = CaptionTracksRuntime
        val windows = ArrayList(runtime.timelineWindowsField.get(host) as List<*>)
        windows.addAll(runtime.timelineWindowsField.get((other as TimelineProxy).host) as List<*>)
        val merged = runtime.timelineConstructor.newInstance(windows)
        return runtime.wrapTimeline(merged)!!.also { it.patch_setBlockState(this.patch_getBlockState()) }
    }

    override fun patch_getCaptionEventTimes(): List<Long> =
        CaptionTracksRuntime.timelineTimesField.get(host) as List<Long>

    override fun toString(): String = "CaptionTimelineProxy($host)"
    override fun equals(other: Any?): Boolean = other is TimelineProxy && other.host === host
    override fun hashCode(): Int = System.identityHashCode(host)
}

/**
 * The native caption track handler of the host, implementing the extension handler interface.
 * The upstream patcher adds the interface methods to the handler class; at runtime the
 * members that the added methods call are invoked through this proxy.
 */
internal class CaptionTrackHandlerProxy(
    val host: Any,
    private val requestClass: Class<*>,
    private val trackClass: Class<*>,
    private val loaderField: Field,
    private val loaderRequest: Method,
    private val callbackInterface: Class<*>,
    private val selectTrack: Method,
    private val subtitleManagerField: Field,
    private val selectedTrackField: Field,
    private val updateCaption: Method,
) : CaptionTrackHandlerInterface {

    override fun patch_fetchCaption(track: Any, callback: UploadedCaptionCallback) {
        val request = requestClass.getConstructor(trackClass).newInstance(unwrapped(track))
        val nativeCallback = Proxy.newProxyInstance(
            callbackInterface.classLoader,
            arrayOf(callbackInterface)
        ) { _, method, args ->
            val params = method.parameterTypes
            if (params.size == 2) {
                if (params[1] == Exception::class.java) {
                    callback.onError(args?.get(1) as? Exception)
                } else {
                    callback.onResult(CaptionTracksRuntime.wrapTimeline(args?.get(1)))
                }
            }
            null
        }
        loaderRequest(loaderField.get(host), request, nativeCallback)
    }

    override fun patch_reloadCaption() {
        val manager = subtitleManagerField.get(host)
        selectTrack(host, manager?.let { selectedTrackField.get(it) })
    }

    override fun patch_updateCaption(time: Long, speed: Float) {
        updateCaption(host, time, speed)
    }

    private fun unwrapped(track: Any): Any = (track as? CaptionTrackProxy)?.host ?: track

    override fun toString(): String = "CaptionTrackHandlerProxy($host)"
}

/**
 * Runtime port of the upstream captionTrackCapturePatch.
 *
 * Observes the selected caption track and its loaded results, including cached results.
 * Uses the native loader to also load the uploaded tracks of the output languages.
 *
 * The upstream patch adds the extension callback interfaces to the host request and handler
 * classes, and prepends injection calls to the handler methods. At runtime the same extension
 * entry points are called with proxies of the host objects, and the result callback arguments
 * are rewritten instead of the request parameter registers of the original method.
 */
val CaptionTrackCapture = patch(
    name = "Caption track capture",
    description = "Tracks the caption source selected by the native player.",
) {
    dependsOn(VersionCheck, VideoId, VideoInformationPatch)

    // The track handler methods are only hooked since 21.15.
    if (!is_21_15_or_greater) {
        return@patch
    }

    CaptionTracksRuntime.apply {
        requestTrackField = ::captionTrackRequestTrackField.field

        languageCodeField = ::captionTrackLanguageCodeField.field
        vssIdField = ::captionTrackVssIdField.field
        urlField = ::captionTrackUrlField.field

        builderCopyConstructor = ::captionTrackBuilderCopyConstructor.constructor
        builderBuild = ::captionTrackBuilderBuildMethod.method
        builderLabelField = ::captionTrackBuilderLabelField.field
        builderSetLanguageCode = ::captionTrackBuilderSetLanguageCodeMethod.method
        builderSetUrl = ::captionTrackBuilderSetUrlMethod.method
        builderSetVssId = ::captionTrackBuilderSetVssIdMethod.method

        timelineClass = ::captionTimelineConstructor.constructor.declaringClass
        timelineWindowsField = ::captionTimelineWindowsField.field
        timelineTimesField = ::captionChunkTimesField.field
        timelineConstructor = ::captionTimelineConstructor.constructor
        timelineSampler = ::captionTimelineSamplerMethod.method
    }

    val selectTrack = ::captionTrackHandlerSelectMethod.method
    val handleResult = ::captionTrackHandlerResultMethod.method
    val updateCaption = ::captionTrackHandlerTimeMethod.method
    val loaderField = ::captionTrackLoaderField.field
    val loaderRequest = ::captionTrackLoaderRequestMethod.method
    val subtitleManagerField = ::captionTrackHandlerSubtitleManagerField.field
    val selectedTrackField = ::subtitleManagerSelectedTrackField.field
    val requestClass = ::captionTrackRequestClass.clazz
    val trackClass = ::captionTrackClass.clazz
    val callbackInterface = classLoader.loadClass(
        ::captionTrackLoaderRequestMethod.dexMethod.paramTypeNames[1]
    )

    fun handler(handler: Any): CaptionTrackHandlerProxy {
        XposedHelpers.getAdditionalInstanceField(handler, HANDLER_EXTRA)
            ?.let { return it as CaptionTrackHandlerProxy }

        return CaptionTrackHandlerProxy(
            handler,
            requestClass,
            trackClass,
            loaderField,
            loaderRequest,
            callbackInterface,
            selectTrack,
            subtitleManagerField,
            selectedTrackField,
            updateCaption,
        ).also { XposedHelpers.setAdditionalInstanceField(handler, HANDLER_EXTRA, it) }
    }

    hookVideoId(CaptionTracks::onVideoId)

    // Track selection: observe the selected track and request the planned uploaded tracks.
    selectTrack.hookMethod {
        before { param ->
            val track = param.args[0]
            CaptionTracks.onSelectedTrack(track?.let(::CaptionTrackProxy))
            if (param.thisObject != null) {
                CaptionTracks.fetchUploadedTracks(
                    handler(param.thisObject!!),
                    track?.let(::CaptionTrackProxy)
                )
            }
        }
    }

    // Result of a track request. Can be a cached native timeline.
    handleResult.hookMethod {
        before { param ->
            val request = param.args[0]?.let(::CaptionRequestProxy)
            val result = param.args[1]

            CaptionTracks.onNativeResult(request, CaptionTracksRuntime.wrapTimeline(result))
            if (result != null) {
                val merged = CaptionTracks.mergeUploadedTracks(CaptionTracksRuntime.wrapTimeline(result))
                if (merged is TimelineProxy && merged.host !== result) {
                    // The original body installs the merged result.
                    param.args[1] = merged.host
                }
            }
            param.setObjectExtra("morphe.caption.request", request)
        }

        after { param ->
            val request = param.getObjectExtra("morphe.caption.request") as? CaptionRequestProxy
            if (param.thisObject != null) {
                CaptionTracks.onNativeResultReady(
                    handler(param.thisObject!!),
                    request,
                    CaptionTracksRuntime.wrapTimeline(param.args[1])
                )
            }
        }
    }
}
