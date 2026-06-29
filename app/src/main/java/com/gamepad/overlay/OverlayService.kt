package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "gamepad_overlay_channel"
        const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private val overlayViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        rebuildButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        clearButtons()
    }

    // 화면 회전 시 재배치
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildButtons()
    }

    private fun clearButtons() {
        overlayViews.forEach { runCatching { wm.removeView(it) } }
        overlayViews.clear()
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = wm.currentWindowMetrics
            Pair(m.bounds.width(), m.bounds.height())
        } else {
            val d = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(d)
            Pair(d.widthPixels, d.heightPixels)
        }
    }

    // ── 버튼 정의 (가로 기준 비율) ──
    // xFrac, yFrac = 중심 위치 (0~1), wFrac, hFrac = 크기 (W, H 비율)
    data class BtnDef(
        val label: String,
        val color: Int,
        val isCircle: Boolean,
        val action: String,
        val xFrac: Float, val yFrac: Float,
        val wFrac: Float, val hFrac: Float
    )

    private fun buildDefs(): List<BtnDef> = listOf(
        // 어깨 버튼 - 상단 좌우
        BtnDef("L1", 0xFF37474F.toInt(), false, "L1",   0.07f, 0.18f, 0.11f, 0.28f),
        BtnDef("L2", 0xFF263238.toInt(), false, "L2",   0.07f, 0.52f, 0.11f, 0.28f),
        BtnDef("R1", 0xFF37474F.toInt(), false, "R1",   0.93f, 0.18f, 0.11f, 0.28f),
        BtnDef("R2", 0xFF263238.toInt(), false, "R2",   0.93f, 0.52f, 0.11f, 0.28f),
        // D-PAD - 좌측 중단
        BtnDef("▲", 0xFF455A64.toInt(), false, "UP",    0.14f, 0.30f, 0.09f, 0.20f),
        BtnDef("▼", 0xFF455A64.toInt(), false, "DOWN",  0.14f, 0.68f, 0.09f, 0.20f),
        BtnDef("◀", 0xFF455A64.toInt(), false, "LEFT",  0.08f, 0.49f, 0.09f, 0.20f),
        BtnDef("▶", 0xFF455A64.toInt(), false, "RIGHT", 0.20f, 0.49f, 0.09f, 0.20f),
        // ABXY - 우측 중단
        BtnDef("Y", 0xFFF9A825.toInt(), true,  "Y",     0.86f, 0.28f, 0.11f, 0.24f),
        BtnDef("A", 0xFF2E7D32.toInt(), true,  "A",     0.86f, 0.70f, 0.11f, 0.24f),
        BtnDef("X", 0xFF1565C0.toInt(), true,  "X",     0.80f, 0.49f, 0.11f, 0.24f),
        BtnDef("B", 0xFFC62828.toInt(), true,  "B",     0.92f, 0.49f, 0.11f, 0.24f),
        // 중앙 버튼
        BtnDef("◀◀", 0xFF37474F.toInt(), false, "SEL",  0.43f, 0.88f, 0.08f, 0.16f),
        BtnDef("⊙",  0xFF455A64.toInt(), true,  "HOME", 0.50f, 0.88f, 0.08f, 0.18f),
        BtnDef("▶▶", 0xFF37474F.toInt(), false, "STA",  0.57f, 0.88f, 0.08f, 0.16f),
        // 아날로그 스틱 - 좌우 하단
        BtnDef("", 0xFF1C2833.toInt(), true, "LS",      0.25f, 0.75f, 0.24f, 0.52f),
        BtnDef("", 0xFF1C2833.toInt(), true, "RS",      0.75f, 0.75f, 0.24f, 0.52f),
    )

    private fun rebuildButtons() {
        clearButtons()
        val (W, H) = screenSize()
        // 항상 가로 기준으로 계산 (W > H 보장)
        val lW = maxOf(W, H)
        val lH = minOf(W, H)

        buildDefs().forEach { def ->
            val bw = (lW * def.wFrac).toInt()
            val bh = (lH * def.hFrac).toInt()
            val bx = (lW * def.xFrac - bw / 2f).toInt().coerceIn(0, lW - bw)
            val by = (lH * def.yFrac - bh / 2f).toInt().coerceIn(0, lH - bh)
            addButtonWindow(def, bx, by, bw, bh)
        }
    }

    private fun addButtonWindow(def: BtnDef, x: Int, y: Int, w: Int, h: Int) {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            w, h, x, y,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val view = BtnView(this, def)
        wm.addView(view, params)
        overlayViews.add(view)
    }

    // ── 개별 버튼 View ──
    inner class BtnView(ctx: android.content.Context, val def: BtnDef) : View(ctx) {

        private val fillP   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0x66FFFFFF; strokeWidth = 3f
        }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private var kx = 0f; private var ky = 0f
        private var stickActive = false
        private var btnPressed = false

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            kx = w / 2f; ky = h / 2f
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val isStick = def.action == "LS" || def.action == "RS"

            if (isStick) {
                val r = minOf(cx, cy) * 0.94f
                fillP.color = def.color; fillP.alpha = 170
                canvas.drawCircle(cx, cy, r, fillP)
                canvas.drawCircle(cx, cy, r, strokeP)
                strokeP.alpha = 28
                canvas.drawLine(cx - r * 0.8f, cy, cx + r * 0.8f, cy, strokeP)
                canvas.drawLine(cx, cy - r * 0.8f, cx, cy + r * 0.8f, strokeP)
                strokeP.alpha = 102
                // 노브
                val kp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(kx, ky - r * 0.1f, r * 0.43f,
                        intArrayOf(0xFFDDDDEE.toInt(), 0xFF7777AA.toInt()),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    alpha = if (stickActive) 240 else 185
                }
                canvas.drawCircle(kx, ky, r * 0.43f, kp)
                strokeP.alpha = 75; canvas.drawCircle(kx, ky, r * 0.43f, strokeP); strokeP.alpha = 102
            } else {
                fillP.color = def.color; fillP.alpha = if (btnPressed) 215 else 160
                if (def.isCircle) {
                    val r = minOf(cx, cy) * 0.90f
                    canvas.drawCircle(cx, cy, r, fillP)
                    canvas.drawCircle(cx, cy, r, strokeP)
                } else {
                    val rr = minOf(width, height) * 0.20f
                    val rect = RectF(4f, 4f, width - 4f, height - 4f)
                    canvas.drawRoundRect(rect, rr, rr, fillP)
                    canvas.drawRoundRect(rect, rr, rr, strokeP)
                }
                if (def.label.isNotEmpty()) {
                    textP.textSize = minOf(cx, cy) * 0.75f
                    canvas.drawText(def.label, cx, cy - (textP.ascent() + textP.descent()) / 2, textP)
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val isStick = def.action == "LS" || def.action == "RS"
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isStick) { stickActive = true; moveKnob(e.x, e.y) }
                    else { btnPressed = true; haptic() }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isStick) { moveKnob(e.x, e.y); return true }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isStick) {
                        stickActive = false; kx = width / 2f; ky = height / 2f
                    } else {
                        btnPressed = false
                    }
                    invalidate(); return true
                }
            }
            return false
        }

        private fun moveKnob(tx: Float, ty: Float) {
            val cx = width / 2f; val cy = height / 2f
            val maxR = minOf(cx, cy) * 0.50f
            val dx = tx - cx; val dy = ty - cy
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            kx = if (d <= maxR) tx else cx + dx / d * maxR
            ky = if (d <= maxR) ty else cy + dy / d * maxR
            invalidate()
        }

        private fun haptic() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                        .defaultVibrator.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        v.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") v.vibrate(8)
                }
            } catch (e: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "게임패드 오버레이", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopPending = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 게임패드 실행 중")
            .setContentText("종료: 이 알림의 [중지] 버튼 탭")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "⏹ 중지", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
