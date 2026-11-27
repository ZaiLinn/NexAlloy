@file:Suppress("DEPRECATION")

package io.github.nexalloy.morphe.music.misc.settings

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toolbar
import app.morphe.extension.music.settings.preference.MusicPreferenceFragment
import app.morphe.extension.shared.settings.BaseActivityHook
import io.github.nexalloy.hookMethod

/** Only the Music Morphe activity and its nested PreferenceScreen dialogs animate. */
internal object MusicSettingsTransitions {
    fun onOpen(activity: Activity) {
        if (activity.intent?.dataString != BaseActivityHook.MORPHE_SETTINGS_INTENT) return
        val enabled = ValueAnimator.areAnimatorsEnabled()
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                if (enabled) android.R.anim.fade_in else 0,
                if (enabled) android.R.anim.fade_out else 0
            )
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                if (enabled) android.R.anim.fade_in else 0,
                if (enabled) android.R.anim.fade_out else 0
            )
        } else {
            activity.overridePendingTransition(
                if (enabled) android.R.anim.fade_in else 0,
                if (enabled) android.R.anim.fade_out else 0
            )
        }
    }

    fun onFinish(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34 || !activity.isFinishing ||
            activity.intent?.dataString != BaseActivityHook.MORPHE_SETTINGS_INTENT
        ) return
        val enabled = ValueAnimator.areAnimatorsEnabled()
        activity.overridePendingTransition(
            if (enabled) android.R.anim.fade_in else 0,
            if (enabled) android.R.anim.fade_out else 0
        )
    }

    fun hookSubpages() {
        MusicPreferenceFragment::class.java.getDeclaredMethod(
            "onPostToolbarSetup", Toolbar::class.java, Dialog::class.java
        ).hookMethod {
            after { param ->
                val dialog = param.args[1] as Dialog
                val window = dialog.window ?: return@after
                window.setWindowAnimations(
                    // WindowManager loads these resources in the host package. Use
                    // framework animations, which also resolve outside this process.
                    if (ValueAnimator.areAnimatorsEnabled()) android.R.style.Animation_Dialog else 0
                )
                // PreferenceScreen has shown its dialog before this callback runs.
                // Animate entry after the toolbar is added; window animations handle
                // exit for both the toolbar back button and system back gestures.
                val decor = window.decorView
                decor.post {
                    if (!dialog.isShowing || !ValueAnimator.areAnimatorsEnabled()) return@post
                    val direction = if (decor.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
                    decor.alpha = 0f
                    decor.translationX = direction * 24 * decor.resources.displayMetrics.density
                    decor.animate().alpha(1f).translationX(0f)
                        .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }
    }
}
