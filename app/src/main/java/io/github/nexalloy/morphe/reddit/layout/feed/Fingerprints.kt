/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.nexalloy.morphe.reddit.layout.feed

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.instanceOf
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string

internal const val AUTO_REFRESH_EVENT = "Lcom/reddit/feeds/ui/events/OnAutoRefreshFeed;"
internal const val SCREEN_PAGER = "Lcom/reddit/screen/widget/ScreenPager;"

@StrictQuery
internal object AutoRefreshFeedFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(
        newInstance(AUTO_REFRESH_EVENT),
        methodCall(definingClass = AUTO_REFRESH_EVENT, name = "<init>"),
        methodCall(name = "onEvent"),
    ),
)

internal val autoRefreshDispatchMethod = findMethodDirect {
    val eventCallback = AutoRefreshFeedFingerprint.instructionMatches.last().instruction.methodRef!!
    val viewModel = getClassData("Lcom/reddit/feeds/impl/ui/RedditFeedViewModel;")!!
    viewModel.methods.single { it.name == eventCallback.name && it.paramTypeNames == eventCallback.paramTypeNames }
}

@StrictQuery
internal object PostDetailPagerSetupFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/PostDetailPagerScreen;",
            type = SCREEN_PAGER,
        ),
        methodCall(definingClass = SCREEN_PAGER, name = "setAdapter"),
    ),
)

internal val postDetailPagerAdapterMethod = findMethodDirect {
    PostDetailPagerSetupFingerprint.instructionMatches.last().instruction.methodRef!!
}

internal object FeedStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("Feed(sections="),
        string(", lastPostIdVisited="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(3),
        ),
    ),
)

internal val lastVisitedPostField = findFieldDirect {
    FeedStateToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

@StrictQuery
internal object RememberOpenedPostFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        newInstance(type = { lastVisitedPostField().type.descriptor }),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = { "this" },
            type = { lastVisitedPostField().type.descriptor },
        ),
    ),
)

@StrictQuery
internal object HomeBackPressedFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/feedslegacy/switcher/impl/homepager/compose/HomePagerScreen;",
            returnType = "Lcom/reddit/screen/BaseScreen;",
            parameters = emptyList(),
        ),
        methodCall(
            returnType = "Z",
            parameters = emptyList(),
            location = MatchAfterWithin(20),
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        newInstance("Lcom/reddit/feedslegacy/switcher/impl/exitapp/ExitAppToastSharedPreferences\$toastSeenTotalCount\$1;"),
        opcode(Opcode.IF_EQZ, location = MatchAfterWithin(5)),
        string("onBackPressedHandler"),
        newInstance("Lcom/reddit/feedslegacy/switcher/impl/exitapp/RedditExitAppOnDoubleBackClickDelegate\$handleAppExit\$1;"),
    ),
)

internal val homeRefreshOnBackCallbackMethods = findMethodListDirect {
    val callback = HomeBackPressedFingerprint.instructionMatches[1].instruction.methodRef!!
    listOf(
        "Lcom/reddit/feeds/home/impl/ui/HomeFeedScreen;",
        "Lcom/reddit/feeds/following/impl/ui/FollowingFeedScreen;",
    ).map { owner ->
        getClassData(owner)!!.methods.single { method ->
            method.name == callback.name && method.paramTypeNames.isEmpty() && method.returnTypeName == "boolean"
        }
    }
}

internal object NavBarStateChangeFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/launch/bottomnav/BottomNavScreen;",
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/launch/bottomnav/BottomNavScreen\$onNavBarStateChange\$1",
            name = "<init>",
        ),
    ),
)

internal object FeedElementProcessorFingerprint : Fingerprint(
    returnType = "Lkotlin/Pair;",
    parameters = listOf(
        "Lcom/reddit/feeds/data/FeedType;",
        "Ljava/util/List;",
        "Ljava/util/List;",
    ),
    filters = listOf(
        methodCall(smali = "Lkotlin/Pair;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V"),
    ),
)

// Feed text and flair model anchors from Morphe #4867 and #4868.
private fun feedElementToStringFingerprint(name: String) = Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("$name(linkId=")
    )
)

/**
 * Fields that the constructor of every feed element sets first:
 * link id, unique id, promoted and identifier.
 */
private fun feedElementFieldFilters() = listOf(
    fieldAccess(
        opcode = Opcode.IPUT_OBJECT,
        definingClass = "this",
        type = "Ljava/lang/String;"
    ),
    fieldAccess(
        opcode = Opcode.IPUT_OBJECT,
        definingClass = "this",
        type = "Ljava/lang/String;",
        location = MatchAfterImmediately()
    ),
    fieldAccess(
        opcode = Opcode.IPUT_BOOLEAN,
        definingClass = "this",
        location = MatchAfterImmediately()
    ),
    fieldAccess(
        opcode = Opcode.IPUT_OBJECT,
        definingClass = "this",
        location = MatchAfterImmediately()
    )
)

internal object PostTitleElementToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("PostTitleElement(linkId=")
    )
)

@StrictQuery
internal object PostTitleElementConstructorFingerprint : Fingerprint(
    classFingerprint = PostTitleElementToStringFingerprint,
    name = "<init>",
    filters = feedElementFieldFilters() + listOf(
        // Title.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        // Translated title.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        )
    )
)

