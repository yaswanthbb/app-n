package com.machine.newsapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private class MemoryDao : FeedDao {
    val rows = MutableStateFlow<Map<String, FeedCache>>(emptyMap())
    override fun observe(key: String): Flow<FeedCache?> = rows.map { it[key] }
    override suspend fun get(key: String) = rows.value[key]
    override suspend fun put(cache: FeedCache) { rows.value += cache.feedKey to cache }
}
private class FakeApi : FeedApi {
    var offline = false
    var calls = 0
    var dealItems = listOf(Deal("Free model", "A free AI model", "https://example.com/deal", "Example", "AI"))
    var pickItems = listOf(Pick("Resource", "Learn something", "https://example.com/pick", "2020-01-01"))
    override suspend fun headlines(apiKey: String, category: String, country: String?, language: String, max: Int): NewsResponse {
        calls++
        if (offline) throw IOException("offline")
        return NewsResponse(listOf(Article("$category headline", url = "https://example.com/$category", publishedAt = "2026-10-08T10:00:00Z")))
    }
    override suspend fun search(apiKey: String, query: String, language: String, max: Int, sort: String) = headlines(apiKey, "AI", null, language, max)
    override suspend fun deals(url: String): List<Deal> { if (offline) throw IOException("offline"); return dealItems }
    override suspend fun picks(url: String): List<Pick> { if (offline) throw IOException("offline"); return pickItems }
}

class FeedRepositoryTest {
    private val api = FakeApi()
    private val dao = MemoryDao()
    private val repository = FeedRepository(dao, api, Json { ignoreUnknownKeys = true }, "test-key", "https://example.com/deals", "https://example.com/picks")

    @Test fun `failed refresh keeps last good feed across repository restart`() = runTest {
        assertTrue(repository.refresh(FeedKind.NEWS))
        api.offline = true
        assertFalse(repository.refresh(FeedKind.NEWS))
        val restarted = FeedRepository(dao, api, Json, "test-key")
        assertEquals(2, restarted.news(NewsFilter.ALL).first().items.size)
        assertNotNull(repository.statuses.value["news:ALL"]?.error)
    }
    @Test fun `news filter cache and freshness are independent`() = runTest {
        repository.refresh(FeedKind.NEWS, NewsFilter.TECH)
        repository.refresh(FeedKind.NEWS, NewsFilter.WORLD)
        repository.refresh(FeedKind.NEWS, NewsFilter.TECH, force = false)
        assertEquals(2, api.calls)
        assertEquals("technology headline", repository.news(NewsFilter.TECH).first().items.single().title)
        assertEquals("world headline", repository.news(NewsFilter.WORLD).first().items.single().title)
        assertTrue(repository.news(NewsFilter.ALL).first().items.isEmpty())
    }
    @Test fun `invalid deals cannot overwrite saved offers`() = runTest {
        assertTrue(repository.refresh(FeedKind.DEALS))
        api.dealItems = listOf(api.dealItems.single().copy(tag = "Telco"))
        assertFalse(repository.refresh(FeedKind.DEALS))
        assertEquals("AI", repository.deals().first().items.single().tag)
    }
    @Test fun `duplicate dates cannot overwrite saved picks`() = runTest {
        assertTrue(repository.refresh(FeedKind.PICKS))
        api.pickItems = api.pickItems + api.pickItems.single().copy(title = "Second pick")
        assertFalse(repository.refresh(FeedKind.PICKS))
        assertEquals(1, repository.picks().first().items.size)
    }
    @Test fun `valid empty feed replaces old content`() = runTest {
        repository.refresh(FeedKind.DEALS)
        api.dealItems = emptyList()
        assertTrue(repository.refresh(FeedKind.DEALS))
        assertTrue(repository.deals().first().items.isEmpty())
        assertNotNull(repository.deals().first().fetchedAt)
    }
    @Test fun `missing configuration produces useful status`() = runTest {
        val unconfigured = FeedRepository(dao, api, Json, "", "", "")
        assertFalse(unconfigured.refresh(FeedKind.DEALS))
        assertTrue(unconfigured.statuses.value["deals"]!!.error!!.contains("DEALS_FEED_URL"))
    }
}
