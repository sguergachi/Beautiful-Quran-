package com.beautifulquran

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.function.Consumer

/** Android 17 profiling tools compiled into debug builds only. */
object DevProfiling {

    private const val Tag = "BeautifulQuranProfile"

    fun install(application: Application) {
        if (Build.VERSION.SDK_INT < 37) return
        Api37.install(application)
    }

    fun reportFullyDrawn(activity: Activity) {
        if (Build.VERSION.SDK_INT < 37) return
        Api37.reportFullyDrawn(activity)
    }

    fun recordSystemTrace(context: Context) {
        if (Build.VERSION.SDK_INT < 37) {
            Log.w(Tag, "ProfilingManager development traces require Android 17")
            return
        }
        Api37.recordSystemTrace(context)
    }

    @RequiresApi(37)
    private object Api37 {
        private const val TraceDurationMs = 10_000L

        private val resultListener = Consumer<ProfilingResult> { result ->
            if (result.errorCode == ProfilingResult.ERROR_NONE) {
                Log.i(
                    Tag,
                    "Profile ready: type=${result.triggerType}, tag=${result.tag}, " +
                        "file=${result.resultFilePath}",
                )
            } else {
                Log.e(
                    Tag,
                    "Profiling failed: code=${result.errorCode}, message=${result.errorMessage}",
                )
            }
        }

        // Android 17 requires every explicit request to supply a callback even
        // when the process-wide listener above owns result reporting.
        private val requestListener = Consumer<ProfilingResult> { }

        fun install(application: Application) {
            val manager = application.getSystemService(ProfilingManager::class.java)
            manager.registerForAllProfilingResults(application.mainExecutor, resultListener)
            manager.addProfilingTriggers(
                listOf(
                    ProfilingTrigger.TRIGGER_TYPE_COLD_START,
                    ProfilingTrigger.TRIGGER_TYPE_APP_FULLY_DRAWN,
                    ProfilingTrigger.TRIGGER_TYPE_ANR,
                    ProfilingTrigger.TRIGGER_TYPE_OOM,
                    ProfilingTrigger.TRIGGER_TYPE_KILL_EXCESSIVE_CPU_USAGE,
                ).map { type ->
                    ProfilingTrigger.Builder(type)
                        .setRateLimitingPeriodHours(1)
                        .build()
                },
            )
            Log.i(Tag, "Android 17 development profiling triggers registered")
        }

        fun reportFullyDrawn(activity: Activity) {
            activity.reportFullyDrawn()
        }

        fun recordSystemTrace(context: Context) {
            val manager = context.getSystemService(ProfilingManager::class.java)
            val cancellation = CancellationSignal()
            try {
                manager.requestProfiling(
                    ProfilingManager.PROFILING_TYPE_SYSTEM_TRACE,
                    null,
                    "manual-system-trace",
                    cancellation,
                    context.mainExecutor,
                    requestListener,
                )
            } catch (error: RuntimeException) {
                Log.e(Tag, "Unable to start system trace", error)
                return
            }
            Handler(Looper.getMainLooper()).postDelayed(cancellation::cancel, TraceDurationMs)
            Log.i(Tag, "Recording a 10-second system trace")
        }
    }
}
