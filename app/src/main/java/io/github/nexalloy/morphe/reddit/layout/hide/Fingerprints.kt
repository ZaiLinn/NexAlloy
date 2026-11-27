@file:Suppress("ClassName")

package io.github.nexalloy.morphe.reddit.layout.hide

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.reddit.layout.feed.PostFlairFeedPresentationModelToStringFingerprint
import io.github.nexalloy.morphe.reddit.layout.feed.PostFlairsElementToStringFingerprint
import io.github.nexalloy.morphe.reddit.layout.feed.PostPreviewFeedElementToStringFingerprint
import io.github.nexalloy.morphe.string

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5296
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Normal Reddit Answers preview section in search results.
 */
internal object SearchAnswersPreviewSectionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("SearchAnswersPreviewSection(viewState=")
    )
)

/**
 * Streaming Reddit Answers preview section in search results.
 */
internal object SearchAnswersPreviewStreamingSectionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("SearchAnswersPreviewStreamingSection(viewState=")
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

internal object AskButtonComposableFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("trailing_ask_button"),
        string("search_ask_icon"),
        string("search_ask_label")
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5156
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Constructor of the comments loaded for a post. Every comment load goes through it,
 * before Reddit builds its comment tree (and its presentation models in older versions).
 */
internal object CommentsResultConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/CommentsResultWithSource;",
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            name = "comments",
            type = "Ljava/util/List;"
        )
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5298
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Getter of the author's user flair text on a comment.
 */
internal object CommentAuthorFlairTextFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Comment;",
    name = "getAuthorFlairText",
    returnType = "Ljava/lang/String;",
    parameters = listOf()
)

/**
 * Getter of the author's rich-text user flair on a comment.
 */
internal object CommentAuthorFlairRichTextFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Comment;",
    name = "getAuthorFlairRichText",
    returnType = "Ljava/util/List;",
    parameters = listOf()
)

/**
 * Getter of the author's community achievement badge on a comment.
 */
internal object CommentAuthorAchievementsBadgeFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Comment;",
    name = "getAuthorAchievementsBadge",
    returnType = "Lcom/reddit/domain/model/AchievementBadge;",
    parameters = listOf()
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5241
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Draw behind lambda of a comment that draws one indent line for each reply level.
 */
internal object CommentIndentLinesFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        // Alpha of faded lines.
        literal(0.4f),
        // Alpha of each reply level.
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "get"),
        methodCall(name = "getLayoutDirection"),
        // drawLine(color, start, end, strokeWidth, cap, pathEffect, alpha, default arguments mask)
        methodCall(
            returnType = "V",
            parameters = listOf("L", "J", "J", "J", "F", "I", "L", "F", "I")
        ),
        // Loop check of the reply level.
        opcode(Opcode.IF_EQ, location = MatchAfterWithin(5))
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

private object CommunityRecommendationSectionParentFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("community_recomendation_section_")
    )
)

internal object CommunityRecommendationSection_2026_18_Fingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    strings = listOf(
        "section_title",
        "recommendation_chaining"
    )
)

internal object CommunityRecommendationsComposeMethodFingerprint : Fingerprint(
    classFingerprint = CommunityRecommendationSectionParentFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "I")
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5297
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Composable of the Community highlights section at the top of subreddits.
 */
internal object CommunityHighlightsFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    filters = listOf(
        methodCall(name = "isEmpty", parameters = listOf(), returnType = "Z"),
        methodCall(
            definingClass = "Ljava/lang/Boolean;",
            name = "valueOf",
            location = MatchAfterWithin(5)
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        string("content visibility"),
        methodCall(
            parameters = listOf("Ljava/lang/Object;", "L", "L", "L", "Ljava/lang/String;", "L", "L", "L", "I", "I"),
            returnType = "V",
            location = MatchAfterWithin(4)
        )
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5303
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Red dot badge on the community menu button.
 */
internal object CommunityMenuBadgeFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lcom/reddit/ui/compose/ds/BadgeSentiment;",
            name = "Brand"
        ),
        string("community_menu_badge", location = MatchAfterWithin(5)),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "Ljava/lang/String;"),
            returnType = "L",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC_RANGE,
            parameters = listOf(
                "Ljava/lang/String;",
                "L",
                "Lcom/reddit/ui/compose/ds/BadgeSentiment;",
                "Z",
                "Z",
                "L",
                "L",
                "I",
                "I"
            ),
            returnType = "V",
            location = MatchAfterWithin(12)
        ),
        opcode(Opcode.GOTO, location = MatchAfterImmediately())
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3260
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

