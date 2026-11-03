package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared presentation for live and explicitly simulated check reports. */
internal class CheckReportRenderer(private val context: Context) {
    private val expanded = mutableSetOf<String>()
    private var previous: ConnectionCheckReport? = null
    private fun dp(n: Int) = (context.resources.displayMetrics.density * n + 0.5f).toInt()
    fun render(parent: LinearLayout, report: ConnectionCheckReport) {
        if (previous == report && parent.childCount > 0) return
        previous = report
        parent.removeAllViews()
        report.items.forEach { item ->
            val color = when (item.state) {
                CheckState.PASS -> Color.rgb(117, 234, 213)
                CheckState.FAILED -> Color.rgb(255, 154, 161)
                CheckState.WARNING -> Color.rgb(250, 194, 100)
                else -> Color.rgb(175, 189, 209)
            }
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply { setColor(Color.rgb(33, 45, 65)); cornerRadius = dp(12).toFloat() }
            }
            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            val title = TextView(context).apply { text = item.title; textSize = 13f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) }
            val badge = TextView(context).apply { text = item.state.label; textSize = 10f; setTextColor(color); gravity = Gravity.END; setTypeface(typeface, Typeface.BOLD) }
            header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(badge, LinearLayout.LayoutParams(dp(84), -2))
            box.addView(header)
            box.addView(TextView(context).apply { text = item.value; textSize = 13f; setTextColor(color) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            val detail = TextView(context).apply {
                text = item.detail; textSize = 12f; setTextColor(Color.rgb(175, 189, 209)); setLineSpacing(dp(3).toFloat(), 1f)
                visibility = if (item.id in expanded) View.VISIBLE else View.GONE
            }
            box.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            box.isFocusable = true
            box.contentDescription = "${report.origin.label}. ${item.title}. ${item.state.label}. ${item.value}. Tap for explanation."
            box.setOnClickListener {
                if (item.id in expanded) expanded.remove(item.id) else expanded.add(item.id)
                detail.visibility = if (item.id in expanded) View.VISIBLE else View.GONE
            }
            parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }
}
