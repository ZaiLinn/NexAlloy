package io.github.nexalloy.morphe.music.layout.hide.notifications

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.string

/**
 * The view factory of a top bar menu item (hnh.a): inflates the button item layout,
 * looks up the new-content dot and count views, and wires the icon request.
 * The factory takes no parameters and returns the inflated item root.
 */
internal object TopBarMenuItemViewFactoryFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    filters = listOf(
        methodCall(
            smali = "Landroid/view/LayoutInflater;->from(Landroid/content/Context;)Landroid/view/LayoutInflater;"
        ),
        methodCall(
            smali = "Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;"
        ),
        methodCall(
            smali = "Landroid/view/ViewGroup;->findViewById(I)Landroid/view/View;"
        ),
        methodCall(
            smali = "Landroid/view/ViewStub;->inflate()Landroid/view/View;"
        ),
    ),
)

/**
 * The badge state update of a top bar menu item (hlq.a(Object)): the unread-count
 * consumer that shows or hides the new-content dot and the unread count pill.
 */
internal object TopBarBadgeUpdateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "V",
    strings = listOf("%d", "%d+"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V"),
        methodCall(smali = "Landroid/widget/TextView;->setVisibility(I)V"),
        methodCall(smali = "Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V"),
    ),
)

/**
 * Adds a top bar menu item (hng.a): the button spec of the item is the second
 * parameter, and the notification bell is the only item created with a present
 * notification-count Optional. The host desugars java.util.Optional.
 */
internal object TopBarMenuItemAddFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Landroid/view/Menu;", "L", "Lj$/util/Optional;", "I", "I"),
    returnType = "V",
    filters = listOf(
        methodCall(smali = "Landroid/view/Menu;->add(IIILjava/lang/CharSequence;)Landroid/view/MenuItem;"),
        methodCall(smali = "Landroid/view/MenuItem;->setShowAsAction(I)V"),
    ),
)
