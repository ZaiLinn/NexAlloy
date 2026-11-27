package io.github.nexalloy.morphe.music.interaction.crossfade

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/**
 * Runtime translation of the upstream crossfade patcher patch
 * (app.morphe.patches.music.interaction.crossfade).
 *
 * The upstream patch discovers the player structure of the host at patch time
 * and injects `patch_*` bridge methods into the host classes, so the extension
 * can talk to them through its Access interfaces. A runtime hook module cannot
 * change class structures, so this port makes the same discovery at hook
 * install time and hands the extension hand-written adapter objects that
 * implement its Access interfaces and forward to the host with reflection.
 *
 * Every query below is scoped by a string anchor, a defining class or a
 * fingerprint of the anchor set in Fingerprints.kt, following the query
 * discipline of keeping dexkit candidate counts in the single digits.
 */

// region Discovery queries. Cached by property name, like every other find query.

/** The player coordinator (9.46.51: `yyr`). */
internal val crossfadeCoordinatorClass = findClassDirect {
    MusicPlayNextInQueueFingerprint().declaredClass!!
}

/** The MedialibPlayer (9.46.51: `yto`). */
internal val crossfadeMedialibPlayerClass = findClassDirect {
    MusicStopVideoFingerprint().declaredClass!!
}

/** The audio/video toggle (9.46.51: `hzr`). */
internal val crossfadeVideoToggleClass = findClassDirect {
    MusicAudioVideoToggleFingerprint().declaredClass!!
}

/** The ExoPlayer implementation (9.46.51: `byy`). */
internal val crossfadeExoPlayerImplClass = findClassDirect {
    MusicExoPlayerImplFingerprint().declaredClass!!
}

/** The coordinator field holding the ExoPlayer (9.46.51: `yyr.f`). */
internal val crossfadeExoPlayerField = findFieldDirect {
    crossfadeCoordinatorClass().fields.single { it.type.descriptor == EXO_PLAYER_TYPE }
}

/**
 * The coordinator field holding the media session. The first object field read
 * of the gapless play-next method (9.46.51: `yyr.h`, read before `this.av`).
 */
internal val crossfadeSessionField = findFieldDirect {
    MusicPlayNextInQueueFingerprint.instructionMatches.first().instruction.fieldRef!!
}

/**
 * The factory field on the session, found by the ExoPlayer factory method it
 * declares (9.46.51: `yyb.a`, of type `yyx`).
 */
internal val crossfadeFactoryField = findFieldDirect {
    crossfadeSessionField().type.fields.single { field ->
        runCatching {
            field.type.methods.any {
                it.returnType?.descriptor == EXO_PLAYER_TYPE && it.paramTypes.size == 3
            }
        }.getOrDefault(false)
    }
}

/** The ExoPlayer factory method (9.46.51: `yyx.a(yyr, bzi, I)`). */
internal val crossfadeFactoryMethod = findMethodDirect {
    crossfadeFactoryField().type.methods.single {
        it.returnType?.descriptor == EXO_PLAYER_TYPE &&
                it.paramTypes.size == 3 &&
                it.paramTypes[2].descriptor == "I"
    }
}

/** The coordinator field holding the load control (9.46.51: `yyr.g`, a `bzi`). */
internal val crossfadeLoadControlField = findFieldDirect {
    val coordinator = crossfadeCoordinatorClass()
    val loadControlType = crossfadeFactoryMethod().paramTypes[1]
    coordinator.fields.firstOrNull { it.type.descriptor == loadControlType.descriptor }
        ?: coordinator.fields.first { field ->
            field.type.interfaces.any { it.descriptor == loadControlType.descriptor }
        }
}

/**
 * The shared state of the players, the first coordinator field the factory
 * reads that is not the session, the player or the load control
 * (9.46.51: `yyr.s`, the track selector `yzw`).
 */
