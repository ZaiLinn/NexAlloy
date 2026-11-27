package io.github.nexalloy.morphe.music.layout.sponsorblock

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterLast
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

private const val REMOVE_OFFLINE_TRACK_STRING = "Couldn't delete single track through orchestration: %s"

/**
 * Matches {@code MusicPlaybackControlsTimeBar.draw(Canvas)}.
 * Draws segment markers on the compact/mini player seekbar.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;->draw(Landroid/graphics/Canvas;)V`.
 */
internal object MusicTimeBarDrawFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;",
    name = "draw",
    returnType = "V"
)

/**
 * Matches {@code MusicPlaybackControlsTimeBar.onMeasure(int, int)}.
 * Resolves the Rect field used for seekbar bounds.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;->onMeasure(II)V`.
 */
internal object MusicTimeBarOnMeasureFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;",
    name = "onMeasure",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
            location = MatchAfterLast()
        )
    )
)

/**
 * The Rect of the seekbar bounds. The track Rect the time bar draws itself with.
 * Anchor (9.46.51): `Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;->o:Landroid/graphics/Rect;`.
 */
internal val musicTimeBarRectField = findFieldDirect {
    MusicTimeBarOnMeasureFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/**
 * Matches {@code MusicTrackDownloadController.removeSingleTrack(String, String)}.
 * Removes a saved song or video, after any prompt is confirmed.
 * Anchor (9.46.51): `Lhws;->b(Ljava/lang/String;Ljava/lang/String;)Lavwv;`.
 */
internal object MusicRemoveOfflineTrackFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf(
        "Ljava/lang/String;", // Video ID
        "Ljava/lang/String;"
    ),
    strings = listOf(REMOVE_OFFLINE_TRACK_STRING)
)

/**
 * Removes a saved song or video with its own copy of the code of [MusicRemoveOfflineTrackFingerprint].
 * Returns early without removing it if the app cannot remove it right now.
 * Anchor (9.46.51): `Lhph;->c(Ljava/lang/String;)V`, which returns early if `Lteh;->k()` is false.
 */
internal object MusicRemoveOfflineTrackActionFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"), // Video ID
    filters = listOf(
        // The guard the early return checks.
        methodCall(returnType = "Z"),
        opcode(Opcode.RETURN_VOID), // Early return.
        string(REMOVE_OFFLINE_TRACK_STRING)
    )
)

/**
 * The guard of the early return of [MusicRemoveOfflineTrackActionFingerprint]. The removal
 * proceeds only while the guard holds, and the upstream injects the call of the removal
 * after the early return, so the segment deletion is run only when the guard holds as well.
 * Anchor (9.46.51): `Lhph;->r:Lteh;`, guarded with `Lteh;->k()Z`.
 */
internal val offlineTrackActionGuardMethod = findMethodDirect {
    MusicRemoveOfflineTrackActionFingerprint.instructionMatches.first().instruction.methodRef!!
}

/** The receiver the guard of [MusicRemoveOfflineTrackActionFingerprint] is read from. */
internal val offlineTrackActionGuardField = findFieldDirect {
    val match = MusicRemoveOfflineTrackActionFingerprint.match()
    val guardIndex = match.instructionMatches.first().index
    val receiver = match.method.instructions[guardIndex].register(0)
    match.method.instructions.take(guardIndex).last {
        Opcode.fromInt(it.opcode) == Opcode.IGET_OBJECT && it.registerOrNull(0) == receiver
    }.fieldRef!!
}

