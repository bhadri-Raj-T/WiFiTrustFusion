package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Small native chart: elapsed time on X, polled RSSI (dBm) on Y. No chart dependency. */
internal class SignalChartView(context: Context, private val waitingLabel: String = "Waiting for live signal readings") : View(context) {
    private val density = resources.displayMetrics.density
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var readings = emptyList<ConnectionAnalytics.Sample>()

    fun submit(samples: List<ConnectionAnalytics.Sample>) {
        readings = samples.filter { ConnectionAnalytics.validRssi(it.rssiDbm) }
        val s = ConnectionAnalytics.summarise(readings)
        contentDescription = if (s.count == 0) "Signal chart. No valid readings yet."
            else "Signal chart. ${s.count} readings over ${s.durationSeconds} seconds. Minimum ${s.minimumDbm}, maximum ${s.maximumDbm} dBm."
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 43f * density
        val right = width - 12f * density
        val top = 19f * density
        val bottom = height - 30f * density
        if (right <= left || bottom <= top) return
        val low = if (readings.isEmpty()) -100.0 else
            (floor((readings.minOf { it.rssiDbm } - 5) / 10.0) * 10).coerceAtLeast(-130.0)
        val high = if (readings.isEmpty()) -40.0 else
            (ceil((readings.maxOf { it.rssiDbm } + 5) / 10.0) * 10).coerceAtMost(0.0)
        val span = (high - low).coerceAtLeast(10.0)
        pen.textSize = 11f * resources.displayMetrics.scaledDensity
        pen.typeface = android.graphics.Typeface.DEFAULT
        pen.strokeWidth = density
        pen.style = Paint.Style.FILL
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            pen.color = Color.rgb(56, 64, 82)
            canvas.drawLine(left, y, right, y, pen)
            pen.color = Color.rgb(170, 183, 201)
            canvas.drawText(String.format(Locale.US, "%.0f", high - span * i / 3), 0f, y + 4f * density, pen)
        }
        pen.color = Color.rgb(170, 183, 201)
        canvas.drawText("dBm", 0f, 10f * density, pen)
        if (readings.isEmpty()) {
            canvas.drawText(waitingLabel, left + 5f * density, (top + bottom) / 2f, pen)
            return
        }
        val start = readings.first().elapsedMs
        val duration = (readings.last().elapsedMs - start).coerceAtLeast(2000)
        fun x(s: ConnectionAnalytics.Sample) = left +
            ((s.elapsedMs - start).toFloat() / duration) * (right - left)
        fun y(s: ConnectionAnalytics.Sample) = bottom -
            ((s.rssiDbm - low) / span * (bottom - top)).toFloat()
        canvas.drawText("0s", left, bottom + 20f * density, pen)
        val end = "${(readings.last().elapsedMs - start) / 1000}s"
        canvas.drawText(end, right - pen.measureText(end), bottom + 20f * density, pen)
        pen.color = Color.rgb(117, 234, 213)
        pen.style = Paint.Style.STROKE
        pen.strokeWidth = 2.5f * density
        val path = Path()
        readings.forEachIndexed { i, s -> if (i == 0) path.moveTo(x(s), y(s)) else path.lineTo(x(s), y(s)) }
        canvas.drawPath(path, pen)
        pen.style = Paint.Style.FILL
        val latest = readings.last()
        canvas.drawCircle(x(latest), y(latest), 4f * density, pen)
    }
}
