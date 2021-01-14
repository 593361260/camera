package com.mingo.runplugin.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.mingo.runplugin.impl.OnCameraOperatorListener

class TouchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var isLongPress = false
    private var progressWidth = 0f
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        isDither = true
    }
    private var startTime = 0L
    private var gestureDetector: GestureDetector? = null
    private val maxRecordTime = 15000

    init {
        progressWidth = context.resources.displayMetrics.density * 3f
        paint.strokeWidth = progressWidth
        val gestureListener = object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent?): Boolean {
                return true
            }

            override fun onShowPress(e: MotionEvent?) {

            }

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                performClick()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent?,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                return true
            }

            override fun onLongPress(e: MotionEvent?) {
                //切换View状态
                isLongPress = true
                startTime = System.currentTimeMillis()
                listener?.onPressStart()
                postInvalidateOnAnimation()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                return true
            }
        }
        gestureDetector = GestureDetector(context, gestureListener)
        setOnClickListener {
            listener?.onClick()
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        paint.color = Color.GRAY
        paint.style = Paint.Style.FILL
        canvas?.drawCircle(
            measuredWidth / 2f,
            measuredHeight / 2f,
            if (isLongPress) (measuredWidth / 2f - progressWidth) else measuredWidth / 3f,
            paint
        )
        paint.color = Color.WHITE
        canvas?.drawCircle(
            measuredWidth / 2f,
            measuredHeight / 2f,
            if (isLongPress) measuredWidth / 6f else measuredWidth / 4f,
            paint
        )
        //边缘绘制 进度条
        if (isLongPress) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.GREEN
            val percent = (System.currentTimeMillis() - startTime) * 1f / maxRecordTime
            val endAngle = 360 * percent
            canvas?.drawArc(
                progressWidth,
                progressWidth,
                measuredWidth - progressWidth,
                measuredHeight - progressWidth,
                -90f,
                endAngle,
                false,
                paint
            )
            postInvalidateOnAnimation()
        }
    }

    fun updateFinish() {
        isLongPress = false
        postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                if (isLongPress) {
                    listener?.onFinish(0)
                }
                isLongPress = false
                postInvalidateOnAnimation()
            }
        }
        return true
    }

    private var listener: OnCameraOperatorListener? = null

    fun setOnCameraListener(listener: OnCameraOperatorListener) {
        this.listener = listener
    }

}