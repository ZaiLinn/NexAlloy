package io.github.nexalloy.morphe.youtube.layout.captions.language

import app.morphe.extension.youtube.patches.AutoTranslateCaptionsPatch
import app.morphe.extension.youtube.patches.PreferredCaptionLanguagePatch
import app.morphe.extension.youtube.patches.TranslatedCaptionPensPatch
import io.github.nexalloy.createProxy
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges the obfuscated caption classes to the extension interfaces.
 *
 * Upstream adds the interface methods to the obfuscated classes while patching.
 * This module instead wraps the host objects and resolves the members the interfaces
 * are added to, so the extension code can call them through the bridges.
 */
internal object CaptionBridge {
    /** Language code field of a caption track. */
    lateinit var languageCodeField: Field

    /** Vss id field of a caption track. */
    lateinit var vssIdField: Field

    /** Url field of a caption track. */
    lateinit var urlField: Field

    /** Copy constructor of the caption track builder, which takes the track to copy. */
    lateinit var builderCopyConstructor: Constructor<*>

    /** Build method of the caption track builder. */
    lateinit var builderBuildMethod: Method

    /** Creates a caption track builder with the default values of every caption track. */
    lateinit var builderDefaultsMethod: Method

    lateinit var setLanguageCodeMethod: Method
    lateinit var setVideoIdMethod: Method
    lateinit var setUrlMethod: Method
    lateinit var setVssIdMethod: Method

    /** Label field of the caption track builder. */
    lateinit var builderLabelField: Field

    /** Tracks manager field of the subtitle manager. */
    lateinit var subtitleManagerTracksField: Field

    /** Native track selection of the subtitle manager. */
    lateinit var selectTrackMethod: Method

    /** Default caption selection reason of the selection reason enum. */
    lateinit var defaultSelectionReason: Any

    /** Direct caption tracks of the caption tracks manager. */
    lateinit var directTracksMethod: Method

    /** Auto-translated caption tracks of the caption tracks manager. */
    lateinit var autoTranslateTracksMethod: Method

    /** Default caption source of the current audio track. */
    lateinit var defaultSourceMethod: Method

    /** Caption track metadata field of the caption tracks manager. */
    lateinit var metadataField: Field

    /** Current audio track field of the caption tracks manager. */
    lateinit var audioTrackField: Field

    /** General translation source indices field of the caption metadata. */
    lateinit var sourceIndicesField: Field

    /** Caption track indices field of the current audio track. */
    lateinit var audioCaptionIndicesField: Field

    /** Caption track metadata list field of the caption metadata. */
    lateinit var captionTracksField: Field

    /** Converts caption track metadata to a caption track. */
    lateinit var convertTrackMethod: Method

    /** Video id field of the caption tracks manager, used by the auto-translate option row. */
    lateinit var optionVideoIdField: Field

    /** Auto-translate option label field of the caption tracks manager. */
    lateinit var optionLabelField: Field

    /** Class of a caption track request. */
    lateinit var requestClass: Class<*>

    /** Caption track field of a caption track request. */
    lateinit var requestTrackField: Field

    /** Current caption track field of the caption overlay presenter. */
    lateinit var presenterTrackField: Field

    /** isPipActive field of the player overlay state. */
    lateinit var overlayPipField: Field

    /** Name of the method that returns the name of a player overlay. */
    lateinit var overlayNameMethodName: String

    /**
     * All caption tracks of the video, including the tracks of other audio tracks.
     * An uploaded track of an output language can belong to another audio track.
     */
    fun allCaptionTracks(tracksManager: Any): List<Any> {
        val metadata = runCatching { metadataField.get(tracksManager) }.getOrNull()
            ?: return emptyList()
        val tracks = runCatching { captionTracksField.get(metadata) as? List<*> }.getOrNull()
            ?: return emptyList()

        return tracks.mapNotNull { metadataTrack ->
            runCatching { convertTrackMethod.invoke(tracksManager, metadataTrack) }.getOrNull()
        }
    }

