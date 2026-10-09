package com.machine.newsapp.data

import com.machine.newsapp.BuildConfig
import com.machine.newsapp.FeedConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class RefreshStatus(val refreshing: Boolean = false, val error: String? = null)
data class CachedFeed<T>(val items: List<T> = emptyList(), val fetchedAt: Long? = null)

class FeedRepository(
    private val dao: FeedDao,
    private val api: FeedApi,
    private val json: Json,
    private val newsKey: String = BuildConfig.NEWS_API_KEY,
    private val dealsUrl: String = FeedConfig.DEALS_FEED_URL,
    private val picksUrl: String = FeedConfig.PICKS_FEED_URL,
    private val deleteApi: DeleteApi? = null,
) {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val _statuses = MutableStateFlow<Map<String, RefreshStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, RefreshStatus>> = _statuses
    fun key(kind: FeedKind, filter: NewsFilter = NewsFilter.ALL): String =
        if (kind == FeedKind.NEWS) "news:${filter.name}" else kind.route

    fun news(filter: NewsFilter) = dao.observe(key(FeedKind.NEWS, filter)).map { cache ->
        CachedFeed(cache?.let { decode<Article>(it.payload) }.orEmpty(), cache?.fetchedAt)
    }
    fun deals(includeExpired: Boolean = false) = dao.observe(key(FeedKind.DEALS)).map { cache ->
        CachedFeed(cache?.let { decode<Deal>(it.payload) }.orEmpty().filter { it.isSoftwareOffer() && (includeExpired || it.isActive()) }, cache?.fetchedAt)
    }
    fun picks(includeFuture: Boolean = false) = dao.observe(key(FeedKind.PICKS)).map { cache ->
        CachedFeed(cache?.let { decode<Pick>(it.payload) }.orEmpty()
            .filter { includeFuture || it.date <= LocalDate.now().toString() }.sortedWith(pickOrder), cache?.fetchedAt)
    }
    private inline fun <reified T> decode(payload: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(payload) }.getOrDefault(emptyList())

    private fun status(key: String, value: RefreshStatus) {
        // Updates can come from parallel daily refreshes; never lose another feed's status.
        synchronized(_statuses) { _statuses.value = _statuses.value + (key to value) }
    }

    suspend fun refresh(kind: FeedKind, filter: NewsFilter = NewsFilter.ALL, force: Boolean = true): Boolean {
        val key = key(kind, filter)
        return locks.getOrPut(key) { Mutex() }.withLock {
            val cached = dao.get(key)
            if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < 15 * 60_000) return@withLock true
            status(key, RefreshStatus(refreshing = true))
            try {
                val payload = when (kind) {
                    FeedKind.NEWS -> json.encodeToString(fetchNews(filter))
                    FeedKind.DEALS -> {
                        requireEndpoint(dealsUrl, "Deals", "DEALS_FEED_URL")
                        val items = api.deals(dealsUrl)
                        require(items.all { it.title.isNotBlank() && it.description.isNotBlank() && it.source.isNotBlank() && isWebUrl(it.url) && it.isSoftwareOffer() && (it.expires == null || validDate(it.expires)) }) { "The deals feed contains an invalid item. Your saved feed is still available." }
                        json.encodeToString(items.distinctBy { it.url })
                    }
                    FeedKind.PICKS -> {
                        requireEndpoint(picksUrl, "Picks", "PICKS_FEED_URL")
                        val items = api.picks(picksUrl)
                        require(items.all { it.title.isNotBlank() && it.body.isNotBlank() && isWebUrl(it.url) && validDate(it.date) }) { "The picks feed contains an invalid item. Your saved feed is still available." }
                        require(items.all { it.id == null || it.id > 0 } && items.map { it.itemKey() }.distinct().size == items.size) { "The picks feed contains duplicate or invalid item IDs. Your saved feed is still available." }
                        json.encodeToString(items.sortedWith(pickOrder))
                    }
                }
                dao.put(FeedCache(key, payload, System.currentTimeMillis()))
                status(key, RefreshStatus())
                true
            } catch (e: CancellationException) {
                status(key, RefreshStatus())
                throw e
            } catch (e: Exception) {
                status(key, RefreshStatus(error = friendlyError(e)))
                false
            }
        }
    }

    private suspend fun fetchNews(filter: NewsFilter): List<Article> = coroutineScope {
        require(newsKey.isNotBlank() && newsKey != "PASTE_YOUR_GNEWS_KEY_HERE") { "Headlines are not connected yet. Add NEWS_API_KEY to local.properties and rebuild." }
        val items = when (filter) {
            NewsFilter.ALL -> {
                val tech = async { api.headlines(newsKey, "technology").articles }
                val world = async { api.headlines(newsKey, "world").articles }
                tech.await() + world.await()
            }
            NewsFilter.TECH -> api.headlines(newsKey, "technology").articles
            NewsFilter.AI -> api.search(newsKey, "\"artificial intelligence\" OR \"machine learning\"").articles
            NewsFilter.INDIA -> api.headlines(newsKey, "nation", "in").articles
            NewsFilter.WORLD -> api.headlines(newsKey, "world").articles
        }
        require(items.all { it.title.isNotBlank() && isWebUrl(it.url) && runCatching { Instant.parse(it.publishedAt) }.isSuccess }) { "The news service returned an invalid feed. Your saved headlines are still available." }
        items.distinctBy { it.url }.sortedByDescending { it.publishedAt }
    }
    private fun requireEndpoint(url: String, label: String, constant: String) {
        require(url.isNotBlank()) { "$label are not connected yet. Add $constant in FeedConfig.kt and rebuild." }
        require(isWebUrl(url, httpsOnly = true)) { "$constant must be a valid HTTPS feed URL." }
    }
    private fun validDate(value: String) = runCatching { LocalDate.parse(value) }.isSuccess

    suspend fun delete(kind: FeedKind, id: Long?, identity: String, token: String) {
        require(kind != FeedKind.NEWS) { "Headlines cannot be deleted." }
        require(token.isNotBlank()) { "Add your publishing token in Settings first." }
        val endpoint = (if (kind == FeedKind.DEALS) dealsUrl else picksUrl).toHttpUrlOrNull()
        require(endpoint != null && endpoint.isHttps && endpoint.username.isEmpty() && endpoint.password.isEmpty() && endpoint.encodedPath.trimEnd('/').endsWith("/api/${kind.route}") && endpoint.query == null && endpoint.fragment == null) {
            "Deletion requires the publishing backend's HTTPS /api/${kind.route} endpoint."
        }
        val service = requireNotNull(deleteApi) { "Deletion is not configured." }
        val key = key(kind)
        // Share the refresh lock: a refresh started before deletion cannot write
        // an old response back into Room after the item has been removed.
        locks.getOrPut(key) { Mutex() }.withLock {
            val resolvedId = id ?: when (kind) {
                FeedKind.DEALS -> api.deals(dealsUrl).firstOrNull { it.url == identity }?.id
                FeedKind.PICKS -> api.picks(picksUrl).firstOrNull { it.legacyIdentity() == identity }?.id
                else -> null
            }
            require(resolvedId != null && resolvedId > 0) { "This item has no server ID. Update the backend and refresh the feed before deleting it." }
            val url = endpoint.newBuilder().encodedPath(endpoint.encodedPath.trimEnd('/') + "/$resolvedId").build().toString()
            val response = service.deleteItem(url, token)
            // 404 means another publisher has already removed this stale item.
            if (response.code() != 204 && response.code() != 404) throw HttpException(response)
            dao.get(key)?.let { cache ->
                val payload = when (kind) {
                    FeedKind.DEALS -> json.encodeToString(decode<Deal>(cache.payload).filterNot { it.id == resolvedId || (it.id == null && it.url == identity) })
                    FeedKind.PICKS -> json.encodeToString(decode<Pick>(cache.payload).filterNot { it.id == resolvedId || (it.id == null && it.legacyIdentity() == identity) })
                    else -> cache.payload
                }
                dao.put(cache.copy(payload = payload))
            }
        }
    }

    fun deleteError(e: Exception): String = when (e) {
        is HttpException -> when (e.code()) {
            401, 403 -> "The token was rejected. Update it in Settings and try again."
            in 300..399 -> "The delete endpoint redirected. Check the configured backend URL."
            else -> "Couldn't delete this item (${e.code()}). Please try again."
        }
        is IOException -> "You're offline or the server isn't responding. Reconnect to delete; the saved item is still available."
        is IllegalArgumentException -> e.message ?: "Deletion is not configured."
        else -> "Couldn't delete this item. Please try again."
    }
    private fun friendlyError(e: Exception): String = when (e) {
        is HttpException -> when (e.code()) {
            401, 403 -> "The feed rejected access. Check the news API key or feed URL."
            429 -> "The service's request limit was reached. Try again later."
            else -> "The feed is temporarily unavailable (${e.code()}). Saved content is still available."
        }
        is SerializationException -> "The feed format is invalid. Saved content is still available."
        is IOException -> "You're offline or the service isn't responding. Showing your last saved feed."
        is IllegalArgumentException -> e.message ?: "The feed configuration is invalid."
        else -> "Couldn't update this feed. Try again; your saved content is still available."
    }
}
