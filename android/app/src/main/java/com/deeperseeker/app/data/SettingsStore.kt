package com.deeperseeker.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "deeperseeker_settings")

/**
 * Persists the two things the app needs between launches: which server it
 * talks to, and which credential to present.
 *
 * The chat client authenticates with the API key (`DEEPSEEKER_API_KEY`), while
 * the admin screens log in with the session cookie. Both live here.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val ADMIN_USER = stringPreferencesKey("admin_user")
        val MODEL = stringPreferencesKey("model")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    }

    val baseUrlFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.BASE_URL] ?: DEFAULT_BASE_URL }

    val apiKeyFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.API_KEY] ?: "" }

    val sessionCookieFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.SESSION_COOKIE] ?: "" }

    val adminUserFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.ADMIN_USER] ?: "" }

    val modelFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.MODEL] ?: DEFAULT_MODEL }

    val systemPromptFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: "" }

    /** Blocking read used by the Retrofit factory, which is not suspend-aware. */
    var baseUrl: String
        get() = kotlinx.coroutines.runBlocking { baseUrlFlow.first() }
        set(value) = kotlinx.coroutines.runBlocking {
            context.dataStore.edit { it[Keys.BASE_URL] = normalize(value) }
        }

    suspend fun setBaseUrl(value: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = normalize(value) }
    }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value.trim() }
    }

    suspend fun setSessionCookie(value: String) {
        context.dataStore.edit { it[Keys.SESSION_COOKIE] = value }
    }

    suspend fun setAdminUser(value: String) {
        context.dataStore.edit { it[Keys.ADMIN_USER] = value }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[Keys.MODEL] = value }
    }

    suspend fun setSystemPrompt(value: String) {
        context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = value }
    }

    suspend fun currentApiKey(): String = apiKeyFlow.first()

    suspend fun currentBaseUrl(): String = baseUrlFlow.first()

    suspend fun currentSessionCookie(): String = sessionCookieFlow.first()

    suspend fun currentModel(): String = modelFlow.first()

    suspend fun currentSystemPrompt(): String = systemPromptFlow.first()

    companion object {
        /** Port 8000 matches the uvicorn default in app.py / Dockerfile. */
        const val DEFAULT_BASE_URL = "http://192.168.1.10:8000"

        /** The server serves every model alias from this single upstream model. */
        const val DEFAULT_MODEL = "v4.1flash"

        fun normalize(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return DEFAULT_BASE_URL
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            return value.trimEnd('/')
        }
    }
}