internal object PostFlairsSectionToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "PostFlairsSection(feedElement="
    )
)

internal object FeedPostTitleSectionToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "FeedPostTitleSection(data="
    )
)

/**
 * Reddit's mapper that creates a flair element with a single flair from a GraphQL fragment.
 * The feed post mapper creates it the same way, but has no community placeholder strings.
 */
internal object PostFlairsElementMapperFingerprint : Fingerprint(
    returnType = { PostFlairsElementToStringFingerprint().declaredClass!!.descriptor },
    strings = listOf(
        "subredditName",
        "subredditId"
    ),
    filters = listOf(
        methodCall(
            definingClass = { PostFlairFeedPresentationModelToStringFingerprint().declaredClass!!.descriptor },
            name = "<init>"
        ),
        // Creates a persistent list from an array.
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("[Ljava/lang/Object;")
        ),
        newInstance(type = { PostFlairsElementToStringFingerprint().declaredClass!!.descriptor }),
        methodCall(
            definingClass = { PostFlairsElementToStringFingerprint().declaredClass!!.descriptor },
            name = "<init>"
        )
    )
)

internal object PostPreviewFeedElementConstructorFingerprint : Fingerprint(
    classFingerprint = PostPreviewFeedElementToStringFingerprint,
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
        // Child elements, converted to an immutable list.
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/Iterable;")
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Kotlin data class copy method with a default value bit mask.
 */
internal object PostPreviewFeedElementCopyFingerprint : Fingerprint(
    definingClass = { PostPreviewFeedElementToStringFingerprint().declaredClass!!.descriptor },
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = { PostPreviewFeedElementToStringFingerprint().declaredClass!!.descriptor },
    filters = listOf(
        // Flairs element default value bit.
        opcode(Opcode.AND_INT_LIT16),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = { PostPreviewFeedElementToStringFingerprint().declaredClass!!.descriptor },
            type = { PostFlairsElementToStringFingerprint().declaredClass!!.descriptor },
            location = MatchAfterImmediately()
        )
    )
)

internal object FeedPostSectionToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "FeedPostSection(linkId=",
        ", sections="
    )
)

internal object FeedPostSectionConstructorFingerprint : Fingerprint(
    classFingerprint = FeedPostSectionToStringFingerprint,
    name = "<init>",
    filters = listOf(
        // Link id.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        // Sections.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        ),
        // Unique id.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        // Identifier.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterImmediately()
        )
    )
)

internal object ClassicPostElementToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "ClassicPostElement(linkId=",
        ", uniqueId="
    )
)

internal object ClassicPostElementConstructorFingerprint : Fingerprint(
    classFingerprint = ClassicPostElementToStringFingerprint,
    name = "<init>",
    filters = listOf(
        // Flairs element.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = { PostFlairsElementToStringFingerprint().declaredClass!!.descriptor }
        )
    )
)

internal object ClassicPostSectionToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "ClassicPostSection(linkId=",
        ", data="
    )
)

internal object ClassicPostSectionConstructorFingerprint : Fingerprint(
    classFingerprint = ClassicPostSectionToStringFingerprint,
    name = "<init>",
    filters = listOf(
        // Classic post element.
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = { ClassicPostElementToStringFingerprint().declaredClass!!.descriptor }
        )
    )
)

internal object FeedPostSectionMapperFingerprint : Fingerprint(
    filters = listOf(
        newInstance(type = { FeedPostSectionToStringFingerprint().declaredClass!!.descriptor }),
        methodCall(
            definingClass = { FeedPostSectionToStringFingerprint().declaredClass!!.descriptor },
            name = "<init>"
        ),
        opcode(Opcode.RETURN_OBJECT, MatchAfterImmediately())
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4990
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * The custom post feed element, which shows a Reddit Developer Platform (Devvit) post in a feed,
 * says which web view resource it loads ahead of time. It is the only feed element of this source.
 *
 * 2026.24.0+
 */
internal object CustomPostFeedElementResourcesFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            smali = "Lcom/reddit/qsf/components/QsfContentType;->CUSTOM_POST_WEBVIEW:Lcom/reddit/qsf/components/QsfContentType;"
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            smali = "Lcom/reddit/qsf/components/QsfSourceName;->CUSTOM_POST_FEED_ELEMENT:Lcom/reddit/qsf/components/QsfSourceName;"
        )
    )
)

