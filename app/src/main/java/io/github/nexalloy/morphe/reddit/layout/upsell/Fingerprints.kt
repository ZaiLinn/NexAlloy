@file:Suppress("ClassName")

/*
 * Ported from the reddidnt module's "Upsell blocker" (GPLv3).
 * Every query fails closed: an ambiguous discovery throws instead of hooking a guess.
 */

package io.github.nexalloy.morphe.reddit.layout.upsell

import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier

/*
 * Settings rows keep their preference key literal. Each row class has a
 * `getVisibility()` that returns a predicate interface; a predicate that is
 * always false hides the row. The Premium rows ("Get Premium") and the Reddit
 * Pro rows ("Reddit Pro trends", "Advertise on Reddit") share this shape.
 *
 * R8 merged `key_pref_reddit_pro_advertise` into a multi-row class, so every
 * key is queried on its own: a single query that needs every key in one method
 * would match nothing. A key with no match logs a warning and is skipped.
 */
internal val getPremiumSettingsRows = findMethodListDirect {
    findMethod { matcher { usingEqStrings("key_pref_get_premium") } }
}

internal val getPremiumBenefitsSettingsRows = findMethodListDirect {
    findMethod { matcher { usingEqStrings("key_pref_get_premium_benefits") } }
}

internal val redditProTrendsSettingsRows = findMethodListDirect {
    findMethod { matcher { usingEqStrings("key_pref_reddit_pro_trends") } }
}

internal val redditProAdvertiseSettingsRows = findMethodListDirect {
    findMethod { matcher { usingEqStrings("key_pref_reddit_pro_advertise") } }
}

/**
 * The drawer presenter's suspend method that returns the Premium item. Kotlin
 * keeps the coroutine class name `CommunityDrawerPresenter$getPremiumItem$1`,
 * and only this method constructs it. Returning null completes the suspend
 * call at once with no item, and the caller drops null items (`listOfNotNull`).
 */
internal val drawerPremiumItemMethod = findMethodDirect {
    findMethod {
        matcher {
            paramCount(1)
            returnType = "java.lang.Object"
            addInvoke {
                declaredClass($$"com.reddit.screens.drawer.community.CommunityDrawerPresenter$getPremiumItem$1")
                name = "<init>"
            }
        }
    }.distinctBy { it.descriptor }.single()
}

/**
 * The drawer's Resources section (Premium, Earn, Reddit Ads) is a `java.util.List`
 * field on the drawer presenter. It is written by the loader that calls the
 * Premium item method; the section builder skips an empty list together with its
 * header and divider.
 */
internal val drawerResourcesField = findFieldDirect {
    val premiumItem = drawerPremiumItemMethod()
    premiumItem.callers.flatMap { caller ->
        caller.usingFields.filter {
            it.usingType == FieldUsingType.Write &&
                it.field.className == premiumItem.className &&
                it.field.typeName == "java.util.List"
        }.map { it.field }
    }.distinctBy { it.name }.single()
}

/**
 * The presenter's state builder: the only no-argument presenter method that
 * reads the Resources field. The loader hands the list to the main thread at
 * once, so the field is emptied here, before each build.
 */
internal val drawerStateBuilderMethod = findMethodDirect {
    val resourcesField = drawerResourcesField()
    resourcesField.readers.filter {
        it.isMethod && it.className == resourcesField.className &&
            it.paramTypeNames.isEmpty() && it.returnTypeName == "void"
    }.distinctBy { it.descriptor }.single()
}

/**
 * The drawer lists (Favorites, Your Communities) show a spinner footer while they
 * load. The presenter maps the pagination state to that footer with this method:
 * the only presenter method that takes the pagination enum and returns an item.
 * Returning null for the LOADING state leaves the footer out, so a refresh stays
 * silent while the data still loads and the ERROR footer keeps its retry.
 */
internal val drawerPaginationMapperMethod = findMethodDirect {
    val presenterClassName = drawerPremiumItemMethod().className
    findMethod {
        matcher {
            declaredClass(presenterClassName)
            paramTypes("com.reddit.screens.drawer.community.adapter.PaginationType")
        }
    }.filter {
        !it.isConstructor && it.returnTypeName != "void" &&
            !it.returnTypeName.startsWith("java.")
    }.distinctBy { it.descriptor }.single()
}

/**
 * The drawer's account collector reads the account's Pro status and skips the
 * whole Reddit Pro section when it is null. It is the only method that calls
 * `MyAccount.getProStatus()` from a class holding a field of the drawer
 * presenter type. The status is only hidden while the collector runs, so the
 * account itself is unchanged.
 */
internal val drawerProCollectorMethod = findMethodDirect {
    val presenterClassName = drawerPremiumItemMethod().className
    findMethod {
        matcher {
            addInvoke {
                declaredClass("com.reddit.domain.model.MyAccount")
                name("getProStatus")
            }
        }
    }.filter { reader ->
        reader.declaredClass!!.fields.any { it.typeName == presenterClassName }
    }.distinctBy { it.descriptor }.single()
}

