/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2261
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package io.github.nexalloy.morphe.youtube.layout.playbackinfeeds

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string

@StrictQuery
internal object PlaybackInFeedsSettingFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("inline_global_play_pause"),
        methodCall(definingClass = "Landroidx/preference/ListPreference;", parameters = listOf("[Ljava/lang/CharSequence;")),
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L", "L"), returnType = "I"),
    ),
)

private val playbackInFeedsControllerClass = findClassDirect {
    PlaybackInFeedsSettingFingerprint.instructionMatches.last().instruction.methodRef!!.declaredClass!!
}

internal object PlaybackInFeedsControllerConstructorFingerprint : Fingerprint(
    definingClass = { playbackInFeedsControllerClass().descriptor },
    name = "<init>",
)

internal object PlaybackInFeedsGetModeFingerprint : Fingerprint(
    definingClass = { playbackInFeedsControllerClass().descriptor },
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(methodCall(smali = "Ljava/util/concurrent/atomic/AtomicInteger;->get()I")),
)

internal object PlaybackInFeedsSetModeFingerprint : Fingerprint(
    definingClass = { playbackInFeedsControllerClass().descriptor },
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(methodCall(returnType = "Lcom/google/common/util/concurrent/ListenableFuture;")),
)

@RequireAppVersion("21.06.000")
@StrictQuery
internal object SimpleAutoplayableToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        string("SimpleAutoplayable{inlinePlaybackStyleType="),
        string(", enableSeekingUi="),
        methodCall(
            smali = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            smali = "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;",
            location = MatchAfterWithin(2),
        ),
    ),
)

@get:RequireAppVersion("21.06.000")
internal val enableSeekingUiGetter = findMethodDirect {
    val match = SimpleAutoplayableToStringFingerprint.match()
    val append = match.instructionMatches!!.last()
    val valueRegister = append.instruction.register(1)
    val field = match.method.instructions.take(append.index).last {
        Opcode.fromInt(it.opcode) == Opcode.IGET_BOOLEAN && it.registerOrNull(0) == valueRegister
    }.fieldRef!!
    Fingerprint(
        definingClass = match.method.declaredClass!!.descriptor,
        returnType = "Z",
        parameters = emptyList(),
        filters = listOf(
            fieldAccess(reference = field, opcode = Opcode.IGET_BOOLEAN),
            opcode(Opcode.RETURN, location = MatchAfterImmediately()),
        ),
    )()
}

@RequireAppVersion("21.24.000")
@StrictQuery
internal object SeekBackwardsToggleInflateFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "seek_backwards_toggle_container_stub"),
        methodCall(smali = "Landroid/view/ViewStub;->inflate()Landroid/view/View;", location = MatchAfterWithin(8)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)
