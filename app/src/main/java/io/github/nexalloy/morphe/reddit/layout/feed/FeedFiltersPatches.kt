package io.github.nexalloy.morphe.reddit.layout.feed

import app.morphe.extension.reddit.patches.FlairFiltersPatch
import app.morphe.extension.reddit.patches.KeywordFiltersPatch
import app.morphe.extension.reddit.patches.ShowFlairsInHomeFeedPatch
import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.shared.Logger
import app.morphe.extension.reddit.patches.RuntimeLink
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.reddit.ad.ListingFingerprint
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private class LinkAccess(val type: Class<*>) {
    private val id = type.getMethod("getKindWithId")
    private val title = type.getMethod("getTitle")
    private val text = type.getMethod("getSelftext")
    private val community = type.getMethod("getSubreddit")
    private val flair = type.getMethod("getLinkFlairText")

    fun adapt(host: Any): RuntimeLink = object : RuntimeLink {
        override fun getKindWithId() = id.invoke(host) as? String
        override fun getTitle() = this@LinkAccess.title.invoke(host) as? String
        override fun getSelftext() = text.invoke(host) as? String
        override fun getSubreddit() = community.invoke(host) as? String
        override fun getLinkFlairText() = flair.invoke(host) as? String
    }

    fun filter(links: List<*>, block: (List<*>) -> List<*>): List<*> {
        val originals = java.util.IdentityHashMap<Any, Any>()
        val adapted = links.map { item ->
            if (item != null && type.isInstance(item)) adapt(item).also { originals[it] = item }
            else item
        }
        val filtered = block(adapted)
        return if (filtered === adapted) links else filtered.map { originals[it] ?: it }
    }
}

private class FeedElementAdapter(
    val element: Any,
    private val linkId: String?,
) : KeywordFiltersPatch.FeedElement, FlairFiltersPatch.FeedElement {
    override fun getLinkId(): String? = linkId
}

private fun Method.adaptFeedElement(element: Any?): FeedElementAdapter? {
    if (element == null || !declaringClass.isInstance(element)) return null
    return FeedElementAdapter(element, invoke(element) as? String)
}

/**
 * The APK's feed elements do not implement Morphe's extension interfaces in the
 * runtime module. Pass adapters to the filters, then restore the host elements.
 */
private fun filterModernFeed(
    elements: List<*>,
    linkIdMethod: Method,
    filter: (List<*>) -> List<*>,
): List<*> {
    val adapters = elements.map { linkIdMethod.adaptFeedElement(it) ?: it }
    val filtered = filter(adapters)
    return if (filtered === adapters) elements else filtered.map {
        if (it is FeedElementAdapter) it.element else it
    }
}

/**
 * Hides feed posts of communities whose name matches a community filter, with the same
 * semantics as the keyword filters: one regular expression per line, ignoring case.
 * <p>
 * The community of a post is read from its [RuntimeLink] when Reddit creates it, and the posts
 * of old and modern feeds are filtered by post id, the same way as the keyword filters.
 */
private object CommunityFilters {
    private const val POST_ID_PREFIX = "t3_"

    /**
     * Number of posts to keep the communities of. More than the posts of a feed
     * that are kept loaded.
     */
    private const val MAX_POSTS = 1000

    private class Post(var community: String?) {
        /**
         * If a modern feed shows the post.
         */
        var shown = false
    }

