package com.gamepad.overlay

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View

class GamepadView(context: Context) : View(context) {

    var onButtonDown: ((ButtonId) -> Unit)? = null
    var onButtonUp:   ((ButtonId) -> Unit)? = null

    enum class ButtonId {
        A, B, X, Y,
        L1, L2, R1, R2,
        START, SELECT, HOME,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        LSTICK, RSTICK
    }

    data class GpButton(
        val id: ButtonId,
        var rect: RectF = RectF(),
        var isCircle: Boolean = true,
        var color: Int = 0,
        var label: String = "",
        var pressed: Boolean = false
    )

    private val buttons = listOf(
        GpButton(ButtonId.A,          color=0xFF2E7D32.toInt(), label="A"),
        GpButton(ButtonId.B,          color=0xFFC62828.toInt(), label="B"),
        GpButton(ButtonId.X,          color=0xFF1565C0.toInt(), label="X"),
        GpButton(ButtonId.Y,          color=0xFFF9A825.toInt(), label="Y"),
        GpButton(ButtonId.L1,         color=0xFF37474F.toInt(), label="L1",  isCircle=false),
        GpButton(ButtonId.L2,         color=0xFF263238.toInt(), label="L2",  isCircle=false),
        GpButton(ButtonId.R1,         color=0xFF37474F.toInt(), label="R1",  isCircle=false),
        GpButton(ButtonId.R2,         color=0xFF263238.toInt(), label="R2",  isCircle=false),
        GpButton(ButtonId.START,      color=0xFF37474F.toInt(), label="▶▶", isCircle=false),
        GpButton(ButtonId.SELECT,     color=0xFF37474F.toInt(), label="◀◀", isCircle=false),
        GpButton(ButtonId.HOME,       color=0xFF455A64.toInt(), label="⊙"),
        GpButton(ButtonId.DPAD_UP,    color=0xFF37474F.toInt(), label="▲",  isCircle=false),
        GpButton(ButtonId.DPAD_DOWN,  color=0xFF37474F.toInt(), label="▼",  isCircle=false),
        GpButton(ButtonId.DPAD_LEFT,  color=0xFF37474F.toInt(), label="◀",  isCircle=false),
        GpButton(ButtonId.DPAD_RIGHT, color=0xFF37474F.toInt(), label="▶",  isCircle=false),
        GpButton(ButtonId.LSTICK,     color=0xFF455A64.toInt(), label=""),
        GpButton(ButtonId.RSTICK,     color=0xFF455A64.toInt(), label=""),
    )

    private fun btn(id: ButtonId) = buttons.first { it.id == id }

    // 스틱 상태
    data class StickState(var cx:Float=0f, var cy:Float=0f,
                          var kx:Float=0f, var ky:Float=0f,
                          var active:Boolean=false)
    private val lstick = StickState()
    private val rstick = StickState()

