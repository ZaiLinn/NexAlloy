package io.github.nexalloy.morphe.music.misc.album

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import java.lang.reflect.Modifier

/**
 * Case of the WatchNextResponse oneof the watch next model reads its playlist from.
 */
private const val WATCH_NEXT_PLAYLIST_CASE = 51779735L

/**
 * Constructor of the watch next model, which every watch next response the app shows is built
 * into, whether it came from the network, the streaming watch response or the offline store.
 * The only parameter is the WatchNextResponse.
 * Anchor (9.46.51): `Lwhm;-><init>(Lamkl;)V`, `whm implements Parcelable, acpi` and reads
 * case `51779735` (`amkm.b == 51779735`).
 */
internal object WatchNextResponseModelConstructorFingerprint : Fingerprint(
    interfaces = listOf("Landroid/os/Parcelable;"),
    name = "<init>",
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        literal(WATCH_NEXT_PLAYLIST_CASE)
    )
)

/**
 * Resolves a command with every registered command resolver, in order.
 * Every command a tap sends is routed through here before it is resolved.
 * Ported from the playlist track tap patch, which shares the resolver with the album patch.
 * The other command resolvers do not call an executor from the resolving method
 * (`vxw.c` logs a colon variant, `gwo.c` only logs the exception).
 * Anchor (9.46.51): `Lvxv;->c(Lakgm;Ljava/util/Map;)V` calls `vxx.b.execute(...)`
 * and logs "Unknown command not resolved %s".
 */
internal object CommandResolverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/util/concurrent/Executor;",
            name = "execute"
        ),
    ),
    strings = listOf(
        string("Unknown command not resolved", comparison = StringComparisonType.CONTAINS)
    )
)

/**
 * Builds a command holding a watch endpoint.
 * Parameters are the video id, playlist id, playlist index, start time,
 * player params, params and whether the watch endpoint is a music video.
 * Ported from the playlist track tap patch, which shares the command builder with the album patch.
 * Anchor (9.46.51): `Labtk;->p(Ljava/lang/String;Ljava/lang/String;IFLjava/lang/String;Ljava/lang/String;Z)Lairj;`.
 */
internal object WatchEndpointCommandBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "I",
        "F",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z"
    ),
    filters = listOf(
        // Default instance of the watch endpoint.
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        // Creates the watch endpoint builder.
        methodCall(name = "createBuilder", opcode = Opcode.INVOKE_VIRTUAL),

        // Video id and playlist id are the first two strings set, in the order of the parameters.
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/lang/String;"),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/lang/String;"),

        // The is music video boolean is set before the presence bits and then the playlist index.
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN),
        fieldAccess(opcode = Opcode.IPUT, type = "I"),
        fieldAccess(opcode = Opcode.IPUT, type = "I"),

        // Default instance of the command holding the watch endpoint.
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        // Extension field of the watch endpoint inside the command.
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        // Sets the watch endpoint extension of the command builder.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "V",
            parameters = listOf("L", "Ljava/lang/Object;")
        )
    )
)

/**
 * The registry the app parses its own messages with.
 * Anchor (9.46.51): `Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()`.
 */
internal object AlbumGeneratedRegistryFingerprint : Fingerprint(
    definingClass = "Lcom/google/protobuf/ExtensionRegistryLite;",
    name = "getGeneratedRegistry",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lcom/google/protobuf/ExtensionRegistryLite;",
    parameters = listOf()
)

/**
 * The protobuf runtime base class of every generated message (GeneratedMessageLite), whose
 * method names are not obfuscated, resolved from the watch endpoint builder's `createBuilder`
 * call. The album patch hooks the extension's bridge stubs to [albumMessageParser] and the
 * `toByteArray` of this class hierarchy.
 * Anchor (9.46.51): `Lairp;` declares `createBuilder()` and
 * `parseFrom(Lairp;[BLcom/google/protobuf/ExtensionRegistryLite;)Lairp;`.
 */
internal val albumMessageClass = findClassDirect {
    WatchEndpointCommandBuilderFingerprint.instructionMatches[1].instruction.methodRef!!.declaredClass!!
}

/**
 * Default instance of the command proto the resolver resolves and the album patch rewrites
 * (the watch endpoint command builder builds it).
 * Anchor (9.46.51): `Lakgm;->a:Lakgm;` (read by the second `sget-object` of `Labtk.p`).
 */
internal val albumCommandDefaultInstanceField = findFieldDirect {
    WatchEndpointCommandBuilderFingerprint.instructionMatches[7].instruction.fieldRef!!
}

/**
 * The WatchNextResponse proto, the only parameter of the watch next model constructor.
 * Anchor (9.46.51): `amkl` (extends `airk implements airl`).
 */
internal val albumWatchNextResponseClass = findClassDirect {
    WatchNextResponseModelConstructorFingerprint().paramTypes.single()
}

/**
 * Default instance of the WatchNextResponse.
 * Anchor (9.46.51): `amkl.a` is the only `amkl` typed field of `amkl`.
 */
internal val albumWatchNextResponseDefaultInstanceField = findFieldDirect {
    defaultInstance(albumWatchNextResponseClass())
}

/**
 * Parses a message of the host's protobuf runtime, together with the host's extension
 * registry, so extensions such as the watch endpoint are not parsed as unknown fields.
 * Anchor (9.46.51): `Lairp;->parseFrom(Lairp;[BLcom/google/protobuf/ExtensionRegistryLite;)Lairp;`.
 */
internal val albumMessageParser = findMethodDirect {
    val messageClass = albumMessageClass()
    val className = messageClass.descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

    findMethod {
        matcher {
            name("parseFrom")
            declaredClass { className(className) }
        }
    }.single { method ->
        Modifier.isPublic(method.modifiers) &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType?.descriptor == messageClass.descriptor &&
                method.paramTypes.map { it.descriptor } == listOf(
            messageClass.descriptor,
            "[B",
            "Lcom/google/protobuf/ExtensionRegistryLite;"
        )
    }
}

/**
 * @return The default instance field of a generated protobuf message.
 */
context(bridge: DexKitBridge)
private fun defaultInstance(type: ClassData) = type.fields.single {
    Modifier.isStatic(it.modifiers) && it.type.descriptor == type.descriptor
}