    /**
     * The most recently created feed posts, by link id.
     */
    private val posts = object : LinkedHashMap<String, Post>(MAX_POSTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Post>): Boolean = size > MAX_POSTS
    }

    @Volatile
    private var cachedRules: String? = null

    @Volatile
    private var cachedPatterns: List<Pattern> = emptyList()

    private fun getPatterns(): List<Pattern> {
        val rules = Settings.COMMUNITY_FILTERS.get()
        if (rules == cachedRules) return cachedPatterns

        val patterns = KeywordFiltersPatch.getRules(rules).mapNotNull { rule ->
            try {
                KeywordFiltersPatch.compile(rule)
            } catch (ex: PatternSyntaxException) {
                // Possible with imported settings, the settings dialog does not save invalid rules.
                Logger.printException({ "Invalid community filter: $rule" }, ex)
                null
            }
        }
        cachedRules = rules
        cachedPatterns = patterns
        return patterns
    }

    private fun matches(patterns: List<Pattern>, community: String?): Boolean =
        community != null && patterns.any { it.matcher(community).find() }

    private fun getFullId(linkId: String): String =
        if (linkId.startsWith(POST_ID_PREFIX)) linkId else POST_ID_PREFIX + linkId

    private fun getPost(linkId: String): Post =
        posts.getOrPut(linkId) { Post(null) }

    /**
     * Called when Reddit creates a post model. Saves the community of the post by link id.
     */
    fun setLink(link: RuntimeLink) {
        try {
            if (getPatterns().isEmpty()) return

            val linkId = link.getKindWithId()
            // Null is not yet known.
            if (linkId == null) return

            synchronized(posts) {
                getPost(getFullId(linkId)).community = link.getSubreddit()
            }
        } catch (ex: Exception) {
            Logger.printException({ "setLink failure" }, ex)
        }
    }

    /**
     * Filters the posts of old feeds, and the posts that are opened from a modern feed.
     */
    fun filterLinks(links: List<*>): List<*> {
        try {
            // A single post is the post that is opened, such as from a link or a notification.
            if (links.size <= 1) return links

            val patterns = getPatterns()
            if (patterns.isEmpty()) return links

            var filtered = false
            val kept = links.filter { item ->
                if (item is RuntimeLink) {
                    val linkId = item.getKindWithId()
                    if (linkId != null && isCommunityFiltered(patterns, getFullId(linkId))) {
                        Logger.printDebug({ "Hiding post: $linkId" })
                        filtered = true
                        return@filter false
                    }
                }
                true
            }
            return if (filtered) kept else links
        } catch (ex: Exception) {
            Logger.printException({ "filterLinks failure" }, ex)
            return links
        }
    }

    /**
     * Filters the posts of modern feeds.
     */
    fun filterFeedElements(elements: List<*>): List<*> {
        try {
            val patterns = getPatterns()
            if (patterns.isEmpty()) return elements

            var filtered = false
            val kept = elements.filter { element ->
                if (element is FeedElementAdapter) {
                    val linkId = element.getLinkId()
                    if (linkId != null) {
                        val fullId = getFullId(linkId)
                        if (isCommunityFiltered(patterns, fullId)) {
                            Logger.printDebug({ "Hiding post: $linkId" })
                            filtered = true
                            return@filter false
                        }
                        setShown(fullId)
                    }
                }
                true
            }
            return if (filtered) kept else elements
        } catch (ex: Exception) {
            Logger.printException({ "filterFeedElements failure" }, ex)
            return elements
        }
    }

    private fun isCommunityFiltered(patterns: List<Pattern>, linkId: String): Boolean {
        val community = synchronized(posts) {
            val post = posts[linkId] ?: return false
            if (post.shown) return false
            post.community
        }
        return matches(patterns, community)
    }

    private fun setShown(linkId: String) {
        synchronized(posts) {
            getPost(linkId).shown = true
        }
    }
}

/** Port of Morphe #4867. */
internal val keywordFilters = patch(
    name = "Keyword filters",
    description = "Hides feed posts with a title or text that matches a configured regular expression, " +
        "and feed posts of a community whose name matches one.",
) {
    val processor = FeedElementProcessorFingerprint.method
    val listing = ListingFingerprint.constructor
    val linkConstructor = FeedFilterLinkConstructorFingerprint.constructor
    val linkAccess = LinkAccess(linkConstructor.declaringClass)
    val linkId = ::feedElementLinkIdMethod.method

    fun textHook(
        fingerprint: Fingerprint,
        textFields: List<Field>,
        vararg handlers: (KeywordFiltersPatch.FeedElement, String?) -> Unit,
    ): () -> Unit {
        val constructor = fingerprint.constructor
        check(textFields.size == handlers.size)
        val fields = textFields.onEach { it.isAccessible = true }.zip(handlers.asList())
        return {
            constructor.hookMethod {
                after { param ->
                    if (param.hasThrowable() || Settings.KEYWORD_FILTERS.get().isBlank()) return@after
                    val element = linkId.adaptFeedElement(param.thisObject) ?: return@after
                    fields.forEach { (field, handler) ->
                        handler(element, field.get(param.thisObject) as? String)
                    }
                }
            }
        }
    }

    val textHooks = listOf(
        textHook(
            PostTitleElementConstructorFingerprint, ::postTitleTextFields.fieldList,
            KeywordFiltersPatch::setTitle, KeywordFiltersPatch::setTranslatedTitle,
        ),
        textHook(
            PreviewTextElementConstructorFingerprint, ::previewTextFields.fieldList,
            KeywordFiltersPatch::setPreviewText, KeywordFiltersPatch::setTranslatedPreviewText,
        ),
        textHook(
            RichTextElementConstructorFingerprint, listOf(::richTextField.field),
            KeywordFiltersPatch::setRichText,
        ),
        textHook(
            ClassicPostElementConstructorFingerprint, ::classicPostTextFields.fieldList,
            KeywordFiltersPatch::setTitle, KeywordFiltersPatch::setTranslatedTitle,
        ),
    )
    textHooks.forEach { it() }

    linkConstructor.hookMethod {
        after {
            if (it.hasThrowable()) return@after
            if (Settings.KEYWORD_FILTERS.get().isBlank() && Settings.COMMUNITY_FILTERS.get().isBlank()) return@after
            val link = linkAccess.adapt(it.thisObject)
            if (Settings.KEYWORD_FILTERS.get().isNotBlank()) {
                KeywordFiltersPatch.setLink(link)
            }
            if (Settings.COMMUNITY_FILTERS.get().isNotBlank()) {
                CommunityFilters.setLink(link)
            }
        }
    }
    listing.hookMethod {
        before {
            val links = it.args[0] as? List<*> ?: return@before
            if (Settings.KEYWORD_FILTERS.get().isBlank() && Settings.COMMUNITY_FILTERS.get().isBlank()) return@before
            it.args[0] = linkAccess.filter(links) { CommunityFilters.filterLinks(KeywordFiltersPatch.filterLinks(it)) }
        }
    }
    processor.hookMethod {
        before {
            if (Settings.KEYWORD_FILTERS.get().isBlank() && Settings.COMMUNITY_FILTERS.get().isBlank()) return@before
            val elements = it.args[1] as? List<*> ?: return@before
            it.args[1] = filterModernFeed(elements, linkId) { adapted ->
                KeywordFiltersPatch.filterFeedElements(CommunityFilters.filterFeedElements(adapted))
            }
        }
    }
    setExtensionIsPatchIncluded(KeywordFiltersPatch::class.java)
}

