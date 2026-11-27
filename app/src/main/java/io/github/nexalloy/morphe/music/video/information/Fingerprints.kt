package io.github.nexalloy.morphe.music.video.information

import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.shared.misc.videoinformation.seekSourceUnknownField
import io.github.nexalloy.morphe.string

/**
 * The seek method of the music player (the media library director), returning true if the
 * player accepted the seek. Its second parameter is the seek source enum.
 * Anchor (9.46.51): `Lackr;->aq(JLaqam;)Z`, the two seek diagnostics are only in this method.
 */
internal object VideoEndFingerprint : Fingerprint(
    parameters = listOf("J", "L"),
    strings = listOf("currentPositionMs.", ";seekTimeUs.")
)

/**
 * Called with the player response model and the video id when a new track loads.
 * Anchor (9.46.51): `Lacnb;->M(Lwkk;Ljava/lang/String;)Lacna;`.
 */
internal object VideoIdFingerprint : Fingerprint(
    parameters = listOf("L", "Ljava/lang/String;"),
    strings = listOf("Null initialPlayabilityStatus")
)

/**
 * Reports the media progress about once a second. The playback position in milliseconds is
 * the second long parameter; the first long parameter is a -1 sentinel
 * (`new abdt(J position, J, J, J, J, J, J, Z, String)` is built with it).
 * Anchor (9.46.51): `Lackr;->bO(Lacoa;JJJJZII)V` (LocalDirector.broadcastCurrentProgress).
 */
internal object PlayerControllerSetTimeReferenceFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("J", "J", "J", "J", "J", "J", "J", "Z", "Ljava/lang/String;")
        ),
        string(
            "Media progress reported outside media playback: ",
            comparison = StringComparisonType.CONTAINS
        )
    )
)

/**
 * The seek method of the music player, resolved from [VideoEndFingerprint].
 * Anchor (9.46.51): `Lackr;->aq(JLaqam;)Z`.
 */
internal val musicPlayerSeekMethod = findMethodDirect {
    VideoEndFingerprint()
}

/**
 * The static SEEK_SOURCE_UNKNOWN constant of the seek source enum
 * (the second parameter of [VideoEndFingerprint]).
 * Anchor (9.46.51): `Laqam;->SEEK_SOURCE_UNKNOWN`.
 */
internal val musicSeekSourceUnknownField = findFieldDirect {
    seekSourceUnknownField(VideoEndFingerprint().paramTypes[1].descriptor)
}

/**
 * The video id getter of the player response model. Upstream reads the id from the model
 * through the first no-argument interface call that returns a String inside the
 * [VideoIdFingerprint] method (`wkk.G()` in 9.46.51, `acnb.M` stores it as currentVideoId).
 */
@get:AllowAbstract
internal val musicVideoIdMethod = findMethodDirect {
    VideoIdFingerprint().instructions?.firstOrNull {
        val methodRef = it.methodRef ?: return@firstOrNull false
        (it.opcode == Opcode.INVOKE_INTERFACE.opCode || it.opcode == Opcode.INVOKE_INTERFACE_RANGE.opCode) &&
                methodRef.returnType?.descriptor == "Ljava/lang/String;" &&
                methodRef.paramTypes.isEmpty()
    }?.methodRef ?: throw NoSuchMethodException("Video id getter not found in the VideoId method")
}

/**
 * The video length getter of the player response model: the first parameterless long method
 * of the model interface, the same way upstream resolves it.
 * Anchor (9.46.51): `Lwkk;->d()J` returns the streaming duration in ms (`wkn.d()` reads
 * `wkf.h` or converts seconds); the other no-arg long method `wkk.e()` is not the length.
 */
@get:AllowAbstract
internal val musicVideoLengthMethod = findMethodDirect {
    val modelClassName = musicVideoIdMethod().declaredClass!!.descriptor
        .removePrefix("L").removeSuffix(";").replace('/', '.')
    findMethod {
        matcher {
            declaredClass(modelClassName)
        }
    }.first {
        it.returnType?.descriptor == "J" && it.paramTypes.isEmpty()
    }
}
