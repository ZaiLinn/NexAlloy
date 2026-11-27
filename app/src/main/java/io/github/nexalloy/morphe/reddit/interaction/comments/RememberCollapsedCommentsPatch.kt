/*
 * Adapted from Morphe's Remember collapsed comments patch.
 * https://github.com/MorpheApp/morphe-patches/pull/3517
 */

package io.github.nexalloy.morphe.reddit.interaction.comments

import android.content.SharedPreferences
import app.morphe.extension.reddit.patches.RememberCollapsedCommentsPatch
import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.shared.settings.preference.SharedPrefCategory
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

internal fun PatchExecutor.resolveCommentCollapsedSetter(): (Any, Boolean) -> Unit {
    val field = ::commentCollapsedField.field.apply { isAccessible = true }
    return field::setBoolean
}

private class RememberedComments(private val preferences: SharedPreferences) {
    private val timestamps = preferences.all.mapNotNull { (id, value) ->
        (value as? Long)?.let { id to it }
    }.toMap(linkedMapOf())
    private val marked = mutableMapOf<String, MutableList<WeakReference<Any>>>()

    init {
        val editor = preferences.edit()
        if (trim(editor, System.currentTimeMillis())) editor.apply()
    }

    @Synchronized
    fun mark(comment: Any, id: String, setCollapsed: (Any, Boolean) -> Unit): Boolean {
        if (id !in timestamps) return false
        setCollapsed(comment, true)
        val references = marked.getOrPut(id) { mutableListOf() }
        references.removeAll { it.get() == null }
        references.add(WeakReference(comment))
        return true
    }

    @Synchronized
    fun remember(id: String) {
        val now = System.currentTimeMillis()
        timestamps[id] = now
        val editor = preferences.edit().putLong(id, now)
        trim(editor, now)
        editor.apply()
    }

    @Synchronized
    fun forget(id: String, setCollapsed: (Any, Boolean) -> Unit) {
        if (timestamps.remove(id) == null) return
        preferences.edit().remove(id).apply()
        marked.remove(id)?.forEach { reference -> reference.get()?.let { setCollapsed(it, false) } }
    }

    private fun trim(editor: SharedPreferences.Editor, now: Long): Boolean {
        val excess = (timestamps.size - 5000).coerceAtLeast(0)
        val expired = timestamps.entries.sortedBy { it.value }
            .filterIndexed { index, entry -> index < excess || now - entry.value > MAX_AGE_MILLIS }
        expired.forEach { (id, _) ->
            timestamps.remove(id)
            marked.remove(id)
            editor.remove(id)
        }
        return expired.isNotEmpty()
    }

    private companion object {
        const val MAX_AGE_MILLIS = 180L * 24 * 60 * 60 * 1000
    }
}

internal val RememberCollapsedComments = patch(
    name = "Remember collapsed comments",
    description = "Adds an option to keep comments collapsed when reopening a post.",
) {
    val collapsedField = ::commentCollapsedField.field.apply { isAccessible = true }
    val setCollapsed = collapsedField::setBoolean
    val collapsedGetter = CommentGetCollapsedFingerprint.method
    val commentClass = collapsedGetter.declaringClass
    val getCommentId = commentClass.getMethod("getKindWithId")
    val replaceItem = CommentTreeReplaceItemFingerprint.method
    val transformType = replaceItem.parameterTypes.last()
    check(transformType.isInterface)
    val state by lazy {
        RememberedComments(SharedPrefCategory("morphe_collapsed_comments").preferences)
    }

    collapsedGetter.hookMethod {
        after { param ->
            if (param.hasThrowable() || param.result == true || !Settings.REMEMBER_COLLAPSED_COMMENTS.get()) {
                return@after
            }
            val comment = param.thisObject
            val id = getCommentId.invoke(comment) as? String ?: return@after
            if (state.mark(comment, id, setCollapsed)) param.result = true
        }
    }

    replaceItem.hookMethod {
        before { param ->
            if (!Settings.REMEMBER_COLLAPSED_COMMENTS.get()) return@before
            val transform = param.args[2] ?: return@before
            param.args[2] = Proxy.newProxyInstance(classLoader, arrayOf(transformType)) { _, method, args ->
                val result = try {
                    method.invoke(transform, *args.orEmpty())
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
                val oldComment = args?.firstOrNull()
                if (method.name == "invoke" && commentClass.isInstance(oldComment) &&
                    commentClass.isInstance(result)
                ) {
                    val wasCollapsed = collapsedGetter.invoke(oldComment) as Boolean
                    val isCollapsed = collapsedField.getBoolean(result)
                    if (wasCollapsed != isCollapsed) {
                        val id = getCommentId.invoke(result) as? String
                        if (id != null) {
                            if (isCollapsed) state.remember(id) else state.forget(id, setCollapsed)
                        }
                    }
                }
                result
            }
        }
    }
    setExtensionIsPatchIncluded(RememberCollapsedCommentsPatch::class.java)
}
