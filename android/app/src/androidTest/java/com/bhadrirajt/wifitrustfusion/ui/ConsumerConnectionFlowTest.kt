package com.bhadrirajt.wifitrustfusion.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.wifi.explorer.WifiExplorerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumerConnectionFlowTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()

    @Test fun launcherOpensConsumerInterface() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertEquals(WifiExplorerActivity::class.java.name, intent?.component?.className)
    }

    @Test fun homeOffersPhoneSelectionWithoutManualNetworkNameEntry() {
        ActivityScenario.launch(WifiExplorerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val views = descendants(activity.findViewById<View>(android.R.id.content))
                assertFalse(views.any { it is android.widget.EditText })
                val buttons = views.filterIsInstance<Button>().map { it.text.toString() }
                assertTrue(buttons.contains("Analyse connected Wi-Fi"))
                assertTrue(buttons.contains("Choose Wi-Fi"))
            }
        }
    }

    @Test fun analysisCanOpenWithoutStartingCollectorOrNetworkChecks() {
        ActivityScenario.launch(WifiExplorerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                descendants(root).filterIsInstance<Button>().first { it.text.toString() == "Analysis" }.performClick()
                val text = descendants(root).filterIsInstance<TextView>().joinToString("\n") { it.text }
                assertTrue(text.contains("How is your Wi-Fi?"))
                assertTrue(text.contains("Not tested yet."))
                assertTrue(text.contains("SIGNAL HISTORY"))
            }
        }
    }

    @Test fun dedicatedDemoTabLabelsFixturesAndSeparatesRealControls() {
        ActivityScenario.launch(WifiExplorerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                descendants(root).filterIsInstance<Button>().first { it.text.toString() == "Demo" }.performClick()
                val text = descendants(root).filterIsInstance<TextView>().joinToString("\n") { it.text }
                assertTrue(text.contains("SIMULATED"))
                assertTrue(text.contains("20 scenarios"))
                assertTrue(text.contains("real Wi-Fi stays unchanged"))
                assertTrue(descendants(root).filterIsInstance<Button>().any { it.text.toString() == "Run all 20 scenarios" })
                assertFalse(descendants(root).filterIsInstance<Button>().any { it.text.toString() == "Pause Wi-Fi on this phone now" })
            }
        }
    }
}
