package io.github.nexalloy.morphe.youtube.interaction.playlistautoplay

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import io.github.nexalloy.morphe.youtube.layout.playlistautoplay.NavigationIntentWrapperConstructorFingerprint
import org.luckypray.dexkit.result.FieldData

/**
 * Constructor of the native navigation state.
 * Captures the playlist repeat mode, as YouTube also uses the AUTOPLAY navigation
 * to repeat the current video.
 */
internal object NativeNavigationStateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    // Previous video available, next video available, repeat mode, shuffle enabled.
    parameters = listOf("Z", "Z", "I", "Z"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = "this"),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = "this", location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT, definingClass = "this", location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = "this", location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

/**
 * Static field of the navigation enum for [intent], such as PREVIOUS.
 */
internal fun playlistNavigationIntentFingerprint(enumType: String, intent: String) = Fingerprint(
    definingClass = enumType,
    name = "<clinit>",
    filters = listOf(
        string(intent),
        fieldAccess(type = enumType, opcode = Opcode.SPUT_OBJECT, location = MatchAfterWithin(3)),
    ),
)

/**
 * Static navigation request for one intent. 21.41+ also passes null descriptors to the constructor.
 */
internal fun playlistNavigationSingletonFingerprint(wrapperType: String, intent: FieldData) = Fingerprint(
    definingClass = wrapperType,
    name = "<clinit>",
    filters = listOf(
        fieldAccess(reference = intent, opcode = Opcode.SGET_OBJECT),
        methodCall(definingClass = wrapperType, name = "<init>", location = MatchAfterWithin(1)),
        fieldAccess(type = wrapperType, opcode = Opcode.SPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

/**
 * Method of the playback queue that picks the queue item for a navigation request.
 * A playlist from the server is loaded into this queue.
 */
internal fun queueNavigationItemFingerprint(requestType: String, enumType: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(requestType),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = requestType, type = enumType),
        methodCall(definingClass = enumType, name = "ordinal"),
    ),
)

/** The PREVIOUS value of the navigation intent enum. */
internal val previousNavigationIntentField = findFieldDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    playlistNavigationIntentFingerprint(enumType, "PREVIOUS").instructionMatches.last().instruction.fieldRef!!
}

/** The app's own request for the Previous button. */
internal val previousNavigationRequestField = findFieldDirect {
    val requestType = NavigationIntentWrapperConstructorFingerprint().declaredClass!!.descriptor
    playlistNavigationSingletonFingerprint(requestType, previousNavigationIntentField())
        .instructionMatches.last().instruction.fieldRef!!
}

internal val queueNavigationItemMethod = findMethodDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    val requestType = NavigationIntentWrapperConstructorFingerprint().declaredClass!!.descriptor
    queueNavigationItemFingerprint(requestType, enumType).match().method
}

/** The navigation intent enum field of a navigation request. */
internal val queueNavigationIntentField = findFieldDirect {
    val enumType = NavigationIntentEnumFingerprint().declaredClass!!.descriptor
    val requestType = NavigationIntentWrapperConstructorFingerprint().declaredClass!!.descriptor
    queueNavigationItemFingerprint(requestType, enumType).instructionMatches.first().instruction.fieldRef!!
}