internal val crossfadeSharedStateField = findFieldDirect {
    val known = setOf(
        crossfadeSessionField().type.descriptor,
        crossfadeExoPlayerField().type.descriptor,
        crossfadeLoadControlField().type.descriptor,
    )
    factoryCoordinatorFieldReads().first { it.type.descriptor !in known }
}

/**
 * The shared callback of the players, the next unread coordinator field of the
 * factory body (9.46.51: `yyr.R`, the analytics collector `cbe`).
 */
internal val crossfadeSharedCallbackField = findFieldDirect {
    val known = setOf(
        crossfadeSessionField().type.descriptor,
        crossfadeExoPlayerField().type.descriptor,
        crossfadeLoadControlField().type.descriptor,
    )
    factoryCoordinatorFieldReads().first {
        it.type.descriptor !in known && it.descriptor != crossfadeSharedStateField().descriptor
    }
}

/**
 * The listener the ExoPlayer constructor refuses to attach with, in the first
 * abstract superclass of the shared state (9.46.51: `cnn.a` of the track
 * selector).
 */
internal val crossfadeGuardField = findFieldDirect {
    var current = crossfadeSharedStateField().type.superClass
    while (current != null && current.descriptor != "Ljava/lang/Object;") {
        if (Modifier.isAbstract(current.modifiers)) {
            current.fields.firstOrNull { !Modifier.isStatic(it.modifiers) }?.let { return@findFieldDirect it }
        }
        current = current.superClass
    }
    error("TrackSelector listener guard field not found")
}

/**
 * The timeline field of the shared state, saved and restored around player
 * creation (9.46.51: `yzw.a`, the session of the track selector).
 */
internal val crossfadeTimelineField = findFieldDirect {
    val sharedState = crossfadeSharedStateField().type
    val looper = "Landroid/os/Looper;"
    val bxkType = allMethodsInHierarchy(sharedState).firstNotNullOfOrNull { method ->
        if (method.paramTypes.size != 2) return@firstNotNullOfOrNull null
        when {
            method.paramTypes[1].descriptor == looper -> method.paramTypes[0]
            method.paramTypes[0].descriptor == looper -> method.paramTypes[1]
            else -> null
        }
    } ?: sharedState.fields.firstOrNull { field ->
        field.type.descriptor.startsWith("L") &&
                field.type.descriptor !in STANDARD_TYPES &&
                !Modifier.isStatic(field.modifiers)
    }?.type ?: error("Timeline type not found on ${sharedState.descriptor}")
    sharedState.fields.first { it.type.descriptor == bxkType.descriptor }
}

/**
 * The video surface of the coordinator, a coordinator field type that holds an
 * ExoPlayer (9.46.51: `yyr.u`, of type `yzl`).
 */
internal val crossfadeVideoSurfaceField = findFieldDirect {
    val coordinator = crossfadeCoordinatorClass()
    val known = setOf(
        crossfadeSessionField().type.descriptor,
        crossfadeExoPlayerField().type.descriptor,
        crossfadeLoadControlField().type.descriptor,
        crossfadeSharedStateField().type.descriptor,
        crossfadeSharedCallbackField().type.descriptor,
    )
    val surfaceClass = coordinator.fields.map { it.type }.first { type ->
        type.descriptor.startsWith("L") &&
                type.descriptor !in known &&
                !Modifier.isInterface(type.modifiers) &&
                type.fields.any { it.type.descriptor == EXO_PLAYER_TYPE }
    }
    coordinator.fields.first { it.type.descriptor == surfaceClass.descriptor }
}

/** The player field of the video surface (9.46.51: `yzl.d`). */
internal val crossfadeVideoSurfaceExoField = findFieldDirect {
    val surface = crossfadeVideoSurfaceField().type
    surface.fields.first { it.type.descriptor == EXO_PLAYER_TYPE }
}

/**
 * The volume the app last set on its player, which includes its volume
 * normalization (9.46.51: `yyr.ao`, written right after the player call of
 * `yyr.P(F)`).
 */
