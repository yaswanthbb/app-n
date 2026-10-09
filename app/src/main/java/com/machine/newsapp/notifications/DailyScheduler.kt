package com.machine.newsapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun nextMorning(now: ZonedDateTime): ZonedDateTime {
    val morning = now.toLocalDate().atTime(9, 0).atZone(now.zone)
    return if (morning.isAfter(now)) morning else now.toLocalDate().plusDays(1).atTime(9, 0).atZone(now.zone)
}

object DailyScheduler {
    const val TAG = "morning-digest"
    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        val next = nextMorning(now)
        val request = OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG).build()
        // One work item per local date. It re-arms at the next local 09:00 after
        // execution, instead of drifting like a fixed 24-hour periodic job.
        WorkManager.getInstance(context).enqueueUniqueWork("digest-${next.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val operation = WorkManager.getInstance(context).cancelAllWorkByTag(DailyScheduler.TAG)
                suspendCancellableCoroutine<Unit> { continuation ->
                    operation.result.addListener({
                        try { operation.result.get(); if (continuation.isActive) continuation.resume(Unit) }
                        catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    }, { runnable -> runnable.run() })
                }
                DailyScheduler.schedule(context)
            } finally { result.finish() }
        }
    }
}
