package io.github.nexalloy.morphe.reddit.ad

import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.shared.Logger
import io.github.nexalloy.getObjectField
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.reddit.misc.version.VersionCheck
import io.github.nexalloy.morphe.reddit.misc.version.is_2026_04_0_or_greater
import io.github.nexalloy.morphe.reddit.misc.version.is_2026_16_0_or_greater
import io.github.nexalloy.patch
import io.github.nexalloy.setObjectField
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

val HideAds = patch(
    name = "Hide ads",
    description = "Adds options to hide ads."
) {
    dependsOn(VersionCheck)

    // region Filter promoted ads (does not work in popular or latest feed)

    fun hideOldAds(fingerprint: Fingerprint) {
        fingerprint.hookMethod {
            val iLink = classLoader.loadClass("com.reddit.domain.model.ILink")
            val getPromoted = iLink.methods.single { it.name == "getPromoted" }
            after { param ->
                if (!Settings.HIDE_POST_ADS.get()) return@after
                val arrayList = param.thisObject.getObjectField("children") as Iterable<Any?>
                val result = mutableListOf<Any?>()
                var filtered = 0
                for (item in arrayList) {
                    try {
                        if (item != null && iLink.isAssignableFrom(item.javaClass) && getPromoted(item) == true) {
                            filtered++
                            continue
                        }
                    } catch (_: Throwable) {
                        Logger.printDebug { "not iLink, keep it" }
                        // not iLink, keep it
                    }
                    result.add(item)
                }
                Logger.printDebug { "Filtered $filtered ads in ${arrayList.count()} posts" }
                param.thisObject.setObjectField("children", result)
            }
        }
    }

    hideOldAds(ListingFingerprint)

    if (!is_2026_16_0_or_greater) {
        hideOldAds(SubmittedListingFingerprint)
    }

    // A missing anchor here must not abort the whole patch, which would also drop
    // every hook installed below, so resolve failures only skip this region.
    runCatching {
        AdPostSectionConstructorFingerprint.hookMethod {
            val immutableListBuilder = ::ImmutableListBuilderReference.method
            before {
                if (!Settings.HIDE_POST_ADS.get()) return@before
                Logger.printDebug { "Emptied AdPostSection" }
                it.args[4] = immutableListBuilder(null, arrayListOf<Any>())
            }
        }
    }.onFailure { Logger.printException({ "AdPostSection hook not installed" }, it as? Exception) }

    // endregion

    // region Filter comment ads

    CommentsViewModelAdLoaderFingerprint.hookMethod {
        before {
            if (Settings.HIDE_COMMENT_ADS.get()) it.result = Unit
        }
    }

    // As of Reddit 2026.04+, placeholders are not hidden unless 'adsLoadCompleted' is false.
    // Hide placeholders by overriding 'adsLoadCompleted' to true.
    if (is_2026_04_0_or_greater) {
        CommentsAdStateConstructorFingerprint.hookMethod {
            val adsLoadCompletedField = ::adsLoadCompletedField.field
            after {
                if (Settings.HIDE_COMMENT_ADS.get()) {
                    adsLoadCompletedField.set(it.thisObject, true)
                }
            }
        }
    }

    // endregion

    // region Hide the promoted post composable

    // The promoted post composable also draws the ads of the feeds the Listing filter does
    // not cover, such as the popular and latest feed. The anchor appears in a shared
    // synthetic dispatcher too, so nothing is hooked unless a single candidate remains.
    val promotedPostCandidates = ::promotedPostComposables.dexMethodList
    if (promotedPostCandidates.size != 1) {
        Logger.printException {
            "Expected 1 promoted post composable, found ${promotedPostCandidates.size}; no hook installed"
        }
    } else {
        promotedPostCandidates.single().toMethod().hookMethod {
            before {
                // A composable returns Unit, which skips its body when set as the result.
                if (Settings.HIDE_POST_ADS.get()) it.result = Unit
            }
        }
    }

    // endregion

    // region Hide feed ad elements and search ads

    // Every feed element goes through one element-to-section converter, and every caller
    // skips a null section. The search ad and every non-post ad format is its own element.
    runCatching {
        val converter = ::elementSectionConverter.method
        val adElements = ::adElementToStrings.dexMethodList.map { it.toMethod().declaringClass }
        if (adElements.isEmpty()) error("No ad element classes found")

        converter.hookMethod {
            before {
                val element = it.args[0]
                if (Settings.HIDE_POST_ADS.get() && element != null &&
                    adElements.any { clazz -> clazz.isInstance(element) }
                ) {
                    it.result = null
                }
            }
        }
        Logger.printDebug { "Hiding ${adElements.size} of ${adElementLiterals.size} ad element type(s)" }
    }.onFailure {
        Logger.printException({ "Could not hide feed ad elements and search ads" }, it)
    }

    // endregion

    // region Hide the comment ad slot dispatcher

    // The dispatcher composes the placeholder, loaded ad and empty states of a comment ad
    // slot, so skipping its body removes the entire slot.
    val commentAdSlotCandidates = ::commentAdSlotDispatchers.dexMethodList
    if (commentAdSlotCandidates.size != 1) {
        Logger.printException {
            "Expected 1 comment ad slot dispatcher, found ${commentAdSlotCandidates.size}; no hook installed"
        }
    } else {
        commentAdSlotCandidates.single().toMethod().hookMethod {
            before {
                if (Settings.HIDE_COMMENT_ADS.get()) it.result = Unit
            }
        }
    }

    // endregion

    // region Hide pager promoted ads

    // The swipe-between-posts pager keeps its promoted posts. Marking every promoted page
    // summary as a promoted community post makes Reddit's own page builder filter drop it.
    val pagerBuilders = ::pagerPageBuilders.dexMethodList.map { it.toMethod() }
    if (pagerBuilders.isEmpty()) {
        Logger.printException { "Pager page builders not found; no hook installed" }
    } else {
        for (builder in pagerBuilders) {
            builder.hookMethod {
                before {
                    if (!Settings.HIDE_POST_ADS.get()) return@before
                    val pages = it.args[0] as? List<*> ?: return@before
                    if (pages.isNotEmpty()) it.args[0] = pages.map { page -> markPagerSummaryFiltered(page) ?: page }
                }
            }
        }
    }

    // endregion
}