internal object CustomPostFeedElementConstructorFingerprint : Fingerprint(
    classFingerprint = CustomPostFeedElementResourcesFingerprint,
    name = "<init>",
    returnType = "V"
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

internal object BottomNavScreenListBuilderFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/launch/bottomnav/BottomNavScreen;",
    returnType = "L",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters = listOf(
        newInstance("Ljava/util/ArrayList;"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            smali = "Ljava/util/Iterator;->hasNext()Z"
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Lcom/reddit/launch/bottomnav/BottomNavTab;"
        ),
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("Ljava/lang/Iterable;"))
    )
)

internal val bottomNavTabField = findFieldDirect {
    BottomNavScreenListBuilderFingerprint.instructionMatches[2].instruction.fieldRef!!
}

internal val bottomNavImmutableList = findMethodDirect {
    BottomNavScreenListBuilderFingerprint.instructionMatches[3].instruction.methodRef!!
}

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5225
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Maps a 'SearchRow' GraphQL element to the app model.
 * Matches the 'SearchQuerySuggestion' type check of the row children.
 */
internal object SearchRowMapperFingerprint : Fingerprint(
    filters = listOf(
        string("SearchRowComponentHeaderPresentation"),
        string("SearchRowOverflowHeaderPresentation"),
        string("SearchQuerySuggestion"),
        // Intrinsics.areEqual(Object, Object), which is obfuscated in some app versions.
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Z",
            location = MatchAfterWithin(5)
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately())
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5022
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * toString of the state of an action bar button, such as the comment, repost, share and mod
 * buttons of posts and the reply button of comments.
 */
internal object ActionBarActionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("ActionBarAction(contentDescription=")
    )
)

/**
 * toString of the state of an action bar button, which logs its label.
 */
internal object ActionBarActionLabelFingerprint : Fingerprint(
    classFingerprint = ActionBarActionToStringFingerprint,
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string(", label="),
        methodCall(name = "append", location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately())
    )
)

/**
 * toString of the state of the vote buttons of an action bar.
 */
internal object ActionBarVoteStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("ActionBarVoteState(selectedDirection=")
    )
)

/**
 * toString of the state of the vote buttons of an action bar, which logs whether the score is hidden.
 */
internal object ActionBarVoteStateHideScoreFingerprint : Fingerprint(
    classFingerprint = ActionBarVoteStateToStringFingerprint,
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string(", hideScore="),
        methodCall(name = "append", location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately())
    )
)

/**
 * Constructor of the vote button state, which sets whether the score is hidden.
 */
internal object ActionBarVoteStateConstructorFingerprint : Fingerprint(
    classFingerprint = ActionBarVoteStateToStringFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(
            reference = { ActionBarVoteStateHideScoreFingerprint.instructionMatches.last().instruction.fieldRef!! },
            opcode = Opcode.IPUT_BOOLEAN
        )
    )
)

/**
 * Whether the score of a comment is hidden, like in new posts of some communities.
 */
internal object CommentScoreHiddenFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/Comment;",
    name = "getScoreHidden",
    returnType = "Z",
    parameters = listOf()
)

/**
 * toString of the action bar section of the post detail screen, which logs its data.
 */
internal object PostActionBarSectionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("PostActionBarSection(data="),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(5)
        )
    )
)

context(bridge: org.luckypray.dexkit.DexKitBridge)
private val actionType get() = ActionBarActionToStringFingerprint().declaredClass!!.descriptor
context(bridge: org.luckypray.dexkit.DexKitBridge)
private val voteStateType get() = ActionBarVoteStateToStringFingerprint().declaredClass!!.descriptor

/**
 * Call of the action bar, which posts, comments and the media viewer use.
 * Parameters: vote state, comment button, modifier, appearance, then the buttons that are null
 * when not shown: the award button before 2026.39.0, a composable lambda, and the repost, share
 * and mod buttons. 2026.37.0 also has a second award button after the mod button,
 * which is shown instead of the first by the refreshed action bar of a server flag.
 */
private val actionBarCallFilter = methodCall(
    parameters = {
        listOf(voteStateType, actionType, "L", "L", actionType, actionType, actionType, "L", "I", "I")
    },
    returnType = "V",
    opcode = Opcode.INVOKE_STATIC_RANGE
)

/**
 * Footer of a post in the feeds.
 */