internal val crossfadePlayerVolumeField = findFieldDirect {
    MusicPlayerVolumeFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/**
 * The player interface ExoPlayer extends, where the setter methods the
 * extension bridges to are declared (9.46.51: `bpq`).
 */
internal val crossfadePlayerInterfaceClass = findClassDirect {
    val target = MusicPlayerVolumeFingerprint.instructionMatches.first().instruction.methodRef!!.declaredClass!!
    if (target.descriptor == EXO_PLAYER_TYPE) target.interfaces.first() else target
}

/**
 * The ListenerSet of the player implementation, recognized by its
 * (CopyOnWriteArraySet, Looper, Thread, ...) constructor
 * (9.46.51: `byy.F`, of type `dfu`).
 */
internal val crossfadeListenerWrapperField = findFieldDirect {
    crossfadeExoPlayerImplClass().fields.first { field ->
        !Modifier.isStatic(field.modifiers) && field.type.descriptor.startsWith("L") && runCatching {
            field.type.methods.any { method ->
                method.name == "<init>" && method.paramTypes.map { it.descriptor }.let {
                    it.firstOrNull() == COPY_ON_WRITE_SET && looperDescriptor in it && "Ljava/lang/Thread;" in it
                }
            }
        }.getOrDefault(false)
    }
}

/**
 * The add method of the ListenerSet, which allocates its listener holder
 * (9.46.51: `dfu.c(Object)`).
 */
internal val crossfadeListenerSetAdd = findMethodDirect {
    val wrapper = crossfadeListenerWrapperField().type
    Fingerprint(
        definingClass = wrapper.descriptor,
        returnType = "V",
        parameters = listOf("Ljava/lang/Object;"),
        filters = listOf(
            fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = wrapper.descriptor),
            fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = wrapper.descriptor),
            opcode(Opcode.NEW_INSTANCE),
            methodCall(smali = "$COPY_ON_WRITE_SET_SMALI->add(Ljava/lang/Object;)Z")
        )
    ).run()
}

/** The wrapped listener set inside the ListenerSet (9.46.51: `dfu.e`). */
internal val crossfadeListenerSetInWrapper = findFieldDirect {
    val wrapper = crossfadeListenerWrapperField().type
    val add = crossfadeListenerSetAdd()
    val holderIndex = add.instructions.indexOfFirst { it.opcode == Opcode.NEW_INSTANCE.opCode }
    add.instructions.subList(0, holderIndex)
        .filter { it.opcode == Opcode.IGET_OBJECT.opCode }
        .mapNotNull { it.fieldRef }
        .last { it.declaredClass.descriptor == wrapper.descriptor }
}

/** The remove method of the ListenerSet (9.46.51: `dfu.g(Object)`). */
internal val crossfadeListenerSetRemove = findMethodDirect {
    Fingerprint(
        definingClass = crossfadeListenerWrapperField().type.descriptor,
        returnType = "V",
        parameters = listOf("Ljava/lang/Object;"),
        filters = listOf(
            methodCall(smali = "$COPY_ON_WRITE_SET_SMALI->remove(Ljava/lang/Object;)Z")
        )
    ).run()
}

/** The audio offload listener set of the player (9.46.51: `byy.M`). */
internal val crossfadeDirectListenerSetField = findFieldDirect {
    crossfadeExoPlayerImplClass().fields.single { it.type.descriptor == COPY_ON_WRITE_SET }
}

/**
 * The type of the audio offload listeners, the parameter of the method that adds
 * to the offload listener set (9.46.51: `bya`, added by `byy.K`, which the
 * ExoPlayer interface types `K(bya)` instead of `K(Object)`).
 */
internal val crossfadeDirectListenerType = findClassDirect {
    Fingerprint(
        definingClass = crossfadeExoPlayerImplClass().descriptor,
        returnType = "V",
        parameters = listOf("L"),
        filters = listOf(
            fieldAccess(opcode = Opcode.IGET_OBJECT, type = COPY_ON_WRITE_SET),
            methodCall(smali = "$COPY_ON_WRITE_SET_SMALI->add(Ljava/lang/Object;)Z")
        )
    ).run().paramTypes.first()
}

