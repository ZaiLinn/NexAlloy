package io.github.nexalloy.morphe.reddit.interaction.avatars

import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.shared.misc.glide.GlideRequestBuilderLoadGenericFingerprint

internal val redditGlideLoadGeneric = findMethodDirect { GlideRequestBuilderLoadGenericFingerprint() }
