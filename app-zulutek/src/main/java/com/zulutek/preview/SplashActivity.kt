/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * Launcher activity: black fullscreen splash (ZuluTekSplash theme, no white flash) showing
 * the pixel-art boot image (tools/make_icon.py -> drawable-nodpi/splash_boombox.png, the
 * Ghetto Blaster x Nyan Cat remix, generated in CI because binaries don't survive the
 * connector), tiny "Flying Zulu" credit at the bottom (charte), then MainActivity (~1.9 s). */
package com.zulutek.preview

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.splash_boombox)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val credit = TextView(this).apply {
            text = "Flying Zulu"
            setTextColor(Color.argb(255, 160, 160, 160))
            typeface = Typeface.MONOSPACE
            textSize = 10f
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(iv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            // crédit façon générique de fin de film : petit, en bas, centré (charte)
            addView(credit, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 16 })
        }
        setContentView(frame)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1900)
    }
}
