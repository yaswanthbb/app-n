package com.machine.newsapp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.core.content.edit
import com.machine.newsapp.NewsApplication
import com.machine.newsapp.data.FeedKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

class DailyDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            val prefs = applicationContext.getSharedPreferences("digest", Context.MODE_PRIVATE)
            val today = LocalDate.now().toString()
            if (prefs.getString("last_delivered_date", null) == today) return Result.success()
            val repository = (applicationContext as NewsApplication).container.repository
            // No network constraint: an offline phone should still get its saved
            // briefing. Bound refresh time, then use Room regardless of failures.
            withTimeoutOrNull(120_000) {
                coroutineScope { FeedKind.entries.map { async { repository.refresh(it) } }.awaitAll() }
            }
            val deals = repository.deals().first().items
            val picks = repository.picks().first().items
            val news = repository.news(com.machine.newsapp.data.NewsFilter.ALL).first().items
            val seenDeals = prefs.getStringSet("seen_deals", emptySet()).orEmpty()
            val seenPicks = prefs.getStringSet("seen_picks", emptySet()).orEmpty()
            val newDeals = deals.count { it.url !in seenDeals }
            val newPicks = picks.take(1).count { it.date !in seenPicks }
            val counts = "$newDeals new ${if (newDeals == 1) "deal" else "deals"} · $newPicks new ${if (newPicks == 1) "pick" else "picks"}"
            val headlines = news.take(2).joinToString(" • ") { it.title }
            val body = "$counts\n${headlines.ifBlank { "Open News App for your daily briefing." }}"
            if (Notifications.show(applicationContext, "Your morning briefing", body, "news", 900, digest = true)) {
                // This runs on a worker thread. Persist synchronously before the
                // process can stop, avoiding another digest after a restart.
                prefs.edit(commit = true) {
                    putStringSet("seen_deals", deals.map { it.url }.toSet())
                    putStringSet("seen_picks", picks.map { it.date }.toSet())
                    putString("last_delivered_date", today)
                }
            }
            return Result.success()
        } finally {
            DailyScheduler.schedule(applicationContext)
        }
    }
}
