package com.deeperseeker.app.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds the two Retrofit stacks the app talks to.
 *
 * A single [OkHttpClient] is shared so connection pooling and DNS caching are
 * reused, but the interceptors differ per surface: the chat client always
 * carries the API key, while the admin client carries a mutable session cookie
 * that is filled in after `POST /login`.
 *
 * Timeouts are generous because a long answer streamed from the bridge can
 * legitimately sit idle while the upstream model is thinking.
 */
object ApiClient {

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 300L
    private const val WRITE_TIMEOUT_SECONDS = 120L

    /** Populated by [com.deeperseeker.app.data.ManageRepository] after login. */
    val sessionCookie = SessionCookieInterceptor()

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // Headers only: bodies may contain the API key and prompts.
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()
    }

    /** OpenAI-compatible surface. Every call carries the API key. */
    fun createChatApi(baseUrl: String, apiKey: String, json: Json): ChatApi {
        val client = baseClient.newBuilder()
            .addInterceptor(ApiKeyInterceptor(apiKey))
            .build()
        return build(client, baseUrl, json).create(ChatApi::class.java)
    }

    /** Admin dashboard surface. Every call carries the session cookie. */
    fun createManageApi(baseUrl: String, json: Json): ManageApi {
        val client = baseClient.newBuilder()
            .addInterceptor(sessionCookie)
            .build()
        return build(client, baseUrl, json).create(ManageApi::class.java)
    }

    private fun build(client: OkHttpClient, baseUrl: String, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /**
     * Retrofit requires the base URL to end with `/`, otherwise the last path
     * segment is silently dropped and every relative route 404s.
     */
    private fun normalizeBaseUrl(raw: String): String =
        raw.trim().trimEnd('/') + "/"
}
