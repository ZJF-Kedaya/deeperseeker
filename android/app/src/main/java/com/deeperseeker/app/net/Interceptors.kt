package com.deeperseeker.app.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Sends the bridge's API key the way every OpenAI-compatible client does.
 * The server accepts either `Authorization: Bearer <key>` or `x-api-key`.
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
            builder.header("x-api-key", apiKey)
        }
        builder.header("Accept", "application/json")
        return chain.proceed(builder.build())
    }
}

/**
 * Holds the admin session cookie in memory and attaches it to dashboard calls.
 *
 * The cookie is set by `POST /login` and is an opaque server-side session id,
 * so it is kept in the app's private DataStore by the settings layer rather
 * than parsed here.
 */
class SessionCookieInterceptor : Interceptor {

    @Volatile
    var cookie: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        val current = cookie
        if (!current.isNullOrBlank()) {
            builder.header("Cookie", current)
        }
        builder.header("Accept", "text/html,application/json")
        return chain.proceed(builder.build())
    }
}