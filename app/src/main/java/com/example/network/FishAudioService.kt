package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

import retrofit2.http.GET
import retrofit2.http.Query

data class FishTtsRequest(
    val text: String,
    val reference_id: String = "98c1f6dca0614f679046c5a67eb1a27d",
    val format: String = "mp3"
)

data class FishModelItem(
    val _id: String? = null,
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val description: String? = null,
    val type: String? = null
) {
    val modelId: String
        get() = (_id ?: id ?: "").trim()
    val displayName: String
        get() = (title ?: name ?: modelId).ifBlank { modelId }
}

interface FishAudioApi {
    @POST("v1/tts")
    @Headers("Content-Type: application/json", "model: s2.1-pro-free")
    suspend fun generateTts(
        @Header("Authorization") authorization: String,
        @Body request: FishTtsRequest,
        @Header("model") model: String = "s2.1-pro-free"
    ): Response<ResponseBody>

    @GET("v1/model")
    suspend fun getUserModels(
        @Header("Authorization") authorization: String,
        @Query("self") self: Boolean = true,
        @Query("pageSize") pageSize: Int = 100
    ): Response<ResponseBody>

    @GET("v1/models")
    suspend fun getUserModelsAlt(
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}

object FishAudioClient {
    private const val BASE_URL = "https://api.fish.audio/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: FishAudioApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FishAudioApi::class.java)
    }

    fun parseFishModelsResponse(jsonStr: String): List<FishModelItem> {
        val list = mutableListOf<FishModelItem>()
        try {
            val root = org.json.JSONObject(jsonStr)
            val array = when {
                root.has("items") -> root.optJSONArray("items")
                root.has("data") -> root.optJSONArray("data")
                root.has("models") -> root.optJSONArray("models")
                root.has("results") -> root.optJSONArray("results")
                else -> null
            }
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("_id").ifEmpty { obj.optString("id") }
                    val title = obj.optString("title").ifEmpty { obj.optString("name") }
                    val desc = obj.optString("description")
                    val type = obj.optString("type")
                    if (id.isNotEmpty()) {
                        list.add(FishModelItem(_id = id, title = title, description = desc, type = type))
                    }
                }
            }
        } catch (_: Exception) {
            try {
                val array = org.json.JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("_id").ifEmpty { obj.optString("id") }
                    val title = obj.optString("title").ifEmpty { obj.optString("name") }
                    val desc = obj.optString("description")
                    val type = obj.optString("type")
                    if (id.isNotEmpty()) {
                        list.add(FishModelItem(_id = id, title = title, description = desc, type = type))
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }
}
