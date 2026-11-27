package io.github.nexalloy.revanced.meta.filter

import app.morphe.extension.shared.Logger
import io.github.nexalloy.hookMethod
import io.github.nexalloy.modulePath
import io.github.nexalloy.patch
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Threads "Sieve" (缕筛) port: blocks simplified-Chinese posts from the home feed
 * (For You + Following) and simplified-Chinese replies on the detail page.
 *
 * Ported from the reference LSPosed module dev.threads.sieve (FxxkThreadsCN), which
 * targets Threads 450.0.0.51.78 with the same obfuscated X.* bindings verified below.
 * The reference implementation uses the modern libxposed intercept chain; this port
 * translates it to the module's XC_MethodHook hookMethod { before { } / after { } } DSL:
 *
 *  - Home feed: X.8kg.A00 methods are hooked with an after-hook. The original method
 *    result (a X.Gao flow) is wrapped by the same two-layer java.lang.reflect.Proxy:
 *    the outer proxy implements X.Gao and intercepts collect(collector, continuation),
 *    the inner proxy implements X.Geo and intercepts emit(value, continuation), replacing
 *    the emitted paging state with the filtered one. Both calls are forwarded to the
 *    original receiver via reflection, passing through the original continuation, so no
 *    new coroutines are created and exceptions are unwrapped and re-thrown as-is.
 *  - Replies: the 23-parameter constructor of X.4FM is hooked with a before-hook that
 *    rewrites the reply lists in arguments 9/10/11 (assigned to fields A0B/A0D/A0C).
 *    The opened post (argument 7, or the media packed in argument 6 as X.2ze) is never
 *    filtered. On any binding error the arguments are left untouched and the original
 *    constructor runs unmodified.
 *
 * Fail-closed everywhere: any missing class/field/method is logged via Logger and the
 * affected piece is skipped without crashing the host.
 */
val SimplifiedChineseFilter = patch(
    name = "Filter simplified Chinese posts",
    description = "Hide simplified-Chinese posts and replies (Threads $SUPPORTED_THREADS_VERSION only).",
) {
    // Version gate: the obfuscated X.* bindings are locked to this exact release.
    val versionName = appContext.packageManager
        .getPackageInfo(appContext.packageName, 0).versionName
    if (versionName != SUPPORTED_THREADS_VERSION) {
        Logger.printInfo {
            "Simplified Chinese filter: unsupported Threads version $versionName, skipping"
        }
        return@patch
    }

    // The OpenCC dictionary ships inside the module APK; read it straight from the APK.
    // ExtensionResourceHook (which adds the module assets to the host) only runs for
    // YouTube/Music/Reddit, so a ZipFile on the module path is the simplest reliable way.
    val script = ZipFile(modulePath).use { zip ->
        val entry = zip.getEntry(DICTIONARY_ENTRY)
            ?: throw IllegalStateException("$DICTIONARY_ENTRY missing from module APK")
        ScriptFilter(zip.getInputStream(entry))
    }

    val loader = classLoader
    // home_simplified = true, home_traditional = false, reply_simplified = true, reply_traditional = false
    val filter = ModelFilter(loader, script, true, false, true, false)

    // Fail-closed: resolve every hook point before installing anything (Sieve's
    // NoSuchMethodException guard); a missing hook point installs nothing.
    val homeMethods = Class.forName(HOME_FLOW_HELPER_CLASS, false, loader)
        .declaredMethods.filter { it.name == "A00" }
    val replyConstructors = Class.forName(REPLY_STATE_CLASS, false, loader)
        .declaredConstructors.filter { it.parameterCount == REPLY_CONSTRUCTOR_PARAMETER_COUNT }
    if (homeMethods.isEmpty() || replyConstructors.isEmpty()) {
        Logger.printException {
            "Simplified Chinese filter: missing hook points " +
                "(home=${homeMethods.size}, replies=${replyConstructors.size}), installing nothing"
        }
        return@patch
    }

    // Both the For You and the Following feed pass their final display flow through this
    // helper; only flows built by BarcelonaFeedRepository are wrapped.
    homeMethods.forEach { method ->
        method.hookMethod {
            after { param ->
                val result = param.result ?: return@after
                if (!fromHomeRepository()) return@after
                try {
                    param.result = wrapFlow(result, loader, filter)
                } catch (t: Throwable) {
                    // Fail-closed: keep the original flow.
                    failure(t)
                }
            }
        }
    }

    // Reply lists of the detail page: parameters 9/10/11 (A0B/A0D/A0C), the opened
    // post is parameter 7 with the packed media of parameter 6 (X.2ze) as fallback.
    replyConstructors.forEach { constructor ->
        constructor.hookMethod {
            before { param ->
                try {
                    val args = param.args
                    var openedMedia = args[REPLY_OPENED_MEDIA_PARAMETER_INDEX]
                    if (openedMedia == null && args[REPLY_PACKED_MEDIA_PARAMETER_INDEX] != null &&
                        args[REPLY_PACKED_MEDIA_PARAMETER_INDEX].javaClass.name == PACKED_MEDIA_CLASS
                    ) {
                        openedMedia = ModelFilter.field(args[REPLY_PACKED_MEDIA_PARAMETER_INDEX], "A00")
                    }
                    val openedId = ModelFilter.mediaId(openedMedia)
                    args[9] = filter.replies(args[9], openedId)
                    args[10] = filter.replies(args[10], openedId)
                    args[11] = filter.replies(args[11], openedId)
                } catch (t: Throwable) {
                    // Fail-closed: leave the arguments untouched, the original constructor runs.
                    failure(t)
                }
            }
        }
    }

    Logger.printInfo { "Simplified Chinese filter installed (Threads $SUPPORTED_THREADS_VERSION)" }
}