/**
 * The generated GraphQL variables serializers of the profile feed request.
 * They write the `includeProfileFeedUpsell` flag that asks the backend to add an
 * upsell unit to the feed.
 */
internal val profileFeedUpsellSerializers = findMethodListDirect {
    findMethod {
        matcher {
            usingEqStrings("includeProfileFeedUpsell")
            returnType = "void"
            paramCount(3)
        }
    }.filter { it.paramTypeNames.last() == "boolean" }
}

/**
 * The writer interface the serializers write through: their first parameter.
 */
internal val profileFeedUpsellWriter = findClassDirect {
    profileFeedUpsellSerializers()
        .map { it.paramTypes.first() }
        .distinctBy { it.descriptor }
        .single()
}

/**
 * The classes that declare the writer's methods: the writer itself when it is
 * concrete, otherwise every class that implements the writer interface. Only
 * a concrete class holds the non-abstract method pair that is hooked; the
 * interface itself declares both methods abstract.
 */
private fun DexKitBridge.writerImplementationClasses(writer: ClassData): List<ClassData> =
    if (Modifier.isInterface(writer.modifiers)) {
        findClass { matcher { addInterface(writer.name) } }.sortedBy { it.name }
    } else {
        listOf(writer)
    }

/**
 * The writer's `name(String)` method. Serializers call it through the
 * interface or through a cast to one implementation; both calls share the
 * method name, so it is the name every serializer invokes with a String and
 * the writer as the return type. Some serializers also write a string value
 * with `value(String)`, which has the same shape; only `name()` is called by
 * every serializer. The interface only declares the method abstract, so the
 * match is the concrete declaration of a class that implements the writer;
 * every implementation declares the same-named concrete method, so the shared
 * name keeps the match unambiguous.
 */
internal val profileFeedUpsellWriterNameMethod = findMethodDirect {
    val writer = profileFeedUpsellWriter()
    val nameNames = profileFeedUpsellSerializers().map { serializer ->
        serializer.invokes.filter {
            it.isMethod && it.paramTypeNames == listOf("java.lang.String") &&
                it.returnTypeName == writer.name
        }.map { it.name }.toSet()
    }.reduceOrNull { names, next -> names intersect next }.orEmpty().single()
    writerImplementationClasses(writer)
        .flatMap { writerClass ->
            writerClass.methods.filter {
                it.name == nameNames && it.paramTypeNames == listOf("java.lang.String") &&
                    it.returnTypeName == writer.name && !Modifier.isAbstract(it.modifiers)
            }
        }
        .distinctBy { it.name }
        .single()
}

/**
 * The writer's `value(boolean)` method: the only writer method that takes a
 * boolean and returns the writer itself. The interface only declares it
 * abstract, so the match is the concrete declaration of a class that
 * implements the writer; every implementation declares the same-named
 * concrete method, so the shared name keeps the match unambiguous.
 */
internal val profileFeedUpsellWriterValueMethod = findMethodDirect {
    val writer = profileFeedUpsellWriter()
    val valueName = writer.methods
        .filter { it.paramTypeNames == listOf("boolean") && it.returnTypeName == writer.name }
        .single()
        .name
    writerImplementationClasses(writer)
        .flatMap { writerClass ->
            writerClass.methods.filter {
                it.name == valueName && it.paramTypeNames == listOf("boolean") &&
                    it.returnTypeName == writer.name && !Modifier.isAbstract(it.modifiers)
            }
        }
        .distinctBy { it.name }
        .single()
}

/**
 * The concrete `name(String)` and `value(boolean)` pair of every class that
 * implements the writer interface. Serializers may call `name()` through a cast
 * to one of these classes, so both methods are hooked wherever they are
 * declared. Classes that do not declare both methods are skipped.
 */
internal val profileFeedUpsellWriterMethods = findMethodListDirect {
    val writer = profileFeedUpsellWriter()
    val name = profileFeedUpsellWriterNameMethod().name
    val value = profileFeedUpsellWriterValueMethod().name
    writerImplementationClasses(writer).flatMap { writerClass ->
        val nameMethod = writerClass.methods.singleOrNull {
            it.name == name && it.paramTypeNames == listOf("java.lang.String") &&
                it.returnTypeName == writer.name && !Modifier.isAbstract(it.modifiers)
        }
        val valueMethod = writerClass.methods.singleOrNull {
            it.name == value && it.paramTypeNames == listOf("boolean") &&
                it.returnTypeName == writer.name && !Modifier.isAbstract(it.modifiers)
        }
        if (nameMethod == null || valueMethod == null) {
            emptyList()
        } else {
            listOf(nameMethod, valueMethod)
        }
    }.distinctBy { it.descriptor }
}
