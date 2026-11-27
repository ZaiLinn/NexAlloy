package io.github.nexalloy.morphe.youtube.interaction.chapterskip

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.opcode

internal object SeekTypeEnumFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    strings = listOf(
        "SEEK_SOURCE_SEEK_TO_NEXT_CHAPTER",
        "SEEK_SOURCE_SEEK_TO_PREVIOUS_CHAPTER"
    )
)

internal object DoubleTapInfoCtorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(
        "Landroid/view/MotionEvent;",
        "I",
        "Z",
        "Lj$/time/Duration;"
    )
)

// The 21.11+ A/B feature flag (literal 45767151) that turns off the two-finger double tap
// chapter skip is overridden with the module's literal override mechanism
// (FeatureOverride in YoutubeHook.kt), which resolves the flag by its literal id
// and does not need a fingerprint.

/**
 * Where the two-finger double tap chapter skip is turned on: a server config value
 * (and on 21.11+ an A/B flag that turns it off, see [DoubleTapChapterSkipFeatureFlagFingerprint]).
 *
 * The third parameter is an android.os.Handler, expressed by pinning the full parameter
 * type list of the method (the Handler parameter is the only concrete type).
 */
internal object DoubleTapChapterSkipConfigFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf(
        "L", "L", "Landroid/os/Handler;", "L", "L", "L", "L", "L"
    ),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, type = "Z"),
        opcode(Opcode.CONST_4, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, type = "Z", location = MatchAfterWithin(10))
    )
)

/**
 * The two-finger double tap info method that returns the seek source,
 * such as SEEK_TO_NEXT_CHAPTER.
 */
internal fun doubleTapInfoGetSeekSourceFingerprint(seekTypeClass: String) = Fingerprint(
    classFingerprint = DoubleTapInfoCtorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = seekTypeClass,
    parameters = listOf("Z")
)
