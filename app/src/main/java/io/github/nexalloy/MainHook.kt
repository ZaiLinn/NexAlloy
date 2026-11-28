package io.github.nexalloy

import android.app.Application
import app.morphe.extension.shared.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.nexalloy.common.CHECK_BETA_UPDATES
import io.github.nexalloy.common.UpdateChecker
import io.github.nexalloy.morphe.ResourceFinder
import io.github.nexalloy.morphe.resourceMappings
import io.github.nexalloy.morphe.reddit.REDDIT_PACKAGE
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.reddit.redditPatchesForVersion

class MainHook : XposedModule() {
    lateinit var param: PackageReadyParam
    lateinit var app: Application
    var targetPackageName: String? = null

    fun shouldHook(packageName: String): Boolean {
        if (!patchesByPackage.containsKey(packageName)) return false
        if (targetPackageName == null) targetPackageName = packageName
        return targetPackageName == packageName
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        modulePath = moduleApplicationInfo.sourceDir
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        if (!shouldHook(param.packageName)) return
        this.param = param

        hookHostLibraryFallback()

        inContext(param) { app ->
            this.app = app
            if (isReVancedPatched(param)) {
                Utils.showToastLong("NexAlloy module does not work with patched app")
                return@inContext
            }

            resourceMappings = object : ResourceFinder {
                override operator fun get(type: String, name: String): Int {
                    val id = app.resources.getIdentifier(name, type, param.packageName)
                    if (id == 0) throw Exception("Could not find resource type: $type name: $name")
                    return id
                }
            }

            val patches = if (param.packageName == REDDIT_PACKAGE) {
                redditPatchesForVersion(app.packageManager.getPackageInfo(REDDIT_PACKAGE, 0).versionName)
            } else {
                patchesByPackage[param.packageName] ?: return@inContext
            }
            PatchExecutor(app, param, this).applyPatches(patches)
        }
    }

    /**
     * The extension code references libraries the host app bundles, such as Litho's yoga,
     * which the module class loader cannot resolve on its own. The patcher merges the
     * extension into the host dex instead, so classes like these were always reachable
     * there. Redirect the module loader's failed lookups to the host class loader, which
     * only happens after its own dex and parents missed, so normal lookups are unaffected.
     */
    private fun hookHostLibraryFallback() {
        val moduleClassLoader = MainHook::class.java.classLoader
        ClassLoader::class.java
            .getDeclaredMethod("loadClass", String::class.java)
            .apply { isAccessible = true }
            .hookMethod {
                after {
                    if (it.throwable !is ClassNotFoundException) return@after
                    if (it.thisObject !== moduleClassLoader) return@after
                    val name = it.args[0] as String
                    // Setting the result alone clears the throwable; setting the
                    // throwable, even to null, would clear the result again.
                    runCatching { param.classLoader.loadClass(name) }.onSuccess { hostClass ->
                        it.result = hostClass
                    }
                }
            }
    }

    private fun isReVancedPatched(param: PackageReadyParam): Boolean {        return runCatching {
            param.classLoader.loadClass("app.morphe.extension.shared.Utils")
        }.isSuccess || runCatching {
            param.classLoader.loadClass("app.morphe.extension.shared.utils.Utils")
        }.isSuccess || runCatching {
            param.classLoader.loadClass("app.revanced.integrations.shared.Utils")
        }.isSuccess || runCatching {
            param.classLoader.loadClass("app.revanced.integrations.shared.utils.Utils")
        }.isSuccess
    }

}

context(xposed: XposedInterface)
fun inContext(lpparam: PackageReadyParam, f: (Application) -> Unit) {
    val appClazz = XposedHelpers.findClass(lpparam.applicationInfo.className, lpparam.classLoader)
    appClazz.getMethod("onCreate").hookMethod {
        before {
            val app = it.thisObject as Application
            Utils.setContext(app)
            f(app)
            if (modulePath.startsWith("/data/app/")) {
                val prefs = xposed.getRemotePreferences("prefs")
                if (!prefs.getBoolean("disable_auto_check_update", false)) {
                    UpdateChecker { prefs.getBoolean(CHECK_BETA_UPDATES, false) }.hookNewActivity()
                }
            }
        }
    }
}
