package io.github.nexalloy.morphe.reddit.layout.copytext

import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.morphe.extension.reddit.patches.CopyTextPatch
import app.morphe.extension.reddit.patches.CopyTextPatch.SelectionManagerInterface
import app.morphe.extension.shared.Logger
import de.robv.android.xposed.XposedBridge
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

private const val SELECTION_GROUP = 0x4d4f5250

val RedditCopyText = patch(
    name = "Copy text",
    description = "Makes post titles and post and comment bodies selectable.",
) {
    // The setting needs an app restart, so the hooks are not installed at all
    // while it is off. One of the hooks sits on the Compose touch dispatch,
    // which runs for every touch event of the whole app, and a no-op hook
    // still pays the bridge dispatch cost on each event.
    if (!app.morphe.extension.reddit.settings.Settings.COPY_TEXT.get()) return@patch

    val rememberLambda = ComposeComposableLambdaFingerprint.method.apply { isAccessible = true }
    val selectionContainer = ComposeSelectionContainerFingerprint.method.apply { isAccessible = true }
    val richText = ComposeRichTextFingerprint.method
    val postTitle = PostTitleFingerprint.method
    val postTitleSection = PostTitleSectionFingerprint.method
    val detectTap = SelectionTapDetectionFingerprint.method
    val detectSlop = PointersUpSlopDetectionFingerprint.method
    val slop = ::readSelectionTouchSlop.method
    val release = SelectionReleaseFingerprint.method.apply { isAccessible = true }
    val selectionChanged = SelectionChangedFingerprint.method
    val touch = ComposeDispatchTouchEventFingerprint.method
    val clearFocus = FocusClearFingerprint.method
    val function2 = classLoader.loadClass("kotlin.jvm.functions.Function2")
    val composerType = rememberLambda.parameterTypes[2]
    val blockType = rememberLambda.parameterTypes[1]
    check(blockType.isAssignableFrom(function2))
    check(release.declaringClass == selectionChanged.declaringClass)
    val richTextComposer = richText.parameterTypes.indexOf(composerType)
    val titleComposer = postTitle.parameterTypes.indexOf(composerType)
    check(richTextComposer >= 0 && titleComposer >= 0)

    fun renderSelectable(renderer: Method, args: Array<Any?>, composerIndex: Int) {
        val originalComposer = args[composerIndex]
        val content = Proxy.newProxyInstance(classLoader, arrayOf(function2)) { proxy, method, arguments ->
            when (method.name) {
                "invoke" -> {
                    val renderArguments = args.copyOf()
                    renderArguments[composerIndex] = arguments[0]
                    XposedBridge.invokeOriginalMethod(renderer, null, renderArguments)
                    null
                }

                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Reddit selectable text"
                else -> throw UnsupportedOperationException(method.toString())
            }
        }
        val composable = rememberLambda.invoke(null, SELECTION_GROUP, content, originalComposer)
        selectionContainer.invoke(null, 48, 1, originalComposer, null, composable)
    }

    richText.hookMethod {
        before {
            if (CopyTextPatch.shouldWrapRichText()) {
                renderSelectable(richText, it.args.copyOf(), richTextComposer)
                it.result = null
            }
        }
    }
    postTitleSection.hookMethod(scopedHook(postTitle) {
        before {
            if (innerDepth == 0 && CopyTextPatch.shouldWrapPostTitle()) {
                renderSelectable(postTitle, it.args.copyOf(), titleComposer)
                it.result = null
            }
        }
    })
    val tapScope = scopedHook(detectSlop) { before { CopyTextPatch.beginSelectionTapDetection() } }
    detectTap.hookMethod(tapScope)
    detectSlop.hookMethod(scopedHook(slop) {
        after {
            if (innerDepth == 0 && !it.hasThrowable()) {
                it.result = CopyTextPatch.adjustSelectionTapSlop(it.result as Float)
            }
        }
    })
    val selectionManagers = WeakHashMap<Any, SelectionManagerInterface>()
    fun selectionManager(manager: Any): SelectionManagerInterface =
        synchronized(selectionManagers) {
            selectionManagers.getOrPut(manager) {
                val reference = WeakReference(manager)
                SelectionManagerInterface { reference.get()?.let { release.invoke(it) } }
            }
        }
    release.hookMethod { before { CopyTextPatch.onSelectionRelease(selectionManager(it.thisObject)) } }
    selectionChanged.hookMethod {
        before { CopyTextPatch.onSelectionChanged(selectionManager(it.thisObject), it.args[0]) }
    }
    touch.hookMethod { before { CopyTextPatch.onComposeTouchEvent(it.args[0] as MotionEvent) } }
    clearFocus.hookMethod { before { if (CopyTextPatch.shouldBlockFocusClear()) it.result = null } }

    // region Block collapsing a comment with the touch that starts or clears a text selection.

    // Upstream injects at the collapsed branch inside the click handler and returns Unit.
    // A runtime hook cannot inject mid-method, so the before-hook reads the collapsed state
    // the same way the handler does: the handler looks the comment up in the comment tree by
    // the model id of the click event. If the extension blocks the collapse, the handler is
    // skipped entirely, so it does not collapse the comment or fire collapse analytics.
    val commentClick = CommentClickEventHandlerFingerprint.method
    val commentTreeLookup = ::findCommentTreeLookup.method
    val commentTreeClass = commentTreeLookup.declaringClass
    val commentClass = classLoader.loadClass("com.reddit.domain.model.Comment")
    val commentGetCollapsed = commentClass.getMethod("getCollapsed")
    val unitClass = classLoader.loadClass("kotlin.Unit")
    val hostUnit = unitClass.declaredFields.single {
        Modifier.isStatic(it.modifiers) && it.type == unitClass
    }.apply { isAccessible = true }.get(null)
    // Field of the click handler that holds the comment tree, found on the first click.
    var commentTreeField: Field? = null

    commentClick.hookMethod {
        before {
            // The event argument is null when the handler resumes after the collapse action.
            val event = it.args[0] ?: return@before
            val handler = it.thisObject ?: return@before
            val treeField = commentTreeField ?: handler.javaClass.fields.firstOrNull { field ->
                field.isAccessible = true
                commentTreeClass.isInstance(field.get(handler))
            }?.also { found -> commentTreeField = found } ?: return@before
            val comment = try {
                val eventId = event.javaClass.declaredFields
                    .singleOrNull { field -> field.type == String::class.java }
                    ?.apply { isAccessible = true }
                    ?.get(event)
                    // The click handler also returns Unit without a comment.
                    ?: return@before
                commentTreeLookup.invoke(treeField.get(handler), eventId)
            } catch (error: Throwable) {
                Logger.printException({ "Copy text: comment lookup failed" }, error)
                return@before
            }
            if (!commentClass.isInstance(comment)) return@before
            val collapsed = commentGetCollapsed.invoke(comment) as Boolean
            if (CopyTextPatch.shouldBlockCommentCollapse(collapsed)) it.result = hostUnit
        }
    }

    // endregion

    // region Legacy View rich text renderer.

    // Some comment and post bodies still use the View-based rich text renderer instead of
    // Compose. After its items are bound, every TextView under it is made selectable, and
    // links keep working by restoring the LinkMovementMethod that setTextIsSelectable removes.
    val setRichTextItems = runCatching {
        classLoader.loadClass("com.reddit.richtext.RichTextView")
            .getDeclaredMethod("setRichTextItems", List::class.java)
    }.getOrNull()
    if (setRichTextItems == null) {
        Logger.printInfo { "Copy text: RichTextView.setRichTextItems(List) not found" }
    } else {
        /**
         * Makes every TextView under [view] selectable. Returns true when it saw any text.
         */
        fun makeSelectable(view: View): Boolean = when (view) {
            is TextView -> {
                if (!view.isTextSelectable && !view.text.isNullOrEmpty()) {
                    val movement = view.movementMethod
                    view.setTextIsSelectable(true)
                    // setTextIsSelectable swaps in ArrowKeyMovementMethod; restore link taps.
                    if (movement is LinkMovementMethod) view.movementMethod = movement
                }
                !view.text.isNullOrEmpty()
            }

            is ViewGroup -> {
                var seenText = false
                for (index in 0 until view.childCount) {
                    if (makeSelectable(view.getChildAt(index))) seenText = true
                }
                seenText
            }

            else -> false
        }

        setRichTextItems.hookMethod {
            after {
                val view = it.thisObject as? View ?: return@after
                if (!CopyTextPatch.shouldWrapRichText()) return@after
                val seenText = makeSelectable(view)
                // Children may be (re)built after layout. Look again only when this pass
                // found no text yet or the view is not laid out, so a bound item is not
                // walked twice.
                if (!seenText || !view.isLaidOut) view.post { makeSelectable(view) }
            }
        }
    }

    // endregion

    setExtensionIsPatchIncluded(CopyTextPatch::class.java)
}
