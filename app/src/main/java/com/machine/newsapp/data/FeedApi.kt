package com.machine.newsapp.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

interface FeedApi {
    @GET("top-headlines")
    suspend fun headlines(
        @Header("X-Api-Key") apiKey: String,
        @Query("category") category: String,
        @Query("country") country: String? = null,
        @Query("lang") language: String = "en",
        @Query("max") max: Int = 10,
    ): NewsResponse
    @GET("search")
    suspend fun search(
        @Header("X-Api-Key") apiKey: String,
        @Query("q") query: String,
        @Query("lang") language: String = "en",
        @Query("max") max: Int = 10,
        @Query("sortby") sort: String = "publishedAt",
    ): NewsResponse
    @GET suspend fun deals(@Url url: String): List<Deal>
    @GET suspend fun picks(@Url url: String): List<Pick>
}
