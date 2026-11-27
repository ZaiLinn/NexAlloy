package io.github.nexalloy.morphe.music.video.playerresponse

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint

/**
 * The player parameter builder. Called for every playback request with the video id and
 * the player parameters. Upstream matches it as "public final, returns an object, at least
 * 13 parameters starting with (String videoId, byte[] trackingParams, String player
 * parameter proto, String playlistId, int playlistIndex)"; the module DSL matches parameter
 * lists exactly, so the full signature is pinned here.
 * Anchor (9.46.51): `Labwu;->c(Ljava/lang/String;[BLjava/lang/String;Ljava/lang/String;IZILapta;Ljava/util/Set;Ljava/lang/String;Ljava/lang/String;Lxju;ZZZLj$/time/Duration;)Labww;`
 * (`abww.a = videoId`, `abww.c = player parameter`, `abww.d = playlistId`, `abww.e = playlistIndex`).
 */
internal object PlayerParameterBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(
        "Ljava/lang/String;", // VideoId.
        "[B",                 // Tracking params.
        "Ljava/lang/String;", // Player parameters proto buffer.
        "Ljava/lang/String;", // PlaylistId.
        "I",                  // PlaylistIndex.
        "Z",
        "I",
        "L",
        "Ljava/util/Set;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "L",
        "Z",
        "Z",
        "Z",
        "L"
    )
)
