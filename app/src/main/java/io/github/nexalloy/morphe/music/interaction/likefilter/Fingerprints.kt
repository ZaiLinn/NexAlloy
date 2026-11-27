package io.github.nexalloy.morphe.music.interaction.likefilter

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.string

/**
 * The success callback of "Play next" and "Add to queue". The first filter reads the list of
 * songs that are added to the queue from the server response.
 * Anchor (9.46.51): `Lifb;->b(Ljava/lang/Object;)V`, the response is `Lihn;` and the item
 * list is `Lihn;->a:Ljava/util/List;`.
 */
internal object QueueEnqueueSuccessFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("L"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/util/List;"),
        string("MusicQueueCallback onSuccess, numItems: %d, insertIndex: %d"),
    )
)

/**
 * The list of queue items the enqueue success callback reads from the server response.
 * Anchor (9.46.51): `Lihn;->a:Ljava/util/List;`.
 */
internal val queueEnqueueSuccessItemsField = findFieldDirect {
    QueueEnqueueSuccessFingerprint.instructionMatches[1].instruction.fieldRef!!
}
