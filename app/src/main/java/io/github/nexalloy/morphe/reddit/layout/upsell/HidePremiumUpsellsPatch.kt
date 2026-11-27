/*
 * Ported from the reddidnt module's "Upsell blocker" (GPLv3).
 *
 * Each hook uses a path that Reddit itself uses to leave an entry out, so the
 * surrounding layout stays as Reddit draws it. Every discovery fails closed:
 * an ambiguous or missing anchor is logged and skipped instead of hooked.
 */

package io.github.nexalloy.morphe.reddit.layout.upsell

import app.morphe.extension.reddit.patches.HidePremiumUpsellsPatch
import app.morphe.extension.shared.Logger
import de.robv.android.xposed.XposedBridge
import io.github.nexalloy.FindMethodListFunc
import io.github.nexalloy.callStaticMethod
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.misc.settings.SettingsHook
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.patch
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlin.reflect.KProperty0

/** Preference keys of the Premium and Reddit Pro settings rows. */
private val SETTINGS_ROW_KEYS = setOf(
    "key_pref_get_premium",
    "key_pref_get_premium_benefits",
    "key_pref_reddit_pro_trends",
    "key_pref_reddit_pro_advertise",
)

/** Pro members keep the drawer's Reddit Pro section; it holds their Pro tools. */
private const val PRO_STATUS_ENABLED = "ENABLED"

/** GraphQL variables that ask the backend to add an upsell unit to a feed. */
private val DISABLED_GRAPHQL_FLAGS = setOf("includeProfileFeedUpsell")

