package io.github.nexalloy.morphe.music.misc.audiovideoswitch

import io.github.nexalloy.AllowAbstract
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.getTypeNameCompat
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.returns
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/**
 * The queue operation that emits stable diagnostic strings from YouTube Music's queue operations
 * manager. Every other queue member is derived from this semantic entry point, the same stable
 * queue relationships the Jam patch of the upstream uses, so enabling both patches never changes
 * what is matched.
 * Anchor (9.46.51): `Lifd;->c(Lakgm;)V`, the queue command is `Lakgm;`.
 */
internal object QueueEnqueueFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf(
        "com/google/android/apps/youtube/music/player/queue/MusicPlaybackQueueOperationsManager",
        "enqueue",
        "enqueue item, QueueTarget: %s, position: %s",
    )
)

/**
 * The wrapper every part of the app reads its account-scoped preferences through, which prefix
 * every key with the account, like 'Don't play music videos'.
 * Anchor (9.46.51): `Ljns;->getBoolean(Ljava/lang/String;Z)Z`.
 */
internal object AccountSharedPreferencesGetBooleanFingerprint : Fingerprint(
    interfaces = listOf("Landroid/content/SharedPreferences;"),
    name = "getBoolean",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Z"),
)

/**
 * The constructor of the account preferences wrapper, whose instance is handed to the extension.
 * Anchor (9.46.51): `Ljns;-><init>(Lzza;Landroid/content/SharedPreferences;)V`.
 */
internal object AccountPreferencesConstructorFingerprint : Fingerprint(
    classFingerprint = AccountSharedPreferencesGetBooleanFingerprint,
    name = "<init>",
    returnType = "V",
)

/**
 * The registry the app parses its own messages with, so extensions such as the watch endpoint are
 * not parsed as unknown fields.
 * Anchor (9.46.51): `Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()`.
 */
internal object GeneratedRegistryFingerprint : Fingerprint(
    definingClass = "Lcom/google/protobuf/ExtensionRegistryLite;",
    name = "getGeneratedRegistry",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lcom/google/protobuf/ExtensionRegistryLite;",
    parameters = listOf(),
)

/**
 * The queue operations manager, the class declaring the enqueue operation.
 * Anchor (9.46.51): `Lifd;`, its only construction/capture point is its single constructor.
 */
internal val queueManagerClass = findClassDirect {
    QueueEnqueueFingerprint().declaredClass!!
}

/**
 * The constructor of the queue operations manager, hooked to capture the queue the extension
 * operates on. Anchor (9.46.51): `Lifd;-><init>(...)V`, a single constructor.
 */
internal val queueManagerConstructor = findMethodDirect {
    Fingerprint(
        definingClass = { queueManagerClass().descriptor },
        name = "<init>",
        returnType = "V",
    ).run()
}

/**
 * The command proto the enqueue operation takes, the same command the resolver of the album patch
 * resolves. Anchor (9.46.51): `Lakgm;`.
 */
internal val queueCommandClass = findClassDirect {
    QueueEnqueueFingerprint().paramTypes.single()
}

/**
 * The default instance of the queue command, the messages of the extension are parsed with.
 * Anchor (9.46.51): `Lakgm;->a:Lakgm;`.
 */
internal val queueCommandDefaultInstanceField = findFieldDirect {
    val command = queueCommandClass()
    command.fields.single { field ->
        Modifier.isStatic(field.modifiers) && field.type.descriptor == command.descriptor
    }
}

/**
 * The parser of the queue command, inherited from the generated message base, whose method names
 * are not obfuscated. Anchor (9.46.51): `Lairp;->parseFrom(Lairp;[BLcom/google/protobuf/ExtensionRegistryLite;)Lairp;`.
 */
internal val queueCommandParser = findMethodDirect {
    var cls: ClassData? = queueCommandClass()
    while (cls != null) {
        val current = cls
        val parser = runCatching {
            Fingerprint(
                definingClass = current.descriptor,
                name = "parseFrom",
                isStatic = true,
            ).matchAllOrNull().orEmpty()
        }.getOrNull().orEmpty().map { it.method }.singleOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                    method.returnType?.descriptor == current.descriptor &&
                    method.paramTypes.map { it.descriptor } ==
                    listOf(current.descriptor, "[B", "Lcom/google/protobuf/ExtensionRegistryLite;")
        }
        if (parser != null) return@findMethodDirect parser

        cls = current.superClass
    }
    error("Unable to resolve audio/video switch queue command parser")
}

/**
 * The executor every native queue mutation runs on, the only executor field of the manager.
 * Anchor (9.46.51): `Lifd;->b:Ljava/util/concurrent/Executor;`.
 */