internal object PostFooterFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("post_footer"),
        // Modifier.testTag
        methodCall(
            parameters = listOf("L", "Ljava/lang/String;"),
            returnType = "L",
            location = MatchAfterImmediately()
        ),
        actionBarCallFilter
    )
)

/**
 * Footer of a post in the feeds that use the older post layout, with RplLinkFooterActionBarState.
 */
internal object LinkFooterActionBarFingerprint : Fingerprint(
    filters = listOf(
        string("rpl_action_bar"),
        actionBarCallFilter
    )
)

/**
 * Action bar of the post in the post detail screen, which shows the comments of the post.
 */
internal object PostDetailActionBarFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        // A value of the section data.
        fieldAccess(
            definingClass = {
                PostActionBarSectionToStringFingerprint.instructionMatches.last().instruction.fieldRef!!.typeSign
            }
        ),
        actionBarCallFilter
    )
)

/**
 * Action bar of a post in the media viewer.
 */
internal object MediaViewerActionBarFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("action_bar"),
        actionBarCallFilter
    )
)

/**
 * Buttons on the side of a post in the media viewer: the vote buttons, then the comment, repost,
 * and share or mod buttons, which are shown by the same button composable.
 * Button parameters: icon, on click, content description, count test tag, and more.
 */
internal object MediaViewerRailFingerprint : Fingerprint(
    filters = listOf(
        string("comment_count"),
        string("comment_button", location = MatchAfterWithin(8)),
        // Comment button.
        methodCall(
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC_RANGE,
            location = MatchAfterWithin(8)
        ),
        string("post_share_count")
    )
)

/**
 * Vote buttons on the side of a post in the media viewer.
 */
internal object MediaViewerRailVoteButtonsFingerprint : Fingerprint(
    definingClass = { MediaViewerRailFingerprint.instructionMatches[2].instruction.methodRef!!.declaredClass!!.descriptor },
    returnType = { "V" },
    filters = listOf(
        string("upvote_button")
    )
)

/**
 * Action bar button, such as the comment, repost, share and mod buttons.
 */
internal object ActionBarButtonFingerprint : Fingerprint(
    definingClass = { PostFooterFingerprint.instructionMatches.last().instruction.methodRef!!.declaredClass!!.descriptor },
    returnType = { "V" },
    parameters = {
        listOf(
            actionType,
            "L",
            "Lcom/reddit/ui/compose/ds/ContentActionButtonAppearance;",
            "L",
            "L",
            "Lcom/reddit/ui/compose/ds/ContentActionButtonSize;",
            "L",
            "L",
            "I",
            "I"
        )
    },
    filters = listOf(
        // Default button size. Methods with the same parameters that other patches add,
        // such as the button of 'Collapse all comments', pass the size on and do not read it.
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lcom/reddit/ui/compose/ds/ContentActionButtonSize;",
            name = "Small"
        )
    )
)

/**
 * Upvote and downvote buttons of an action bar.
 */
internal object ActionBarVoteButtonsFingerprint : Fingerprint(
    definingClass = { PostFooterFingerprint.instructionMatches.last().instruction.methodRef!!.declaredClass!!.descriptor },
    returnType = { "V" },
    parameters = {
        listOf(
            voteStateType,
            "Lcom/reddit/ui/compose/ds/VoteButtonGroupAppearance;",
            "L",
            "F",
            "F",
            "Z",
            "Lcom/reddit/ui/compose/ds/VoteButtonGroupSize;",
            "L",
            "I",
            "I"
        )
    }
)

/**
 * Join button in the header of a post in the feeds.
 */
internal object PostJoinButtonFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("post_join_button")
    )
)

/**
 * Award button of a comment.
 * Parameters: enabled, comment id, awards state, on award click, composer, flags.
 */
internal object CommentAwardButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z", "Ljava/lang/String;", "L", "L", "L", "I"),
    filters = listOf(
        string("award_comment_icon")
    )
)

private val postDetailHostScreenFilter = fieldAccess(
    opcode = Opcode.SGET_OBJECT,
    definingClass = "Lcom/reddit/subscriptions/HostScreen;",
    name = "POST_DETAIL",
)

private val postDetailSubscriptionStateConstructor = methodCall(
    name = "<init>",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Lcom/reddit/subscriptions/HostScreen;",
        "Lkotlin/jvm/functions/Function1;",
        "I",
    ),
)

internal object PostDetailLegacyHeaderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "Lkotlin/jvm/functions/Function0;", "L", "L", "I"),
    filters = listOf(postDetailHostScreenFilter, postDetailSubscriptionStateConstructor),
)

internal object PostDetailMetadataHeaderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "Lkotlin/jvm/functions/Function1;", "L", "Ljava/lang/String;", "L", "L", "L", "I"),
    filters = listOf(postDetailHostScreenFilter, postDetailSubscriptionStateConstructor),
)

internal val postDetailJoinState = findClassDirect {
    PostDetailMetadataHeaderFingerprint.instructionMatches.last().instruction.methodRef!!.declaredClass!!
}

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/5181
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

/**
 * Lazy list key method of 'RelatedPostLazyListSection'.
 */
private object RelatedPostSectionKeyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("related_posts_")
    )
)

/**
 * Adds the 'Related Posts' label and posts below the comments to the lazy list.
 */
internal object RelatedPostSectionContentFingerprint : Fingerprint(
    classFingerprint = RelatedPostSectionKeyFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "L", "L")
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

internal const val HEADER_ITEM_CLASS = "Lcom/reddit/screens/drawer/community/HeaderItem;"

private const val COMMUNITY_DRAWER_SCREEN_CLASS =
    "Lcom/reddit/screens/drawer/community/CommunityDrawerScreen;"

private const val BIND_LIST_ON_VIEW_CLASS =
    $$"Lcom/reddit/screens/drawer/community/CommunityDrawerPresenter$bindListOnView$2;"

private object CommunityDrawerBuilderParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = $$"Lcom/reddit/navdrawer/analytics/CommunityDrawerAnalytics$Section;",
    parameters = listOf(HEADER_ITEM_CLASS),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Enum;->ordinal()I"),
        fieldAccess(smali = $$"Lcom/reddit/navdrawer/analytics/CommunityDrawerAnalytics$Section;->" +
                $$"ABOUT:Lcom/reddit/navdrawer/analytics/CommunityDrawerAnalytics$Section;")
    )
)

internal object CommunityDrawerBuilderFingerprint : Fingerprint(
    classFingerprint = CommunityDrawerBuilderParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    filters = listOf(
        methodCall(smali = "Ljava/util/Collection;->isEmpty()Z"),
    ),
    parameters = listOf("Ljava/util/List;", "Ljava/util/Collection;", "L", "L", "Z", "I")
)

internal object CtaItemUiModelToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("CTAItemUiModel(uniqueId="),
        string(", titleResId="),
        fieldAccess(opcode = Opcode.IGET, type = "I", definingClass = "this", location = MatchAfterWithin(5))
    )
)

internal object GenericItemUiModelToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("GenericItemUiModel(uniqueId="),
        string(", titleResId="),
        fieldAccess(opcode = Opcode.IGET, type = "I", definingClass = "this", location = MatchAfterWithin(5))
    )
)

internal object DividerItemUiModelToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("DividerItemUiModel(uniqueId=", StringComparisonType.STARTS_WITH)
    )
)

/**
 * Presenter method that handles the click of a sidebar item.
 */
internal class CommunityDrawerItemClickFingerprint(ctaItemType: String) : Fingerprint(
    definingClass = { CommunityDrawerBindListFingerprint.instructionMatches.first().instruction.fieldRef!!.declaredClass!!.descriptor },
    returnType = { "V" },
    parameters = { listOf("L") },
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = COMMUNITY_DRAWER_SCREEN_CLASS
        ),
        checkCast(ctaItemType),
        // Closes the sidebar.
        methodCall(
            definingClass = COMMUNITY_DRAWER_SCREEN_CLASS,
            returnType = "V",
            parameters = listOf()
        ),
        fieldAccess(smali = $$"Lcom/reddit/navdrawer/analytics/CommunityDrawerAnalytics$Noun;->" +
                $$"REDDIT_RULES:Lcom/reddit/navdrawer/analytics/CommunityDrawerAnalytics$Noun;")
    )
)

/**
 * Passes the finished sidebar list to the adapter.
 */
internal object CommunityDrawerBindListFingerprint : Fingerprint(
    definingClass = BIND_LIST_ON_VIEW_CLASS,
    name = "invokeSuspend",
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/util/List;")
    )
)

internal object HeaderItemUiModelToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("HeaderItemUiModel(uniqueId=")
    )
)

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

