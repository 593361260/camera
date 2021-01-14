package com.mingo.runplugin.widget.paintView

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class MagnifierView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var bitmapShader: BitmapShader? = null
    private var view: View? = null
    private val matrix1 = Matrix()
    private var centerX = 0f
    private var centerY = 0f
    private val paint = Paint().apply {
        isFilterBitmap = true
    }

    override fun onDraw(canvas: Canvas) {
        matrix1.reset()
        matrix1.postScale(1.5f, 1.5f)
        matrix1.postTranslate(-(1.5f * x + (scaleX - 1) * x / 2), -(scaleY * y + (1.5f - 1) * x / 2))
        bitmapShader?.setLocalMatrix(matrix1)
        paint.shader = bitmapShader
        canvas.drawCircle(centerX - 100, centerY - 100, 100f, paint)
    }

    fun attachView(view: View) {
        this.view = view
    }

    fun onMove(x: Float, y: Float) {
        centerX = x
        centerY = y
        getBitmap()
        invalidate()
    }

    private fun getBitmap(): Bitmap? {
        if (view == null)
            return null
        view!!.buildDrawingCache()
        val bitmap = Bitmap.createBitmap(view!!.drawingCache)
        bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        view!!.destroyDrawingCache()
        return bitmap
    }

}