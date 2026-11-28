/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Runtime port of the upstream music tree-node hook patch
 * (morphe-patches/patches/src/main/kotlin/app/morphe/patches/music/misc/litho/node/TreeNodeElementHookPatch.kt).
 *
 * The upstream patch also adds the LithoGetBufferContainerInterface to the tree-node list
 * classes so the extension can walk the reachable object graph of a tree-node entry. A
 * runtime module cannot add interfaces to host classes, so the music patches that need the
 * walk (see layout/buttons/action) replace the extension's interface dispatch instead.
 */

package io.github.nexalloy.morphe.music.misc.litho.node

import io.github.nexalloy.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.nexalloy.morphe.shared.misc.litho.node.createTreeNodeElementHookPatch
import io.github.nexalloy.patch

val musicTreeNodeElementHook = createTreeNodeElementHookPatch(
    // The extension sources are compiled into the runtime module, so the patcher's
    // sharedExtensionPatch has no runtime equivalent beyond loading the module assets.
    patch {},
    conversionContextPatch,
    addLithoContainerInterface = false,
    useLegacyContextRegister = { false }
)
