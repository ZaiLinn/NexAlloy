package io.github.nexalloy.morphe.youtube.misc.bypasslinkredirects

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string

/**
 * Command resolver that opens a comment link in the in-app WebView bottom sheet.
 * The redirect Uri is held in a local register that flows into the VIEW intent constructor.
 */
internal object VideoCommentsUriFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("L", "Ljava/util/Map;"),
    returnType = "V",
    strings = listOf(
        "parentCsn",
        "URL_KEY",
        "navigation_endpoint",
        "WEB_VIEW_BOTTOM_SHEET_TAG",
    ),
    filters = listOf(
        string("android.intent.action.VIEW"),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            smali = "Landroid/content/Intent;-><init>(Ljava/lang/String;Landroid/net/Uri;)V",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Opens a video description or community post link,
 * with the redirect Uri as its second parameter.
 */
internal object DescriptionAndPostUriFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;", "Landroid/net/Uri;"),
    returnType = "Z",
    strings = listOf(
        "android.intent.action.VIEW",
        "text/html",
        "Activity not found to view uri",
    ),
)
