package io.github.nexalloy.morphe.reddit.ad

import app.morphe.extension.shared.Logger
import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string
import org.luckypray.dexkit.DexAccessFlags
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher


internal object ListingFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/listing/Listing;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/Listing;->children:Ljava/util/List;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/Listing;->after:Ljava/lang/String;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/Listing;->before:Ljava/lang/String;"
        )
    )
)

// Class appears to be removed in 2026.16.0+
@RequireAppVersion(maxVersion = "2026.16.0")
internal object SubmittedListingFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/listing/SubmittedListing;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/SubmittedListing;->children:Ljava/util/List;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/SubmittedListing;->videoUploads:Ljava/util/List;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/SubmittedListing;->after:Ljava/lang/String;"
        )
    )
)

private object AdPostSectionToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("AdPostSection(linkId=")
    )
)

internal object AdPostSectionConstructorFingerprint : Fingerprint(
    classFingerprint = AdPostSectionToStringFingerprint,
    name = "<init>",
    returnType = "V"
)

/**
 * 2026.04+
 */
@RequireAppVersion("2026.04.0")
internal object CommentsAdStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("CommentsAdState(conversationAdViewState="),
        string(", adsLoadCompleted="),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN)
    )
)

@RequireAppVersion("2026.04.0")
internal object CommentsAdStateConstructorFingerprint : Fingerprint(
    classFingerprint = CommentsAdStateToStringFingerprint,
    name = "<init>",
    returnType = "V"
)

@get:RequireAppVersion("2026.04.0")
val adsLoadCompletedField = findFieldDirect {
    CommentsAdStateToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal object CommentsViewModelAdLoaderFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/comments/presentation/CommentsViewModel;",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Z", "L", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("Z", "I"),
            returnType = "V"
        )
    )
)

internal object ImmutableListBuilderFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Lcom/reddit/accessibility/AutoplayVideoPreviewsOption;",
            name = "getEntries"
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/Iterable;")
        )
    )
)

val ImmutableListBuilderReference = findMethodDirect {
    ImmutableListBuilderFingerprint.instructionMatches
        .last().instruction.methodRef!!
}

// region Promoted post composable

/**
 * The promoted post composable of the ads feed (2026.39.1: com.reddit.ads.impl.feeds.composables.c.e),
 * which fills the popular and latest feed gap of the Listing filter. The anchor also appears in a
 * shared synthetic dispatcher, so the parameter envelope must resolve a single candidate.
 */
internal object PromotedPostUnitFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/ads/impl/feeds/composables/",
    returnType = "V",
    strings = listOf("promoted_post_unit"),
    // Compose envelope of a composable that ends with the (composer, $changed, $default) ints.
    custom = { paramCount(6..14) }
)

/**
 * The promoted post composable: the fingerprint's candidate that also ends with an int,
 * which excludes the shared synthetic dispatcher.
 */
internal val promotedPostComposables = findMethodListDirect {
    PromotedPostUnitFingerprint.matchAllOrNull()
        ?.map { it.method }
        // Constructors can carry the anchor literal of their class; only the composable counts.
        ?.filter { it.name != "<init>" && it.paramTypes.lastOrNull()?.descriptor == "I" }
        .orEmpty()
}

// endregion

// region Feed ad elements and search ads

/**
 * toString of the feed post element, which keeps its Kotlin class name in the literal.
 */
internal object PostElementToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("PostElement(linkId=")
    )
)

/**
 * The base class of every feed element: the superclass of the post element.
 */
internal val feedElementBaseClass = findClassDirect {
    val postElements = PostElementToStringFingerprint.matchAllOrNull()
        ?.map { it.method.declaredClass!! }
        ?.distinctBy { it.descriptor }
        .orEmpty()
    val postElement = postElements.singleOrNull()
        ?: throw Exception("Expected 1 post element, found ${postElements.size}")
    postElement.superClass ?: throw Exception("Post element has no element base class")
}

/**
 * The only method invoked from the feed paging package that converts a single feed element
 * into a section (2026.39.1: w3f.a). Every caller skips a null section.
 */
internal val elementSectionConverter = findMethodDirect {
    val elementDescriptor = feedElementBaseClass().descriptor
    val converters = findMethod {
        searchPackages(FEED_PAGING_PACKAGE)
    }.flatMap { it.invokes }
        .distinctBy { it.descriptor }
        .filter {
            it.name != "<init>" &&
                (it.accessFlags and DexAccessFlags.STATIC == 0) &&
                it.paramTypes.map { parameter -> parameter.descriptor } == listOf(elementDescriptor) &&
                it.returnTypeName != "void" &&
                !it.returnTypeName.startsWith("java.")
        }
    converters.singleOrNull() ?: throw Exception(
        "Expected 1 feed element section converter, found ${converters.size}"
    )
}

private const val FEED_PAGING_PACKAGE = "com.reddit.feeds.impl.domain.paging"

