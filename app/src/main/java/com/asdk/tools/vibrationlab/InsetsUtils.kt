package com.asdk.tools.vibrationlab

import android.content.res.Resources
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun ComponentActivity.enableEdgeToEdgeWithPadding(view: View) {
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        )
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(view)
}

fun Int.dp(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
fun Float.dp(): Float = this * Resources.getSystem().displayMetrics.density
