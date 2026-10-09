package com.machine.newsapp.data

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Url

interface DeleteApi {
    @DELETE
    suspend fun deleteItem(@Url url: String, @Header("X-Publish-Token") token: String): Response<Unit>
}
