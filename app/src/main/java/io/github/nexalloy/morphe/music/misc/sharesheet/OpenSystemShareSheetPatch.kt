package io.github.nexalloy.morphe.music.misc.sharesheet

import app.morphe.extension.music.patches.OpenSystemShareSheetPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.scopedHook
import io.github.nexalloy.morphe.music.misc.settings.PreferenceScreen
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.patch

val openSystemShareSheetPatch = patch(
    name = "Open system share sheet",
    description = "Adds an option to always open the system share sheet instead of the in-app share sheet."
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_open_system_share_sheet", summary = true)
    )

    val endpointClass = ::shareEntityServiceEndpointClass.clazz

    // The endpoint proto is created in the middle of the command resolver, so it is
    // stashed from the conversion the resolver calls before it casts the result.
    // The ThreadLocal keeps the stash per resolver invocation and per thread.
    val endpointStash = ThreadLocal<Any?>()

    // Clear the stash after the resolver returned, also when it threw.
    ShareEntityServiceEndpointCommandFingerprint.hookMethod {
        before { endpointStash.remove() }
        after { endpointStash.remove() }
    }

    val fromExtension = ::shareEntityFromExtensionValueMethod.method
    val networkConnected = ::shareNetworkConnectedMethod.method
    val parseSerializedEntity = ::parseSerializedEntityMethod.method

    ShareEntityServiceEndpointCommandFingerprint.hookMethod(
        scopedHook(
            fromExtension to {
                after {
                    val endpoint = it.result ?: return@after
                    if (endpointClass.isInstance(endpoint) && endpointStash.get() == null) {
                        endpointStash.set(endpoint)
                    }
                }
            },
            networkConnected to {
                after {
                    val endpoint = endpointStash.get() ?: return@after
                    // Without a network connection, the app does not open the in-app share
                    // sheet. It builds the music.youtube.com link of the song, playlist or
                    // artist itself and opens the system share sheet. The extension
                    // reports no connection when a link can be built.
                    it.result = OpenSystemShareSheetPatch.useInAppShareSheet(
                        it.result as Boolean,
                        endpoint
                    )
                }
            },
            parseSerializedEntity to {
                before {
                    val endpoint = endpointStash.get() ?: return@before
                    // The serialized share entity of the endpoint can be empty, with the
                    // share entity in a share entity endpoint nested in the command of the
                    // endpoint. The extension unwraps the nested entity.
                    it.args[0] = OpenSystemShareSheetPatch.getSerializedShareEntity(
                        it.args[0] as String?,
                        endpoint
                    )
                }
            },
        )
    )
}
