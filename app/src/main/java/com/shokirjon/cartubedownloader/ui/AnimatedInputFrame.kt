package com.shokirjon.cartubedownloader.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.os.PowerManager

/** One small, lifecycle-bound border animation. No layout or bitmap work per frame. */
class AnimatedInputFrame(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val outline = GradientDrawable().apply {
        cornerRadius = 19 * density
        setColor(Color.rgb(18, 37, 48))
        setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.rgb(36, 108, 112))
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { value ->
            val color = ArgbEvaluator().evaluate(value.animatedValue as Float,
                Color.rgb(36, 108, 112), Color.rgb(76, 216, 195)) as Int
            outline.setStroke((1.5f * density).toInt().coerceAtLeast(1), color)
        }
    }

    init { background = outline }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateMotion()
    }

    private fun updateMotion() {
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        if (isAttachedToWindow && windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled() && !powerSave) {
            if (!animator.isStarted) animator.start()
        } else animator.cancel()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
