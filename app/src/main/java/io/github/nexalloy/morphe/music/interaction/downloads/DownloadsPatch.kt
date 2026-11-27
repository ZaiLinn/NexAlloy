package io.github.nexalloy.morphe.music.interaction.downloads

import android.net.Uri
import app.morphe.extension.music.patches.DownloadsPatch
import app.morphe.extension.music.patches.components.PlaylistPageFilter
import app.morphe.extension.music.patches.components.SongMenuFilter
import app.morphe.extension.music.patches.downloads.ExportMenuItem
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.BaseInAppDownloadsPatch
import app.morphe.extension.shared.patches.BaseInAppDownloadsPatch.FormatStreamInterface
import app.morphe.extension.shared.patches.BaseInAppDownloadsPatch.StreamingDataInterface
import app.morphe.extension.shared.settings.preference.ExportFolderPreference
import app.morphe.extension.shared.settings.preference.ExternalDownloaderPreference
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch
import app.morphe.extension.shared.spoof.requests.StreamingDataRequest
import io.github.nexalloy.callMethod
import io.github.nexalloy.callMethodOrNull
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.music.misc.litho.filter.lithoFilterPatch
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.shared.SpannableStringBuilderFingerprint
import io.github.nexalloy.morphe.shared.misc.downloads.FormatStreamProxy
import io.github.nexalloy.morphe.shared.misc.downloads.formatStreamInterfacePatch
import io.github.nexalloy.morphe.shared.misc.litho.context.ConversionContext
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.request.buildRequestPatch
import io.github.nexalloy.morphe.shared.misc.request.hookBuildRequest
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexalloy.morphe.shared.misc.spoof.plainStreamRequestsPatch
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Method

