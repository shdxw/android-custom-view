package com.example.android.customfancontroller

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayList
import kotlin.math.*
import kotlin.math.sqrt


private enum class FanSpeed(val label: Int) {
    OFF(R.string.fan_off),
    LOW(R.string.fan_low),
    MEDIUM(R.string.fan_medium),
    HIGH(R.string.fan_high);

    fun next() = when (this) {
        OFF -> LOW
        LOW -> MEDIUM
        MEDIUM -> HIGH
        HIGH -> OFF
    }

    fun get(num: Int): FanSpeed = when (num) {
        0 -> OFF
        1 -> LOW
        2 -> MEDIUM
        3 -> HIGH
        else -> OFF
    }
}

private const val RADIUS_OFFSET_LABEL = 30
private const val RADIUS_OFFSET_INDICATOR = -35

class DialView : View {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 55.0f
        typeface = Typeface.create("", Typeface.BOLD)
    }

    private var radius = 0.0f
    private var fanSpeed = FanSpeed.OFF
    private val pointPosition: PointF = PointF(0.0f, 0.0f)

    private var fanSpeedLowColor: Int = 0
    private var fanSpeedMediumColor: Int = 0
    private var fanSpeedMaxColor: Int = 0
    private var fanPoints: ArrayList<PointF> = ArrayList()

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        if (!isInEditMode()) {
            setOnTouchListener(OnTouchListener(fun(v: View, event: MotionEvent): Boolean {
                computePoints()
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    computeChoice(x, y)
                }
                return true
            }))
            isSaveEnabled = true
            isClickable = true
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DialView)
            fanSpeedLowColor = typedArray.getColor(R.styleable.DialView_fanColor1, 0)
            fanSpeedMediumColor = typedArray.getColor(R.styleable.DialView_fanColor2, 0)
            fanSpeedMaxColor = typedArray.getColor(R.styleable.DialView_fanColor3, 0)
            typedArray.recycle()
            updateContentDescription()
            ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    val customClick = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfo.ACTION_CLICK,
                        context.getString(if (fanSpeed != FanSpeed.HIGH) R.string.change else R.string.reset)
                    )
                    info.addAction(customClick)
                }
            })
        }

    }

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putString("speed", fanSpeed.name)
        bundle.putParcelable("superState", super.onSaveInstanceState())
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        var viewState = state
        if (viewState is Bundle) {
            fanSpeed = FanSpeed.valueOf(viewState.getString("speed", "off"))
            viewState = viewState.getParcelable("superState")!!
        }
        super.onRestoreInstanceState(viewState)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        radius = (min(width, height) / 2.0 * 0.8).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // цвет бэкграунда
        paint.color = when (fanSpeed) {
            FanSpeed.OFF -> Color.GRAY
            FanSpeed.LOW -> fanSpeedLowColor
            FanSpeed.MEDIUM -> fanSpeedMediumColor
            FanSpeed.HIGH -> fanSpeedMaxColor
        }
        canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radius, paint)
        // отрисовка кружочка
        val markerRadius = radius + RADIUS_OFFSET_INDICATOR
        pointPosition.computeXYForSpeed(fanSpeed, markerRadius)
        paint.color = Color.BLACK
        canvas.drawCircle(pointPosition.x, pointPosition.y, radius / 12, paint)
        // отрисовка лейбла
        val labelRadius = radius + RADIUS_OFFSET_LABEL
        for (i in FanSpeed.values()) {
            pointPosition.computeXYForSpeed(i, labelRadius)
            val label = resources.getString(i.label)
            canvas.drawText(label, pointPosition.x, pointPosition.y, paint)
        }
    }

    private fun PointF.computeXYForSpeed(pos: FanSpeed, radius: Float) {
        val startAngle = Math.PI * (9 / 8.0)
        val angle = startAngle + pos.ordinal * (Math.PI / 4)
        x = (radius * cos(angle)).toFloat() + width / 2
        y = (radius * sin(angle)).toFloat() + height / 2
    }

    private fun computePoints() {
        val startAngle = Math.PI * (9 / 8.0)
        fanPoints.clear()
        for (i in 0..3) {
            val angle = startAngle + i * (Math.PI / 4)
            var point: PointF = PointF(
                (radius * cos(angle)).toFloat() + width / 2,
                (radius * sin(angle)).toFloat() + height / 2
            )
            Log.d("", point.toString())
            fanPoints.add(point)
            Log.d("", point.toString())
        }
        Log.d("", fanPoints.toString())
    }

    private fun updateContentDescription() {
        contentDescription = resources.getString(fanSpeed.label)
    }

    private fun computeChoice(x: Int, y: Int): Unit {
        Log.d("", fanPoints.toString())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            var point: PointF = fanPoints.stream().min({ a, b ->
                var f1: Float = sqrt(((x - a.x).pow(2)) + ((y - a.y).pow(2)))
                var f2: Float = sqrt(((x - b.x).pow(2)) + ((y - b.y).pow(2)))
                f1.compareTo(f2)
            }).get()
            Log.d("", point.toString())
            Log.d("", fanPoints.toString())
            fanSpeed = fanSpeed.get(fanPoints.indexOf(point))
        }

        updateContentDescription()
        // перерисовываем
        invalidate()
    }
}