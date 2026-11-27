package io.github.nexalloy.morphe.reddit.font

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall

private const val RESOURCES = "Landroid/content/res/Resources;"
private const val PATH = "Ljava/lang/String;"
private const val TYPEFACE = "Landroid/graphics/Typeface;"
private val createFont = methodCall(
    smali = $$"Landroid/graphics/fonts/Font$Builder;-><init>(Landroid/content/res/Resources;I)V",
)

private val legacyTypefaceLoader = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = TYPEFACE,
    parameters = listOf(RESOURCES, "I", PATH, "I", "I"),
    filters = listOf(createFont),
)

private val modernTypefaceLoader = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = TYPEFACE,
    parameters = listOf("Landroid/content/Context;", RESOURCES, "I", PATH, "I"),
    filters = listOf(createFont),
)

internal val redditTypefaceLoader = findMethodDirect {
    runCatching { legacyTypefaceLoader() }.getOrElse { modernTypefaceLoader() }
}
