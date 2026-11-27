package io.github.nexalloy.morphe.music.layout.returnyoutubedislike

import app.morphe.extension.music.patches.ReturnYouTubeDislikePatch
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.returnyoutubedislike.ui.ReturnYouTubeDislikeAboutPreference
import app.morphe.extension.shared.returnyoutubedislike.ui.ReturnYouTubeDislikeDebugStatsPreferenceCategory
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.music.video.information.musicVideoIdHook
import io.github.nexalloy.morphe.music.video.information.musicVideoInformationPatch
import io.github.nexalloy.morphe.shared.SpannableStringBuilderFingerprint
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.LikeEndpointParserFingerprint
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.endpointServiceNameField
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.hookLikeDislikeButtons
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.votePayloadPresenceField
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.votePayloadTargetField
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.votePayloadUnpackMethod
import io.github.nexalloy.morphe.shared.layout.returnyoutubedislike.voteVideoIdField
import io.github.nexalloy.morphe.shared.misc.litho.context.ConversionContext
import io.github.nexalloy.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook

/**
 * The Return YouTube Dislike screen of the music settings. The key, icon and sorting match the
 * upstream music settings patch, so the screen and its preferences keep their translations.
 */
private val returnYouTubeDislikeScreen = PreferenceScreen.Screen(
    key = "morphe_settings_music_screen_07_return_youtube_dislike",
    summaryKey = null,
    icon = "@drawable/morphe_settings_screen_09_return_youtube_dislike",
    iconBold = "@drawable/morphe_settings_screen_09_return_youtube_dislike_bold",
    layout = "@layout/morphe_preference_with_icon",
    sorting = PreferenceScreenPreference.Sorting.UNSORTED,
)

/**
 * Runtime port of the music Return YouTube Dislike patch. The votes are captured with the same
 * payload fields the YouTube port resolves, and the dislike count replaces the segmented like
 * and dislike text of the action bar, like the upstream `hookSpannableString(overrideSpan=true)`.
 */
val returnYouTubeDislikePatch = patch(
    name = "Return YouTube Dislike",
    description = "Adds an option to show the dislike count of tracks with Return YouTube Dislike.",
) {
    dependsOn(musicVideoInformationPatch)

    returnYouTubeDislikeScreen.addPreferences(
        SwitchPreference("morphe_ryd_enabled"),
        SwitchPreference("morphe_ryd_dislike_percentage", summary = true),
        SwitchPreference("morphe_ryd_estimated_like", summary = true),
        SwitchPreference("morphe_ryd_toast_on_connection_error", summary = true),
        screenInfoPreferenceCategory(
            key = "morphe_ryd_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_ryd_attribution",
                    titleKey = null,
                    tag = ReturnYouTubeDislikeAboutPreference::class.java,
                    selectable = true
                )
            )
        ),
        PreferenceCategory(
            key = "morphe_ryd_statistics_category",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = emptySet(), // Preferences are added by custom class at runtime.
            tag = ReturnYouTubeDislikeDebugStatsPreferenceCategory::class.java
        )
    )

    // region Fail-closed helper.

    fun hookRegion(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Logger.printException({ "Return YouTube Dislike hook failed: $name" }, it) }
    }

    // endregion

    // region Hook like/dislike/remove like button clicks to send votes to the API.

    hookRegion("send vote") {
        val endpointField = ::endpointServiceNameField.field
        val videoIdField = ::voteVideoIdField.field
        val targetField = ::votePayloadTargetField.field
        val presenceField = ::votePayloadPresenceField.field

        LikeEndpointParserFingerprint.hookMethod(scopedHook(::votePayloadUnpackMethod.method) {
            after {
                val payload = it.result ?: return@after
                if (targetField.declaringClass.isInstance(payload) &&
                    presenceField.getInt(payload) and 2 != 0
                ) {
                    outerParam.setObjectExtra("rydVoteTarget", targetField.get(payload))
                }
            }
        })
        LikeEndpointParserFingerprint.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                val target = it.getObjectExtra("rydVoteTarget") ?: return@after
                ReturnYouTubeDislikePatch.sendVote(
                    endpointField.get(it.thisObject) as String,
                    videoIdField.get(target) as String
                )
            }
        }
    }

    // endregion

    // region Hook the creation and cached lookup of text spans.

    // The span the segmented like and dislike button builds is replaced with the span of
    // the extension, like the upstream overrideSpan injection after SpannableString.valueOf.
    hookRegion("dislike span") {
        SpannableStringBuilderFingerprint.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                val original = it.result as? CharSequence ?: return@after
                val context = it.args[0] ?: return@after
                it.result = ReturnYouTubeDislikePatch.onLithoTextLoaded(
                    ConversionContext(context), original
                )
            }
        }
    }

    // endregion

    // region Inject newVideoLoaded event handler to update dislikes when a new track is loaded.

    musicVideoIdHook(ReturnYouTubeDislikePatch::newVideoLoaded)

    // endregion

    // region Draw the counts over the icon-only like and dislike buttons of the action bar.

    hookRegion("like dislike buttons") {
        hookLikeDislikeButtons(
            ReturnYouTubeDislikePatch::onComponentHostContentDescription,
            ReturnYouTubeDislikePatch::onYogaSetWidth
        )
    }

    // endregion
}