// 2026.16.0+
internal object LocaleLanguageManagerInitFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/content/res/Configuration;"),
    filters = listOf(
        methodCall(smali = "Landroid/content/Context;->getApplicationContext()Landroid/content/Context;"),
        fieldAccess(smali = "Ljava/util/Locale;->ENGLISH:Ljava/util/Locale;"),
        string("UI_LANGUAGE_TAG")
    )
)

internal object LocaleLanguageManagerContentLanguagesFingerprint : Fingerprint(
    classFingerprint = LocaleLanguageManagerInitFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/", // 'Ljava/util/ArrayList;' or 'Ljava/util/List;'
    parameters = listOf(),
    filters = listOf(
        opcode(Opcode.IF_EQZ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/util/ArrayList;",
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.RETURN_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

internal object SearchSectionHeaderFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("search_section_header"),
        string("search_section_title")
    )
)

internal object TrendingItemFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("search_trending_item")
    )
)

internal object TypeaheadSuggestionItemFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("typeahead_suggestion_item")
    )
)

internal object TrendingFeedUnitSectionFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("trending_feed_unit_section")
    )
)

internal object TrendingFeedUnitDismissedSectionFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("trending_feed_unit_dismissed_section")
    )
)

internal val homeFlairPresentationConstructor = findMethodDirect {
    PostFlairsElementMapperFingerprint.instructionMatches[0].instruction.methodRef!!
}

internal val homeFlairPersistentList = findMethodDirect {
    PostFlairsElementMapperFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val homeFlairElementConstructor = findMethodDirect {
    PostFlairsElementMapperFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val homeFlairPreviewFields = findFieldListDirect {
    val fields = PostPreviewFeedElementConstructorFingerprint.instructionMatches
    listOf(fields[0], fields[1], fields[3], fields[6]).map { it.instruction.fieldRef!! } +
        PostPreviewFeedElementCopyFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val homeFlairSectionFields = findFieldListDirect {
    FeedPostSectionConstructorFingerprint.instructionMatches.map { it.instruction.fieldRef!! }
}

internal val homeFlairClassicSectionFields = findFieldListDirect {
    listOf(ClassicPostSectionConstructorFingerprint, ClassicPostElementConstructorFingerprint)
        .map { it.instructionMatches.first().instruction.fieldRef!! }
}

internal val ctaTitleResource = findFieldDirect {
    CtaItemUiModelToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val genericTitleResource = findFieldDirect {
    GenericItemUiModelToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val drawerBoundList = findFieldDirect {
    CommunityDrawerBindListFingerprint.instructionMatches.first().instruction.fieldRef!!
}

private fun org.luckypray.dexkit.DexKitBridge.drawerClickFingerprint() =
    CommunityDrawerItemClickFingerprint(CtaItemUiModelToStringFingerprint().declaredClass!!.descriptor)

internal val drawerItemClick = findMethodDirect { drawerClickFingerprint().run() }

internal val drawerScreen = findFieldDirect {
    drawerClickFingerprint().instructionMatches[0].instruction.fieldRef!!
}

internal val drawerClose = findMethodDirect {
    drawerClickFingerprint().instructionMatches[2].instruction.methodRef!!
}

internal val shareActionLabel = findFieldDirect {
    ActionBarActionLabelFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val voteScoreHiddenField = findFieldDirect {
    ActionBarVoteStateHideScoreFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal val postActionBarComposable = findMethodDirect {
    val calls = listOf(
        PostFooterFingerprint,
        LinkFooterActionBarFingerprint,
        PostDetailActionBarFingerprint,
        MediaViewerActionBarFingerprint,
    ).map { it.instructionMatches.last().instruction.methodRef!! }
    check(calls.map { it.descriptor }.distinct().size == 1) {
        "Reddit post action bars use different composables"
    }
    calls.first()
}

internal val mediaViewerRailButton = findMethodDirect {
    MediaViewerRailFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val commentIndentDrawLine = findMethodDirect {
    CommentIndentLinesFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val communityMenuBadgeTestTag = findMethodDirect {
    CommunityMenuBadgeFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val communityMenuBadgeComposable = findMethodDirect {
    CommunityMenuBadgeFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val highlightsAnimatedContent = findMethodDirect {
    CommunityHighlightsFingerprint.instructionMatches.last().instruction.methodRef!!
}

internal val querySuggestionComparison = findMethodDirect {
    SearchRowMapperFingerprint.instructionMatches[3].instruction.methodRef!!
}