/**
 * The coordinator's audio offload listener, which has to move to the new player
 * on a swap (9.46.51: `yyr.b`, of type `bya`).
 */
internal val crossfadeCoordinatorListenerField = findFieldDirect {
    val listenerType = crossfadeDirectListenerType().descriptor
    crossfadeCoordinatorClass().fields.single { field ->
        !Modifier.isStatic(field.modifiers) && field.type.descriptor == listenerType
    }
}

/**
 * The listener registration of the analytics collector, recognized by
 * forwarding to the ListenerSet add (9.46.51: `cbe.F(cal)` calling `dfu.c`).
 */
internal val crossfadeCwhAddListener = findMethodDirect {
    Fingerprint(
        definingClass = crossfadeSharedCallbackField().type.descriptor,
        returnType = "V",
        parameters = listOf("L"),
        filters = listOf(
            methodCall(
                definingClass = crossfadeListenerWrapperField().type.descriptor,
                name = crossfadeListenerSetAdd().name
            )
        )
    ).matchAllOrNull()?.single()?.method ?: error("AnalyticsCollector addListener not found")
}

/**
 * The coordinator's MediaSession listener, which has to be registered on the
 * new player's analytics collector (9.46.51: `yyr.i`, `yye` implementing the
 * listener type `cal`).
 */
internal val crossfadeCoordinatorCwhListenerField = findFieldDirect {
    val listenerType = crossfadeCwhAddListener().paramTypes.first()
    crossfadeCoordinatorClass().fields.single { field ->
        !Modifier.isStatic(field.modifiers) &&
                field.type.descriptor != crossfadeExoPlayerField().type.descriptor &&
                field.type.descriptor != crossfadeSharedCallbackField().type.descriptor &&
                field.type.interfaces.any { it.descriptor == listenerType.descriptor }
    }
}

/**
 * The analytics collector field of the player implementation, the same type as
 * the coordinator's shared callback (9.46.51: `byy.D`).
 */
internal val crossfadeExoCwhField = findFieldDirect {
    crossfadeExoPlayerImplClass().fields.single {
        it.type.descriptor == crossfadeSharedCallbackField().type.descriptor
    }
}

/**
 * The interface-typed field of the shared callback the factory re-sets on the
 * new player (9.46.51: `cbe.c`, the player reference `bpq`).
 */
internal val crossfadeCqbField = findFieldDirect {
    allFieldsInHierarchy(crossfadeSharedCallbackField().type).firstOrNull { field ->
        field.type.descriptor.startsWith("L") &&
                field.type.descriptor != "Ljava/lang/Object;" && runCatching {
            Modifier.isInterface(field.type.modifiers) &&
                    field.type.methods.none { it.name == "<clinit>" }
        }.getOrDefault(false)
    } ?: error("cqb field not found on ${crossfadeSharedCallbackField().type.descriptor}")
}

/**
 * The Clock field of the shared callback, which a player release also clears
 * and has to be restored (9.46.51: `cbe.a`, of the interface type `brm`).
 */
internal val crossfadeDltFieldOnShared = findFieldDirect {
    val sharedCallback = crossfadeSharedCallbackField().type
    allFieldsInHierarchy(sharedCallback).firstOrNull { field ->
        field.descriptor != crossfadeCqbField().descriptor &&
                field.type.descriptor.startsWith("L") &&
                field.type.descriptor != "Ljava/lang/Object;" &&
                field.type.descriptor != "Ljava/util/List;" &&
                field.type.descriptor != crossfadeSessionField().type.descriptor && runCatching {
            Modifier.isAbstract(field.type.modifiers) || Modifier.isInterface(field.type.modifiers)
        }.getOrDefault(false)
    } ?: error("dlt field not found on ${sharedCallback.descriptor}")
}