val Downloads = patch(
    name = "Downloads",
    description = "Adds support to download songs, albums, and playlists using the in-app download " +
        "button, either inside YouTube Music or with an external downloader app, " +
        "and exports songs and videos saved by the app to the device.",
) {
    dependsOn(
        lithoFilterPatch,
        formatStreamInterfacePatch,
        buildRequestPatch,
        plainStreamRequestsPatch,
        // The player action bar Download button needs the video ID of the playing song.
        musicVideoInformationPatch,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_external_downloader_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                ListPreference("morphe_download_mode"),
                SwitchPreference("morphe_download_force_4k", summary = true),
                NonInteractivePreference(
                    "morphe_export_download_folder",
                    summaryKey = null,
                    tag = ExportFolderPreference::class.java,
                    selectable = true
                ),
                SwitchPreference("morphe_external_downloader_menu", summary = true),
                TextPreference(
                    "morphe_external_downloader_name",
                    tag = ExternalDownloaderPreference::class.java
                ),
            )
        )
    )

    // The module and the host use separate protobuf implementations, so the host
    // serializes its own messages the extension reads.
    DownloadsPatch.setMessageSerializer { message -> message.callMethod("toByteArray") as ByteArray }

    // The host parses the messages the extension builds, with its own protobuf registry.
    val parseFrom = MusicProtobufParseFromWithRegistryFingerprint.method
    val generatedRegistry = MusicGeneratedRegistryFingerprint.method.invoke(null)

    fun parseMessage(defaultInstance: Any, bytes: ByteArray): Any? = runCatching {
        parseFrom.invoke(null, defaultInstance, bytes, generatedRegistry)
    }.onFailure { Logger.printException({ "Downloads message parsing failed" }, it) }.getOrNull()

    // region Fail-closed helper.

    fun hookRegion(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Logger.printException({ "Downloads hook failed: $name" }, it) }
    }

    // endregion

    // region Download button label.

    // Captures the localized download button label from Litho text creation. Unlike the
    // extension text hook of the shared text component patch, the result of the text builder
    // also includes the texts without any style runs, which the label may not have.
    hookRegion("download button label") {
        SpannableStringBuilderFingerprint.hookMethod {
            after {
                val text = it.result as? CharSequence ?: return@after
                val context = it.args[0] ?: return@after
                DownloadsPatch.onLithoTextLoaded(ConversionContext(context), text)
            }
        }
    }

    // endregion

    // region Litho filters.

    // Finds the song of a menu, for the menu Download of accounts offered Premium.
    addLithoFilter(SongMenuFilter())

    // Finds the playlist or album of a page, for the page and menu Download.
    addLithoFilter(PlaylistPageFilter())

    // endregion

    // region Command resolution.

    // Replace premium upsell and offline commands in CommandResolver with native OfflineCommand
    // so songs, albums, and playlists are saved directly by YouTube Music's offline engine,
    // or open the external downloader.
    hookRegion("download command") {
        val commandDefault = ::musicOfflineCommandDefaultInstance.field.get(null)!!
        MusicResolveCommandFingerprint.hookMethod {
            before {
                @Suppress("UNCHECKED_CAST")
                val bytes = DownloadsPatch.replaceDownloadCommand(
                    it.args[0],
                    it.args[1] as? Map<Any, Any>
                ) ?: return@before
                if (bytes.isEmpty()) {
                    // The command was handled, such as by the external downloader.
                    it.result = null
                } else {
                    parseMessage(commandDefault, bytes)?.let { command -> it.args[0] = command }
                }
            }
        }
    }

    // endregion

    // region Offlineability.

    // Unlock the offlineability of the songs, albums and playlists the endpoints save.
    hookRegion("offlineability") {
        val offlineabilityDefault = ::musicOfflineabilityDefaultInstance.field.get(null)!!
        ::musicSaveOfflineVideoMethod.method.hookMethod {
            before { param ->
                DownloadsPatch.unlockOfflineability(param.args[1])?.let { bytes ->
                    parseMessage(offlineabilityDefault, bytes)?.let { offlineability ->
                        param.args[1] = offlineability
                    }
                }
            }
        }
        ::musicSaveOfflinePlaylistMethod.method.hookMethod {
            before { param ->
                DownloadsPatch.unlockOfflineability(param.args[1])?.let { bytes ->
                    parseMessage(offlineabilityDefault, bytes)?.let { offlineability ->
                        param.args[1] = offlineability
                    }
                }
            }
        }
    }

    // endregion

    // region Offline upsell.

    // Save the track instead of showing the Premium upsell of the offline upsell endpoint.
    hookRegion("offline upsell") {
        val commandField = ::musicOfflineUpsellCommandField.field
        val upsellDefault = ::musicOfflineUpsellDefaultInstance.field.get(null)!!
        MusicOfflineUpsellResponseFingerprint.hookMethod {
            before { param ->
                DownloadsPatch.replaceOfflineUpsell(commandField.get(param.thisObject), param.args[0])
                    ?.let { bytes ->
                        parseMessage(upsellDefault, bytes)?.let { response -> param.args[0] = response }
                    }
            }
        }
    }

    // endregion

    // region Offline player response.

    // Save the track with streams of another client, if the server declines to save the track.
    hookRegion("offline player response") {
        val getterName = MusicPlayerResponseGetterFingerprint.method.name
        val fromBytes = MusicPlayerResponseModelFromBytesFingerprint.method
        MusicOfflinePlayerResponseFingerprint.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                val model = it.result ?: return@after
                val playerResponse = runCatching { model.callMethod(getterName) }.getOrNull()
                    ?: return@after
                val bytes = DownloadsPatch.replaceDeclinedOfflinePlayerResponse(playerResponse, false)
                    ?: return@after

                // The playback stream override would replace the streams that can be saved.
                SpoofVideoStreamsPatch.setKeepStreamsOnThread(true)
                val replacement = try {
                    // The response time, used for the expiration of the stream urls.
                    fromBytes.invoke(null, bytes, System.currentTimeMillis())
                } finally {
                    SpoofVideoStreamsPatch.setKeepStreamsOnThread(false)
                }
                DownloadsPatch.onOfflinePlayerResponseReplaced(replacement)
                if (replacement != null) it.result = replacement
            }
        }
    }

    // endregion

    // region Offline policy refreshes.

    // Keep saved tracks playable if the server disables them during background policy refreshes.
    hookRegion("offline state") {
        val offlineStateDefault = ::musicOfflineStateDefaultInstance.field.get(null)!!
        ::musicUpdateOfflineVideoPolicyMethods.dexMethodList.forEach { method ->
            method.toMethod().hookMethod {
                before { param ->
                    DownloadsPatch.replaceDisabledOfflineState(param.args[2])?.let { bytes ->
                        parseMessage(offlineStateDefault, bytes)?.let { offlineState ->
                            param.args[2] = offlineState
                        }
                    }
                }
            }
        }
    }

    // endregion

    // region Download qualities.

    // Every screen is offered the qualities up to 2160p, also when the app's device
    // or server checks rule the higher streams out.
    hookRegion("download quality height") {
        MusicOfflineSettingsConstructorFingerprint.hookMethod {
            before {
                it.args[2] = BaseInAppDownloadsPatch.getMaxDownloadQualityHeight(it.args[2] as Int)
            }
        }
    }

    // Labels of the qualities above 1080p, which the app has no labels for.
    hookRegion("download quality label") {
        MusicDownloadQualityLabelFingerprint.hookMethod {
            before {
                val label = BaseInAppDownloadsPatch.getDownloadQualityLabel(it.args[0])
                if (label != 0) it.result = label
            }
        }
    }

    // The media quality setting offers only 360p and 720p, so its entries are rebuilt
    // from every quality the app can save.
    hookRegion("download quality setting") {
        val labelMethod = MusicDownloadQualityLabelFingerprint.method
        val valueMethod = MusicOfflineQualityValueFingerprint.method
        val formatTypeEnum = labelMethod.parameterTypes.first()
        MusicDownloadQualitySettingFingerprint.hookMethod {
            after { rebuildQualitySetting(it.thisObject, formatTypeEnum, labelMethod, valueMethod) }
        }
    }

    // endregion

    // region Download streams.

    // Log the download qualities that are not available, and optionally save 1440p and 2160p
    // even if the device or server checks of the app rule out their streams.
    // The audio stream of a download is matched to the container of the video stream,
    // so the download can be exported as one file.
    hookRegion("download streams") {
        val streamsField = ::musicStreamingDataFormatStreamsField.field
        val pairFields = ::musicDownloadStreamPairFields.fieldList
        val videoField = pairFields.first().apply { isAccessible = true }
        val audioField = pairFields.last().apply { isAccessible = true }
        MusicDownloadStreamPairFingerprint.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                val pair = it.result ?: return@after
                val streamingData = it.args[0] ?: return@after
                val quality = it.args[2] as Int
                val audioOnly = it.args[5] as Boolean

                val video = videoField.get(pair)
                val audio = audioField.get(pair)
                val data = MusicStreamingData(streamingData, streamsField)

                val selectedVideo = DownloadsPatch.selectDownloadVideoStream(
                    video?.let(::MusicFormatStream), quality, data, audioOnly
                )
                val newVideo = (selectedVideo as? MusicFormatStream)?.stream ?: video
                if (newVideo !== video) videoField.set(pair, newVideo)

                val selectedAudio = DownloadsPatch.selectDownloadAudioStream(
                    audio?.let(::MusicFormatStream),
                    newVideo?.let(::MusicFormatStream),
                    data
                )
                val newAudio = (selectedAudio as? MusicFormatStream)?.stream ?: audio
                if (newAudio !== audio) audioField.set(pair, newAudio)
            }
        }
    }

    // endregion

    // region Export to device.

    // Add the export items to the menus of the songs and videos the app saved.
    hookRegion("export menu items") {
        val menuField = ::musicMenuBottomSheetMenuField.field.apply { isAccessible = true }
        val itemDefault = ::musicMenuItemDefaultInstance.field.get(null)!!
        MusicMenuBottomSheetItemsFingerprint.hookMethod {
            after {
                val items = it.result as? MutableList<Any> ?: return@after
                val menu = menuField.get(it.thisObject) ?: return@after
                ExportMenuItem.getExportMenuItems(menu).forEach { bytes ->
                    parseMessage(itemDefault, bytes)?.let { item -> items.add(item) }
                }
            }
        }
    }

    // The export items have no command, so the app only closes the menu when one is clicked.
    hookRegion("export menu item click") {
        val itemField = ::musicMenuItemField.field
        MusicMenuItemClickFingerprint.hookMethod {
            before { ExportMenuItem.onMenuItemClick(itemField.get(it.thisObject)) }
        }
    }

    // endregion

    // region Stream requests of the download clients.

    // Keep download authentication headers without starting playback spoof requests.
    hookBuildRequest { url, headers ->
        if (url != null && headers != null && Uri.parse(url).path?.startsWith("/youtubei/") == true) {
            StreamingDataRequest.setAppHeaders(headers)
        }
        headers
    }

    // endregion
}

