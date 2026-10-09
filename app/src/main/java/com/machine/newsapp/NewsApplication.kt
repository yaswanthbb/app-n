package com.machine.newsapp

import android.app.Application
import androidx.room.Room
import com.machine.newsapp.data.FeedApi
import com.machine.newsapp.data.FeedDatabase
import com.machine.newsapp.data.FeedRepository
import com.machine.newsapp.data.DeleteApi
import com.machine.newsapp.data.EncryptedPublishTokenStore
import com.machine.newsapp.notifications.DailyScheduler
import com.machine.newsapp.notifications.Notifications
import com.machine.newsapp.notifications.TopicSubscriptionWorker
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class NewsApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        DailyScheduler.schedule(this)
        TopicSubscriptionWorker.schedule(this)
    }
}

class AppContainer(application: Application) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val database = Room.databaseBuilder(application, FeedDatabase::class.java, "feeds.db").build()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // Allow Render's free service time to wake up.
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val api = Retrofit.Builder()
        .baseUrl(FeedConfig.NEWS_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(FeedApi::class.java)
    // Custom publish-token headers must never be forwarded by an HTTP redirect.
    private val deleteApi = Retrofit.Builder()
        .baseUrl(FeedConfig.NEWS_BASE_URL)
        .client(client.newBuilder().followRedirects(false).followSslRedirects(false).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(DeleteApi::class.java)
    val repository = FeedRepository(database.feeds(), api, json, deleteApi = deleteApi)
    val tokenStore = EncryptedPublishTokenStore(application)
}
