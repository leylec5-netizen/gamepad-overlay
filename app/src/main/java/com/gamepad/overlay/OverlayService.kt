package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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

    // 가로 화면 실제 크기 (오버레이가 그려지는 기준)
    private var LW = 0  // 가로 길이 (긴 쪽)
    private var LH = 0  // 세로 길이 (짧은 쪽)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        // 실제 디스플레이 크기 확정 (회전 무관한 물리 해상도)
        val dm = android.util.DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            LW = maxOf(bounds.width(), bounds.height())
            LH = minOf(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            LW = maxOf(dm.widthPixels, dm.heightPixels)
            LH = minOf(dm.widthPixels, dm.heightPixels)
        }

        buildAllButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayViews.forEach { runCatching { wm.removeView(it) } }
        overlayViews.clear()
    }

    // ── 버튼 정의: 가로(LW) x 세로(LH) 기준 비율 ──
    // xFrac,yFrac = 중심  wFrac = LW 비율  hFrac = LH 비율
    data class BtnDef(
        val label: String,
        val color: Int,
        val isCircle: Boolean,
        val action: String,
        val xFrac: Float, val yFrac: Float,
        val wFrac: Float, val hFrac: Float
    )

    private val defs = listOf(
        // 어깨 버튼
        BtnDef("L1", 0xFF37474F.toInt(), false, "L1",    0.06f, 0.20f, 0.10f, 0.25f),
        BtnDef("L2", 0xFF263238.toInt(), false, "L2",    0.06f, 0.52f, 0.10f, 0.25f),
        BtnDef("R1", 0xFF37474F.toInt(), false, "R1",    0.94f, 0.20f, 0.10f, 0.25f),
        BtnDef("R2", 0xFF263238.toInt(), false, "R2",    0.94f, 0.52f, 0.10f, 0.25f),
        // D-PAD
        BtnDef("▲",  0xFF455A64.toInt(), false, "UP",    0.13f, 0.28f, 0.08f, 0.18f),
        BtnDef("▼",  0xFF455A64.toInt(), false, "DOWN",  0.13f, 0.65f, 0.08f, 0.18f),
        BtnDef("◀",  0xFF455A64.toInt(), false, "LEFT",  0.08f, 0.47f, 0.08f, 0.18f),
        BtnDef("▶",  0xFF455A64.toInt(), false, "RIGHT", 0.18f, 0.47f, 0.08f, 0.18f),
        // ABXY
        BtnDef("Y",  0xFFF9A825.toInt(), true,  "Y",     0.855f,0.22f, 0.09f, 0.20f),
        BtnDef("A",  0xFF2E7D32.toInt(), true,  "A",     0.855f,0.62f, 0.09f, 0.20f),
        BtnDef("X",  0xFF1565C0.toInt(), true,  "X",     0.805f,0.42f, 0.09f, 0.20f),
        BtnDef("B",  0xFFC62828.toInt(), true,  "B",     0.905f,0.42f, 0.09f, 0.20f),
        // 중앙
        BtnDef("◀◀", 0xFF37474F.toInt(), false, "SEL",   0.43f, 0.87f, 0.07f, 0.16f),
        BtnDef("⊙",  0xFF455A64.toInt(), true,  "HOME",  0.50f, 0.87f, 0.07f, 0.17f),
        BtnDef("▶▶", 0xFF37474F.toInt(), false, "STA",   0.57f, 0.87f, 0.07f, 0.16f),
        // 스틱 (D-PAD 아래 / ABXY 아래)
        BtnDef("",   0xFF1C2833.toInt(), true,  "LS",    0.24f, 0.72f, 0.20f, 0.45f),
        BtnDef("",   0xFF1C2833.toInt(), true,  "RS",    0.76f, 0.72f, 0.20f, 0.45f),
    )

    private fun buildAllButtons() {
        defs.forEach { def ->
            val bw = (LW * def.wFrac).toInt()
            val bh = (LH * def.hFrac).toInt()
            // 중심 좌표 → 좌상단 좌표
            val bx = (LW * def.xFrac - bw / 2f).toInt().coerceIn(0, LW - bw)
            val by = (LH * def.yFrac - bh / 2f).toInt().coerceIn(0, LH - bh)
            addWindow(def, bx, by, bw, bh)
        }
    }

    private fun addWindow(def: BtnDef, x: Int, y: Int, w: Int, h: Int) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            w, h, x, y, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        // ★ 가로 고정: 오버레이 창을 항상 가로 기준으로 그림
        p.gravity = Gravity.TOP or Gravity.START
        p.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val v = BtnView(this, def)
        wm.addView(v, p)
        overlayViews.add(v)
    }

    // ── 버튼 View ──
    inner class BtnView(ctx: android.content.Context, val def: BtnDef) : View(ctx) {

        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x66FFFFFF
        }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private var kx = 0f; private var ky = 0f
        private var stickOn = false
        private var pressed = false

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            kx = w / 2f; ky = h / 2f
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            when {
                def.action == "LS" || def.action == "RS" -> drawStick(c, cx, cy)
                def.isCircle -> drawCircleBtn(c, cx, cy)
                else -> drawRectBtn(c, cx, cy)
            }
        }

        private fun drawStick(c: Canvas, cx: Float, cy: Float) {
            val r = minOf(cx, cy) * 0.93f
            fillP.color = def.color; fillP.alpha = 165
            c.drawCircle(cx, cy, r, fillP)
            c.drawCircle(cx, cy, r, strokeP)
            val old = strokeP.alpha; strokeP.alpha = 30
            c.drawLine(cx - r * .8f, cy, cx + r * .8f, cy, strokeP)
            c.drawLine(cx, cy - r * .8f, cx, cy + r * .8f, strokeP)
            strokeP.alpha = old
            // 노브
            val kPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(kx, ky, r * .44f,
                    intArrayOf(0xFFE0E0F0.toInt(), 0xFF6666AA.toInt()),
                    null, Shader.TileMode.CLAMP)
                alpha = if (stickOn) 245 else 190
            }
            c.drawCircle(kx, ky, r * .44f, kPaint)
            strokeP.alpha = 80; c.drawCircle(kx, ky, r * .44f, strokeP); strokeP.alpha = 102
        }

        private fun drawCircleBtn(c: Canvas, cx: Float, cy: Float) {
            val r = minOf(cx, cy) * 0.88f
            fillP.color = def.color; fillP.alpha = if (pressed) 220 else 165
            c.drawCircle(cx, cy, r, fillP); c.drawCircle(cx, cy, r, strokeP)
            if (def.label.isNotEmpty()) {
                textP.textSize = r * 0.80f
                c.drawText(def.label, cx, cy - (textP.ascent() + textP.descent()) / 2, textP)
            }
        }

        private fun drawRectBtn(c: Canvas, cx: Float, cy: Float) {
            val rr = minOf(width, height) * .22f
            fillP.color = def.color; fillP.alpha = if (pressed) 220 else 165
            val rect = RectF(4f, 4f, width - 4f, height - 4f)
            c.drawRoundRect(rect, rr, rr, fillP); c.drawRoundRect(rect, rr, rr, strokeP)
            if (def.label.isNotEmpty()) {
                textP.textSize = minOf(cx, cy) * .70f
                c.drawText(def.label, cx, cy - (textP.ascent() + textP.descent()) / 2, textP)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val isStick = def.action == "LS" || def.action == "RS"
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isStick) { stickOn = true; moveKnob(e.x, e.y) }
                    else { pressed = true; haptic() }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> if (isStick) { moveKnob(e.x, e.y); return true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isStick) { stickOn = false; kx = width/2f; ky = height/2f }
                    else pressed = false
                    invalidate(); return true
                }
            }
            return false
        }

        private fun moveKnob(tx: Float, ty: Float) {
            val cx = width/2f; val cy = height/2f
            val r = minOf(cx, cy) * .50f
            val dx = tx - cx; val dy = ty - cy
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            kx = if (d <= r) tx else cx + dx/d*r
            ky = if (d <= r) ty else cy + dy/d*r
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
            } catch (_: Exception) {}
        }
    }

    // ── 알림 ──
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "게임패드 오버레이", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(this, 0,
            Intent(this, OverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 게임패드 실행 중")
            .setContentText("종료하려면 알림에서 [중지] 탭")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "⏹ 중지", stop)
            .setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