@StrictQuery
internal object PreviewTextElementConstructorFingerprint : Fingerprint(
    classFingerprint = feedElementToStringFingerprint("PreviewTextElement"),
    name = "<init>",
    filters = feedElementFieldFilters() + listOf(
        // Text.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        // Number of lines.
        fieldAccess(
            opcode = Opcode.IPUT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        // Is read.
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        // Translated text.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        )
    )
)

@StrictQuery
internal object RichTextElementConstructorFingerprint : Fingerprint(
    classFingerprint = feedElementToStringFingerprint("RichTextElement"),
    name = "<init>",
    filters = feedElementFieldFilters() + listOf(
        // Rich text JSON.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Post of the classic feed layout, which has its own title fields.
 */
@StrictQuery
internal object ClassicPostElementConstructorFingerprint : Fingerprint(
    classFingerprint = feedElementToStringFingerprint("ClassicPostElement"),
    name = "<init>",
    filters = listOf(
        // Title.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        // Is read. Some versions move a register in between. A larger distance
        // would match the String fields a few fields before the title.
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            location = MatchAfterWithin(1)
        ),
        // Thumbnail.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(3)
        ),
        // Flairs.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(3)
        ),
        // Mod role indicators.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/util/List;",
            location = MatchAfterWithin(3)
        ),
        // Mod action indicators.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/util/List;",
            location = MatchAfterWithin(3)
        ),
        // Mod note label.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Lcom/reddit/mod/notes/domain/model/NoteLabel;",
            location = MatchAfterWithin(3)
        ),
        // Translated title.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(3)
        )
    )
)

internal object PostFlairFeedPresentationModelToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "PostFlairFeedPresentationModel(text=",
        ", richText="
    )
)

internal object PostFlairsElementToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "PostFlairsElement(linkId=",
        ", uniqueId="
    )
)

internal object PostPreviewFeedElementToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "PostPreviewFeedElement(linkId=",
        ", uniqueId="
    )
)

@StrictQuery
internal object PostsToLoadLegacyFingerprint : Fingerprint(
    returnType = "Ljava/util/ArrayList;",
    parameters = listOf(
        "Ljava/util/List;",
        "Lcom/reddit/feeds/data/FeedType;"
    ),
    filters = listOf(
        instanceOf(type = { PostPreviewFeedElementToStringFingerprint().declaredClass!!.descriptor }),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        // Secondary fetch removed flag of one group of feed types.
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.GOTO, MatchAfterImmediately()),
        // Secondary fetch removed flag of the other group of feed types.
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, MatchAfterImmediately())
    )
)

internal object PostFlairFeedPresentationModelConstructorFingerprint : Fingerprint(
    classFingerprint = PostFlairFeedPresentationModelToStringFingerprint,
    name = "<init>",
    filters = listOf(
        // Text.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        // Rich text.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        // Id.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        // Community name.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        )
    )
)

internal object PostFlairsElementConstructorFingerprint : Fingerprint(
    classFingerprint = PostFlairsElementToStringFingerprint,
    name = "<init>",
    filters = listOf(
        // Link id.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        // Unique id.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        // Promoted.
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        // Identifier.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        // Flairs.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        )
    )
)

internal object FeedFilterLinkConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Link;",
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            name = "url",
            type = "Ljava/lang/String;",
        ),
    ),
)

internal val feedElementLinkIdMethod = findMethodDirect {
    val base = PostTitleElementToStringFingerprint().declaredClass!!.superClass!!
    base.methods.single {
        it.name == "getLinkId" && it.paramTypeNames.isEmpty() && it.returnTypeName == "java.lang.String"
    }
}

internal val postTitleTextFields = findFieldListDirect {
    PostTitleElementConstructorFingerprint.instructionMatches.let {
        listOf(it[4], it[5]).map { match -> match.instruction.fieldRef!! }
    }
}

internal val previewTextFields = findFieldListDirect {
    PreviewTextElementConstructorFingerprint.instructionMatches.let {
        listOf(it[4], it[7]).map { match -> match.instruction.fieldRef!! }
    }
}

internal val richTextField = findFieldDirect {
    RichTextElementConstructorFingerprint.instructionMatches[4].instruction.fieldRef!!
}

internal val classicPostTextFields = findFieldListDirect {
    ClassicPostElementConstructorFingerprint.instructionMatches.let {
        listOf(it[0], it[7]).map { match -> match.instruction.fieldRef!! }
    }
}

internal val postFlairsField = findFieldDirect {
    PostFlairsElementConstructorFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val postFlairModelFields = findFieldListDirect {
    PostFlairFeedPresentationModelConstructorFingerprint.instructionMatches.let {
        listOf(it[0], it[3]).map { match -> match.instruction.fieldRef!! }
    }
}

internal val secondaryFetchFlagFields = findFieldListDirect {
    val match = PostsToLoadLegacyFingerprint.match()
    val instructions = match.method.instructions.toList()
    val lazyReads = listOf(match.instructionMatches[2], match.instructionMatches[5])
    lazyReads.map { read ->
        // The lazy flag field is read immediately before getValue(), the Boolean cast and unboxing.
        instructions[read.index - 4].fieldRef!!
    }
}

internal val secondaryFetchFlagOwner = findFieldDirect {
    val match = PostsToLoadLegacyFingerprint.match()
    val ownerType = secondaryFetchFlagFields().first().declaredClass!!
    val ownerCastIndex = match.instructionMatches[2].index - 5
    val flagsInterface = ownerType.interfaces.single()
    match.method.instructions.toList()
        .take(ownerCastIndex)
        .last { it.fieldRef?.type?.descriptor == flagsInterface.descriptor }
        .fieldRef!!
}
