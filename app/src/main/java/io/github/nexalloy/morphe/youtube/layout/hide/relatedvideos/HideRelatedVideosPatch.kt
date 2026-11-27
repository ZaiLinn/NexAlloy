package io.github.nexalloy.morphe.youtube.layout.hide.relatedvideos

import app.morphe.extension.youtube.patches.HideRelatedVideosPatch
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.protobufListIsModifiableMethod
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.protobufListMutableCopyMethod
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.watchNextRowsMethod
import io.github.nexalloy.morphe.youtube.misc.fix.videoactionbar.watchNextSectionsField
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch

val HideRelatedVideos = patch(
    name = "Hide related videos",
    description = "Adds options to hide related videos.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_hide_player_related_videos")
    )

    val contentsField = ::watchNextSectionsField.field
    val continuationsField = ::relatedContinuationsField.field
    val itemSectionRendererField = ::relatedItemSectionRendererField.field
    val shelfRendererField = ::relatedShelfRendererField.field
    val isModifiable = ::protobufListIsModifiableMethod.method
    val mutableCopy = ::protobufListMutableCopyMethod.method
    val emptyProtobufList = ::emptyProtobufListMethod.method
    val itemSectionRendererDefaultInstance = itemSectionRendererField.type.getDefaultProtoInstance()
    val shelfRendererDefaultInstance = shelfRendererField.type.getDefaultProtoInstance()

    // Upstream adds a helper method that runs at the start of the watch next results method and
    // replaces related ItemSectionRenderers and ShelfRenderers with default instances, and
    // replaces the contents and continuations fields when anything was filtered.
    // At runtime the same transformation is applied in a before-hook.
    ::watchNextRowsMethod.hookMethod {
        before {
            val results = it.args[0] ?: return@before
            if (!HideRelatedVideosPatch.hideRelatedVideos()) return@before

            val contents = contentsField.get(results) as? List<*> ?: return@before
            // Only hide when the parsed ProtoList is immutable, same as upstream.
            if (isModifiable.invoke(contents) as Boolean) return@before
            val copy = mutableCopy.invoke(null, contents) as List<*>

            for (section in copy) {
                if (!itemSectionRendererField.declaringClass.isInstance(section)) continue
                when {
                    HideRelatedVideosPatch.isRelatedItems(section) ->
                        itemSectionRendererField.set(section, itemSectionRendererDefaultInstance)

                    HideRelatedVideosPatch.isShelfRenderer(section) ->
                        shelfRendererField.set(section, shelfRendererDefaultInstance)
                }
            }

            if (HideRelatedVideosPatch.isFiltered()) {
                // Replaces the contents field with the copy of the ProtoList.
                contentsField.set(results, copy)

                // Replaces the continuations field with an empty ProtoList if filtered,
                // otherwise unnecessary spam API requests are made.
                continuationsField.set(results, emptyProtobufList.invoke(null))
            }
        }
    }
}

/** The default instance of a generated protobuf class, held by its static 'a' field. */
private fun Class<*>.getDefaultProtoInstance(): Any =
    getDeclaredField("a").apply { isAccessible = true }.get(null)
