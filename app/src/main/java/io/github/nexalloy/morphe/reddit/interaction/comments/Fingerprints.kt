/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3517
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.interaction.comments

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode

internal const val COMMENT_CLASS = "Lcom/reddit/domain/model/Comment;"

/**
 * Getter of the domain comment collapsed flag.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object CommentGetCollapsedFingerprint : Fingerprint(
    definingClass = COMMENT_CLASS,
    name = "getCollapsed",
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z"
        ),
        opcode(
            opcode = Opcode.RETURN,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Comment tree store (RedditCommentTreeStore) helper that replaces one item of the
 * id -> comment map with the result of a transform:
 *
 * ```
 * static Map update(Map map, Object key, Function1 transform) {
 *     Object old = map.get(key);
 *     return old == null ? map : plus(map, new Pair(key, transform.invoke(old)));
 * }
 * ```
 *
 * Collapse, expand and collapse-thread all go through it (with a transform that copies
 * the comment with a different `collapsed` value).
 *
 * Before 2026.14.0 the Function1 type is obfuscated, so it is not used for matching.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object CommentTreeReplaceItemFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/comments/tree/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/util/Map;",
    parameters = listOf(
        "Ljava/util/Map;",
        "Ljava/lang/Object;",
        "L"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/util/Map;",
            name = "get"
        ),
        methodCall(
            name = "invoke",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Ljava/lang/Object;"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

internal val commentCollapsedField = findFieldDirect {
    CommentGetCollapsedFingerprint.instructionMatches.first().instruction.fieldRef!!
}
