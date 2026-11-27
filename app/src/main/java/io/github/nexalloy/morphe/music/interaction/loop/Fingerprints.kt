package io.github.nexalloy.morphe.music.interaction.loop

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterAnywhere
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode

internal const val TIME_BAR_CLASS =
    "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;"

/**
 * The seek method of the media library player wrapper.
 * Example: `Lyto;->r(JLaqam;)V`
 */
internal object MedialibPlayerSeekToFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("J", "L"),
    strings = listOf("MedialibPlayer.seekTo(positionMillis=%d)")
)

/**
 * Sets the scrub thumb position from the touch x coordinate. The first field access gives
 * the Rect of the seekbar thumb center bounds, used to draw the loop markers.
 * Example: `MusicPlaybackControlsTimeBar->c(F)V`, Rect field `o`.
 */
internal object TimeBarSetScrubPositionFingerprint : Fingerprint(
    definingClass = TIME_BAR_CLASS,
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;"
        ),
        methodCall(definingClass = "Ljava/lang/Math;", name = "max")
    )
)

/**
 * The time formatter call, its toString() and the resulting label string.
 */
private fun timeLabelFilters(location: io.github.nexalloy.morphe.InstructionLocation) = listOf(
    methodCall(
        opcode = Opcode.INVOKE_STATIC,
        parameters = listOf("J"),
        returnType = "Ljava/lang/CharSequence;",
        location = location
    ),
    methodCall(
        definingClass = "Ljava/lang/CharSequence;",
        name = "toString",
        location = MatchAfterWithin(3)
    ),
    opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
)

/**
 * Draws the time bar. The remaining time label is formatted from a negated value right
 * after the negation, the elapsed time label afterwards.
 * Example: `MusicPlaybackControlsTimeBar->draw(Landroid/graphics/Canvas;)V`
 */
internal object TimeBarDrawFingerprint : Fingerprint(
    definingClass = TIME_BAR_CLASS,
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;"),
    filters = listOf(
        // Remaining time, formatted from a negated value.
        opcode(Opcode.NEG_LONG),
    ) + timeLabelFilters(MatchAfterImmediately()) +
        // Elapsed time.
        timeLabelFilters(MatchAfterAnywhere())
)

/**
 * The elapsed time formatter shared by the time bar labels.
 * Example: `Lidm;->a(J)Ljava/lang/CharSequence;`
 */
internal val timeLabelFormatter = findMethodDirect {
    TimeBarDrawFingerprint.match().instructionMatches[1].instruction.methodRef!!
}

/**
 * The Rect of the seekbar thumb center bounds, updated on every draw.
 * Example: `MusicPlaybackControlsTimeBar->o:Landroid/graphics/Rect;`
 */
internal val timeBarSeekbarRectField = findFieldDirect {
    TimeBarSetScrubPositionFingerprint.match().instructionMatches.first().instruction.fieldRef!!
}
