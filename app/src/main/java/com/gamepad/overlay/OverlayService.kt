package com.gamepad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
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
    private var LW = 0  // 가로 픽셀 (긴 쪽)
    private var LH = 0  // 세로 픽셀 (짧은 쪽)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        measureScreen()
        buildAllButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayViews.forEach { runCatching { wm.removeView(it) } }
        overlayViews.clear()
    }

    private fun measureScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            LW = maxOf(b.width(), b.height())
            LH = minOf(b.width(), b.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            LW = maxOf(dm.widthPixels, dm.heightPixels)
            LH = minOf(dm.widthPixels, dm.heightPixels)
        }
    }

    // ── 버튼 정의 ──
    // xF,yF = 중심 비율(가로LW×세로LH 기준)  wF = LW비율  hF = LH비율
    data class BtnDef(
        val label: String, val color: Int, val isCircle: Boolean,
        val action: String,
        val xF: Float, val yF: Float, val wF: Float, val hF: Float
    )

    // Wii U 컨트롤러 기준 키 매핑
    // Cemu Android: KeyEvent 또는 MotionEvent axis로 입력 전달
    private val KEY_MAP = mapOf(
        "A"    to KeyEvent.KEYCODE_BUTTON_A,
        "B"    to KeyEvent.KEYCODE_BUTTON_B,
        "X"    to KeyEvent.KEYCODE_BUTTON_X,
        "Y"    to KeyEvent.KEYCODE_BUTTON_Y,
        "L1"   to KeyEvent.KEYCODE_BUTTON_L1,
        "L2"   to KeyEvent.KEYCODE_BUTTON_L2,
        "R1"   to KeyEvent.KEYCODE_BUTTON_R1,
        "R2"   to KeyEvent.KEYCODE_BUTTON_R2,
        "UP"   to KeyEvent.KEYCODE_DPAD_UP,
        "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
        "RIGHT"to KeyEvent.KEYCODE_DPAD_RIGHT,
        "SEL"  to KeyEvent.KEYCODE_BUTTON_SELECT,
        "STA"  to KeyEvent.KEYCODE_BUTTON_START,
        "HOME" to KeyEvent.KEYCODE_BUTTON_MODE,
    )

    private val defs = listOf(
        // ── 어깨 버튼 (상단 좌/우) ──
        BtnDef("L1", 0xFF37474F.toInt(), false, "L1",   0.055f, 0.22f, 0.085f, 0.32f),
        BtnDef("L2", 0xFF263238.toInt(), false, "L2",   0.055f, 0.62f, 0.085f, 0.32f),
        BtnDef("R1", 0xFF37474F.toInt(), false, "R1",   0.945f, 0.22f, 0.085f, 0.32f),
        BtnDef("R2", 0xFF263238.toInt(), false, "R2",   0.945f, 0.62f, 0.085f, 0.32f),
        // ── D-PAD (좌측, 스틱 위) ──
        BtnDef("▲",  0xFF455A64.toInt(), false, "UP",   0.155f, 0.22f, 0.075f, 0.22f),
        BtnDef("▼",  0xFF455A64.toInt(), false, "DOWN", 0.155f, 0.65f, 0.075f, 0.22f),
        BtnDef("◀",  0xFF455A64.toInt(), false, "LEFT", 0.105f, 0.44f, 0.075f, 0.22f),
        BtnDef("▶",  0xFF455A64.toInt(), false, "RIGHT",0.205f, 0.44f, 0.075f, 0.22f),
        // ── ABXY (우측, 스틱 위) ──
        BtnDef("Y",  0xFFF9A825.toInt(), true,  "Y",    0.845f, 0.20f, 0.085f, 0.22f),
        BtnDef("A",  0xFF2E7D32.toInt(), true,  "A",    0.845f, 0.62f, 0.085f, 0.22f),
        BtnDef("X",  0xFF1565C0.toInt(), true,  "X",    0.795f, 0.41f, 0.085f, 0.22f),
        BtnDef("B",  0xFFC62828.toInt(), true,  "B",    0.895f, 0.41f, 0.085f, 0.22f),
        // ── 아날로그 스틱 (하단 좌/우) ──
        BtnDef("",   0xFF1C2833.toInt(), true,  "LS",   0.22f,  0.72f, 0.19f,  0.46f),
        BtnDef("",   0xFF1C2833.toInt(), true,  "RS",   0.78f,  0.72f, 0.19f,  0.46f),
        // ── 중앙 버튼 ──
        BtnDef("◀◀", 0xFF37474F.toInt(), false, "SEL",  0.435f, 0.82f, 0.065f, 0.20f),
        BtnDef("⊙",  0xFF455A64.toInt(), true,  "HOME", 0.500f, 0.82f, 0.060f, 0.18f),
        BtnDef("▶▶", 0xFF37474F.toInt(), false, "STA",  0.565f, 0.82f, 0.065f, 0.20f),
        // ── 설정/종료 버튼 ──
        BtnDef("⚙",  0xFF263238.toInt(), true,  "SETTINGS", 0.50f, 0.12f, 0.045f, 0.12f),
    )

    private fun buildAllButtons() {
        defs.forEach { def ->
            val bw = (LW * def.wF).toInt()
            val bh = (LH * def.hF).toInt()
            val bx = (LW * def.xF - bw / 2f).toInt().coerceIn(0, LW - bw)
            val by = (LH * def.yF - bh / 2f).toInt().coerceIn(0, LH - bh)
            addWindow(def, bx, by, bw, bh)
        }
    }

    private fun addWindow(def: BtnDef, x: Int, y: Int, w: Int, h: Int) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(w, h, x, y, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        p.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val v = BtnView(this, def)
        wm.addView(v, p)
        overlayViews.add(v)
    }

    // ── 설정 패널 (⚙ 눌렀을 때) ──
    private var settingsPanel: View? = null

    private fun showSettingsPanel() {
        if (settingsPanel != null) { hideSettingsPanel(); return }

        val pw = (LW * 0.22f).toInt()
        val ph = (LH * 0.55f).toInt()
        val px = (LW / 2f - pw / 2f).toInt()
        val py = (LH * 0.20f).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(pw, ph, px, py, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        p.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val panel = object : View(this) {
            private val bg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE1A1A2E.toInt() }
            private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            private val tp   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

            override fun onDraw(c: Canvas) {
                val rr = 28f
                c.drawRoundRect(RectF(0f,0f,width.toFloat(),height.toFloat()), rr, rr, bg)
                // 투명도 라벨
                tp.textSize = height * 0.085f
                c.drawText("투명도", width/2f, height*0.13f, tp)
                // 종료 버튼
                val exitBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBBC62828.toInt() }
                val exitR = RectF(width*0.1f, height*0.55f, width*0.9f, height*0.75f)
                c.drawRoundRect(exitR, 16f, 16f, exitBg)
                tp.textSize = height * 0.09f
                c.drawText("⏹ 오버레이 종료", width/2f, height*0.68f, tp)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    // 종료 버튼 영역 탭
                    val exitTop = height * 0.55f; val exitBot = height * 0.75f
                    if (e.y in exitTop..exitBot) {
                        hideSettingsPanel()
                        stopSelf()
                        return true
                    }
                    hideSettingsPanel()
                }
                return true
            }
        }

        settingsPanel = panel
        wm.addView(panel, p)
        overlayViews.add(panel)
    }

    private fun hideSettingsPanel() {
        settingsPanel?.let {
            runCatching { wm.removeView(it) }
            overlayViews.remove(it)
        }
        settingsPanel = null
    }

    // ── 입력 전달 (KeyEvent 방식) ──
    // Cemu Android는 Android Gamepad (KeyEvent BUTTON_* + AXIS_*)를 읽음
    // 실제 물리 컨트롤러처럼 KeyEvent를 브로드캐스트하는 방식은
    // root 없이는 불가능하므로, AccessibilityService나 InputManager 우회 필요.
    // 현재 구현: 버튼 상태를 로그로 남기고 향후 확장 가능한 구조
    private fun sendKey(action: String, down: Boolean) {
        val keycode = KEY_MAP[action] ?: return
        android.util.Log.d("GamePad", "${if(down)"DOWN" else "UP"}: $action → keycode $keycode")
        // TODO: root 환경이라면 아래 주석 해제
        // val cmd = "sendevent /dev/input/event0 1 $keycode ${if(down) 1 else 0}"
        // Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
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

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { kx=w/2f; ky=h/2f }

        override fun onDraw(c: Canvas) {
            val cx = width/2f; val cy = height/2f
            when {
                def.action=="LS"||def.action=="RS" -> drawStick(c,cx,cy)
                def.action=="SETTINGS"             -> drawSettings(c,cx,cy)
                def.isCircle                       -> drawCircleBtn(c,cx,cy)
                else                               -> drawRectBtn(c,cx,cy)
            }
        }

        private fun drawSettings(c: Canvas, cx: Float, cy: Float) {
            val r = minOf(cx,cy)*0.88f
            fillP.color = if(pressed) 0xFF37474F.toInt() else 0xAA263238.toInt()
            fillP.alpha = 200
            c.drawCircle(cx,cy,r,fillP); c.drawCircle(cx,cy,r,strokeP)
            textP.textSize = r*0.90f
            c.drawText("⚙",cx,cy-(textP.ascent()+textP.descent())/2,textP)
        }

        private fun drawStick(c: Canvas, cx: Float, cy: Float) {
            val r = minOf(cx,cy)*0.93f
            fillP.color=def.color; fillP.alpha=165
            c.drawCircle(cx,cy,r,fillP); c.drawCircle(cx,cy,r,strokeP)
            val old=strokeP.alpha; strokeP.alpha=30
            c.drawLine(cx-r*.8f,cy,cx+r*.8f,cy,strokeP)
            c.drawLine(cx,cy-r*.8f,cx,cy+r*.8f,strokeP); strokeP.alpha=old
            val kp=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader=RadialGradient(kx,ky,r*.44f,
                    intArrayOf(0xFFE0E0F0.toInt(),0xFF6666AA.toInt()),
                    null,Shader.TileMode.CLAMP)
                alpha=if(stickOn) 245 else 190
            }
            c.drawCircle(kx,ky,r*.44f,kp)
            strokeP.alpha=80; c.drawCircle(kx,ky,r*.44f,strokeP); strokeP.alpha=102
        }

        private fun drawCircleBtn(c: Canvas, cx: Float, cy: Float) {
            val r=minOf(cx,cy)*0.88f
            fillP.color=def.color; fillP.alpha=if(pressed) 225 else 170
            c.drawCircle(cx,cy,r,fillP); c.drawCircle(cx,cy,r,strokeP)
            if(def.label.isNotEmpty()){
                textP.textSize=r*0.78f
                c.drawText(def.label,cx,cy-(textP.ascent()+textP.descent())/2,textP)
            }
        }

        private fun drawRectBtn(c: Canvas, cx: Float, cy: Float) {
            val rr=minOf(width,height)*.22f
            fillP.color=def.color; fillP.alpha=if(pressed) 225 else 170
            val rect=RectF(4f,4f,width-4f,height-4f)
            c.drawRoundRect(rect,rr,rr,fillP); c.drawRoundRect(rect,rr,rr,strokeP)
            if(def.label.isNotEmpty()){
                textP.textSize=minOf(cx,cy)*.68f
                c.drawText(def.label,cx,cy-(textP.ascent()+textP.descent())/2,textP)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val isStick=def.action=="LS"||def.action=="RS"
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN -> {
                    if(def.action=="SETTINGS"){ pressed=true; invalidate(); return true }
                    if(isStick){ stickOn=true; moveKnob(e.x,e.y) }
                    else { pressed=true; haptic(); sendKey(def.action, true) }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> if(isStick){ moveKnob(e.x,e.y); return true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if(def.action=="SETTINGS"){
                        pressed=false; invalidate()
                        if(e.actionMasked==MotionEvent.ACTION_UP) showSettingsPanel()
                        return true
                    }
                    if(isStick){ stickOn=false; kx=width/2f; ky=height/2f }
                    else { pressed=false; sendKey(def.action, false) }
                    invalidate(); return true
                }
            }
            return false
        }

        private fun moveKnob(tx: Float, ty: Float) {
            val cx=width/2f; val cy=height/2f
            val r=minOf(cx,cy)*.50f
            val dx=tx-cx; val dy=ty-cy
            val d=Math.hypot(dx.toDouble(),dy.toDouble()).toFloat()
            kx=if(d<=r) tx else cx+dx/d*r
            ky=if(d<=r) ty else cy+dy/d*r
            invalidate()
        }

        private fun haptic() {
            try {
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                        .defaultVibrator.vibrate(VibrationEffect.createOneShot(8,VibrationEffect.DEFAULT_AMPLITUDE))
                else {
                    @Suppress("DEPRECATION") val v=getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                        v.vibrate(VibrationEffect.createOneShot(8,VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") v.vibrate(8)
                }
            } catch(_:Exception){}
        }
    }

    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            val ch=NotificationChannel(CHANNEL_ID,"게임패드 오버레이",NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stop=PendingIntent.getService(this,0,
            Intent(this,OverlayService::class.java).apply{action="STOP"},
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open=PendingIntent.getActivity(this,0,
            Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL_ID)
            .setContentTitle("🎮 게임패드 실행 중")
            .setContentText("화면의 ⚙ 버튼 또는 여기서 종료 가능")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete,"⏹ 중지",stop)
            .setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="STOP") stopSelf()
        return START_STICKY
    }
}
