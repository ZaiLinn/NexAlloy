/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/1953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/buttons/action/Fingerprints.kt
 * and the tree-node list fingerprints of
 * morphe-patches/patches/src/main/kotlin/app/morphe/patches/shared/misc/litho/node/Fingerprints.kt).
 *
 * The tree-node list fingerprints are upstream shared-litho fingerprints but are only used by
 * this music patch, so they live here instead of the shared module package, whose fingerprints
 * are also tested against the YouTube host.
 */

package io.github.nexalloy.morphe.music.layout.buttons.action

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode

/**
 * The public final method of the serialized proto message wrapper that encodes its buffer.
 * Upstream adds the ButtonProtoBufferInterface to this class; at runtime the class and the
 * getter are resolved the same way and invoked with reflection.
 * Anchor (9.46.51): `Lmhs;->c()[B`, calling `UpbMessage.jniEncode(JJ)[B`.
 */
internal object ButtonProtoBufferGetterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "[B",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Lcom/google/android/libraries/elements/adl/UpbMessage;->jniEncode(JJ)[B"
        )
    )
)

/**
 * The lazy tree-node list that resolves its converted element through atomic references.
 * Upstream adds the LithoGetBufferContainerInterface to this class, returning the field of
 * its first instruction match; the runtime walk reads the same field with reflection.
 * Anchor (9.46.51): `Lnvs;->aC(Lfim;)Lfih;`, the container field is `nvs.b`.
 */
@StrictQuery
internal object TreeNodeListFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "L",
            location = MatchAfterWithin(5) // Match close to start of method.
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/util/concurrent/atomic/AtomicReference;",
            location = MatchAfterWithin(5)
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/util/concurrent/atomic/AtomicReference;",
            location = MatchAfterWithin(2)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Ljava/util/concurrent/atomic/AtomicReference;->get()Ljava/lang/Object;",
            location = MatchAfterWithin(2)
        )
    )
)

/**
 * The parent of the lazy element helper, recognized by the range invoke of the layout method
 * it delegates to. The upstream fingerprint additionally checks the parameter layout of that
 * range invoke (8 parameters, the 6th an int and the 8th a boolean) inside a custom query;
 * the runtime module disambiguates with instruction matching instead.
 * Anchor (9.46.51): `Laelk;->a(Lfim;Lnpv;)Lfih;`, the cid 1 branch delegating to `mhm.d`.
 */
@StrictQuery
internal object TreeNodeListHelperParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L", "L"),
    filters = listOf(
        newInstance("Ljava/util/ArrayList;"),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            smali = "Ljava/util/ArrayList;-><init>()V"
        ),
        // The upstream fingerprint checks the parameter layout of the range invoke in a
        // custom query (8 parameters, the 6th an int and the 8th a boolean); the runtime
        // module constrains the same positions of the invoked method instead.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL_RANGE,
            parameters = listOf("L", "L", "L", "L", "Ljava/util/List;", "I", "L", "Z"),
            returnType = "L"
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "V",
            parameters = listOf("L", "L")
        )
    )
)

/**
 * The constructor of the lazy element helper. The class can be a merged lambda, and the
 * constructor of the other lambda only stores two objects before the int.
 * Instruction match 1 is the container field the upstream interface returns.
 * Anchor (9.46.51): `Laelk;-><init>(Lmhm;Lmzh;Lnqq;ILkmm;I)V`, the container field is `aelk.b`.
 */
@StrictQuery
internal object TreeNodeListHelperConstructorFingerprint : Fingerprint(
    classFingerprint = TreeNodeListHelperParentFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_OBJECT),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT, location = MatchAfterImmediately())
    )
)
