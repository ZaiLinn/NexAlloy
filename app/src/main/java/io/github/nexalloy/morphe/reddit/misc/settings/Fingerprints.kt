package io.github.nexalloy.morphe.reddit.misc.settings

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.string

/**
 * Constructor of the Compose settings row "Privacy policy", which is copied as the Morphe settings
 * row. The class is shared with the other rows of the About section, such as "User agreement".
 * Its constructor is (settings context, row kind). The field of the row key is written right after
 * the const-string of the privacy policy key.
 *
 * 2026.30.0+
 */
internal object PrivacyPolicySettingRowConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        string("key_pref_privacy_policy"),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately(),
        ),
    ),
)

/** Field of the row key, of the class found by [PrivacyPolicySettingRowConstructorFingerprint]. */
internal val settingRowKeyField = findFieldDirect {
    PrivacyPolicySettingRowConstructorFingerprint.instructionMatches[1].instruction.fieldRef!!
}

/**
 * Composable content of the "Privacy policy" settings row. Its last filter match is the call of
 * the settings row composable (title, on click, modifier, has subtitle?, subtitle, leading icon,
 * trailing content, ?, has divider?, composer, changed, defaults).
 *
 * 2026.30.0+
 */
internal object SettingRowContentFingerprint : Fingerprint(
    classFingerprint = PrivacyPolicySettingRowConstructorFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC_RANGE,
            returnType = "V",
            parameters = listOf(
                "Ljava/lang/String;", // Title.
                "Lkotlin/jvm/functions/Function0;", // On click.
                "L", // Modifier.
                "Z",
                "Ljava/lang/String;", // Subtitle.
                "Lkotlin/jvm/functions/Function2;", // Leading icon.
                "Lkotlin/jvm/functions/Function2;", // Trailing content.
                "L",
                "Z",
                "L", // Composer.
                "I",
                "I",
            ),
        ),
    ),
)

/** The settings row composable called by the content of the row. */
internal val settingRowComposable = findMethodDirect {
    SettingRowContentFingerprint.instructionMatches.last().instruction.methodRef!!
}

/**
 * Section of the settings row, an instance method without parameters.
 *
 * 2026.30.0+
 */
internal object SettingRowSectionFingerprint : Fingerprint(
    classFingerprint = PrivacyPolicySettingRowConstructorFingerprint,
    isStatic = false,
    returnType = "Lcom/reddit/settings/usersettings/UserSettingsSection;",
    parameters = listOf(),
)

/**
 * Order of the settings row in its section.
 *
 * 2026.30.0+
 */
internal object SettingRowOrderFingerprint : Fingerprint(
    classFingerprint = PrivacyPolicySettingRowConstructorFingerprint,
    name = "getOrder",
    returnType = "I",
    parameters = listOf(),
)

/**
 * Constructor of the registry of all Compose settings rows and sections: (rows, sections).
 * It throws if two rows have the same key.
 *
 * 2026.30.0+
 */
@StrictQuery
internal object SettingsRegistryConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Ljava/util/Set;", "Ljava/util/Set;"),
    filters = listOf(
        newInstance("Lcom/reddit/settings/usersettings/UserSettingsContributionException;"),
    ),
)

/**
 * onCreate of the in-app browser activity, which formerly hosted the Morphe settings of the
 * older app settings. Still intercepted, so other entry points keep working.
 */
internal object WebBrowserActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/webembed/browser/WebBrowserActivity;",
    name = "onCreate",
    returnType = "V",
    strings = listOf("com.reddit.extra.initial_url"),
)

/**
 * toString of the icon data of the Reddit icon set, a vector drawable resource and its size.
 */
internal object IconDataToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Icon(resourceId="),
    ),
)

/**
 * Icon composable of the Reddit icon set:
 * (icon, content description, modifier, tint, auto mirrored, composer, changed, defaults).
 */
internal class IconComposableFingerprint(iconDataType: String) : Fingerprint(
    isStatic = true,
    returnType = "V",
    parameters = listOf(
        iconDataType,
        "Ljava/lang/String;",
        "L",
        "J",
        "Z",
        "L",
        "I",
        "I",
    ),
)