/** Threads release the obfuscated X.* bindings were verified against. */
private const val SUPPORTED_THREADS_VERSION = "450.0.0.51.78"

/** Entry of the OpenCC dictionary inside the module APK. */
private const val DICTIONARY_ENTRY = "assets/STCharacters.txt"

// Bindings verified against the original DEX names of Threads 450.0.0.51.78.
private const val HOME_FLOW_HELPER_CLASS = "X.8kg"
private const val FLOW_CLASS = "X.Gao"
private const val COLLECTOR_CLASS = "X.Geo"
private const val REPLY_STATE_CLASS = "X.4FM"
private const val PACKED_MEDIA_CLASS = "X.2ze"
private const val HOME_REPOSITORY_CLASS =
    "com.instagram.barcelona.feed.data.repository.BarcelonaFeedRepository"
private const val REPLY_CONSTRUCTOR_PARAMETER_COUNT = 23
private const val REPLY_OPENED_MEDIA_PARAMETER_INDEX = 7
private const val REPLY_PACKED_MEDIA_PARAMETER_INDEX = 6

/** Log at most the first five binding errors, like the reference implementation. */
private val failures = AtomicInteger(0)

private fun failure(t: Throwable) {
    if (failures.incrementAndGet() <= 5) {
        Logger.printException({ "Simplified Chinese filter skipped after binding error" }, t)
    }
}

private fun fromHomeRepository(): Boolean {
    for (frame in Thread.currentThread().stackTrace) {
        if (frame.className == HOME_REPOSITORY_CLASS) return true
    }
    return false
}

/** Invokes a host method, unwrapping InvocationTargetException like the reference implementation. */
private fun invokeHost(method: Method, target: Any?, args: Array<Any?>?): Any? {
    method.isAccessible = true
    return try {
        method.invoke(target, *(args ?: arrayOfNulls<Any?>(0)))
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
}

/**
 * Two-layer proxy around the flow returned by X.8kg.A00: the X.Gao proxy intercepts
 * collect and hands the downstream collector an X.Geo adapter whose emit replaces the
 * paging state with the filtered one. Continuations and return values are passed through.
 */
private fun wrapFlow(original: Any, loader: ClassLoader, filter: ModelFilter): Any {
    val flowClass = Class.forName(FLOW_CLASS, false, loader)
    val collectorClass = Class.forName(COLLECTOR_CLASS, false, loader)
    return Proxy.newProxyInstance(loader, arrayOf(flowClass)) { _, method, args ->
        if (method.name == "collect" && args != null && args.size == 2) {
            val downstream = args[0]
            val adapter = Proxy.newProxyInstance(loader, arrayOf(collectorClass)) { _, emit, values ->
                if (emit.name == "emit" && values != null && values.size == 2) {
                    val forwarded = values.copyOf()
                    try {
                        forwarded[0] = filter.homeState(values[0])
                    } catch (t: Throwable) {
                        // Fail-closed: emit the original value.
                        failure(t)
                    }
                    invokeHost(emit, downstream, forwarded)
                } else {
                    invokeHost(emit, downstream, values)
                }
            }
            invokeHost(method, original, arrayOf(adapter, args[1]))
        } else {
            invokeHost(method, original, args)
        }
    }
}
