package com.machine.newsapp.data

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class NewsFilter(val label: String) { ALL("All"), TECH("Tech"), AI("AI"), INDIA("India"), WORLD("World") }
enum class FeedKind(val route: String) { NEWS("news"), DEALS("deals"), PICKS("picks") }

@Serializable
data class NewsResponse(val articles: List<Article> = emptyList())
@Serializable
data class Article(
    val title: String,
    val description: String? = null,
    val url: String,
    val image: String? = null,
    val publishedAt: String,
    val source: NewsSource = NewsSource(),
)
@Serializable
data class NewsSource(val name: String = "Unknown source", val url: String? = null)
@Serializable
data class Deal(
    val title: String,
    val description: String,
    val url: String,
    val source: String,
    val tag: String,
    val expires: String? = null,
)
@Serializable
data class Pick(val title: String, val body: String, val url: String, val date: String)

val softwareTags = setOf("AI", "Dev tools", "SaaS", "Software", "Cloud", "Data", "Security", "Design", "Learning")
private val phoneOffer = Regex("\\b(telco|telecom|cellular|prepaid|postpaid|recharge|smartphone|iphone|handset)\\b|\\b(sim card|phone plan|mobile plan|data plan)\\b", RegexOption.IGNORE_CASE)

fun isWebUrl(value: String, httpsOnly: Boolean = false): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme?.lowercase() in (if (httpsOnly) setOf("https") else setOf("http", "https")) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

fun Deal.isSoftwareOffer(): Boolean = tag in softwareTags && !phoneOffer.containsMatchIn("$title $description")
fun Deal.isActive(today: LocalDate = LocalDate.now()): Boolean =
    expires == null || runCatching { !LocalDate.parse(expires).isBefore(today) }.getOrDefault(false)

fun timeAgo(value: String, now: Instant = Instant.now()): String = runCatching {
    val minutes = Duration.between(Instant.parse(value), now).toMinutes().coerceAtLeast(0)
    when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}.getOrDefault("Recently")
