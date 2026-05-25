package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TikWmResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String,
    @Json(name = "data") val data: TikWmData?
)

@JsonClass(generateAdapter = true)
data class TikWmData(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "cover") val cover: String?,
    @Json(name = "play") val play: String?,
    @Json(name = "wmplay") val wmplay: String?,
    @Json(name = "music") val music: String?,
    @Json(name = "author") val author: TikWmAuthor?
)

@JsonClass(generateAdapter = true)
data class TikWmAuthor(
    @Json(name = "id") val id: String?,
    @Json(name = "unique_id") val uniqueId: String?,
    @Json(name = "nickname") val nickname: String?
)

object TikWmApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(TikWmResponse::class.java)

    /**
     * Resolves a TikTok URL into download links via the TikWM public API.
     */
    suspend fun fetchVideoInfo(tiktokUrl: String): TikWmResponse? = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("url", tiktokUrl)
            .add("hd", "1")
            .build()

        val request = Request.Builder()
            .url("https://www.tikwm.com/api/")
            .post(formBody)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                return@withContext adapter.fromJson(bodyString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
