package com.buildai.app

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Do not operate a previous activity's semantics during an Android window transition. */
internal object UiTestWindows {
    fun awaitFocus(type: Class<out Activity>? = null, allowDialog: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            var owner = false
            instrumentation.runOnMainSync {
                val activities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filter { !it.isFinishing && !it.isDestroyed && (type == null || type.isInstance(it)) }
                owner = activities.isNotEmpty()
                ready = activities.any { it.window.decorView.hasWindowFocus() && !it.window.decorView.isLayoutRequested }
            }
            val dialog = allowDialog && owner && instrumentation.uiAutomation.rootInActiveWindow?.packageName == instrumentation.targetContext.packageName
            if (ready || dialog) return
            SystemClock.sleep(25)
        }
        error("The expected app window did not gain focus: ${type?.simpleName ?: "any activity"}")
    }
}
