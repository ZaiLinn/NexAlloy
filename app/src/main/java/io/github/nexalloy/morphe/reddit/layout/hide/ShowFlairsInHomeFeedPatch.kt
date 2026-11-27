package io.github.nexalloy.morphe.reddit.layout.hide

import app.morphe.extension.reddit.patches.ShowFlairsInHomeFeedPatch
import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.shared.Logger
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.layout.feed.FeedElementProcessorFingerprint
import io.github.nexalloy.morphe.reddit.layout.feed.FeedFilterLinkConstructorFingerprint
import io.github.nexalloy.morphe.reddit.layout.feed.postFlairsField
import io.github.nexalloy.morphe.reddit.layout.feed.postPreviewSecondaryFetch
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import java.lang.ref.WeakReference
import java.lang.reflect.Field

private const val PREVIEW_FLAIR_DEFAULT_BIT = 0x400

private data class PostFlair(
    val title: String,
    val subreddit: String,
    val textColor: String,
    val backgroundColor: String,
)

private class HomeFlairCache {
    private val flairs = object : LinkedHashMap<String, PostFlair>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PostFlair>?): Boolean = size > 1000
    }
    private val waiting = object : LinkedHashMap<String, MutableList<WeakReference<Any>>>() {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MutableList<WeakReference<Any>>>?
        ): Boolean = size > 500
    }

    @Synchronized
    fun retain(id: String, element: Any): PostFlair? {
        val flair = flairs[id]
        if (flair != null) return flair
        val entries = waiting.getOrPut(id) { mutableListOf() }
        entries.removeAll { it.get() == null }
        if (entries.none { it.get() === element }) entries.add(WeakReference(element))
        return null
    }

    @Synchronized
    fun update(id: String, flair: PostFlair): List<Any> {
        flairs[id] = flair
        return waiting.remove(id)?.mapNotNull { it.get() } ?: emptyList()
    }
}

