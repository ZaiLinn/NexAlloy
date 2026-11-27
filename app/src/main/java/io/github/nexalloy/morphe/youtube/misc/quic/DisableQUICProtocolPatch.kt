package io.github.nexalloy.morphe.youtube.misc.quic

import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.youtube.shared.quic.disableQUICProtocolPatch
import io.github.nexalloy.patch

val DisableQUICProtocol = patch(
    name = "Disable QUIC protocol",
    description = "Adds an option to disable QUIC (Quick UDP Internet Connections) network protocol.",
) {
    disableQUICProtocolPatch(PreferenceScreen.MISC)
}