    /**
     * Original caption track YouTube translates captions of the current audio track from,
     * otherwise the default caption track of the current audio track.
     */
    fun translationSource(tracksManager: Any): Any? {
        val metadata = runCatching { metadataField.get(tracksManager) }.getOrNull()
            ?: return defaultSource(tracksManager)
        val audioTrack = runCatching { audioTrackField.get(tracksManager) }.getOrNull()
            ?: return defaultSource(tracksManager)
        val sourceIndices = runCatching { sourceIndicesField.get(metadata) as? List<*> }.getOrNull()
            ?: return defaultSource(tracksManager)
        val audioCaptionIndices = runCatching { audioCaptionIndicesField.get(audioTrack) as? List<*> }.getOrNull()

        for (index in sourceIndices) {
            if (index == null || audioCaptionIndices?.contains(index) != true) continue
            val metadataTrack = runCatching {
                (captionTracksField.get(metadata) as? List<*>)?.get(index as Int)
            }.getOrNull() ?: continue

            runCatching { convertTrackMethod.invoke(tracksManager, metadataTrack) }.getOrNull()
                ?.let { return it }
        }
        return defaultSource(tracksManager)
    }

    /** Default caption source of the current audio track, without translation overrides. */
    fun defaultSource(tracksManager: Any): Any? =
        runCatching { defaultSourceMethod.invoke(tracksManager) }.getOrNull()

    /** Direct caption tracks of the video, including the disable and auto-translate pseudo options. */
    fun directTracks(tracksManager: Any): Any? =
        runCatching { directTracksMethod.invoke(tracksManager) }.getOrNull()

    /**
     * The auto-translate pseudo option row of the direct tracks, built like the manager
     * builds its own row when it offers auto-translations.
     */
    fun buildAutoTranslateOptionRow(tracksManager: Any): Any? = runCatching {
        val builder = builderDefaultsMethod.invoke(null)!!
        setLanguageCodeMethod.invoke(builder, AUTO_TRANSLATE_CAPTIONS_OPTION)
        setVideoIdMethod.invoke(builder, optionVideoIdField.get(tracksManager))
        setVssIdMethod.invoke(builder, "")
        setUrlMethod.invoke(builder, "")
        builderLabelField.set(builder, optionLabelField.get(tracksManager))
        builderBuildMethod.invoke(builder)
    }.getOrNull()

    /** Name of a player overlay, or null if the overlay has no name. */
    private val overlayNameMethods = ConcurrentHashMap<Class<*>, Method?>()

    fun overlayName(overlay: Any): String? {
        val method = overlayNameMethods.getOrPut(overlay.javaClass) {
            runCatching { overlay.javaClass.getMethod(overlayNameMethodName) }.getOrNull()
        } ?: return null

        return runCatching { method.invoke(overlay) as? String }.getOrNull()
    }
}

/** Pseudo language code of the auto-translate option of the caption menu. */
internal const val AUTO_TRANSLATE_CAPTIONS_OPTION = "AUTO_TRANSLATE_CAPTIONS_OPTION"

/** Members of obfuscated classes are not accessible for direct invocation otherwise. */
internal fun <T : AccessibleObject> T.accessible() = apply { isAccessible = true }

/**
 * A caption track of the host, for the extension code.
 *
 * The host is held strongly, because translated tracks created by [patch_copyTranslation]
 * are otherwise only referenced by this wrapper until they are unwrapped again.
 * The host never references the wrapper, so there is no reference cycle.
 */
internal class HostCaptionTrack(@JvmField val host: Any) : AutoTranslateCaptionsPatch.CaptionTrackInterface {

    override fun patch_getLanguageCode(): String? =
        runCatching { CaptionBridge.languageCodeField.get(host) as? String }.getOrNull()

    override fun patch_getVssId(): String? =
        runCatching { CaptionBridge.vssIdField.get(host) as? String }.getOrNull()

    override fun patch_getUrl(): String? =
        runCatching { CaptionBridge.urlField.get(host) as? String }.getOrNull()

    override fun patch_copyTranslation(
        languageCode: String?,
        url: String?,
        vssId: String?,
        label: CharSequence?,
    ): AutoTranslateCaptionsPatch.CaptionTrackInterface? = runCatching {
        val builder = CaptionBridge.builderCopyConstructor.newInstance(host)
        CaptionBridge.setLanguageCodeMethod.invoke(builder, languageCode)
        CaptionBridge.setUrlMethod.invoke(builder, url)
        CaptionBridge.setVssIdMethod.invoke(builder, vssId)
        CaptionBridge.builderLabelField.set(builder, label)
        CaptionBridge.builderBuildMethod.invoke(builder)
    }.getOrNull()?.let(::HostCaptionTrack)

