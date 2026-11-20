/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * Launcher activity: black fullscreen splash (ZuluTekSplash theme, no white flash) showing
 * the pixel-art boot image (tools/make_icon.py -> drawable-nodpi/splash_boombox.png,
 * generated in CI because binaries don't survive the connector), then MainActivity. */
package com.zulutek.preview

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.splash_boombox)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(iv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        }
        setContentView(frame)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1400)
    }
}
