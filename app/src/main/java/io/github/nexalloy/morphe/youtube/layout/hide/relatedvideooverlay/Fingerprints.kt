package io.github.nexalloy.morphe.youtube.layout.hide.relatedvideooverlay

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.resourceLiteral

/**
 * The class that inflates the related videos end screen layout, which is the overlay
 * shown when swiping up in fullscreen.
 *
 * Anchor (21.45.82): `asic` inflates `R.layout.app_related_endscreen_results`.
 */
private object RelatedEndScreenResultsParentFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "app_related_endscreen_results")
    ),
)

/**
 * The method that shows the related video end screen results.
 *
 * Anchor (21.45.82): `asic.p(int, boolean, int)V`, the only (I, Z, I)V method of the class.
 */
@StrictQuery
internal object RelatedEndScreenResultsFingerprint : Fingerprint(
    classFingerprint = RelatedEndScreenResultsParentFingerprint,
    returnType = "V",
    parameters = listOf(
        "I",
        "Z",
        "I",
    ),
)
