/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/1953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of "Hide music action buttons"
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/buttons/action/HideMusicActionButtonsPatch.kt).
 *
 * The upstream patch hands two patch-added interfaces to the extension: the
 * ButtonProtoBufferInterface on the class that owns each button's serialized proto buffer,
 * and the LithoGetBufferContainerInterface on the tree-node list classes, so the extension's
 * extractButtonProto walk can reach the buffer. A runtime module cannot add interfaces to
 * host classes, so this port hooks the extension's private extractButtonProto method (it is
 * compiled into the module and kept by the module's ProGuard rules) and performs the same
 * dispatch with reflection over the members resolved with the upstream fingerprints.
 */

package io.github.nexalloy.morphe.music.layout.buttons.action

import app.morphe.extension.music.patches.components.MusicActionButtonsFilter
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.music.misc.litho.filter.lithoFilterPatch
import io.github.nexalloy.morphe.music.misc.litho.node.musicTreeNodeElementHook
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexalloy.morphe.shared.misc.litho.node.hookTreeNodeResult
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch
import java.lang.reflect.Field
import java.lang.reflect.Method

/** The container field of the lazy tree-node list (9.46.51: `nvs.b`). */
internal val treeNodeListContainerField = findFieldDirect {
    TreeNodeListFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/** The container field of the lazy element helper (9.46.51: `aelk.b`). */
internal val treeNodeHelperContainerField = findFieldDirect {
    TreeNodeListHelperConstructorFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * Runtime port of "Hide music action buttons".
 */
val hideMusicActionButtonsPatch = patch(
    name = "Hide music action buttons",
    description = "Adds options to hide action buttons under the player.",
) {
    dependsOn(
        lithoFilterPatch,
        musicTreeNodeElementHook
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_action_buttons_screen",
            preferences = setOf(
                SwitchPreference("morphe_music_hide_action_bar"),
                SwitchPreference("morphe_music_hide_like_dislike_button"),
                SwitchPreference("morphe_music_hide_comments_button"),
                SwitchPreference("morphe_music_hide_live_chat_replay_button"),
                SwitchPreference("morphe_music_hide_lyrics_button"),
                SwitchPreference("morphe_music_hide_share_button"),
                SwitchPreference("morphe_music_hide_save_button"),
                SwitchPreference("morphe_music_hide_download_button"),
                SwitchPreference("morphe_music_hide_radio_button")
            )
        )
    )

    addLithoFilter(MusicActionButtonsFilter())
    hookTreeNodeResult({ identifier, treeNodeResultList ->
        MusicActionButtonsFilter.onLazilyConvertedElementLoaded(identifier, treeNodeResultList)
    })

    // region Replace the patch-added interface dispatch of extractButtonProto.

    // The class that owns the button's serialized proto buffer, and its getter.
    // Anchor (9.46.51): `Lmhs;` and `mhs.c()[B`.
    val protoBufferClass = ButtonProtoBufferGetterFingerprint.declaredClass
    val protoBufferGetter: Method = ButtonProtoBufferGetterFingerprint.method

    // The tree-node list classes and the fields the upstream interfaces return.
    val listClass = TreeNodeListFingerprint.declaredClass
    val listContainerField: Field = ::treeNodeListContainerField.field.apply { isAccessible = true }
    val helperClass = TreeNodeListHelperConstructorFingerprint.declaredClass
    val helperContainerField: Field = ::treeNodeHelperContainerField.field.apply { isAccessible = true }

    /**
     * The same object-graph walk the patch-added interfaces perform upstream: the buffer
     * holder is checked first, then each container follows its container field. The
     * extension classifies and hides the buttons; only the interface dispatch is replicated.
     */
    fun extractButtonProto(item: Any?, depth: Int = 0): ByteArray? {
        if (item == null || depth > 16) return null
        return runCatching {
            when {
                protoBufferClass.isInstance(item) -> protoBufferGetter.invoke(item) as? ByteArray
                listClass.isInstance(item) -> extractButtonProto(listContainerField.get(item), depth + 1)
                helperClass.isInstance(item) -> extractButtonProto(helperContainerField.get(item), depth + 1)
                else -> null
            }
        }.getOrNull()
    }

    // The extension walks a tree-node entry by dispatching on the patch-added interfaces,
    // which no host object implements at runtime. Hook the walk entry point and supply the
    // resolved members instead. ProGuard keeps every member of app.morphe.extension.**.
    MusicActionButtonsFilter::class.java
        .getDeclaredMethod("extractButtonProto", Any::class.java)
        .apply { isAccessible = true }
        .hookMethod {
            before { it.result = extractButtonProto(it.args[0]) }
        }

    // endregion
}