/**
 * A stream of the app wrapped for the extension. The wrapper keeps the original stream,
 * so the stream the extension picks can be written back to the pair of the download.
 */
private class MusicFormatStream(val stream: Any) : FormatStreamInterface by FormatStreamProxy(stream)

/**
 * The streaming data of a video wrapped for the extension, which reads its every stream.
 */
private class MusicStreamingData(
    private val streamingData: Any,
    private val streamsField: Field
) : StreamingDataInterface {
    override fun patch_getFormatStreams(): List<FormatStreamInterface> = runCatching {
        @Suppress("UNCHECKED_CAST")
        val streams = streamsField.get(streamingData) as? List<Any> ?: return emptyList()
        streams.filterNotNull().map(::MusicFormatStream)
    }.getOrDefault(emptyList())
}

/**
 * Rebuilds the entries and values of the media quality setting from every quality the app
 * can save, with the labels of the qualities above 1080p the module adds.
 */
private fun rebuildQualitySetting(
    fragment: Any,
    formatTypeEnum: Class<*>,
    labelMethod: Method,
    valueMethod: Method,
) {
    runCatching {
        val formatType = formatTypeEnum.enumConstants?.firstOrNull() ?: return
        @Suppress("UNCHECKED_CAST")
        val formatTypes = DownloadsPatch.getDownloadQualityFormatTypes(listOf(formatType)) as List<Any>
        if (formatTypes.size <= 1) return // Download mode keeps the entries of the app.

        val preference = fragment.callMethod("findPreference", "offline_quality") ?: return
        val context = preference.callMethod("getContext") ?: return

        val entries = Array<CharSequence>(formatTypes.size) { index ->
            qualityLabel(formatTypes[index], context, labelMethod)
        }
        val values = Array<CharSequence>(formatTypes.size) { index ->
            valueMethod.invoke(null, formatTypes[index], -1).toString()
        }
        val selected = preference.callMethodOrNull("getValue") as? String

        preference.callMethod("setEntries", entries)
        preference.callMethod("setEntryValues", values)
        // The values are a superset of the entries of the app, so the selection stays valid.
        selected?.let { preference.callMethod("setValue", it) }
    }.onFailure { Logger.printException({ "Downloads quality setting failed" }, it) }
}

private fun qualityLabel(formatType: Any, context: Any, labelMethod: Method): CharSequence {
    // Labels of the qualities above 1080p, which the app has no labels for.
    val moduleLabel = BaseInAppDownloadsPatch.getDownloadQualityLabel(formatType)
    if (moduleLabel != 0) return context.callMethod("getString", moduleLabel) as CharSequence

    val appLabel = labelMethod.invoke(null, formatType) as Int
    return if (appLabel <= 0) "" else context.callMethod("getString", appLabel) as CharSequence
}
