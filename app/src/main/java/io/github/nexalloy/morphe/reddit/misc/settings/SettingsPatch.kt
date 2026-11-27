package io.github.nexalloy.morphe.reddit.misc.settings

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import app.morphe.extension.reddit.patches.HideAdsPatch
import app.morphe.extension.reddit.patches.SanitizeSharingLinksPatch
import app.morphe.extension.reddit.settings.RedditActivityHook
import app.morphe.extension.reddit.settings.preference.RedditSettingsStyle
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import java.lang.reflect.Proxy

/** Key of the settings row injected by the extension, must match RedditActivityHook. */
private const val SETTINGS_ROW_KEY = "key_pref_morphe"
private const val PRIVACY_POLICY_ROW_KEY = "key_pref_privacy_policy"

/**
 * Packed size of a 20dp icon, the size of the Reddit settings icons (width shl 32 or height).
 */
private const val SETTING_ICON_PACKED_SIZE = 0x1400000014L

/**
 * Adds the Morphe settings to the Compose settings of Reddit 2026.30.0+:
 * a copy of the "Privacy policy" row that opens the Morphe settings dialog.
 */
val SettingsHook = patch(
    name = "Settings",
    description = "Adds the Morphe settings to the Reddit settings.",
) {
    /**
     * Draws the icon of the settings row with the icon composable of the Reddit icon set.
     * Like the compiled patch of upstream, the icon is drawn with a null modifier, a null tint
     * and the default mask 0x1c, which tells the composable that the modifier, the tint and
     * the unknown boolean were omitted, so it falls back to the Compose default modifier.
     */
    fun installSettingIconComposable() {
        try {
            val iconDataClass = IconDataToStringFingerprint.declaredClass
            val iconDataConstructor = iconDataClass.constructors.single { constructor ->
                constructor.parameterTypes.size == 3 &&
                    constructor.parameterTypes[0] == java.lang.Long.TYPE &&
                    constructor.parameterTypes[1] == java.lang.Integer.TYPE &&
                    constructor.parameterTypes[2] == java.lang.Boolean.TYPE
            }
            val iconComposable = IconComposableFingerprint(
                "L${iconDataClass.name.replace('.', '/')};",
            ).method

            RedditActivityHook::class.java.getDeclaredMethod(
                "drawSettingIcon", Object::class.java, Int::class.javaPrimitiveType,
            ).hookMethod(object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    try {
                        val icon = iconDataConstructor.newInstance(
                            SETTING_ICON_PACKED_SIZE, param.args[1], false,
                        )
                        // Icon, content description, modifier, tint, unknown, composer,
                        // changed 0x30, defaults 0x1c — same values as the settings icons
                        // of the app, with the default modifier and tint of upstream.
                        return iconComposable.invoke(
                            null, icon, null, null, 0L, false, param.args[0], 48, 28,
                        )
                    } catch (ex: Exception) {
                        Logger.printException({ "drawSettingIcon failure" }, ex)
                    }
                    return null
                }
            })
        } catch (ex: Exception) {
            Logger.printException({ "Setting icon composable not installed" }, ex)
        }
    }

    val rowClass = PrivacyPolicySettingRowConstructorFingerprint.declaredClass
    val keyField = ::settingRowKeyField.field.apply { isAccessible = true }
    val rowConstructor = PrivacyPolicySettingRowConstructorFingerprint.constructor
    val contextField =
        rowClass.declaredFields.single { it.type == rowConstructor.parameterTypes[0] }
            .apply { isAccessible = true }
    val rowKindField =
        rowClass.declaredFields.first { it.type == Int::class.javaPrimitiveType }
            .apply { isAccessible = true }

    val generalSection = classLoader
        .loadClass("com.reddit.settings.usersettings.UserSettingsSection")
        .getDeclaredField("General").apply { isAccessible = true }.get(null)

    // Copy the privacy policy row with the settings row key, and add it to the registered rows
    // before the registry builds its key map, so it does not throw on the duplicate key check.
    // The copy keeps the section, the order and the content of the privacy policy row,
    // which are overridden for the settings row by the hooks below.
    SettingsRegistryConstructorFingerprint.hookMethod {
        before {
            if (it.hasThrowable()) return@before
            val rows = it.args[0] as Set<*>
            val privacyRow =
                rows.firstOrNull { row ->
                    row != null && rowClass.isInstance(row) &&
                        keyField.get(row) == PRIVACY_POLICY_ROW_KEY
                }
            if (privacyRow == null) return@before
            val copy = rowConstructor.newInstance(
                contextField.get(privacyRow),
                rowKindField.get(privacyRow),
            )
            keyField.set(copy, SETTINGS_ROW_KEY)
            it.args[0] = LinkedHashSet(rows).apply { add(copy) }
        }
    }

    // The row class is shared by several rows, which differ by their key.
    // Sort the settings row to the top of the General section.
    SettingRowSectionFingerprint.hookMethod {
        after {
            if (!it.hasThrowable() && keyField.get(it.thisObject!!) == SETTINGS_ROW_KEY) {
                it.result = generalSection
            }
        }
    }
    SettingRowOrderFingerprint.hookMethod {
        after {
            if (!it.hasThrowable() && keyField.get(it.thisObject!!) == SETTINGS_ROW_KEY) {
                it.result = -1000
            }
        }
    }

    // Title, click action and leading icon of the settings row. The on click and the icon
    // are host interface implementations: the classes of the extension are loaded by the module
    // class loader and do not implement the interfaces of the host class loader, so both are
    // wrapped with a proxy of the host class loader.
    val hostFunction0 = classLoader.loadClass("kotlin.jvm.functions.Function0")
    val hostFunction2 = classLoader.loadClass("kotlin.jvm.functions.Function2")

    SettingRowContentFingerprint.hookMethod(scopedHook(::settingRowComposable.method) {
        before {
            if (innerDepth != 0 || it.hasThrowable()) return@before
            val row = outerParam.thisObject ?: return@before
            if (!rowClass.isInstance(row) || keyField.get(row) != SETTINGS_ROW_KEY) return@before

            it.args[0] = RedditActivityHook.getSettingLabel()
            val originalOnClick = it.args[1]
            it.args[1] = newHostFunction0(hostFunction0) {
                val activity = findActivityOf(originalOnClick) ?: Utils.getActivity()
                RedditActivityHook.showSettingsDialog(activity)
            }
            val iconComposable = RedditActivityHook.getSettingIconComposable()
            it.args[5] = Proxy.newProxyInstance(classLoader, arrayOf(hostFunction2)) { proxy, method, args ->
                when (method.name) {
                    "invoke" -> iconComposable.invoke(args[0], args[1])
                    "toString" -> "NexAlloySettingRowIcon"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }
        }
    })

    // The settings dialog of the extension, reachable from in-app links that use
    // the settings activity of the older app settings.
    val browserOnCreate = WebBrowserActivityOnCreateFingerprint.method
    val browserSuperOnCreate = xposed.getInvoker(
        browserOnCreate.declaringClass.superclass.getDeclaredMethod("onCreate", Bundle::class.java)
    )
    browserOnCreate.hookMethod {
        before { param ->
            val activity = param.thisObject as Activity
            if (activity.intent.getStringExtra("com.reddit.extra.initial_url") != "Morphe") return@before

            // Keep the host base activity lifecycle and dependency initialization,
            // replacing only the browser-specific body with the settings content.
            browserSuperOnCreate.invokeSpecial(activity, *param.args)
            RedditActivityHook.hook(activity)
            param.result = Unit
        }
    }

    // Placeholders of the extension, which upstream replaces during patching.
    RedditSettingsStyle::class.java.getDeclaredMethod("isModernStyle")
        .hookMethod(XC_MethodReplacement.returnConstant(true))
    installSettingIconComposable()

    // Settings of the already installed patches. Later patches mark themselves.
    setExtensionIsPatchIncluded(HideAdsPatch::class.java)
    setExtensionIsPatchIncluded(SanitizeSharingLinksPatch::class.java)
}