val RedditShowFlairsInHomeFeed = patch(
    name = "Show flairs in home feed",
    description = "Shows post flair badges in the home feed."
) {
    dependsOn(SettingsHook, postPreviewSecondaryFetch)
    val linkConstructor = FeedFilterLinkConstructorFingerprint.constructor
    val linkType = linkConstructor.declaringClass
    val getId = linkType.getMethod("getKindWithId")
    val getFlair = linkType.getMethod("getLinkFlairText")
    val getSubreddit = linkType.getMethod("getSubreddit")
    val getTextColor = linkType.getMethod("getLinkFlairTextColor")
    val getBackgroundColor = linkType.getMethod("getLinkFlairBackgroundColor")
    val presentation = ::homeFlairPresentationConstructor.constructor.apply { isAccessible = true }
    val persistentList = ::homeFlairPersistentList.method.apply { isAccessible = true }
    val elementConstructor = ::homeFlairElementConstructor.constructor.apply { isAccessible = true }
    val sectionConstructor = PostFlairsSectionToStringFingerprint.declaredClass
        .getDeclaredConstructor(elementConstructor.declaringClass).apply { isAccessible = true }
    val titleSection = FeedPostTitleSectionToStringFingerprint.declaredClass
    val (previewId, previewUniqueId, previewIdentifier, previewChildren, previewFlair) =
        ::homeFlairPreviewFields.fieldList.map { it.apply { isAccessible = true } }
    val (sectionId, sectionChildren, sectionUniqueId, sectionIdentifier) =
        ::homeFlairSectionFields.fieldList.map { it.apply { isAccessible = true } }
    val (classicPostElement, classicFlair) =
        ::homeFlairClassicSectionFields.fieldList.map { it.apply { isAccessible = true } }
    val flairContents = ::postFlairsField.field.apply { isAccessible = true }
    val previewCopy = PostPreviewFeedElementCopyFingerprint.method.apply { isAccessible = true }
    val previewCopyTypes = previewCopy.parameterTypes
    val modelType = elementConstructor.declaringClass
    check(previewCopyTypes.count { it == modelType } == 1)
    val sectionMapper = FeedPostSectionMapperFingerprint.method
    val processor = FeedElementProcessorFingerprint.method
    val cache = HomeFlairCache()

    fun createFlairElement(post: Any, id: Field, uniqueId: Field, identifier: Field, flair: PostFlair): Any {
        val linkId = id.get(post)
        val model = presentation.newInstance(
            flair.title, null, linkId, flair.subreddit, "", flair.textColor, flair.backgroundColor,
            flair.title, flair.title,
        )
        val flairList = persistentList.invoke(null, arrayOf(model))
        return elementConstructor.newInstance(linkId, uniqueId.get(post), false, identifier.get(post), flairList)
    }

    fun addFlair(post: Any, flair: PostFlair) = synchronized(post) {
        if (previewId.declaringClass.isInstance(post)) {
            if (previewFlair.get(post) != null) return@synchronized
            val element = createFlairElement(post, previewId, previewUniqueId, previewIdentifier, flair)
            val args = Array<Any?>(previewCopyTypes.size) { index ->
                when {
                    index == 0 -> post
                    index == previewCopyTypes.lastIndex -> PREVIEW_FLAIR_DEFAULT_BIT.inv()
                    previewCopyTypes[index] == modelType -> element
                    previewCopyTypes[index] == Boolean::class.javaPrimitiveType -> false
                    previewCopyTypes[index] == Int::class.javaPrimitiveType -> 0
                    previewCopyTypes[index] == Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
            val copy = previewCopy.invoke(null, *args)
            previewChildren.set(post, previewChildren.get(copy))
            previewFlair.set(post, element)
        } else if (sectionId.declaringClass.isInstance(post)) {
            val sections = sectionChildren.get(post) as List<*>
            val hasFlair = sections.any { section ->
                when {
                    sectionConstructor.declaringClass.isInstance(section) -> true
                    classicPostElement.declaringClass.isInstance(section) -> {
                        val classic = classicPostElement.get(section)
                        val element = classic?.let(classicFlair::get)
                        val values = element?.let(flairContents::get) as? Collection<*>
                        values?.isNotEmpty() == true
                    }
                    else -> false
                }
            }
            if (hasFlair) return@synchronized
            val element = createFlairElement(post, sectionId, sectionUniqueId, sectionIdentifier, flair)
            val flairSection = sectionConstructor.newInstance(element)
            val titleIndex = sections.indexOfFirst(titleSection::isInstance)
            val updated = sections.toMutableList()
            updated.add(if (titleIndex < 0) updated.size else titleIndex + 1, flairSection)
            sectionChildren.set(post, persistentList.invoke(null, updated.toTypedArray()))
        }
    }

    fun addFlairIfAvailable(post: Any?) {
        if (post == null) return
        val idField = when {
            previewId.declaringClass.isInstance(post) -> previewId
            sectionId.declaringClass.isInstance(post) -> sectionId
            else -> return
        }
        val id = (idField.get(post) as? String)?.takeIf(String::isNotEmpty)?.let(::fullPostId) ?: return
        cache.retain(id, post)?.let { addFlair(post, it) }
    }

    linkConstructor.hookMethod {
        after {
            if (it.hasThrowable() || !Settings.SHOW_FLAIRS_IN_HOME_FEED.get()) return@after
            val link = it.thisObject
            val id = (getId.invoke(link) as? String)?.takeIf(String::isNotEmpty)?.let(::fullPostId) ?: return@after
            val title = (getFlair.invoke(link) as? String)?.takeIf(String::isNotEmpty) ?: return@after
            val flair = PostFlair(
                title,
                getSubreddit.invoke(link) as? String ?: "",
                (getTextColor.invoke(link) as? String)?.takeIf(String::isNotEmpty) ?: "LIGHT",
                (getBackgroundColor.invoke(link) as? String)?.takeIf(String::isNotEmpty) ?: "#3A3D40",
            )
            for (post in cache.update(id, flair)) {
                runCatching { addFlair(post, flair) }
                    .onFailure { Logger.printException({ "Could not add Reddit post flair" }, it as? Exception) }
            }
        }
    }
    sectionMapper.hookMethod {
        after {
            if (!it.hasThrowable() && Settings.SHOW_FLAIRS_IN_HOME_FEED.get()) addFlairIfAvailable(it.result)
        }
    }
    processor.hookMethod {
        before {
            if (Settings.SHOW_FLAIRS_IN_HOME_FEED.get()) {
                (it.args[1] as? List<*>)?.forEach(::addFlairIfAvailable)
            }
        }
    }
    setExtensionIsPatchIncluded(ShowFlairsInHomeFeedPatch::class.java)
}