/** The same callback field on the player implementation (9.46.51: `byy.i`). */
internal val crossfadeDltFieldOnExo = findFieldDirect {
    crossfadeExoPlayerImplClass().fields.single {
        it.type.descriptor == crossfadeDltFieldOnShared().type.descriptor
    }
}

/**
 * The playback info of the player implementation, recognized by its many int
 * and long fields (9.46.51: `byy.A`, of type `bzw`).
 */
internal val crossfadePlaybackInfoClass = findClassDirect {
    crossfadeExoPlayerImplClass().fields.asSequence()
        .map { it.type }
        .filter { it.descriptor.startsWith("L") && it.descriptor != "Ljava/lang/Object;" }
        .first { type ->
            !Modifier.isInterface(type.modifiers) &&
                    type.interfaces.isEmpty() &&
                    type.fields.count { it.type.descriptor == "I" } >= 3 &&
                    type.fields.count { it.type.descriptor == "J" } >= 1
        }
}

/** The playback info field (9.46.51: `byy.A`). */
internal val crossfadePlaybackInfoField = findFieldDirect {
    crossfadeExoPlayerImplClass().fields.single {
        it.type.descriptor == crossfadePlaybackInfoClass().descriptor
    }
}

/** The playback state field of the playback info (9.46.51: `bzw.f`). */
internal val crossfadePlaybackStateField = findFieldDirect {
    crossfadePlaybackInfoClass().fields.first { it.type.descriptor == "I" }
}

/**
 * The current position getter, the void-less long getter calling into the
 * playback info (9.46.51: `byy.r` calling `ai(Lbzw;)J`).
 */
internal val crossfadeCurrentPositionMethod = findMethodDirect {
    Fingerprint(
        definingClass = crossfadeExoPlayerImplClass().descriptor,
        returnType = "J",
        parameters = emptyList(),
        filters = listOf(
            methodCall(
                parameters = listOf(crossfadePlaybackInfoClass().descriptor),
                returnType = "J"
            )
        )
    ).matchAllOrNull()?.single()?.method ?: error("getCurrentPosition not found")
}

/**
 * The duration getter, the long getter with the C.TIME_UNSET literal
 * (9.46.51: `byy.s`).
 */
internal val crossfadeDurationMethod = findMethodDirect {
    Fingerprint(
        definingClass = crossfadeExoPlayerImplClass().descriptor,
        returnType = "J",
        parameters = emptyList(),
        filters = listOf(literal(C_TIME_UNSET))
    ).matchAllOrNull()?.single()?.method ?: error("getDuration not found")
}

/**
 * The player chain of the MedialibPlayer, its first non-library object field
 * (9.46.51: `yto.c`, the `zhk` interface).
 */
internal val crossfadePlayerChainField = findFieldDirect {
    crossfadeMedialibPlayerClass().fields.first {
        !Modifier.isStatic(it.modifiers) &&
                it.type.descriptor.startsWith("L") &&
                it.type.descriptor != "Ljava/lang/Object;"
    }
}

/**
 * The delegate fields of the player chain, one per decorator base class: every
 * class that implements the chain interface and holds the next node in a field
 * of the same interface type (9.46.51: `zfx.a` for the `zgo`/`zgb` decorators
 * and `zgl.i` for the queue player the chain starts at). The extension walks
 * the whole chain, so every decorator base needs its own field, like upstream
 * adds DelegateAccess to each of them.
 */
internal val crossfadeDelegateFields = findFieldListDirect {
    val chainType = crossfadePlayerChainField().type
    findClass { matcher { addInterface(chainType.name) } }
        .filter { cls ->
            !Modifier.isInterface(cls.modifiers) &&
                    cls.interfaces.any { it.descriptor == chainType.descriptor }
        }
        .flatMap { cls -> cls.fields.filter { it.type.descriptor == chainType.descriptor } }
}

/**
 * The queue-advancing play-next of the MedialibPlayer
 * (9.46.51: `yto.m`, logging "MedialibPlayer.playNextInQueue()").
 */