internal val queueExecutorField = findFieldDirect {
    queueManagerClass().fields.single { it.type.descriptor == "Ljava/util/concurrent/Executor;" }
}

// region Queue storage.

/**
 * A snapshot of the queue the storage class of the manager serves, one lane of the queue for.
 * Anchor (9.46.51): `Labor;->o(I)Ljava/util/List;`.
 */
private fun queueSnapshotFingerprint(type: String) = Fingerprint(
    definingClass = type,
    parameters = listOf("I"),
    returnType = "Ljava/util/List;",
)

/**
 * The lane accessor of the queue storage, an observable, movable lane. The queue slice check of
 * the upstream is applied to the returned lane type before the lane is accepted.
 * Anchor (9.46.51): `Labor;->d(I)Ltab;`.
 */
private fun queueLaneAccessorFingerprint(type: String) = Fingerprint(
    definingClass = type,
    parameters = listOf("I"),
    returnType = "L",
)

/**
 * The queue storage reachable from the queue manager, resolved the same way the Jam patch of the
 * upstream resolves it: a manager field type whose class serves queue snapshots with lanes that
 * can move their entries and be sliced. Exactly one such storage exists.
 */
private val queueStorageCandidates: DexKitBridge.() -> List<Triple<ClassData, MethodData, List<MethodData>>> = {
    val candidates = queueManagerClass().fields
        .map { it.type }
        .distinctBy { it.descriptor }
        .flatMap { fieldType ->
            val snapshots = runCatching {
                queueSnapshotFingerprint(fieldType.descriptor).matchAllOrNull().orEmpty()
            }.getOrNull().orEmpty()
            snapshots.flatMap { snapshot ->
                val lanes = runCatching {
                    queueLaneAccessorFingerprint(snapshot.method.declaredClass!!.descriptor)
                        .matchAllOrNull().orEmpty()
                }.getOrNull().orEmpty()
                    .map { it.method }
                    .filter { it.returnType!!.descriptor != "Ljava/util/List;" }
                    .filter { lane ->
                        val laneType = lane.returnType!!.descriptor
                        // The lane can move its entries and be sliced.
                        runCatching {
                            Fingerprint(
                                definingClass = laneType,
                                parameters = listOf("I", "I"),
                                returnType = "V",
                            ).matchAllOrNull() != null &&
                                    Fingerprint(
                                        definingClass = laneType,
                                        parameters = listOf("I", "I"),
                                        returnType = "Ljava/util/List;",
                                    ).matchAllOrNull() != null
                        }.getOrDefault(false)
                    }
                if (lanes.isEmpty()) emptyList()
                else listOf(Triple(snapshot.method.declaredClass!!, snapshot.method, lanes))
            }
        }
    check(candidates.size == 1) {
        "Missing or ambiguous audio/video switch queue storage: ${candidates.size}"
    }
    candidates
}

/**
 * The queue storage class of the manager. Anchor (9.46.51): `Labor;`
 * ("com/google/android/libraries/youtube/player/features/queue/PlaybackQueueManager").
 */
internal val queueStorageClass = findClassDirect {
    queueStorageCandidates().single().first
}

/**
 * The storage field of the queue manager. Anchor (9.46.51): `Lifd;->c:Labor;`.
 */
internal val queueStorageField = findFieldDirect {
    val storage = queueStorageClass().descriptor
    queueManagerClass().fields.single { it.type.descriptor == storage }
}

/**
 * The queue snapshot accessor of the storage, which copies the items of one lane.
 * Anchor (9.46.51): `Labor;->o(I)Ljava/util/List;`.
 */
internal val queueStorageItemsMethod = findMethodDirect {
    queueStorageCandidates().single().second
}

/**
 * The lane accessor of the queue storage. The lane of index 0 is the primary lane of the queue.
 * Anchor (9.46.51): `Labor;->d(I)Ltab;`.
 */
internal val queueLaneMethod = findMethodDirect {
    queueStorageCandidates().single().third.single()
}

/**
 * The observable queue lane type of the storage. Anchor (9.46.51): `Ltab;`.
 */
internal val queueLaneClass = findClassDirect {
    queueLaneMethod().returnType!!
}

/**
 * The move operation of a queue lane. Anchor (9.46.51): `Ltab;->j(II)V`.
 */
@get:AllowAbstract
internal val queueLaneMoveMethod = findMethodDirect {
    Fingerprint(
        definingClass = { queueLaneClass().descriptor },
        parameters = listOf("I", "I"),
        returnType = "V",
    ).run()
}

/**
 * The current index of the queue, forwarded to the queue state the storage holds.
 * Anchor (9.46.51): `Labor;->a()I`, which calls `Labon;->hh()I`.
 */
