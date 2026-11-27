package io.github.nexalloy.morphe.youtube.layout.hide.rollingnumber

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance

internal object RollingNumberTextViewAnimationUpdateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/graphics/Bitmap;"),
    filters = listOf(
        newInstance("Landroid/text/style/ImageSpan;"),
        methodCall(smali = "Landroid/text/style/ImageSpan;-><init>(Landroid/content/Context;Landroid/graphics/Bitmap;I)V"),
        methodCall(smali = "Landroid/text/SpannableString;->length()I"),
        methodCall(smali = "Landroid/text/SpannableString;->setSpan(Ljava/lang/Object;III)V"),
        methodCall(name = "setText", parameters = listOf("Ljava/lang/CharSequence;"))
    ),
    custom = {
        declaredClass {
            addAnyOf { superClass { descriptor = "Landroid/support/v7/widget/AppCompatTextView;" } }
            addAnyOf { superClass { descriptor = "Lcom/google/android/libraries/youtube/rendering/ui/spec/typography/YouTubeAppCompatTextView;" } }
        }
    }
)

// The setSpan call that applies the animated image span to the text.
val rollingNumberSetSpanMethod = findMethodDirect {
    RollingNumberTextViewAnimationUpdateFingerprint.instructionMatches[3].instruction.methodRef!!
}
