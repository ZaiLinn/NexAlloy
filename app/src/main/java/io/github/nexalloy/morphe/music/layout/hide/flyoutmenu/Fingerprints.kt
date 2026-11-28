/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream patcher fingerprints
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/layout/hide/flyoutmenu/Fingerprints.kt).
 */

package io.github.nexalloy.morphe.music.layout.hide.flyoutmenu

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.resourceLiteral
import io.github.nexalloy.morphe.string

/**
 * The method that binds a flyout menu item and adds its view to the menu.
 * Anchor (9.46.51): `Ldefpackage/jfp;->e()V`, which reads the toggle mutations of the item,
 * binds the recycled item view and adds it to its FrameLayout container.
 */
internal object MenuItemFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("toggleMenuItemMutations"),
        // Menu item container the item view is added to.
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Landroid/widget/FrameLayout;")
    )
)

/**
 * Sets the menu item text and icon. Before 9.44 this is the same method as [MenuItemFingerprint].
 * Instruction match 1 is the renderer field, match 2 the icon getter, match 4 the icon type
 * field and match 5 the icon type enum factory.
 * Anchor (9.46.51): `Ldefpackage/jfp;->f()V`, the renderer is `jfp.c`, the icon getter
 * `Lynw;->aX(Lanqk;)Laenu;`, the icon type `aenu.c` and the enum factory `Laltd;->a(I)Laltd;`.
 */
internal object MenuItemIconFingerprint : Fingerprint(
    classFingerprint = MenuItemFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V"),
        // Menu item renderer.
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT),
        // Icon of the menu item renderer.
        methodCall(opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // Icon type.
        fieldAccess(type = "I", opcode = Opcode.IGET, location = MatchAfterImmediately()),
        // Icon type enum.
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("I"), location = MatchAfterImmediately()),
        methodCall(smali = "Landroid/widget/ImageView;->setImageResource(I)V")
    )
)

/**
 * The constructor of the flyout menu title that finds the legacy like/dislike container.
 * Anchor (9.46.51): `Ldefpackage/jhd;-><init>`, reading `R.id.end_buttons_container`.
 */
internal object EndButtonsContainerFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "end_buttons_container")
    )
)