    /** The track toString is the displayed name, which the tracks are sorted by. */
    override fun toString(): String = host.toString()

    override fun equals(other: Any?): Boolean =
        if (other is HostCaptionTrack) host == other.host else host == other

    override fun hashCode(): Int = host.hashCode()
}

/** The subtitle manager of the host, for the extension code. */
internal class SubtitleManagerBridge(private val managerRef: WeakReference<Any>) :
    PreferredCaptionLanguagePatch.SubtitleManagerInterface {

    private val tracksManager: Any?
        get() = managerRef.get()?.let {
            runCatching { CaptionBridge.subtitleManagerTracksField.get(it) }.getOrNull()
        }

    override fun patch_getDirectCaptionTracks():
            List<PreferredCaptionLanguagePatch.CaptionTrackInterface>? =
        tracksManager?.let(CaptionBridge::directTracks).let(::wrapTrackList)

    override fun patch_getAllCaptionTracks():
            List<PreferredCaptionLanguagePatch.CaptionTrackInterface>? =
        tracksManager?.let(CaptionBridge::allCaptionTracks).let(::wrapTrackList)

    override fun patch_getAutoTranslateCaptionTracks():
            List<PreferredCaptionLanguagePatch.CaptionTrackInterface>? =
        tracksManager?.let {
            runCatching { CaptionBridge.autoTranslateTracksMethod.invoke(it) }.getOrNull()
        }.let(::wrapTrackList)

    override fun patch_getCaptionTranslationSource():
            PreferredCaptionLanguagePatch.CaptionTrackInterface? =
        tracksManager?.let(CaptionBridge::translationSource).let(::wrapTrack)

    override fun patch_getCurrentAudioCaptionSource():
            PreferredCaptionLanguagePatch.CaptionTrackInterface? =
        tracksManager?.let(CaptionBridge::defaultSource).let(::wrapTrack)

    override fun patch_selectCaptionTrack(track: PreferredCaptionLanguagePatch.CaptionTrackInterface) {
        val manager = managerRef.get() ?: return

        runCatching {
            CaptionBridge.selectTrackMethod.invoke(manager, unwrapTrack(track), CaptionBridge.defaultSelectionReason)
        }
    }
}

/** A caption track request of the host, for the extension code. */
internal class CaptionRequestBridge(private val requestRef: WeakReference<Any>) :
    TranslatedCaptionPensPatch.CaptionRequestInterface {

    override fun patch_getCaptionTrack():
            PreferredCaptionLanguagePatch.CaptionTrackInterface? =
        requestRef.get()?.let {
            runCatching { CaptionBridge.requestTrackField.get(it) }.getOrNull()
        }?.let(::wrapTrack)
}

internal fun wrapTrack(host: Any?): HostCaptionTrack? = host?.let(::HostCaptionTrack)

internal fun wrapTrackList(list: Any?):
        List<PreferredCaptionLanguagePatch.CaptionTrackInterface>? =
    (list as? List<*>)?.mapNotNull {
        wrapTrack(it) as PreferredCaptionLanguagePatch.CaptionTrackInterface?
    }

/** Variant of [wrapTrackList] for the methods of the auto-translate patch. */
internal fun wrapAutoTranslateTracks(list: Any?):
        List<AutoTranslateCaptionsPatch.CaptionTrackInterface>? =
    (list as? List<*>)?.mapNotNull {
        wrapTrack(it) as AutoTranslateCaptionsPatch.CaptionTrackInterface?
    }

/** The host object of a track bridge, or the object itself if it is not a bridge. */
internal fun unwrapTrack(track: Any?): Any? = when (track) {
    null -> null
    is HostCaptionTrack -> track.host
    else -> track
}

internal fun wrapSubtitleManager(manager: Any?):
        PreferredCaptionLanguagePatch.SubtitleManagerInterface? =
    manager?.createProxy { managerRef -> SubtitleManagerBridge(managerRef) }

/** Wraps a request for the extension code, if it is a caption track request. */
internal fun wrapRequest(request: Any?): Any? {
    val host = request ?: return null
    if (host.javaClass != CaptionBridge.requestClass) return host

    return host.createProxy { requestRef -> CaptionRequestBridge(requestRef) }
}
