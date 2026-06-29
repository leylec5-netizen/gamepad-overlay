package com.gamepad.overlay

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View

/**
 * 순수 Kotlin Canvas로 게임패드를 그리는 View.
 * 버튼이 없는 영역은 onTouchEvent에서 false를 반환 → 아래 앱으로 터치 통과.
 */
class GamepadView(context: Context) : View(context) {

    // ── 콜백 ──
    var onButtonDown: ((ButtonId) -> Unit)? = null
    var onButtonUp:   ((ButtonId) -> Unit)? = null

    enum class ButtonId {
        A, B, X, Y,
        L1, L2, R1, R2,
        START, SELECT, HOME,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        LSTICK, RSTICK
    }

    // ── 버튼 정의 ──
    data class GpButton(
        val id: ButtonId,
        var rect: RectF = RectF(),
        var isCircle: Boolean = true,
        var color: Int = 0,
        var label: String = "",
        var pressed: Boolean = false
    )

    private val buttons = listOf(
        GpButton(ButtonId.A,           color = 0xFF2E7D32.toInt(), label = "A"),
        GpButton(ButtonId.B,           color = 0xFFC62828.toInt(), label = "B"),
        GpButton(ButtonId.X,           color = 0xFF1565C0.toInt(), label = "X"),
        GpButton(ButtonId.Y,           color = 0xFFF9A825.toInt(), label = "Y"),
        GpButton(ButtonId.L1,          color = 0xFF37474F.toInt(), label = "L1", isCircle = false),
        GpButton(ButtonId.L2,          color = 0xFF263238.toInt(), label = "L2", isCircle = false),
        GpButton(ButtonId.R1,          color = 0xFF37474F.toInt(), label = "R1", isCircle = false),
        GpButton(ButtonId.R2,          color = 0xFF263238.toInt(), label = "R2", isCircle = false),
        GpButton(ButtonId.START,       color = 0xFF37474F.toInt(), label = "▶▶", isCircle = false),
        GpButton(ButtonId.SELECT,      color = 0xFF37474F.toInt(), label = "◀◀", isCircle = false),
        GpButton(ButtonId.HOME,        color = 0xFF455A64.toInt(), label = "⊙"),
        GpButton(ButtonId.DPAD_UP,     color = 0xFF37474F.toInt(), label = "▲", isCircle = false),
        GpButton(ButtonId.DPAD_DOWN,   color = 0xFF37474F.toInt(), label = "▼", isCircle = false),
        GpButton(ButtonId.DPAD_LEFT,   color = 0xFF37474F.toInt(), label = "◀", isCircle = false),
        GpButton(ButtonId.DPAD_RIGHT,  color = 0xFF37474F.toInt(), label = "▶", isCircle = false),
        GpButton(ButtonId.LSTICK,      color = 0xFF455A64.toInt(), label = ""),
        GpButton(ButtonId.RSTICK,      color = 0xFF455A64.toInt(), label = ""),
    )

    private fun btn(id: ButtonId) = buttons.first { it.id == id }

    // ── 스틱 상태 ──
    data class StickState(var cx: Float = 0f, var cy: Float = 0f,
                          var kx: Float = 0f, var ky: Float = 0f,
                          var pointerId: Int = -1)
    private val lstick = StickState()
    private val rstick = StickState()

