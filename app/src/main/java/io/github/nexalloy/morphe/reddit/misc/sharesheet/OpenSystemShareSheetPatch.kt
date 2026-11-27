package io.github.nexalloy.morphe.reddit.misc.sharesheet

import app.morphe.extension.reddit.patches.OpenSystemShareSheetPatch
import app.morphe.extension.reddit.patches.OpenSystemShareSheetPatch.ShareSheetEventHandlerInterface
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

val OpenRedditSystemShareSheet = patch(
    name = "Open system share sheet",
    description = "Uses Android's share sheet for Reddit shares.",
) {
    val shareVia = ::shareViaAction.field.apply { isAccessible = true }
    val overflowShare = ::overflowShareAction.field.apply { isAccessible = true }
    val nonShareable = ::nonShareableData.field.apply { isAccessible = true }
    val actionConstructors = ::shareActionEventConstructors.dexMethodList.map { it.toConstructor() }
    val overflowClick = ::handleShareSheetAction.method
    val shareTriggerType = classLoader.loadClass("com.reddit.sharing.SharingNavigator\$ShareTrigger")

    fun resolveSheet(
        screenInit: Member,
        handler: Method,
        argsClass: Class<*>,
        hasActions: Boolean,
    ): ShareSheetHooks {
        val eventType = handler.parameterTypes[0]
        val constructor = actionConstructors.single { eventType.isAssignableFrom(it.declaringClass) }
            .apply { isAccessible = true }
        check(constructor.parameterTypes.first().isAssignableFrom(shareVia.type))
        check(constructor.parameterTypes.drop(1).none { it.isPrimitive })
        fun Class<*>.fieldOfType(type: Class<*>) =
            declaredFields.single { it.type == type }.apply { isAccessible = true }
        val argsField = handler.declaringClass.fieldOfType(argsClass)
        val dataField = argsClass.declaredFields.single { it.type.isAssignableFrom(nonShareable.type) }
            .apply { isAccessible = true }
        val triggerField = argsClass.fieldOfType(shareTriggerType)
        val actionsField = if (hasActions) argsClass.fieldOfType(List::class.java) else null

        return ShareSheetHooks(screenInit, handler) { receiver ->
            val args = argsField.get(receiver)
            object : ShareSheetEventHandlerInterface {
                override fun patch_isShareable() = dataField.get(args) !== nonShareable.get(null)
                override fun patch_getActions() = actionsField?.get(args) as? List<*>
                override fun patch_getShareTrigger() = triggerField.get(args) as Enum<*>
                override fun patch_getShareViaEvent(): Any = constructor.newActionEvent(checkNotNull(shareVia.get(null)))
            }
        }
    }

    val actionSheet = resolveSheet(
        ActionSheetScreenInitFingerprint.method,
        ::actionSheetEventHandler.method,
        ActionSheetArgsToStringFingerprint.declaredClass,
        hasActions = true,
    )
    val bottomSheet = resolveSheet(
        ShareBottomSheetScreenInitFingerprint.method,
        ::bottomShareSheetEventHandler.method,
        ShareBottomSheetArgsToStringFingerprint.declaredClass,
        hasActions = false,
    )

    for (sheet in listOf(actionSheet, bottomSheet)) {
        sheet.screenInit.hookMethod(scopedHook(sheet.handler) {
            before {
                if (innerDepth != 0) return@before
                val receiver = it.thisObject ?: return@before
                it.args[0] = OpenSystemShareSheetPatch.getScreenInitEvent(
                    it.args[0], sheet.wrap(receiver),
                )
            }
        })
    }
    overflowClick.hookMethod {
        before {
            it.args[0] = OpenSystemShareSheetPatch.getShareSheetAction(
                it.args[0], overflowShare.get(null), shareVia.get(null),
            )
        }
    }
    setExtensionIsPatchIncluded(OpenSystemShareSheetPatch::class.java)
}

private class ShareSheetHooks(
    val screenInit: Member,
    val handler: Method,
    val wrap: (Any) -> ShareSheetEventHandlerInterface,
)

private fun Constructor<*>.newActionEvent(action: Any): Any {
    val arguments = arrayOfNulls<Any>(parameterCount)
    arguments[0] = action
    return newInstance(*arguments)
}