/**
 * Literals that only the `toString` of the search ad element and the ad feed elements uses.
 * The promoted post itself is covered by `AdElement` next to its composable hook.
 */
internal val adElementLiterals = listOf(
    "SearchAdPostFeedElement(viewState=",
    "AdElement(linkId=",
    "AdGalleryElement(linkId=",
    "AdFreeFormElement(linkId=",
    "AdSpotlightVideoElement(linkId=",
    "LetterboxAdVideoElement(linkId=",
    "AdCallToActionElement(linkId=",
    "AdBrandLiftStudyElement(linkId=",
)

private fun adElementToStringFingerprint(literal: String) = Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string(literal)
    )
)

/**
 * toString of the search ad element and the ad feed elements. Each literal is looked up
 * independently: a literal without a unique element is logged and skipped, so one renamed
 * ad type does not drop the others.
 */
internal val adElementToStrings = findMethodListDirect {
    adElementLiterals.mapNotNull { literal ->
        val matches = adElementToStringFingerprint(literal).matchAllOrNull()
            ?.map { it.method }
            ?.distinctBy { it.declaredClass!!.descriptor }
            .orEmpty()
        if (matches.size != 1) {
            Logger.printInfo { "Expected 1 element for \"$literal\", found ${matches.size}" }
            null
        } else {
            matches.single()
        }
    }
}

// endregion

// region Comment ad slot dispatcher

/**
 * The comment ad slot dispatcher (2026.39.1: gga.b), which composes the placeholder, loaded
 * ad and empty states of a comment ad slot, so skipping its body removes the entire slot.
 * Anchored by the placeholder's blank container.
 */
internal object CommentAdSlotFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("blank_ad_container"),
    // Compose envelope of a dispatcher that takes several composable lambdas.
    custom = { paramCount(15..24) }
)

/**
 * The comment ad slot dispatcher: the fingerprint's candidate that also takes at least two
 * Kotlin function parameters and ends with at least two ints.
 */
internal val commentAdSlotDispatchers = findMethodListDirect {
    CommentAdSlotFingerprint.matchAllOrNull()
        ?.map { it.method }
        ?.filter { method ->
            // Constructors can carry the anchor literal of their class; skip them.
            if (method.name == "<init>") return@filter false
            val parameters = method.paramTypes.map { it.descriptor }
            val functionCount = parameters.count { it.startsWith("Lkotlin/jvm/functions/Function") }
            val trailingIntCount = parameters.asReversed().takeWhile { it == "I" }.size
            functionCount >= 2 && trailingIntCount >= 2
        }
        .orEmpty()
}

// endregion

// region Pager promoted ads

/**
 * Kotlin coroutine classes of the pager links provider, which keep their names. The provider
 * (2026.39.1: linkpager.refactor.e) constructs one of them to load the pages of the
 * swipe-between-posts pager.
 */
private val pagerCoroutineDescriptors = listOf(
    "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/StandardParamsPagedLinksProvider\$initialPages\$1;",
    "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/StandardParamsPagedLinksProvider\$nextPage\$1;",
    "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/StandardParamsPagedLinksProvider\$loadInitialPagesImproved\$1;",
)

/**
 * The page builders of the pager links provider (2026.39.1: aud0.Q and aud0.R), which turn
 * page summaries into pages and already drop promoted community, survey and blank ads, but
 * keep plain promoted posts. A single provider method is expected, but up to three are
 * tolerated; discovery fails closed otherwise.
 */
internal val pagerPageBuilders = findMethodListDirect {
    val providers = pagerCoroutineDescriptors.flatMap { coroutine ->
        findMethod {
            matcher(
                MethodMatcher().addInvoke(
                    MethodMatcher()
                        .declaredClass(ClassMatcher().descriptor(coroutine))
                        .name("<init>")
                )
            )
        }.filter { it.declaredClass!!.descriptor != coroutine }
    }.distinctBy { it.descriptor }

    if (providers.isEmpty() || providers.size > 3) {
        Logger.printException { "Expected 1-3 pager links providers, found ${providers.size}" }
        emptyList()
    } else {
        val builders = providers.flatMap { it.invokes }
            .distinctBy { it.descriptor }
            .filter { method ->
                (method.accessFlags and DexAccessFlags.STATIC != 0) &&
                    method.paramTypeNames.size == 4 &&
                    method.paramTypeNames[0] == "java.util.List" &&
                    method.paramTypeNames.drop(1).none { it.startsWith("java.") || it in PRIMITIVES } &&
                    method.returnTypeName != "void" &&
                    !method.returnTypeName.startsWith("java.")
            }
        // Reddit keeps two builders (normal and highlights) with one shared shape.
        if (builders.size !in 1..2 || builders.map { it.paramTypeNames }.distinct().size != 1) {
            Logger.printException { "Expected 1-2 page builders with one shape, found ${builders.size}" }
            emptyList()
        } else {
            builders
        }
    }
}

private val PRIMITIVES = setOf(
    "int", "long", "boolean", "byte", "short", "char", "float", "double"
)

// endregion