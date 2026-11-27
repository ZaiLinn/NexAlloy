package io.github.nexalloy.morphe.youtube.video.quality.hidepremium

import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.youtube.video.quality.CurrentVideoFormatConstructorFingerprint

/**
 * Field of the current video format model that holds the video qualities,
 * which is the field the upstream patch filters before it is assigned.
 */
internal val currentVideoFormatQualitiesField = findFieldDirect {
    CurrentVideoFormatConstructorFingerprint.instructionMatches.last().instruction.fieldRef!!
}