/** Port of Morphe #4868. */
internal val flairFilters = patch(
    name = "Flair filters",
    description = "Hides feed posts with selected community flairs.",
) {
    dependsOn(postPreviewSecondaryFetch)
    val processor = FeedElementProcessorFingerprint.method
    val listing = ListingFingerprint.constructor
    val linkConstructor = FeedFilterLinkConstructorFingerprint.constructor
    val linkAccess = LinkAccess(linkConstructor.declaringClass)
    val linkId = ::feedElementLinkIdMethod.method

    val elementConstructor = PostFlairsElementConstructorFingerprint.constructor
    val flairsField = ::postFlairsField.field.apply { isAccessible = true }
    val (nameField, communityField) = ::postFlairModelFields.fieldList.onEach { it.isAccessible = true }
    val flairClass = nameField.declaringClass

    elementConstructor.hookMethod {
        after { param ->
            if (param.hasThrowable() || (Settings.FLAIR_FILTERS.get().isBlank() &&
                    !Settings.FLAIR_FILTERS_REMEMBER_SEEN.get())) return@after
            val element = linkId.adaptFeedElement(param.thisObject) ?: return@after
            val models = flairsField.get(param.thisObject) as? Iterable<*> ?: return@after
            val adaptedFlairs = models.filter(flairClass::isInstance).map { model ->
                object : FlairFiltersPatch.Flair {
                    override fun patch_getText() = nameField.get(model) as? String
                    override fun patch_getCommunity() = communityField.get(model) as? String
                }
            }
            FlairFiltersPatch.setFlairs(element, adaptedFlairs)
        }
    }
    linkConstructor.hookMethod {
        after {
            if (it.hasThrowable() || Settings.FLAIR_FILTERS.get().isBlank()) return@after
            FlairFiltersPatch.setLinkFlair(linkAccess.adapt(it.thisObject))
        }
    }
    listing.hookMethod {
        before {
            val links = it.args[0] as? List<*> ?: return@before
            if (Settings.FLAIR_FILTERS.get().isBlank()) return@before
            it.args[0] = linkAccess.filter(links, FlairFiltersPatch::filterLinks)
        }
    }
    processor.hookMethod {
        before {
            if (Settings.FLAIR_FILTERS.get().isBlank()) return@before
            val elements = it.args[1] as? List<*> ?: return@before
            it.args[1] = filterModernFeed(elements, linkId, FlairFiltersPatch::filterFeedElements)
        }
    }
    setExtensionIsPatchIncluded(FlairFiltersPatch::class.java)
}

/** Ensures home previews load their complete flair data when either consumer asks for it. */
internal val postPreviewSecondaryFetch = patch {
    val flagOwner = ::secondaryFetchFlagOwner.field.apply { isAccessible = true }
    val flags = ::secondaryFetchFlagFields.fieldList.onEach { it.isAccessible = true }
    val getValue = classLoader.loadClass("kotlin.SynchronizedLazyImpl").getDeclaredMethod("getValue")

    PostsToLoadLegacyFingerprint.hookMethod(scopedHook(getValue) {
        after {
            if (innerDepth != 0 || it.hasThrowable() || it.result != true) return@after
            // Neither consumer needs the flag changed while these settings are off.
            // The filters remain live, so check here rather than skipping installation.
            if (!Settings.SHOW_FLAIRS_IN_HOME_FEED.get() && Settings.FLAIR_FILTERS.get().isBlank()) return@after
            val owner = flagOwner.get(outerParam.thisObject) ?: return@after
            if (flags.none { flag -> flag.declaringClass.isInstance(owner) &&
                    flag.get(owner) === it.thisObject }) return@after
            val removeForFilters = !FlairFiltersPatch.isPatchIncluded() ||
                FlairFiltersPatch.isPostPreviewSecondaryFetchRemoved(true)
            val removeForDisplay = !ShowFlairsInHomeFeedPatch.isPatchIncluded() ||
                ShowFlairsInHomeFeedPatch.isPostPreviewSecondaryFetchRemoved(true)
            it.result = removeForFilters && removeForDisplay
        }
    })
}
