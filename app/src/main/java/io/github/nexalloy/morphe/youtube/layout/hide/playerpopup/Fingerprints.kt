package io.github.nexalloy.morphe.youtube.layout.hide.playerpopup

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string

internal object PlayerPopupPanelsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;", "L"),
    filters = listOf(
        string(
            string = "triggered_on_ui_ready",
            location = MatchAfterWithin(6)
        ),
        methodCall(
            smali = "Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            location = MatchAfterWithin(3)
        ),
        methodCall(
            smali = "Ljava/util/Iterator;->hasNext()Z",
            location = MatchAfterWithin(4)
        )
    )
)