internal val crossfadeMedialibPlayNextInQueue = findMethodDirect {
    Fingerprint(
        definingClass = crossfadeMedialibPlayerClass().descriptor,
        returnType = "V",
        strings = listOf(string("playNextInQueue", StringComparisonType.CONTAINS))
    ).run()
}

/**
 * The state provider of the audio/video toggle, found by its enum getter with
 * static state checks (9.46.51: `hzr.n`, of type `hgf`).
 */
internal val crossfadeStateProviderField = findFieldDirect {
    crossfadeVideoToggleClass().fields.first { field ->
        field.type.descriptor.startsWith("L") && stateMethodOrNull(field.type) != null
    }
}

/** The enum getter of the state provider (9.46.51: `hgf.j`). */
internal val crossfadeStateGetter = findMethodDirect {
    stateMethodOrNull(crossfadeStateProviderField().type)!!
}

/**
 * The audio check of the state provider, the longer of the two static state
 * checks because the audio check compares 3 states and the video check 2
 * (9.46.51: `hgf.o`).
 */
internal val crossfadeIsAudioModeMethod = findMethodDirect {
    val provider = crossfadeStateProviderField().type
    val stateType = crossfadeStateGetter().returnType!!
    provider.methods.filter { method ->
        Modifier.isStatic(method.modifiers) &&
                method.returnType?.descriptor == "Z" &&
                method.paramTypes.map { it.descriptor } == listOf(stateType.descriptor)
    }.maxBy { it.instructions.size }
}

/**
 * The notifying state setter of the state provider (9.46.51: `hgf.n(idw)`,
 * reading `this.b` and broadcasting through it).
 */
internal val crossfadeSetStateMethod = findMethodDirect {
    val provider = crossfadeStateProviderField().type
    Fingerprint(
        definingClass = provider.descriptor,
        isStatic = false,
        returnType = "V",
        parameters = listOf(crossfadeStateGetter().returnType!!.descriptor),
        filters = listOf(opcode(Opcode.IGET_OBJECT))
    ).run().also { check(!it.isConstructor) { "setState matched a constructor" } }
}

/** The broadcast target of the state setter (9.46.51: `hgf.b`, of type `awwm`). */
internal val crossfadeBroadcasterField = findFieldDirect {
    crossfadeSetStateMethod().instructions.first {
        it.opcode == Opcode.IGET_OBJECT.opCode
    }.fieldRef!!
}

/**
 * The broadcast method of the state setter (9.46.51: `awwm.ku(Object)`, the
 * first invoke of `hgf.n`).
 */
internal val crossfadeBroadcastMethod = findMethodDirect {
    crossfadeSetStateMethod().instructions.first {
        it.opcode in INVOKE_OPCODES
    }.methodRef!!
}

/**
 * The internal setter behind the broadcast, which skips notifying the
 * subscribers (9.46.51: `awwm.aH(Object)`, the first (Object)V call of
 * `awwm.ku`).
 */
internal val crossfadeSilentSetMethod = findMethodDirect {
    val broadcast = crossfadeBroadcastMethod()
    broadcast.declaredClass!!.methods.single { method ->
        method.name == broadcast.name &&
                method.returnType?.descriptor == "V" &&
                method.paramTypes.map { it.descriptor } == listOf("Ljava/lang/Object;")
    }.instructions.first { instruction ->
        val reference = instruction.methodRef ?: return@first false
        instruction.opcode in INVOKE_OPCODES &&
                reference.paramTypes.map { it.descriptor } == listOf("Ljava/lang/Object;") &&
                reference.returnType?.descriptor == "V"
    }.methodRef!!
}

/**
 * The runnable a player release posts on the analytics collector, which
 * releases the shared listener set. 9.28+ inlines the collector's release into
 * the player's release, so the posted runnable is suppressed while an outgoing
 * player is released (9.46.51: `bhu`, its case 8 captures the `cbe`).
 */
