package com.example.coupleapp.widget.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.coupleapp.widget.LocationWidgetProvider
import com.example.coupleapp.widget.LocketWidgetProvider
import com.example.coupleapp.widget.MissingWidgetProvider
import com.example.coupleapp.widget.SleepWidgetProvider
import com.example.coupleapp.widget.diagnostics.WidgetDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug receiver for testing widget functionality
 * Send broadcast: adb shell am broadcast -a com.example.coupleapp.DEBUG_WIDGETS
 */
class WidgetDebugReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WidgetDebugReceiver"
        const val ACTION_DEBUG_WIDGETS = "com.example.coupleapp.DEBUG_WIDGETS"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DEBUG_WIDGETS -> {
                Log.d(TAG, "Debug widgets broadcast received")
                CoroutineScope(Dispatchers.Main).launch {
                    runWidgetDiagnostics(context)
                }
            }
        }
    }
    
    private suspend fun runWidgetDiagnostics(context: Context) {
        try {
            Toast.makeText(context, "🔧 Chạy kiểm tra widget...", Toast.LENGTH_SHORT).show()
            
            // Run health check
            val report = WidgetDiagnostics.runHealthCheck(context)
            
            val message = if (report.hasAnyIssues()) {
                "❌ Widget issues found: ${report.getMainIssue()}"
            } else {
                "✅ All widgets healthy"
            }
            
            Log.d(TAG, "Widget diagnostics: $message")
            Log.d(TAG, "Full report: $report")
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            
            // Force update all widgets
            SleepWidgetProvider.updateWidgets(context)
            LocationWidgetProvider.updateWidgets(context)
            MissingWidgetProvider.updateWidgets(context)
            LocketWidgetProvider.updateWidgets(context)
            
            Toast.makeText(context, "🔄 Widgets refreshed", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during widget diagnostics", e)
            Toast.makeText(context, "❌ Lỗi kiểm tra: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}