internal val RedditHidePremiumUpsells = patch(
    name = "Hide Premium upsells",
    description = "Adds an option to hide the Premium upsells of the settings, the side drawer and the profile feed.",
) {
    dependsOn(SettingsHook)

    // region Settings rows (Premium, Premium benefits, Reddit Pro trends, Advertise on Reddit)

    // One query per key: a row class holds one key (or a few after R8 merging), so a
    // single query that needs every key in one method would match nothing.
    val settingsRows = listOf(
        "key_pref_get_premium" to ::getPremiumSettingsRows,
        "key_pref_get_premium_benefits" to ::getPremiumBenefitsSettingsRows,
        "key_pref_reddit_pro_trends" to ::redditProTrendsSettingsRows,
        "key_pref_reddit_pro_advertise" to ::redditProAdvertiseSettingsRows,
    )
    val rowClassNames = settingsRows.flatMap { (key, rows: KProperty0<FindMethodListFunc>) ->
        val classNames = rows.dexMethodList.map { it.className }.distinct()
        if (classNames.isEmpty()) {
            Logger.printInfo { "Hide Premium upsells: settings key $key not found" }
        }
        classNames
    }.distinct()

    var installedRows = 0
    for (rowClassName in rowClassNames) {
        // Each row class has a getVisibility() that returns a predicate; a predicate
        // that is always false hides the row.
        val rowClass = runCatching { classLoader.loadClass(rowClassName) }.getOrNull()
        if (rowClass == null) {
            Logger.printException { "Hide Premium upsells: settings row $rowClassName not loadable" }
            continue
        }
        val getVisibility = rowClass.declaredMethods.singleOrNull {
            it.name == "getVisibility" && it.parameterCount == 0 &&
                it.returnType.isInterface && !Modifier.isStatic(it.modifiers)
        }
        if (getVisibility == null) {
            Logger.printException { "Hide Premium upsells: $rowClassName has no getVisibility()" }
            continue
        }

        val hidden = alwaysFalse(getVisibility.returnType)
        val keyFields = rowClass.declaredFields
            .filter { it.type == String::class.java && !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }

        getVisibility.hookMethod {
            before {
                val row = it.thisObject
                if (HidePremiumUpsellsPatch.hideUpsells() &&
                    keyFields.any { field -> field.get(row) in SETTINGS_ROW_KEYS }
                ) {
                    it.result = hidden
                }
            }
        }
        installedRows++
    }
    Logger.printInfo { "Hide Premium upsells: settings rows installed $installedRows hook(s)" }

    // endregion

    // region Side drawer Premium item

    runCatching {
        // A suspend method that returns null completes at once with no item, and the
        // caller drops null items from the drawer's resource list.
        ::drawerPremiumItemMethod.hookMethod {
            before { if (HidePremiumUpsellsPatch.hideUpsells()) it.result = null }
        }
    }.onFailure { Logger.printException({ "Hide Premium upsells: drawer premium item failed" }, it) }

    // endregion

    // region Side drawer Resources section (Premium, Earn, Reddit Ads)

    runCatching {
        // The loader hands the Resources list to the main thread at once, so it is
        // emptied in the presenter's state builder, before each build. The section
        // builder skips an empty list together with its header and divider.
        val resourcesField = ::drawerResourcesField.field.apply { isAccessible = true }
        ::drawerStateBuilderMethod.hookMethod {
            before {
                if (HidePremiumUpsellsPatch.hideUpsells() &&
                    (resourcesField.get(it.thisObject) as? List<*>)?.isNotEmpty() == true
                ) {
                    resourcesField.set(it.thisObject, emptyList<Any>())
                }
            }
        }
    }.onFailure { Logger.printException({ "Hide Premium upsells: drawer Resources failed" }, it) }

    // endregion

    // region Side drawer "Try Reddit Pro" section

    runCatching {
        val collector = ::drawerProCollectorMethod.method
        val inDrawer = ThreadLocal.withInitial { IntArray(1) }
        collector.hookMethod {
            before { inDrawer.get()[0]++ }
            after { inDrawer.get()[0]-- }
        }

        // The drawer adds its Reddit Pro section only when the account's Pro status is
        // not null. The status is only hidden while the collector runs (a resumed
        // suspend call enters it again and is counted again, so a thread switch does
        // not lose it), so the account itself is unchanged.
        val proStatus = classLoader.loadClass("com.reddit.domain.model.MyAccount")
            .getDeclaredMethod("getProStatus")
        proStatus.hookMethod {
            after {
                if (it.hasThrowable()) return@after
                val status = it.result
                if (inDrawer.get()[0] > 0 && HidePremiumUpsellsPatch.hideUpsells() &&
                    (status as? Enum<*>)?.name != PRO_STATUS_ENABLED
                ) {
                    it.result = null
                }
            }
        }

        // getProStatus() is a plain getter. ART can inline it into the collector, and
        // an inlined call never reaches the hook. Deoptimizing the collector keeps
        // the call. This runs after the hooks are in place and can never undo them.
        runCatching { XposedBridge::class.java.callStaticMethod("deoptimizeMethod", collector as Member) }
            .onFailure { Logger.printException({ "Hide Premium upsells: collector deoptimize failed" }, it) }
    }.onFailure { Logger.printException({ "Hide Premium upsells: drawer Reddit Pro failed" }, it) }

    // endregion

    // region Side drawer silent refresh (Favorites, Your Communities)

    runCatching {
        // The drawer lists show a spinner footer while their pagination state is
        // LOADING. Returning null leaves the footer out, so a refresh stays silent
        // while the data still loads and the ERROR footer keeps its retry.
        ::drawerPaginationMapperMethod.hookMethod {
            before {
                val state = it.args[0] as? Enum<*> ?: return@before
                if (state.name == "LOADING" && HidePremiumUpsellsPatch.silentDrawerRefresh()) {
                    it.result = null
                }
            }
        }
    }.onFailure { Logger.printException({ "Hide Premium upsells: drawer pagination failed" }, it) }

    // endregion

    // region Profile feed upsell (GraphQL)

    runCatching {
        // The serializers write writer.name(flag) and then the Boolean adapter calls
        // writer.value(boolean). Both writer methods are hooked wherever they are
        // declared: the last name is remembered per thread, and its flag is sent as
        // false, so the backend leaves the upsell unit out.
        val pendingFlag = ThreadLocal<String?>()
        for (writerMethod in ::profileFeedUpsellWriterMethods.dexMethodList.map { it.toMethod() }) {
            when {
                writerMethod.parameterTypes.contentEquals(arrayOf(String::class.java)) ->
                    writerMethod.hookMethod {
                        before {
                            val name = it.args[0] as? String
                            pendingFlag.set(name?.takeIf { name -> name in DISABLED_GRAPHQL_FLAGS })
                        }
                    }

                writerMethod.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE)) ->
                    writerMethod.hookMethod {
                        before {
                            if (pendingFlag.get() == null) return@before
                            pendingFlag.set(null)
                            if (HidePremiumUpsellsPatch.hideUpsells()) it.args[0] = false
                        }
                    }
            }
        }
    }.onFailure { Logger.printException({ "Hide Premium upsells: GraphQL flags failed" }, it) }

    // endregion

    setExtensionIsPatchIncluded(HidePremiumUpsellsPatch::class.java)
}

/**
 * An instance of [type] whose boolean methods all return false, the predicate that
 * hides a settings row. Like the host interfaces in SettingsPatch, the instance is
 * a java.lang.reflect.Proxy, so it needs no host class at compile time.
 */
private fun alwaysFalse(type: Class<*>): Any {
    val handler = InvocationHandler { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "NexAlloyHidden"
            else -> if (method.returnType == java.lang.Boolean.TYPE) false
            else throw UnsupportedOperationException(method.toString())
        }
    }
    return Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler)
}