/**
 * Creates an implementation of the Function0 interface of the host class loader that runs
 * [invoke]. The classes of the extension are loaded by the module class loader and do not
 * implement the interfaces of the host class loader.
 */
private fun newHostFunction0(function0Class: Class<*>, invoke: () -> Any?): Any =
    Proxy.newProxyInstance(function0Class.classLoader, arrayOf(function0Class)) { proxy, method, args ->
        when (method.name) {
            "invoke" -> invoke()
            "toString" -> "NexAlloySettingRowClick"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.getOrNull(0)
            else -> throw UnsupportedOperationException(method.toString())
        }
    }

/**
 * The activity of the context captured by a Kotlin lambda, or null. Mirrors
 * [RedditActivityHook]#findActivity, which is not visible from outside the class.
 */
private fun findActivityOf(lambda: Any?): Activity? {
    if (lambda == null) return null
    return try {
        for (field in lambda.javaClass.declaredFields) {
            if (field.type.isPrimitive) continue
            field.isAccessible = true
            var context: Any? = field.get(lambda)
            while (context is ContextWrapper) {
                if (context is Activity) return context
                val base = context.baseContext
                if (base === context) break
                context = base
            }
        }
        null
    } catch (ex: Exception) {
        Logger.printException({ "findActivityOf failure" }, ex)
        null
    }
}