internal val queueStorageCurrentIndexMethod = findMethodDirect {
    Fingerprint(
        definingClass = { queueStorageClass().descriptor },
        parameters = listOf(),
        returnType = "I",
        filters = listOf(
            methodCall(
                opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE),
                parameters = listOf(),
                returnType = "I",
            ),
        ),
    ).run()
}

/**
 * The local/remote mode of the queue, forwarded to the queue state the storage holds. The mode is
 * the only such forward whose return type is the enum that names its local constant "LOCAL".
 * Anchor (9.46.51): `Labor;->f()Labom;`, which calls `Labon;->b()Labom;`.
 */
internal val queueStorageModeMethod = findMethodDirect {
    Fingerprint(
        definingClass = { queueStorageClass().descriptor },
        parameters = listOf(),
        returnType = "L",
        filters = listOf(
            methodCall(
                opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE),
                parameters = listOf(),
                returnType = "L",
            ),
        ),
    ).matchAllOrNull().orEmpty().map { it.method }.singleOrNull { method ->
        // The queue mode is the enum whose local constant is bound to its native singleton.
        val modeType = method.returnType ?: return@singleOrNull false
        runCatching {
            Fingerprint(
                definingClass = modeType.descriptor,
                name = "<clinit>",
                filters = listOf(string("LOCAL")),
            ).matchAllOrNull() != null
        }.getOrDefault(false)
    } ?: error("Missing or ambiguous audio/video switch queue mode")
}

/**
 * The LOCAL constant of the queue mode enum, bound to its native singleton by the enum
 * initialization. Anchor (9.46.51): `Labom;->a:Labom;` ("LOCAL").
 */
internal val queueLocalModeField = findFieldDirect {
    val modeType = queueStorageModeMethod().returnType!!.descriptor
    Fingerprint(
        definingClass = modeType,
        name = "<clinit>",
        filters = listOf(
            string("LOCAL"),
            fieldAccess(opcode = Opcode.SPUT_OBJECT, type = modeType),
        ),
    ).match().instructionMatches[1].instruction.fieldRef!!
}

/**
 * The listener interface of the queue lanes, the only parameter type the lane's single-parameter
 * methods take. Anchor (9.46.51): `Ltaa;`.
 */
internal val queueLaneListenerClass = findClassDirect {
    Fingerprint(
        definingClass = { queueLaneClass().descriptor },
        parameters = listOf("L"),
        returnType = "V",
    ).matchAll(2..2).map { it.method.paramTypes.single() }.distinct().single()
}

/**
 * The listener attach operation of a queue lane, the lane method whose implementations store the
 * listener in a set. Every attach implementation carries the name of the declaration it
 * implements, so the names of the set-storing implementations identify the operation.
 * Anchor (9.46.51): `Ltab;->hM(Ltaa;)V`, `Labov;->hM(Ltaa;)V` adds to a set.
 */
@get:AllowAbstract
internal val queueListenerAttachMethod = findMethodDirect {
    val laneType = queueLaneClass()
    val listenerType = queueLaneListenerClass().descriptor

    // Implementations of the lane listener operations that store the listener in a set.
    val adders = Fingerprint(
        parameters = listOf(listenerType),
        returnType = "V",
        filters = listOf(
            methodCall(
                definingClass = "Ljava/util/Set;",
                name = "add",
                opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE),
            ),
        ),
    ).matchAllOrNull().orEmpty().map { it.method }.filter { implementation ->
        interfaceClosureOf(implementation.declaredClass!!).any { it.descriptor == laneType.descriptor }
    }

    val declaration = adders
        .map { implementation -> implementation.name to implementation.paramTypes.map { it.descriptor } }
        .distinct()
        .singleOrNull()
        ?: error("Missing or ambiguous audio/video switch queue listener attach operation")

    // The lane interface declares the attach operation the implementations implement.
    Fingerprint(
        definingClass = { laneType.descriptor },
        name = declaration.first,
        parameters = declaration.second,
        returnType = "V",
    ).run()
}

// endregion

// region Queue display and removal.

/**
 * The two displayed queue controllers of the manager, whose classes hold the queue storage, a UI
 * handler and an observable lane. Removal updates both, unlike lane-specific operations.
 * Anchor (9.46.51): `Lifd;->f:Lifi;` (primary lane 0) and `Lifd;->v:Lifi;` (autoplay lane 1).
 */