internal val crossfadeReleaseRunnableClass = findClassDirect {
    Fingerprint(
        definingClass = crossfadeExoPlayerImplClass().descriptor,
        name = MusicExoPlayerReleaseFingerprint.match().method.name,
        returnType = "V",
        parameters = emptyList(),
        filters = listOf(
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = crossfadeSharedCallbackField().type.descriptor
            ),
            opcode(Opcode.NEW_INSTANCE),
            methodCall(parameters = listOf("Ljava/lang/Runnable;"), returnType = "V")
        )
    ).match().instructionMatches[1].instruction.classRef!!
}

// endregion

// region Discovery helpers.

private const val COPY_ON_WRITE_SET = "Ljava/util/concurrent/CopyOnWriteArraySet;"
private const val COPY_ON_WRITE_SET_SMALI = "Ljava/util/concurrent/CopyOnWriteArraySet;"
private const val looperDescriptor = "Landroid/os/Looper;"
private const val C_TIME_UNSET = -9223372036854775807L

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL.opCode,
    Opcode.INVOKE_VIRTUAL_RANGE.opCode,
    Opcode.INVOKE_SUPER.opCode,
    Opcode.INVOKE_SUPER_RANGE.opCode,
    Opcode.INVOKE_DIRECT.opCode,
    Opcode.INVOKE_DIRECT_RANGE.opCode,
    Opcode.INVOKE_STATIC.opCode,
    Opcode.INVOKE_STATIC_RANGE.opCode,
    Opcode.INVOKE_INTERFACE.opCode,
    Opcode.INVOKE_INTERFACE_RANGE.opCode,
)

private val STANDARD_TYPES = setOf(
    "Ljava/lang/Object;", "Ljava/lang/String;",
    "Ljava/util/List;", "Ljava/util/Map;", "Ljava/util/Set;",
    "Ljava/util/ArrayList;", "Ljava/util/HashMap;",
    "Landroid/util/SparseArray;", "Landroid/os/Handler;",
    looperDescriptor, COPY_ON_WRITE_SET,
)

/**
 * The coordinator fields the factory reads, in the order the factory body
 * reads them.
 */
context(bridge: DexKitBridge)
private fun factoryCoordinatorFieldReads(): List<FieldData> {
    val coordinator = crossfadeCoordinatorClass(bridge).descriptor
    return crossfadeFactoryMethod(bridge).instructions
        .filter { it.opcode == Opcode.IGET_OBJECT.opCode }
        .mapNotNull { it.fieldRef }
        .filter { it.declaredClass.descriptor == coordinator }
}

context(bridge: DexKitBridge)
private fun allFieldsInHierarchy(start: ClassData): List<FieldData> {
    val result = mutableListOf<FieldData>()
    var current: ClassData? = start
    while (current != null && current.descriptor != "Ljava/lang/Object;") {
        result.addAll(current.fields)
        current = current.superClass
    }
    return result
}

context(bridge: DexKitBridge)
private fun allMethodsInHierarchy(start: ClassData): List<MethodData> {
    val result = mutableListOf<MethodData>()
    var current: ClassData? = start
    while (current != null && current.descriptor != "Ljava/lang/Object;") {
        result.addAll(current.methods)
        current = current.superClass
    }
    return result
}

/**
 * The enum getter of an audio/video state provider, which also has static
 * state checks.
 */
context(bridge: DexKitBridge)
private fun stateMethodOrNull(provider: ClassData): MethodData? =
    provider.methods.firstOrNull { method ->
        !Modifier.isStatic(method.modifiers) &&
                method.paramTypes.isEmpty() &&
                method.returnType?.descriptor?.startsWith("L") == true &&
                runCatching {
                    val stateType = method.returnType!!
                    ((stateType.modifiers and AccessFlags.ENUM.modifier) != 0) &&
                            provider.methods.any { check ->
                                Modifier.isStatic(check.modifiers) &&
                                        check.returnType?.descriptor == "Z" &&
                                        check.paramTypes.map { it.descriptor } == listOf(stateType.descriptor)
                            }
                }.getOrDefault(false)
    }

// endregion
