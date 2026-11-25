package com.khedr.smartassistant.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import com.khedr.smartassistant.data.AssistantStore

class FloatingBubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    override fun onCreate() {
        super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "AI"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF176B55.toInt()) }
            setOnClickListener { val s = AssistantStore(this@FloatingBubbleService); s.setEnabled(!s.enabled()); text = if (s.enabled()) "ON" else "OFF" }
        }
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(58, 58, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL; params.x = 18
        view.setOnTouchListener(object : android.view.View.OnTouchListener {
            var sx=0f; var sy=0f; var ox=0; var oy=0
            override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=params.x; oy=params.y; return true }
                    MotionEvent.ACTION_MOVE -> { params.x=(ox+(sx-e.rawX)).toInt(); params.y=(oy+(e.rawY-sy)).toInt(); wm.updateViewLayout(view, params); return true }
                    MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX-sx)<12 && kotlin.math.abs(e.rawY-sy)<12) v.performClick(); return true }
                }; return false
            }
        })
        wm.addView(view, params); bubble=view
    }
    override fun onDestroy() { bubble?.let { wm.removeView(it) }; bubble=null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
