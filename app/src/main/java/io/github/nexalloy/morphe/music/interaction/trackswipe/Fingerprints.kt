package io.github.nexalloy.morphe.music.interaction.trackswipe

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.ResourceType
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.resourceLiteral

internal const val VIEW_PAGER_CLASS = "Landroidx/viewpager2/widget/ViewPager2;"

/**
 * Constructor of the controller of the track pagers. Swiping a pager to the left or right
 * plays the next or previous track: one pager shows the artwork of the player, the other
 * one the miniplayer. Both pagers are kept in fields of the controller.
 * Example: `Ljtz;-><init>(Landroid/widget/FrameLayout;Landroid/widget/FrameLayout;...)V`
 */
internal object TrackPagerControllerConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "player_view_pager"),
        resourceLiteral(ResourceType.ID, "mini_player_view_pager")
    )
)

/**
 * ViewPager2.setUserInputEnabled(boolean). It is the only boolean setter of the class
 * (the method name is obfuscated in the app, e.g. `h(Z)V`).
 */
internal object ViewPagerSetUserInputEnabledFingerprint : Fingerprint(
    definingClass = VIEW_PAGER_CLASS,
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(definingClass = "this", opcode = Opcode.IPUT_BOOLEAN, type = "Z")
    )
)
