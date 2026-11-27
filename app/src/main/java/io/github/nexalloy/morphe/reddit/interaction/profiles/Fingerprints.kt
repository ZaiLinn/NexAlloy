/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5189
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.interaction.profiles

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.Opcode

private const val QUERY_CLASS = "Lcom/reddit/domain/model/search/Query;"

@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object ProfileModelToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("ProfileModel(username="),
        // Username.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(5)
        ),
        string(", isProfileFiltered="),
        // Is filtered (posts or comments hidden by the profile settings).
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            location = MatchAfterWithin(8)
        )
    )
)

/**
 * Click handler of the search button of a profile, that opens search scoped to the profile.
 */
@RequireAppVersion(minVersion = "2026.39.1", maxVersion = "2026.39.1")
@StrictQuery
internal object ProfileSearchClickFingerprint : Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        // Profile model.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            name = $$"$model"
        ),
        methodCall(
            definingClass = QUERY_CLASS,
            name = "<init>"
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lcom/reddit/domain/model/search/OriginPageType;",
            name = "PROFILE"
        ),
        // Opens search.
        methodCall(
            returnType = "V",
            parameters = listOf(
                "L",
                "Landroid/content/Context;",
                QUERY_CLASS,
                "Lcom/reddit/domain/model/search/SearchCorrelation;"
            )
        )
    )
)

internal val profileUsernameField = findFieldDirect {
    ProfileModelToStringFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal val profileFilteredField = findFieldDirect {
    ProfileModelToStringFingerprint.instructionMatches[3].instruction.fieldRef!!
}

internal val profileModelField = findFieldDirect {
    ProfileSearchClickFingerprint.instructionMatches.first().instruction.fieldRef!!
}

internal val openProfileSearchCall = findMethodDirect {
    ProfileSearchClickFingerprint.instructionMatches.last().instruction.methodRef!!
}
