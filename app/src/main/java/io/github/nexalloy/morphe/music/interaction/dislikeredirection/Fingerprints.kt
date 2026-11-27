package io.github.nexalloy.morphe.music.interaction.dislikeredirection

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.string

/**
 * The static initializer of the notification like button controller. The string is a
 * substring so the class matches before 8.x (`NotificationLikeButtonController`) and
 * from 8.x on (`DefaultNotificationLikeButtonController`).
 */
internal object NotificationLikeButtonControllerClinitFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = listOf(),
    strings = listOf(
        string(
            "NotificationLikeButtonController",
            comparison = StringComparisonType.CONTAINS
        )
    )
)

/**
 * The listener of the notification like buttons that skips to the next track after a
 * track is disliked. The last filter matches the queue navigation dispatch.
 * Example: `Licd;->f(Lamvu;)V`
 */
internal object NotificationLikeButtonOnClickListenerFingerprint : Fingerprint(
    classFingerprint = NotificationLikeButtonControllerClinitFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("L", "L", "Ljava/util/Map;")
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "V",
            parameters = listOf("L")
        )
    )
)

/**
 * The code that skips to the next track after the current track is disliked, found in the
 * dislike command handler (and on 9.36+ also in the like button click listener):
 * `String.equals()` -> `queue.canSkip(NEXT)` -> `helper.command(NEXT, null, null)` ->
 * `queue.navigate(command)`.
 */
private fun dislikeSkipToNextFingerprint() = Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(definingClass = "Ljava/lang/String;", name = "equals"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "Z",
            parameters = listOf("L"),
            location = MatchAfterWithin(5)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("L", "L", "Ljava/util/Map;"),
            location = MatchAfterWithin(5)
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "V",
            parameters = listOf("L"),
            location = MatchAfterWithin(2)
        )
    )
)

context(dexkit: org.luckypray.dexkit.DexKitBridge)
private fun dislikeSkipMatches() = dislikeSkipToNextFingerprint().matchAllOrNull() ?: emptyList()

/**
 * The dislike handlers that skip to the next track, each with a single queue navigation
 * call reached only on the dislike path. 9.35+.
 * Example: `Laaea;->I(Lakgm;Ljava/util/Map;)V` and `Lhhw;->onClick(Landroid/view/View;)V`.
 */
internal val dislikeSkipMethods = findMethodListDirect {
    dislikeSkipMatches().map { it.method }
}

/**
 * The implementations of the queue navigation interface method the dislike handlers call
 * (e.g. `Lacaq;->i(Labzj;)V` -> `Lacan;->i(Labzj;)V` and `Lihz;->i(Labzj;)V`).
 */
internal val dislikeNavigateMethods = findMethodListDirect {
    val navigate = dislikeSkipMatches().first().instructionMatches.last().instruction.methodRef!!
    Fingerprint(
        interfaces = listOf(navigate.declaredClass!!.descriptor),
        name = navigate.name,
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = navigate.paramTypes.map { it.descriptor },
    ).matchAllOrNull()?.map { it.method } ?: emptyList()
}

/**
 * The command factory call of the dislike handlers, whose first parameter is the
 * navigation command type enum (e.g. `Libw;->c(Labzi;...)` -> `Labzi;` with NEXT).
 */
internal val dislikeCommandFactoryMethod = findMethodDirect {
    dislikeSkipMatches().first().instructionMatches[2].instruction.methodRef!!
}
