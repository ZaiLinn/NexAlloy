package io.github.nexalloy.morphe.youtube.layout.hide.settingsmenu

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

/**
 * Preference.setTitle(CharSequence). The only final (CharSequence)V method of the class.
 *
 * Anchor (21.45.82): `androidx.preference.Preference.Q(CharSequence)V`.
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
 *
 * Anchor (21.45.82): `androidx.preference.Preference.R(boolean)V`, which stores `y`
 * and notifies the listener field `E`.
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

/**
 * The runnable of the standard settings screen host that shows the settings fragment
 * and then reads the preference screen of the fragment.
 *
 * Anchor (21.45.82): the synthetic `run()` class member (jadx: inlined into
 * `pkv.l()`), which reads its captured settings fragment field, compares the
 * `:android:show_fragment_args` intent extra and calls the fragment's
 * `getPreferenceScreen()`. The first matched field is the settings fragment,
 * the third match is the `getPreferenceScreen()` call.
 */
@StrictQuery
internal object PreferenceScreenSyntheticFingerprint : Fingerprint(
    name = "run",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this"),
        string(":android:show_fragment_args"),
        methodCall(parameters = listOf(), returnType = "Landroidx/preference/PreferenceScreen;"),
        opcode(Opcode.RETURN_VOID),
    ),
)

/** The settings fragment field of the screen show runnable. */
internal val settingsFragmentField = findFieldDirect {
    PreferenceScreenSyntheticFingerprint.instructionMatches[0].instruction.fieldRef!!
}

/** The getPreferenceScreen() method of the settings fragment. */
internal val getPreferenceScreenMethod = findMethodDirect {
    PreferenceScreenSyntheticFingerprint.instructionMatches[2].instruction.methodRef!!
}

/** Preference.setVisible(boolean). */
internal val preferenceSetVisibleMethod = findMethodDirect {
    PreferenceSetVisibleFingerprint.run()
}

/** The visibility field of Preference, stored by setVisible(boolean). */
internal val preferenceVisibleField = findFieldDirect {
    PreferenceSetVisibleFingerprint.instructionMatches[0].instruction.fieldRef!!
}
