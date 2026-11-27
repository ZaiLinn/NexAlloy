package io.github.nexalloy.morphe.youtube.layout.disableupdates

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.string

/**
 * The method that adds a header to the Cronet request builder.
 *
 * In YouTube 21.45.82 this resolves to `defpackage.byaj#a(Ljava/lang/String;Ljava/lang/String;)V`,
 * the header add method of the builder of `org.chromium.net.impl.CronetUrlRequest`
 * (created by `CronetUrlRequestContext#newUrlRequestBuilder`).
 *
 * In YouTube 19.16.39 or earlier, there are two methods with almost the same structure
 * (the second being the equivalent method of the native Cronet builder, `bxyx#b` in 21.45.82).
 * The class of the correct one has a long field, which the custom query requires.
 */
internal object CronetHeaderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        string("Accept-Encoding")
    ),
    custom = { declaredClass { addField { type = "long" } } },
)
