/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2029
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/misc/settingsmenu/Fingerprints.kt
 * and morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/misc/settings/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.music.layout.hide.settingsmenu

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.anyInstruction
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

/**
 * Preference.setTitle(CharSequence). The only final (CharSequence)V method of the class.
 */
internal object PreferenceSetTitleFingerprint : Fingerprint(
    definingClass = "Landroidx/preference/Preference;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/CharSequence;"),
    returnType = "V",
)

/**
 * Preference.setVisible(boolean). The only (Z)V setter that stores + notifies via a
 * listener field. The first matched field is the visibility field of the preference.
 */
@StrictQuery
internal object PreferenceSetVisibleFingerprint : Fingerprint(
    definingClass = "Landroidx/preference/Preference;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Z"),
    returnType = "V",
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = "this"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this"),
    ),
)

internal const val SETTINGS_HEADERS_FRAGMENT_CLASS =
    "Lcom/google/android/apps/youtube/music/settings/fragment/SettingsHeadersFragment;"

/**
 * The YT Music top-level Settings fragment. onCreatePreferences reassigns its receiver
 * register to the peer object at entry; the runtime hook reads the real fragment receiver
 * of the invocation instead.
 */
@StrictQuery
internal object SettingsHeadersOnCreatePreferencesFingerprint : Fingerprint(
    definingClass = SETTINGS_HEADERS_FRAGMENT_CLASS,
    name = "onCreatePreferences",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;", "Ljava/lang/String;"),
    filters = listOf(
        anyInstruction(
            methodCall(definingClass = "this", name = "peer"),
            methodCall(definingClass = "this", name = "internalPeer")
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = SETTINGS_HEADERS_FRAGMENT_CLASS)
    )
)

/** Preference.setVisible(boolean). */
internal val preferenceSetVisibleMethod = findMethodDirect {
    PreferenceSetVisibleFingerprint.run()
}

/** The visibility field of Preference, stored by setVisible(boolean). */
internal val preferenceVisibleField = findFieldDirect {
    PreferenceSetVisibleFingerprint.instructionMatches[0].instruction.fieldRef!!
}