private val queueDisplayFieldsValue: DexKitBridge.() -> List<FieldData> = {
    val storage = queueStorageClass().descriptor
    val lane = queueLaneClass().descriptor
    val displays = queueManagerClass().fields.filter { field ->
        val display = field.type
        display.fields.any { it.type.descriptor == storage } &&
                display.fields.any { it.type.descriptor == "Landroid/os/Handler;" } &&
                display.fields.any { it.type.descriptor == lane }
    }
    check(displays.size == 2) {
        "Unable to resolve audio/video switch displayed queue controllers: ${displays.size}"
    }
    displays
}

internal val queueDisplayFields = findFieldListDirect {
    queueDisplayFieldsValue()
}

/**
 * The queue removal operation of the manager, the only single-item operation that updates both
 * displayed queues. Its parameter is the queue item type.
 * Anchor (9.46.51): `Lifd;->e(Libm;)V`.
 */
internal val queueRemoveMethod = findMethodDirect {
    val displays = queueDisplayFieldsValue()
    Fingerprint(
        definingClass = { queueManagerClass().descriptor },
        parameters = listOf("L"),
        returnType = "V",
        filters = listOf(
            fieldAccess(smali = displays[0].descriptor, opcode = Opcode.IGET_OBJECT),
            fieldAccess(smali = displays[1].descriptor, opcode = Opcode.IGET_OBJECT),
        ),
    ).run()
}

/**
 * The queue item type the removal operation takes. Anchor (9.46.51): `Libm;`.
 */
internal val queueItemClass = findClassDirect {
    queueRemoveMethod().paramTypes.single()
}

/**
 * The video ID accessor of a queue item, declared by exactly one of its types, which declares
 * exactly one no-argument String accessor. Anchor (9.46.51): `Labpg;->t()Ljava/lang/String;`.
 */
@get:AllowAbstract
internal val queueItemVideoIdMethod = findMethodDirect {
    val closure = interfaceClosureOf(queueItemClass())
    findMethod {
        searchInClass(closure)
        matcher {
            paramTypes(listOf())
            returns("Ljava/lang/String;")
        }
    }.groupBy { it.declaredClass!!.descriptor }
        .values.singleOrNull { declarations -> declarations.size == 1 }
        ?.single()
        ?: error("Missing or ambiguous audio/video switch queue video ID")
}

// endregion

// region Audio/video switch pill.

/**
 * R.id.audio_video_switch_pill_container of YT Music 9.46.51.
 */
internal const val AUDIO_VIDEO_SWITCH_PILL_CONTAINER_ID = 2131427648L

/**
 * R.id.player_page of YT Music 9.46.51, the view the full player page, including the pill
 * container, is looked up from.
 */
internal const val PLAYER_PAGE_ID = 2131429707

/**
 * The view creations that find the audio/video switch pill container. The upstream patch injects
 * a call after each lookup, passing the found view.
 * Anchor (9.46.51): `Ljsi;-><init>(...)V` (full player page), `Lhyo;-><init>(...)V` (player
 * controls view) and `Lcom/google/android/apps/youtube/music/watch/WatchFragment;->onCreateView(...)`.
 */
private fun audioVideoSwitchPillContainerFingerprint() = Fingerprint(
    filters = listOf(
        literal(AUDIO_VIDEO_SWITCH_PILL_CONTAINER_ID),
        methodCall(name = "findViewById", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = InstructionLocation.MatchAfterImmediately()),
    )
)

internal val audioVideoSwitchPillContainerMethods = findMethodListDirect {
    audioVideoSwitchPillContainerFingerprint().matchAll(3..3).map { it.method }
}

/**
 * The instance field each pill lookup reads its receiver from, an object field load at most a few
 * instructions before the findViewById call, or null when the receiver is the enclosing view, a
 * parameter or an earlier lookup result.
 * Anchor (9.46.51): only `Lcom/google/android/apps/youtube/music/watch/WatchFragment;->A:Lhyo;`.
 */
internal val audioVideoSwitchPillReceiverFields = findFieldListDirect {
    audioVideoSwitchPillContainerFingerprint().matchAll(3..3).mapNotNull { match ->
        val invoke = match.instructionMatches[1].index
        val instructions = match.method.instructions
        (1..3).firstNotNullOfOrNull { distance ->
            val instruction = instructions[invoke - distance]
            if (Opcode.fromInt(instruction.opcode) == Opcode.IGET_OBJECT) instruction.fieldRef else null
        }
    }
}

// endregion

/**
 * @return Every type the given type implements or extends, including itself.
 */
private fun interfaceClosureOf(type: ClassData): List<ClassData> {
    val visited = mutableSetOf<String>()
    val closure = mutableListOf<ClassData>()

    fun visit(cls: ClassData?) {
        if (cls == null || !visited.add(cls.descriptor)) return
        closure.add(cls)
        cls.interfaces.forEach(::visit)
        visit(cls.superClass)
    }

    visit(type)
    return closure
}
