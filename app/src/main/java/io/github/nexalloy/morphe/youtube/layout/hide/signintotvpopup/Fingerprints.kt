package io.github.nexalloy.morphe.youtube.layout.hide.signintotvpopup

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.StringComparisonType
import io.github.nexalloy.morphe.StrictQuery
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string

/**
 * The fragment that inflates the 'sign in to TV' ExpressSignInLayout modal.
 *
 * Anchor (21.45.82): `zze.O(LayoutInflater, ViewGroup, Bundle)View` reads
 * `R.id.express_sign_in_modal` from the inflated view. The method is the only
 * public final View-returning method that references the id.
 */
@StrictQuery
internal object SignInToTVPopupFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "express_sign_in_modal"),
        opcode(Opcode.RETURN_OBJECT),
    ),
)

/**
 * The dismiss method of the sign in to TV popup fragment.
 *
 * Anchor (21.45.82): `zze.aR()V` calls `ExpressSignInLayout.b(...)`, then its own
 * `dismiss()` and reads its `Runnable` callback field. Upstream calls this method
 * when the popup must not be shown.
 */
@StrictQuery
internal object SignInToTVPopupDismissFingerprint : Fingerprint(
    classFingerprint = SignInToTVPopupFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/google/android/libraries/onegoogle/expresssignin/ExpressSignInLayout;"
        ),
        methodCall(name = "dismiss", location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/Runnable;", location = MatchAfterImmediately()),
    ),
)

/**
 * The resolver of the MDX 'connect your devices' handoff promo command,
 * which shows the pairing promo popup.
 *
 * Anchor (21.45.82): `nhf.c(begh, Map)V` logs the two placeholder strings.
 */
@StrictQuery
internal object HandoffPromoCommandResolverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        string("Unsupported RunCase: ", StringComparisonType.STARTS_WITH),
        string("Unspecified PlaceholderType."),
    ),
)

/**
 * The dismiss method to call when the sign in to TV popup is disabled.
 * Upstream inserts a call to this method before the popup method returns.
 */
internal val signInToTVPopupDismissMethod = findMethodDirect {
    SignInToTVPopupDismissFingerprint.run()
}
