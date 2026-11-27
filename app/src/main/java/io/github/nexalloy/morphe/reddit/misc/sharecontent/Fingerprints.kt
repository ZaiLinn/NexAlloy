package io.github.nexalloy.morphe.reddit.misc.sharecontent

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.checkCast
import io.github.nexalloy.morphe.findMethodListDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string

private const val LINK = "Lcom/reddit/domain/model/Link;"
private const val CONTINUATION = "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"

@StrictQuery
private object SharePostSubjectFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L", CONTINUATION),
    filters = listOf(
        checkCast(LINK),
        methodCall(definingClass = LINK, name = "getSubredditNamePrefixed"),
        string("] "),
    ),
)

internal val sharePostSubjects = findMethodListDirect {
    SharePostSubjectFingerprint.matchAll().map { it.method }
}

internal val sharePostTargets = findMethodListDirect {
    sharePostSubjects().map { subject ->
        Fingerprint(
            definingClass = checkNotNull(subject.declaredClass).descriptor,
            returnType = "Ljava/lang/Object;",
            parameters = listOf("L", CONTINUATION),
            filters = listOf(
                methodCall(returnType = "Landroid/content/Intent;"),
                methodCall(
                    definingClass = "Landroid/app/Activity;",
                    name = "startActivity",
                    parameters = listOf("Landroid/content/Intent;"),
                ),
            ),
        ).match().method
    }
}
