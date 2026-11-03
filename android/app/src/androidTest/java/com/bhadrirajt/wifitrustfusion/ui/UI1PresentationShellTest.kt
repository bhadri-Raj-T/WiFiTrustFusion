package com.bhadrirajt.wifitrustfusion.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.MainActivity
import com.bhadrirajt.wifitrustfusion.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UI1PresentationShellTest {
    @Test fun overviewIsDefault() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pageOverview).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.pageScan).visibility)
            }
        }
    }

    @Test fun scanTabShowsOriginalControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.tabScan).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pageScan).visibility)
                assertTrue(activity.findViewById<View>(R.id.buttonStartSession).isShown)
            }
        }
    }

    @Test fun historyTabShowsReadOnlyControl() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.tabHistory).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pageHistory).visibility)
                assertTrue(activity.findViewById<View>(R.id.buttonLoadSessionHistory).isShown)
            }
        }
    }

    @Test fun detailsTabRetainsTechnicalEvidence() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.tabDetails).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pageDetails).visibility)
                assertTrue(activity.findViewById<View>(R.id.textReviewDashboard).isShown)
                assertTrue(activity.findViewById<View>(R.id.textAssessmentQuality).isShown)
            }
        }
    }
}
