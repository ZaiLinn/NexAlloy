package io.github.nexalloy.morphe.youtube.interaction.viewerdiscretion

import app.morphe.extension.shared.patches.RemoveViewerDiscretionDialogPatch
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findFieldListDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

/**
 * The Runnable that handles accepted adult content.
 */
internal val adultContentRunnableMethod = findMethodDirect {
    AdultContentRunnableFingerprint.run()
}

private val skipDiscretionDialogMatch: DexKitBridge.() -> MethodData = {
    skipDiscretionDialogFingerprint(
        adultContentRunnableMethod().declaredClass!!.descriptor
    ).match().method
}

/**
 * The method that decides if the viewer discretion dialog is skipped.
 */
internal val skipDiscretionDialogMethod = findMethodDirect {
    skipDiscretionDialogMatch()
}

/** The host getter producing the Boolean unboxed by the skip decision method. */
internal val adultContentAllowedMethod = findMethodDirect {
    val match = skipDiscretionDialogFingerprint(
        adultContentRunnableMethod().declaredClass!!.descriptor
    ).match()
    match.method.instructions.take(match.instructionMatches.first().index)
        .mapNotNull { it.methodRef }
        .last { it.returnTypeName == "java.lang.Boolean" && it.paramCount == 0 }
}

/**
 * The boolean check of the skip decision whose result is forced to true by the patch.
 * Upstream transforms the result of this call and the result of the preceding
 * Boolean.booleanValue() call where they are used in the skip decision method.
 */
internal val skipDiscretionDialogCheckMethod = findMethodDirect {
    skipDiscretionDialogFingerprint(
        adultContentRunnableMethod().declaredClass!!.descriptor
    ).match().instructionMatches[2].instruction.methodRef!!
}

private val adultContentPropertiesFields: DexKitBridge.() -> List<FieldData> = {
    val propertiesMatches = AdultContentSetPropertiesFingerprint.match().instructionMatches
    listOf(
        propertiesMatches[0].instruction.fieldRef!!,
        propertiesMatches[2].instruction.fieldRef!!,
    )
}

/**
 * The method that stores the adult content properties of the video.
 */
internal val writeAdultContentPropertiesMethod = findMethodDirect {
    val fields = adultContentPropertiesFields()
    writeAdultContentPropertiesFingerprint(
        skipDiscretionDialogMethod().declaredClass!!.descriptor,
        fields[0],
        fields[1],
    ).run()
}

/**
 * The fields that store the adult content properties of the video.
 */
internal val adultContentPropertiesFieldList = findFieldListDirect {
    adultContentPropertiesFields()
}

/**
 * True while the skip decision method runs. Used to scope the hooks of the
 * methods it calls to that method, matching the upstream instruction-level transforms.
 */
private val inSkipDiscretionDialogMethod = ThreadLocal.withInitial { false }

val RemoveViewerDiscretionDialog = patch(
    name = "Remove viewer discretion dialog",
    description = "Adds an option to remove the dialog that appears when opening a video that has been age-restricted " +
        "by accepting it automatically. This does not bypass the age restriction.",
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_remove_viewer_discretion_dialog"),
    )

    val skipDialogMethod = ::skipDiscretionDialogMethod.method
    val checkMethod = ::skipDiscretionDialogCheckMethod.method
    val allowedMethod = ::adultContentAllowedMethod.method

    // region skip discretion dialog

    // The upstream patch transforms the two boolean results read in the skip decision
    // method. At runtime the method executions are gated with a thread local flag, and
    // the called methods have their result transformed instead.
    skipDialogMethod.hookMethod {
        before { inSkipDiscretionDialogMethod.set(true) }
        after { inSkipDiscretionDialogMethod.set(false) }
    }

    checkMethod.hookMethod {
        after {
            if (inSkipDiscretionDialogMethod.get() == true) {
                it.result = RemoveViewerDiscretionDialogPatch.hideViewDiscretionDialog(it.result as Boolean)
            }
        }
    }

    // Hook the host's boxed-value producer, scoped to the skip decision. Hooking
    // Boolean.booleanValue itself also intercepts unboxing inside the hook bridge
    // and our callbacks, recursively overflowing the startup thread's stack.
    allowedMethod.hookMethod {
        after {
            if (inSkipDiscretionDialogMethod.get() == true) {
                it.result = RemoveViewerDiscretionDialogPatch.hideViewDiscretionDialog(it.result as Boolean)
            }
        }
    }

    // endregion

    // region unlock related videos for restricted videos

    // The upstream patch transforms the boolean values the method stores into the adult
    // content properties. At runtime the stored fields are set to the transformed value
    // after the method has run.
    val propertiesFields = ::adultContentPropertiesFieldList.fieldList

    ::writeAdultContentPropertiesMethod.hookMethod {
        after {
            if (!RemoveViewerDiscretionDialogPatch.hideViewDiscretionDialog(false)) {
                return@after
            }

            for (target in listOf(it.thisObject, *it.args)) {
                if (target == null) continue
                for (field in propertiesFields) {
                    if (field.declaringClass.isInstance(target)) {
                        field.setBoolean(target, true)
                    }
                }
            }
        }
    }

    // endregion
}