    // ── Paint ──
    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x66FFFFFF; strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCCCCCFF.toInt()
    }

    // ── 활성 포인터 → 버튼 매핑 ──
    private val pointerMap = mutableMapOf<Int, ButtonId>()

    // ── 레이아웃 계산 ──
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutButtons(w.toFloat(), h.toFloat())
    }

    private fun layoutButtons(W: Float, H: Float) {
        val s  = minOf(W, H) * 0.001f   // 기준 스케일
        val bs = W * 0.072f              // 기본 버튼 크기
        val ss = W * 0.115f              // 어깨 버튼 너비
        val sh = H * 0.055f             // 어깨 버튼 높이
        val dp = W * 0.060f             // D-PAD 셀 크기
        val stR = W * 0.095f            // 스틱 반지름

        // 어깨 버튼
        btn(ButtonId.L1).apply { isCircle=false; rect = RectF(W*0.01f, H*0.01f, W*0.01f+ss, H*0.01f+sh) }
        btn(ButtonId.L2).apply { isCircle=false; rect = RectF(W*0.01f, H*0.01f+sh+H*0.012f, W*0.01f+ss, H*0.01f+sh*2+H*0.012f) }
        btn(ButtonId.R1).apply { isCircle=false; rect = RectF(W-W*0.01f-ss, H*0.01f, W-W*0.01f, H*0.01f+sh) }
        btn(ButtonId.R2).apply { isCircle=false; rect = RectF(W-W*0.01f-ss, H*0.01f+sh+H*0.012f, W-W*0.01f, H*0.01f+sh*2+H*0.012f) }

        // D-PAD (좌하단)
        val dCx = W * 0.175f
        val dCy = H * 0.72f
        btn(ButtonId.DPAD_UP).apply    { isCircle=false; rect = RectF(dCx-dp/2, dCy-dp*1.55f, dCx+dp/2, dCy-dp*0.55f) }
        btn(ButtonId.DPAD_DOWN).apply  { isCircle=false; rect = RectF(dCx-dp/2, dCy+dp*0.55f, dCx+dp/2, dCy+dp*1.55f) }
        btn(ButtonId.DPAD_LEFT).apply  { isCircle=false; rect = RectF(dCx-dp*1.55f, dCy-dp/2, dCx-dp*0.55f, dCy+dp/2) }
        btn(ButtonId.DPAD_RIGHT).apply { isCircle=false; rect = RectF(dCx+dp*0.55f, dCy-dp/2, dCx+dp*1.55f, dCy+dp/2) }

        // ABXY (우하단)
        val fCx = W * 0.825f
        val fCy = H * 0.72f
        val fr  = bs * 0.55f
        btn(ButtonId.Y).apply { rect = RectF(fCx-fr, fCy-bs-fr, fCx+fr, fCy-bs+fr) }
        btn(ButtonId.A).apply { rect = RectF(fCx-fr, fCy+bs-fr, fCx+fr, fCy+bs+fr) }
        btn(ButtonId.B).apply { rect = RectF(fCx+bs-fr, fCy-fr, fCx+bs+fr, fCy+fr) }
        btn(ButtonId.X).apply { rect = RectF(fCx-bs-fr, fCy-fr, fCx-bs+fr, fCy+fr) }

        // 아날로그 스틱
        val lsX = W * 0.22f; val lsY = H * 0.88f
        val rsX = W * 0.78f; val rsY = H * 0.88f
        btn(ButtonId.LSTICK).rect = RectF(lsX-stR, lsY-stR, lsX+stR, lsY+stR)
        btn(ButtonId.RSTICK).rect = RectF(rsX-stR, rsY-stR, rsX+stR, rsY+stR)
        lstick.cx=lsX; lstick.cy=lsY; lstick.kx=lsX; lstick.ky=lsY
        rstick.cx=rsX; rstick.cy=rsY; rstick.kx=rsX; rstick.ky=rsY

        // 중앙 버튼
        val midY = H * 0.92f
        val cbW  = W * 0.09f; val cbH = H * 0.04f
        btn(ButtonId.SELECT).apply { isCircle=false; rect = RectF(W/2-cbW*1.8f, midY-cbH, W/2-cbW*0.8f, midY+cbH) }
        btn(ButtonId.HOME).apply   { val r=cbH*1.1f; rect = RectF(W/2-r, midY-r, W/2+r, midY+r) }
        btn(ButtonId.START).apply  { isCircle=false; rect = RectF(W/2+cbW*0.8f, midY-cbH, W/2+cbW*1.8f, midY+cbH) }
    }

    // ── 그리기 ──
    override fun onDraw(canvas: Canvas) {
        buttons.forEach { b ->
            if (b.id == ButtonId.LSTICK || b.id == ButtonId.RSTICK) return@forEach
            val alpha = if (b.pressed) 220 else 140
            fillPaint.color = b.color; fillPaint.alpha = alpha
            textPaint.textSize = b.rect.height() * 0.38f

            if (b.isCircle) {
                val cx = b.rect.centerX(); val cy = b.rect.centerY()
                val r  = b.rect.width() / 2
                canvas.drawCircle(cx, cy, r, fillPaint)
                canvas.drawCircle(cx, cy, r, strokePaint)
            } else {
                val rr = b.rect.height() * 0.28f
                canvas.drawRoundRect(b.rect, rr, rr, fillPaint)
                canvas.drawRoundRect(b.rect, rr, rr, strokePaint)
            }
            if (b.label.isNotEmpty()) {
                canvas.drawText(b.label, b.rect.centerX(),
                    b.rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
        }

        // 스틱 그리기
        drawStick(canvas, btn(ButtonId.LSTICK), lstick)
        drawStick(canvas, btn(ButtonId.RSTICK), rstick)
    }

    private fun drawStick(canvas: Canvas, b: GpButton, st: StickState) {
        val cx = b.rect.centerX(); val cy = b.rect.centerY()
        val r  = b.rect.width() / 2
        // 외곽 링
        fillPaint.color = 0xFF263238.toInt(); fillPaint.alpha = 160
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        // 내부 격자
        strokePaint.alpha = 40
        canvas.drawLine(cx-r, cy, cx+r, cy, strokePaint)
        canvas.drawLine(cx, cy-r, cx, cy+r, strokePaint)
        strokePaint.alpha = 102
        // 노브
        knobPaint.alpha = if (st.pointerId >= 0) 230 else 180
        canvas.drawCircle(st.kx, st.ky, r * 0.44f, knobPaint)
    }

    // ── 터치 처리 ──
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var consumed = false
        val action = event.actionMasked
        val pIdx   = event.actionIndex
        val pId    = event.getPointerId(pIdx)
        val px     = event.getX(pIdx)
        val py     = event.getY(pIdx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val hit = hitTest(px, py)
                if (hit != null) {
                    consumed = true
                    pointerMap[pId] = hit.id
                    pressButton(hit, px, py, true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x  = event.getX(i); val y = event.getY(i)
                    val bid = pointerMap[id] ?: continue
                    consumed = true
                    if (bid == ButtonId.LSTICK) updateStick(lstick, x, y)
                    else if (bid == ButtonId.RSTICK) updateStick(rstick, x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val bid = pointerMap.remove(pId)
                if (bid != null) {
                    consumed = true
                    pressButton(buttons.first { it.id == bid }, px, py, false)
                    if (bid == ButtonId.LSTICK) resetStick(lstick)
                    if (bid == ButtonId.RSTICK) resetStick(rstick)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerMap.keys.toList().forEach { id ->
                    val bid = pointerMap.remove(id) ?: return@forEach
                    buttons.first { it.id == bid }.pressed = false
                    onButtonUp?.invoke(bid)
                }
                resetStick(lstick); resetStick(rstick)
                invalidate()
            }
        }
        if (consumed) invalidate()
        return consumed   // false면 아래 앱으로 터치 통과!
    }

    private fun hitTest(x: Float, y: Float): GpButton? {
        return buttons.firstOrNull { b ->
            if (b.isCircle) {
                val cx = b.rect.centerX(); val cy = b.rect.centerY()
                val r  = b.rect.width() / 2 + 12f
                (x-cx)*(x-cx) + (y-cy)*(y-cy) <= r*r
            } else {
                b.rect.contains(x - 8f, y - 8f) || // 패딩 포함
                b.rect.contains(x + 8f, y + 8f)
            }
        }
    }

    private fun pressButton(b: GpButton, x: Float, y: Float, down: Boolean) {
        b.pressed = down
        if (down) {
            haptic()
            if (b.id == ButtonId.LSTICK) { lstick.pointerId = 0; updateStick(lstick, x, y) }
            if (b.id == ButtonId.RSTICK) { rstick.pointerId = 0; updateStick(rstick, x, y) }
            onButtonDown?.invoke(b.id)
        } else {
            onButtonUp?.invoke(b.id)
        }
    }

    private fun updateStick(st: StickState, x: Float, y: Float) {
        val maxR = (if (st == lstick) btn(ButtonId.LSTICK) else btn(ButtonId.RSTICK)).rect.width() / 2 * 0.6f
        val dx = x - st.cx; val dy = y - st.cy
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist <= maxR) { st.kx = x; st.ky = y }
        else { st.kx = st.cx + dx/dist*maxR; st.ky = st.cy + dy/dist*maxR }
    }

    private fun resetStick(st: StickState) {
        st.kx = st.cx; st.ky = st.cy; st.pointerId = -1
    }

    fun getStickAxes(): FloatArray {
        fun axis(st: StickState, b: GpButton): Pair<Float,Float> {
            val maxR = b.rect.width() / 2 * 0.6f
            return Pair((st.kx - st.cx) / maxR, (st.ky - st.cy) / maxR)
        }
        val (lx,ly) = axis(lstick, btn(ButtonId.LSTICK))
        val (rx,ry) = axis(rstick, btn(ButtonId.RSTICK))
        return floatArrayOf(lx, ly, rx, ry)
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
        } catch (e: Exception) { /* 무시 */ }
    }
}
