package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** In-app fixture runner. No Wi-Fi manager, network, probe or real pause calls. */
internal class FailureLabPanel(private val context: Context, private val openLiveControls: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val white = Color.rgb(246,248,255)
    private val muted = Color.rgb(175,189,209)
    private val amber = Color.rgb(250,194,100)
    private val teal = Color.rgb(117,234,213)
    private val renderer = CheckReportRenderer(context)
    private var generation = 0
    private var active = false
    private var running = false
    private var lastReport: ConnectionCheckReport? = null
    private val viewRoot = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val view: View get() = viewRoot
    private lateinit var choice: Spinner
    private lateinit var pauseOption: CheckBox
    private lateinit var chart: SignalChartView
    private lateinit var progress: ProgressBar
    private lateinit var resultTitle: TextView
    private lateinit var response: TextView
    private lateinit var completed: TextView
    private lateinit var rows: LinearLayout
    private lateinit var runOne: Button
    private lateinit var runAll: Button
    private val history = mutableListOf<String>()
    private lateinit var historyView: TextView
    private var completedCount = 0
    private var demonstratedPauses = 0
    init { build() }
    private fun dp(n: Int) = (context.resources.displayMetrics.density*n+0.5f).toInt()
    private fun label(s: String, size: Float = 14f, color: Int = white, bold: Boolean = false) = TextView(context).apply {
        text=s; textSize=size; setTextColor(color); setLineSpacing(dp(3).toFloat(),1f)
        if(bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun add(parent: LinearLayout, child: View, top: Int = 10) {
        parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top) })
    }
    private fun button(s: String, action: () -> Unit) = Button(context).apply {
        text=s; isAllCaps=false; textSize=13f; setTextColor(white); minHeight=dp(48)
        backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(33,45,65))
        setOnClickListener { action() }
    }
    private fun card(title: String, fill: (LinearLayout) -> Unit) {
        val box=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14))
            background=GradientDrawable().apply { setColor(Color.rgb(23,32,49)); cornerRadius=dp(16).toFloat() }
        }
        add(box,label(title,12f,amber,true),0); fill(box); add(viewRoot,box,14)
    }
    private fun build() {
        add(viewRoot,label("Failure lab",24f,white,true),18)
        add(viewRoot,label("SIMULATED • 20 scenarios",13f,amber,true),5)
        add(viewRoot,label("Run a case or the complete suite. Every input and response below is invented. Your real Wi-Fi stays unchanged.",13f,muted),8)
        card("CHOOSE A TEACHING CASE") { box ->
            choice=Spinner(context).apply {
                adapter=object:ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,
                    FailureScenarios.all.map { "${it.category} · ${it.title}" }) {
                    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View =
                        super.getView(position,convertView,parent).apply { (this as? TextView)?.setTextColor(white) }
                    override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View =
                        super.getDropDownView(position,convertView,parent).apply {
                            (this as? TextView)?.apply { setTextColor(white); setPadding(dp(12),dp(14),dp(12),dp(14)) }
                        }
                }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                minimumHeight=dp(48); setPopupBackgroundDrawable(ColorDrawable(Color.rgb(33,45,65))); contentDescription="Choose one simulated failure scenario"
            }; add(box,choice)
            pauseOption=CheckBox(context).apply {
                text="Simulate automatic pause for eligible failed checks"; textSize=13f; setTextColor(white); isChecked=true
                contentDescription="Simulator policy only; cannot disconnect the real phone"
            }; add(box,pauseOption)
            runOne=button("Run selected case") { start(listOf(FailureScenarios.all[choice.selectedItemPosition])) }
            runAll=button("Run all 20 scenarios") { start(FailureScenarios.all) }
            add(box,runOne); add(box,runAll)
            add(box,button("Stop / reset simulation") {
                stop("SIMULATED: stopped by the user. No new result is marked complete.")
                lastReport=null; rows.removeAllViews(); chart.submit(emptyList()); chart.visibility=View.GONE; resultTitle.text="Simulation reset. Choose a case."; progress.progress=0; completedCount=0; demonstratedPauses=0
                completed.text="No completed cases in this run."
                history.clear(); historyView.text="No completed simulated cases."
            })
        }
        card("SIMULATED TEST REPORT") { box ->
            resultTitle=label("Choose a case, then press Run.",17f,white,true); add(box,resultTitle)
            progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply { max=12; progress=0 }
            add(box,progress)
            chart=SignalChartView(context, "Waiting for simulated readings").apply { visibility=View.GONE }
            box.addView(chart,LinearLayout.LayoutParams(-1,dp(155)).apply { topMargin=dp(8) })
            response=label("No simulation run yet. No settings changed.",13f,amber); add(box,response)
            completed=label("No completed cases in this run.",12f,muted); add(box,completed)
            historyView=label("No completed simulated cases.",12f,muted).apply { visibility=View.GONE }
            add(box,button("Show / hide simulated run summary") { historyView.visibility=if(historyView.visibility==View.VISIBLE) View.GONE else View.VISIBLE })
            add(box,historyView)
            rows=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }; add(box,rows)
            add(box,button("Copy last completed simulated report") {
                lastReport?.let {
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SIMULATED Wi-Fi report",it.asText()))
                    Toast.makeText(context,"Copied with SIMULATED label",Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(context,"Complete a simulated case first",Toast.LENGTH_SHORT).show()
            })
        }
        card("REAL PHONE RESPONSE — SEPARATE CONTROLS") { box ->
            add(box,label("The simulator never changes your connection. Live pause controls use actual readings and must be explicitly armed. Android 10+ requires completion in system settings.",13f,muted))
            add(box,button("Open live Wi-Fi pause controls") { stop("Simulation stopped before opening live controls."); openLiveControls() })
            add(box,label("Failed connectivity checks are not proof of an attack. This app does not promise to disconnect for every possible vulnerability or protect data carried by mobile networks.",12f,muted))
        }
    }
    fun resume() { active=true }
    fun pause() { active=false; if(running) stop("SIMULATED: paused because the Demo tab is no longer active. Run again to restart.") }
    fun close() { pause(); handler.removeCallbacksAndMessages(null) }
    private fun stop(message:String) {
        generation++; handler.removeCallbacksAndMessages(null); running=false
        runOne.isEnabled=true; runAll.isEnabled=true; choice.isEnabled=true; pauseOption.isEnabled=true
        response.text=message
    }
    private fun start(queue:List<FailureScenario>) {
        if(!active) return
        stop("Preparing simulated cases...")
        running=true; runOne.isEnabled=false; runAll.isEnabled=false; choice.isEnabled=false; pauseOption.isEnabled=false
        completedCount=0; demonstratedPauses=0; lastReport=null; history.clear(); historyView.text="No completed simulated cases."
        val simulatePause=pauseOption.isChecked
        val token=generation
        fun play(index:Int) {
            if(!active || token!=generation || index>=queue.size) return
            val scenario=queue[index]
            resultTitle.text="SIMULATED · ${index+1}/${queue.size}\n${scenario.title}"
            response.text="${scenario.why}\n\n${scenario.advice}"
            completed.text="Completed $completedCount/${queue.size} cases · $demonstratedPauses virtual pauses"
            progress.progress=0
            chart.visibility=if(scenario.samples.isEmpty()) View.GONE else View.VISIBLE
            chart.submit(emptyList())
            var count=0
            val step=object:Runnable {
                override fun run() {
                    if(!active || token!=generation) return
                    count++
                    progress.progress=count
                    chart.submit(scenario.samples.take(count).mapIndexed { i,value -> ConnectionAnalytics.Sample(i*2000L,value) })
                    chart.contentDescription="SIMULATED signal chart; invented values for ${scenario.title}"
                    val visible=scenario.checks.mapIndexed { i,row ->
                        if(i<count) row else row.copy(state=CheckState.PENDING,value="Waiting for simulated step")
                    }
                    renderer.render(rows,ConnectionCheckReport(ReportOrigin.SIMULATED,visible,"Invented fixture; no live measurements or radio changes."))
                    if(count<scenario.checks.size) handler.postDelayed(this,180)
                    else {
                        val outcome=FailureScenarios.outcome(scenario,simulatePause)
                        lastReport=outcome.report; completedCount++
                        if(outcome.virtualPaused) demonstratedPauses++
                        history += "SIMULATED ${completedCount}. ${scenario.title}: " + if(outcome.virtualPaused) "virtual pause" else "baseline/advice/skip"
                        historyView.text=history.joinToString("\n\n")
                        response.text="${outcome.response}\n\n${scenario.why}\n\n${scenario.advice}"
                        response.setTextColor(if(outcome.virtualPaused) amber else teal)
                        completed.text="Completed $completedCount/${queue.size} cases · $demonstratedPauses virtual pauses"
                        if(index+1<queue.size) handler.postDelayed({ play(index+1) },700)
                        else { running=false; runOne.isEnabled=true; runAll.isEnabled=true; choice.isEnabled=true; pauseOption.isEnabled=true }
                    }
                }
            }
            handler.post(step)
        }
        play(0)
    }
}
