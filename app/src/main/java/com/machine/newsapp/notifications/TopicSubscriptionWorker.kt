package com.machine.newsapp.notifications

import android.content.Context
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.machine.newsapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TopicSubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FCM_CONFIGURED) return Result.success()
        return try {
            FirebaseApp.initializeApp(applicationContext) ?: return Result.retry()
            for (topic in listOf("deals", "picks")) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    FirebaseMessaging.getInstance().subscribeToTopic(topic)
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) {
            if (!BuildConfig.FCM_CONFIGURED) return
            val request = OneTimeWorkRequestBuilder<TopicSubscriptionWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("subscribe-content-topics", ExistingWorkPolicy.KEEP, request)
        }
    }
}
