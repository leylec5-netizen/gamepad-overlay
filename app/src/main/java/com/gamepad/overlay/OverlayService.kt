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
    // 각 버튼마다 독립 View + 독립 WindowManager 창 → 빈 영역은 완전 통과
    private val overlayViews = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addAllButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayViews.forEach { runCatching { wm.removeView(it) } }
        overlayViews.clear()
    }

    // ── 화면 크기 ──
    private fun screenSize(): Pair<Int,Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = wm.currentWindowMetrics
            Pair(m.bounds.width(), m.bounds.height())
        } else {
            val d = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(d)
            Pair(d.widthPixels, d.heightPixels)
        }
    }

    // ── 버튼 정의 ──
    data class BtnDef(
        val label: String,
        val color: Int,
        val isCircle: Boolean = true,
        val action: String,           // 알아보기 쉬운 이름
        val xFrac: Float, val yFrac: Float,   // 중심 비율
        val wFrac: Float, val hFrac: Float    // 크기 비율
    )

    private fun buildDefs(W: Int, H: Int): List<BtnDef> {
        // 가로(Landscape) 기준: W > H
        val sw = W; val sh = H
        return listOf(
            // 어깨 버튼
            BtnDef("L1", 0xFF37474F.toInt(), false, "L1",   0.065f, 0.14f, 0.10f, 0.17f),
            BtnDef("L2", 0xFF263238.toInt(), false, "L2",   0.065f, 0.38f, 0.10f, 0.17f),
            BtnDef("R1", 0xFF37474F.toInt(), false, "R1",   0.935f, 0.14f, 0.10f, 0.17f),
            BtnDef("R2", 0xFF263238.toInt(), false, "R2",   0.935f, 0.38f, 0.10f, 0.17f),
            // D-PAD
            BtnDef("▲", 0xFF37474F.toInt(), false, "UP",   0.135f, 0.36f, 0.08f, 0.18f),
            BtnDef("▼", 0xFF37474F.toInt(), false, "DOWN", 0.135f, 0.72f, 0.08f, 0.18f),
            BtnDef("◀", 0xFF37474F.toInt(), false, "LEFT", 0.075f, 0.54f, 0.08f, 0.18f),
            BtnDef("▶", 0xFF37474F.toInt(), false, "RIGHT",0.195f, 0.54f, 0.08f, 0.18f),
            // ABXY
            BtnDef("Y", 0xFFF9A825.toInt(), true,  "Y",    0.865f, 0.30f, 0.10f, 0.22f),
            BtnDef("A", 0xFF2E7D32.toInt(), true,  "A",    0.865f, 0.70f, 0.10f, 0.22f),
            BtnDef("X", 0xFF1565C0.toInt(), true,  "X",    0.810f, 0.50f, 0.10f, 0.22f),
            BtnDef("B", 0xFFC62828.toInt(), true,  "B",    0.920f, 0.50f, 0.10f, 0.22f),
            // 중앙
            BtnDef("◀◀",0xFF37474F.toInt(), false, "SEL",  0.43f,  0.88f, 0.07f, 0.14f),
            BtnDef("⊙", 0xFF455A64.toInt(), true,  "HOME", 0.50f,  0.88f, 0.07f, 0.16f),
            BtnDef("▶▶",0xFF37474F.toInt(), false, "STA",  0.57f,  0.88f, 0.07f, 0.14f),
            // 스틱 (크게)
            BtnDef("",  0xFF1C2833.toInt(), true,  "LS",   0.22f,  0.72f, 0.22f, 0.50f),
            BtnDef("",  0xFF1C2833.toInt(), true,  "RS",   0.78f,  0.72f, 0.22f, 0.50f),
        )
    }

    // ── 모든 버튼을 개별 Window로 추가 ──
    private fun addAllButtons() {
        val (W, H) = screenSize()
        val defs = buildDefs(W, H)
        defs.forEach { def ->
            val bw = (W * def.wFrac).toInt()
            val bh = (H * def.hFrac).toInt()
            val bx = (W * def.xFrac - bw / 2).toInt()
            val by = (H * def.yFrac - bh / 2).toInt()
            addButtonView(def, bx, by, bw, bh)
        }
    }

    private fun addButtonView(def: BtnDef, x: Int, y: Int, w: Int, h: Int) {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            w, h, x, y,
            layoutType,
            // 이 창 크기 딱 맞게만 터치 받음 → 나머지는 자동으로 아래 앱에 전달
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val view = ButtonView(this, def)
        wm.addView(view, params)
        overlayViews.add(view)
    }

    // ── 개별 버튼 View ──
    inner class ButtonView(ctx: android.content.Context, val def: BtnDef) : View(ctx) {

        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = 0x55FFFFFF; strokeWidth = 3f
        }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        // 스틱 전용
        private var knobX = 0f; private var knobY = 0f
        private var stickActive = false

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            knobX = w / 2f; knobY = h / 2f
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val isStick = def.action == "LS" || def.action == "RS"

            if (isStick) {
                // 베이스 링
                fillP.color = def.color; fillP.alpha = 160
                canvas.drawCircle(cx, cy, cx * 0.95f, fillP)
                canvas.drawCircle(cx, cy, cx * 0.95f, strokeP)
                // 십자
                strokeP.alpha = 30
                canvas.drawLine(cx*0.15f, cy, cx*1.85f, cy, strokeP)
                canvas.drawLine(cx, cy*0.15f, cx, cy*1.85f, strokeP)
                strokeP.alpha = 85
                // 노브
                val kp = Paint(Paint.ANTI_ALIAS_FLAG)
                kp.shader = RadialGradient(knobX, knobY - cy*0.08f, cx*0.42f,
                    intArrayOf(0xFFE0E0F0.toInt(), 0xFF8888AA.toInt()),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                kp.alpha = if (stickActive) 235 else 180
                canvas.drawCircle(knobX, knobY, cx * 0.42f, kp)
                strokeP.alpha = 70
                canvas.drawCircle(knobX, knobY, cx * 0.42f, strokeP)
                strokeP.alpha = 85
            } else {
                fillP.color = def.color
                fillP.alpha = if (isPressed) 210 else 155
                if (def.isCircle) {
                    val r = minOf(cx, cy) * 0.92f
                    canvas.drawCircle(cx, cy, r, fillP)
                    canvas.drawCircle(cx, cy, r, strokeP)
                } else {
                    val rr = minOf(cx, cy) * 0.35f
                    val rect = RectF(cx*0.06f, cy*0.06f, width-cx*0.06f, height-cy*0.06f)
                    canvas.drawRoundRect(rect, rr, rr, fillP)
                    canvas.drawRoundRect(rect, rr, rr, strokeP)
                }
                if (def.label.isNotEmpty()) {
                    textP.textSize = minOf(cx, cy) * 0.72f
                    val ty = cy - (textP.ascent() + textP.descent()) / 2
                    canvas.drawText(def.label, cx, ty, textP)
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val isStick = def.action == "LS" || def.action == "RS"
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isStick) { stickActive = true; moveKnob(e.x, e.y) }
                    else { isPressed = true; haptic(); invalidate() }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isStick) moveKnob(e.x, e.y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isStick) { stickActive = false; knobX=width/2f; knobY=height/2f; invalidate() }
                    else { isPressed = false; invalidate()
                        // 종료 버튼: 알림의 중지 버튼 대신 HOME 버튼 5초 길게 누르면 종료
                    }
                    return true
                }
            }
            return false
        }

        private fun moveKnob(tx: Float, ty: Float) {
            val cx = width/2f; val cy = height/2f
            val maxR = cx * 0.50f
            val dx = tx - cx; val dy = ty - cy
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            knobX = if (d <= maxR) tx else cx + dx/d*maxR
            knobY = if (d <= maxR) ty else cy + dy/d*maxR
            invalidate()
        }

        private fun haptic() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
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

    // ── 알림 ──
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
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 게임패드 실행 중 — 탭해서 종료")
            .setContentText("알림을 탭하면 앱으로 돌아가 중지할 수 있습니다")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "⏹ 오버레이 중지", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
