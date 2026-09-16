package com.deeperseeker.app.di

import android.content.Context
import com.deeperseeker.app.data.ChatRepository
import com.deeperseeker.app.data.ConversationStore
import com.deeperseeker.app.data.ManageRepository
import com.deeperseeker.app.data.SettingsStore
import com.deeperseeker.app.net.ApiClient
import com.deeperseeker.app.net.ChatApi
import com.deeperseeker.app.net.ManageApi
import kotlinx.serialization.json.Json

/**
 * Minimal hand-rolled DI. The app has a single long-lived server base URL that
 * the user can change at runtime, so Retrofit instances are rebuilt on demand
 * rather than injected once.
 */
object ServiceLocator {

    lateinit var settings: SettingsStore
        private set

    lateinit var conversations: ConversationStore
        private set

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    fun init(context: Context) {
        settings = SettingsStore(context)
        conversations = ConversationStore(context)
    }

    fun chatRepository(): ChatRepository = ChatRepository(settings)

    fun manageRepository(): ManageRepository = ManageRepository(settings)

    /**
     * OpenAI-compatible surface (`/v1/chat/completions`, `/v1/models`, ...).
     *
     * Both values are passed in rather than read from [settings] so callers can
     * resolve them from a single DataStore snapshot instead of blocking on the
     * flow twice.
     */
    fun chatApi(baseUrl: String, apiKey: String): ChatApi =
        ApiClient.createChatApi(baseUrl, apiKey, json)

    /** Admin dashboard surface (`/login`, `/dashboard`, `/tokens`, ...). */
    fun manageApi(): ManageApi = ApiClient.createManageApi(settings.baseUrl, json)
}