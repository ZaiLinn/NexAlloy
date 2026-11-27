package io.github.nexalloy.morphe.reddit.misc.openlink

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

private object ArticleToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("Article(postId=")),
)

internal object ArticleConstructorFingerprint : Fingerprint(
    classFingerprint = ArticleToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
)

@StrictQuery
private object CustomReportsFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("https://www.crisistextline.org/"),
        methodCall(smali = "Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;"),
        methodCall(returnType = "V"),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

internal val screenNavigatorOpenLink = findMethodDirect {
    val forwardingMethod = checkNotNull(CustomReportsFingerprint.instructionMatches[2].instruction.methodRef)
    forwardingMethod.instructions.firstNotNullOf { instruction ->
        instruction.methodRef?.takeIf {
            it.returnTypeName == "void" &&
                it.paramTypeNames.take(2) == listOf("android.app.Activity", "android.net.Uri")
        }
    }
}

internal object FbpActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/fullbleedplayer/common/FbpActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