/**
 * Returns a copy of the page summary flagged like an ad that Reddit already drops, or null
 * when the summary should stay unchanged. Pages may arrive from multiple threads, so the
 * reflection metadata of each summary type is cached once, thread-safely.
 */
private fun markPagerSummaryFiltered(item: Any?): Any? {
    item ?: return null
    val type = pagerSummaryTypes.getOrPut(item.javaClass) { PagerSummaryType.probe(item.javaClass) }
    return runCatching {
        type.markFiltered(item)
    }.onFailure {
        Logger.printException({ "Could not mark pager page summary ${item.javaClass.name}" }, it)
    }.getOrNull()
}

private val pagerSummaryTypes = ConcurrentHashMap<Class<*>, PagerSummaryType>()

/**
 * Maps each constructor parameter of a pager page summary to its field. The mapping is
 * probed by constructing instances with sentinel values, so it does not depend on R8's
 * field names.
 */
private class PagerSummaryType private constructor(
    private val constructor: Constructor<*>?,
    private val fields: List<Field>,
) {
    /**
     * Returns a copy of [item] whose `promoted` and `promotedCommunityPostV2` flags are set,
     * or null when the summary is not promoted or already flagged. Reddit's own pager filter
     * only drops a page when promoted is set as well.
     */
    fun markFiltered(item: Any): Any? {
        constructor ?: return null
        val values = fields.map { it.get(item) }.toTypedArray()
        if (values[PROMOTED_COMMUNITY_POST_INDEX] == true) return null
        if (values[PROMOTED_INDEX] != true) return null
        values[PROMOTED_INDEX] = true
        values[PROMOTED_COMMUNITY_POST_INDEX] = true
        return constructor.newInstance(*values)
    }

    companion object {
        // (id, uniqueId, subredditId, subreddit, promoted, promotedCommunityPostV2, surveyAd, blankAd)
        private const val PROMOTED_INDEX = 4
        private const val PROMOTED_COMMUNITY_POST_INDEX = 5
        private val SUMMARY_CONSTRUCTOR_PARAMETERS = List(4) { "java.lang.String" } + List(4) { "boolean" }

        private val UNSUPPORTED = PagerSummaryType(null, emptyList())

        fun probe(type: Class<*>): PagerSummaryType {
            val constructor = type.declaredConstructors.singleOrNull {
                it.parameterTypes.map(Class<*>::getName) == SUMMARY_CONSTRUCTOR_PARAMETERS
            }?.apply { isAccessible = true } ?: return UNSUPPORTED
            val instanceFields = type.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) }
                .onEach { it.isAccessible = true }
            if (instanceFields.size != 8) return UNSUPPORTED

            val strings = List(4) { "nexalloy-probe-$it" }
            val base: List<Any> = strings + List(4) { false }
            val mapping = (0 until 8).map { index ->
                val args = base.toMutableList()
                if (index >= 4) args[index] = true
                val probe = constructor.newInstance(*args.toTypedArray())
                val expected = args[index]
                instanceFields.singleOrNull { it.get(probe) == expected } ?: return UNSUPPORTED
            }
            if (mapping.distinct().size != 8) return UNSUPPORTED
            return PagerSummaryType(constructor, mapping)
        }
    }
}