    // Paint
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE; color=0x55FFFFFF; strokeWidth=2.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.WHITE; textAlign=Paint.Align.CENTER; typeface=Typeface.DEFAULT_BOLD
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 포인터 → 버튼 추적
    private val pointerMap = mutableMapOf<Int, ButtonId>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout(w.toFloat(), h.toFloat())
    }

    private fun layout(W: Float, H: Float) {
        // ── 크기 기준값 ──
        val sh   = H * 0.10f   // 어깨버튼 높이
        val sw   = W * 0.10f   // 어깨버튼 너비
        val dp   = H * 0.11f   // D-PAD 셀 크기
        val fr   = H * 0.09f   // ABXY 버튼 반지름
        val stR  = H * 0.18f   // 스틱 반지름
        val cbH  = H * 0.055f  // 중앙버튼 높이
        val cbW  = W * 0.075f  // 중앙버튼 너비
        val pad  = W * 0.01f   // 가장자리 여백

        // ── 어깨버튼 (상단 좌/우) ──
        btn(ButtonId.L1).rect = RectF(pad,          pad,          pad+sw,       pad+sh)
        btn(ButtonId.L2).rect = RectF(pad,          pad+sh+pad,   pad+sw,       pad+sh*2+pad)
        btn(ButtonId.R1).rect = RectF(W-pad-sw,     pad,          W-pad,        pad+sh)
        btn(ButtonId.R2).rect = RectF(W-pad-sw,     pad+sh+pad,   W-pad,        pad+sh*2+pad)

        // ── D-PAD (좌하단) ──
        // 중심을 스틱 위쪽으로 올려서 겹치지 않게
        val dCx = W * 0.14f
        val dCy = H * 0.52f
        btn(ButtonId.DPAD_UP).rect    = RectF(dCx-dp/2, dCy-dp*1.5f, dCx+dp/2, dCy-dp*0.5f)
        btn(ButtonId.DPAD_DOWN).rect  = RectF(dCx-dp/2, dCy+dp*0.5f, dCx+dp/2, dCy+dp*1.5f)
        btn(ButtonId.DPAD_LEFT).rect  = RectF(dCx-dp*1.5f, dCy-dp/2, dCx-dp*0.5f, dCy+dp/2)
        btn(ButtonId.DPAD_RIGHT).rect = RectF(dCx+dp*0.5f, dCy-dp/2, dCx+dp*1.5f, dCy+dp/2)

        // ── ABXY (우하단) ──
        val fCx = W * 0.86f
        val fCy = H * 0.52f
        btn(ButtonId.Y).rect = RectF(fCx-fr, fCy-fr*2.2f-fr, fCx+fr, fCy-fr*2.2f+fr)
        btn(ButtonId.A).rect = RectF(fCx-fr, fCy+fr*2.2f-fr, fCx+fr, fCy+fr*2.2f+fr)
        btn(ButtonId.X).rect = RectF(fCx-fr*2.2f-fr, fCy-fr, fCx-fr*2.2f+fr, fCy+fr)
        btn(ButtonId.B).rect = RectF(fCx+fr*2.2f-fr, fCy-fr, fCx+fr*2.2f+fr, fCy+fr)

        // ── 아날로그 스틱 (하단 좌/우, D-PAD·ABXY 아래) ──
        val lsX = W * 0.22f;  val lsY = H * 0.80f
        val rsX = W * 0.78f;  val rsY = H * 0.80f
        btn(ButtonId.LSTICK).rect = RectF(lsX-stR, lsY-stR, lsX+stR, lsY+stR)
        btn(ButtonId.RSTICK).rect = RectF(rsX-stR, rsY-stR, rsX+stR, rsY+stR)
        lstick.cx=lsX; lstick.cy=lsY; lstick.kx=lsX; lstick.ky=lsY
        rstick.cx=rsX; rstick.cy=rsY; rstick.kx=rsX; rstick.ky=rsY

        // ── 중앙버튼 (하단 가운데) ──
        val midY = H * 0.92f
        btn(ButtonId.SELECT).rect = RectF(W/2-cbW*2.2f, midY-cbH, W/2-cbW*1.1f, midY+cbH)
        btn(ButtonId.HOME).rect   = RectF(W/2-cbH,      midY-cbH, W/2+cbH,      midY+cbH)
        btn(ButtonId.START).rect  = RectF(W/2+cbW*1.1f, midY-cbH, W/2+cbW*2.2f, midY+cbH)
    }

    // ── 그리기 ──
    override fun onDraw(canvas: Canvas) {
        buttons.forEach { b ->
            if (b.id == ButtonId.LSTICK || b.id == ButtonId.RSTICK) return@forEach
            drawButton(canvas, b)
        }
        drawStick(canvas, btn(ButtonId.LSTICK), lstick)
        drawStick(canvas, btn(ButtonId.RSTICK), rstick)
    }

    private fun drawButton(canvas: Canvas, b: GpButton) {
        fillPaint.color = b.color
        fillPaint.alpha = if (b.pressed) 210 else 150
        textPaint.textSize = b.rect.height() * 0.36f

        if (b.isCircle) {
            val cx=b.rect.centerX(); val cy=b.rect.centerY(); val r=b.rect.width()/2
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)
        } else {
            val rr = b.rect.height() * 0.25f
            canvas.drawRoundRect(b.rect, rr, rr, fillPaint)
            canvas.drawRoundRect(b.rect, rr, rr, strokePaint)
        }
        if (b.label.isNotEmpty()) {
            val ty = b.rect.centerY() - (textPaint.ascent()+textPaint.descent())/2
            canvas.drawText(b.label, b.rect.centerX(), ty, textPaint)
        }
    }

    private fun drawStick(canvas: Canvas, b: GpButton, st: StickState) {
        val cx=b.rect.centerX(); val cy=b.rect.centerY(); val r=b.rect.width()/2
        // 베이스
        fillPaint.color=0xFF1C2833.toInt(); fillPaint.alpha=170
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        // 십자선
        val old=strokePaint.alpha; strokePaint.alpha=35
        canvas.drawLine(cx-r*0.85f, cy, cx+r*0.85f, cy, strokePaint)
        canvas.drawLine(cx, cy-r*0.85f, cx, cy+r*0.85f, strokePaint)
        strokePaint.alpha=old
        // 노브
        val kAlpha = if (st.active) 230 else 175
        knobPaint.shader = RadialGradient(
            st.kx, st.ky-r*0.12f, r*0.44f,
            intArrayOf(0xFFE0E0F0.toInt(), 0xFF9090B0.toInt()),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        knobPaint.alpha = kAlpha
        canvas.drawCircle(st.kx, st.ky, r*0.44f, knobPaint)
        strokePaint.alpha = 80
        canvas.drawCircle(st.kx, st.ky, r*0.44f, strokePaint)
        strokePaint.alpha = 85
    }

    // ── 터치 ──
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var consumed = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i   = event.actionIndex
                val pid = event.getPointerId(i)
                val hit = hitTest(event.getX(i), event.getY(i))
                if (hit != null) {
                    consumed = true
                    pointerMap[pid] = hit.id
                    onPress(hit, event.getX(i), event.getY(i), true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val bid = pointerMap[pid] ?: continue
                    consumed = true
                    if (bid == ButtonId.LSTICK) moveStick(lstick, event.getX(i), event.getY(i))
                    if (bid == ButtonId.RSTICK) moveStick(rstick, event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i   = event.actionIndex
                val pid = event.getPointerId(i)
                val bid = pointerMap.remove(pid)
                if (bid != null) {
                    consumed = true
                    val b = buttons.first { it.id == bid }
                    onPress(b, event.getX(i), event.getY(i), false)
                    if (bid == ButtonId.LSTICK) resetStick(lstick)
                    if (bid == ButtonId.RSTICK) resetStick(rstick)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerMap.keys.toList().forEach { pid ->
                    val bid = pointerMap.remove(pid) ?: return@forEach
                    buttons.first { it.id == bid }.pressed = false
                    onButtonUp?.invoke(bid)
                }
                resetStick(lstick); resetStick(rstick)
                invalidate()
            }
        }
        if (consumed) invalidate()
        // ★ 버튼 위가 아니면 false → 터치가 아래 앱으로 통과
        return consumed
    }

    private fun hitTest(x: Float, y: Float): GpButton? {
        val PAD = 14f
        return buttons.firstOrNull { b ->
            if (b.isCircle) {
                val cx=b.rect.centerX(); val cy=b.rect.centerY(); val r=b.rect.width()/2+PAD
                (x-cx)*(x-cx)+(y-cy)*(y-cy) <= r*r
            } else {
                RectF(b.rect.left-PAD, b.rect.top-PAD, b.rect.right+PAD, b.rect.bottom+PAD).contains(x,y)
            }
        }
    }

    private fun onPress(b: GpButton, x: Float, y: Float, down: Boolean) {
        b.pressed = down
        if (down) {
            haptic()
            if (b.id == ButtonId.LSTICK) { lstick.active=true; moveStick(lstick, x, y) }
            if (b.id == ButtonId.RSTICK) { rstick.active=true; moveStick(rstick, x, y) }
            onButtonDown?.invoke(b.id)
        } else {
            onButtonUp?.invoke(b.id)
        }
    }

    private fun moveStick(st: StickState, x: Float, y: Float) {
        val r  = (if (st==lstick) btn(ButtonId.LSTICK) else btn(ButtonId.RSTICK)).rect.width()/2 * 0.56f
        val dx = x-st.cx; val dy = y-st.cy
        val d  = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (d <= r) { st.kx=x; st.ky=y } else { st.kx=st.cx+dx/d*r; st.ky=st.cy+dy/d*r }
        invalidate()
    }

    private fun resetStick(st: StickState) {
        st.kx=st.cx; st.ky=st.cy; st.active=false; invalidate()
    }

    fun getAxes(): FloatArray {
        fun ax(st: StickState, b: GpButton): Pair<Float,Float> {
            val r = b.rect.width()/2*0.56f
            return Pair((st.kx-st.cx)/r, (st.ky-st.cy)/r)
        }
        val (lx,ly) = ax(lstick, btn(ButtonId.LSTICK))
        val (rx,ry) = ax(rstick, btn(ButtonId.RSTICK))
        return floatArrayOf(lx,ly,rx,ry)
    }

    private fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") v.vibrate(8)
            }
        } catch(e: Exception) {}
    }
}
