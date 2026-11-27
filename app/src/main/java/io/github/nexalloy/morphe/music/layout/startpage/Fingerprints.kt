package io.github.nexalloy.morphe.music.layout.startpage

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.result.FieldData

/**
 * Returns the browse id the app opens on a cold start.
 *
 * YT Music 9.46.51: jqq.g() — returns the browse id of the account settings or
 * "FEmusic_home", or "FEmusic_library_sideloaded_tracks" when the account has no home feed.
 */
internal object ColdStartUpFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("FEmusic_library_sideloaded_tracks"),
        string("FEmusic_home")
    )
)

/**
 * 9.26+
 * Checks if a library page opens inside the Library tab:
 * `libraryPages.contains(browseId) && (libraryRootPages.contains(browseId) || "FEmusic_library_landing".equals(activeRootTag))`.
 *
 * YT Music 9.46.51: ggh.r(String) — the active root tag is the c field of the c field of this.
 */
internal object OpenInLibraryTabFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string("FEmusic_library_landing"),
        methodCall(
            definingClass = "Ljava/lang/String;",
            name = "equals",
            location = MatchAfterWithin(6)
        )
    )
)

/**
 * The fields that hold the active root tag the library tab check reads:
 * this -> c (the tab state holder) -> c (the tag of the active root tab).
 *
 * Resolved by walking the field reads that produce the equals argument register.
 */
val activeRootTagOwnerFields = findFieldListDirect {
    val match = OpenInLibraryTabFingerprint.match()
    val equals = match.instructionMatches[1]
    val instructions = match.method.instructions!!
    var receiver = equals.instruction.registers[1]
    var before = equals.index
    val fields = mutableListOf<FieldData>()
    while (true) {
        val producer = instructions.take(before).lastOrNull {
            it.opcode == Opcode.IGET_OBJECT.ordinal && it.registers[0] == receiver
        } ?: break // The receiver is the this register, which has no producer.
        fields += producer.fieldRef!!
        receiver = producer.registers[1]
        before = producer.index
    }
    check(fields.isNotEmpty()) { "Active root tag field chain not found" }
    fields.reversed()
}
