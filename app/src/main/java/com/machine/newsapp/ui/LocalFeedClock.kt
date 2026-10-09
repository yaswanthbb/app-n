package com.machine.newsapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LocalFeedClock(val instant: Instant, val zone: ZoneId) {
    val today: LocalDate get() = instant.atZone(zone).toLocalDate()
}

@Composable
fun rememberLocalFeedClock(): State<LocalFeedClock> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalFeedClock(Instant.now(), ZoneId.systemDefault()), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                value = LocalFeedClock(now, zone)
                val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
                delay(minOf(60_000L, Duration.between(now, midnight).toMillis().coerceAtLeast(1)))
            }
        }
    }
}
