package com.deeperseeker.app.data

import com.deeperseeker.app.di.ServiceLocator
import com.deeperseeker.app.net.ChatApi
import com.deeperseeker.app.net.ChatCompletionRequest
import com.deeperseeker.app.net.ChatMessage as ApiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Owns every call to the OpenAI-compatible chat surface.
 *
 * The repository converts between the app's local [ChatMessage] list and the
 * wire format, and exposes both a blocking and a streaming send. Streaming is
 * modelled as a cold [Flow] of [ChatEvent] so the ViewModel can render tokens
 * as they arrive without polling.
 */
class ChatRepository(private val settings: SettingsStore) {

    /** One increment of progress during a streaming answer. */
    sealed interface ChatEvent {
        data class Delta(val content: String?, val reasoning: String?) : ChatEvent
        data class ToolCall(val id: String?, val name: String?, val arguments: String?) : ChatEvent
        data class Failed(val message: String) : ChatEvent
        data object Completed : ChatEvent
    }

    /**
     * Builds the chat surface for the current settings.
     *
     * Suspending rather than reading `settings.baseUrl` directly avoids a
     * `runBlocking` on the DataStore on every request, and lets the API key —
     * which every `/v1` route requires — be resolved from the same snapshot.
     */
    private suspend fun api(): ChatApi =
        ServiceLocator.chatApi(settings.currentBaseUrl(), settings.currentApiKey())

    /**
     * Sends [history] and streams the answer.
     *
     * The tool schema is passed through untouched: the bridge parses DSML/XML
     * and JSON tool calls server-side, so the client only needs to forward what
     * the model asked for.
     */
    fun stream(
        history: List<ChatMessage>,
        model: String,
        systemPrompt: String,
        search: Boolean,
        thinking: Boolean,
        tools: kotlinx.serialization.json.JsonElement? = null,
    ): Flow<ChatEvent> = callbackFlow {
        val request = ChatCompletionRequest(
            model = model,
            messages = buildWireMessages(history, systemPrompt),
            stream = true,
            search = search,
            tools = tools,
            reasoningEffort = if (thinking) "high" else null,
        )

        val response = runCatching { api().chatCompletionsStream(request) }
            .getOrElse { throwable ->
                trySend(ChatEvent.Failed(describe(throwable)))
                close()
                return@callbackFlow
            }

        if (!response.isSuccessful) {
            val body = runCatching { response.errorBody()?.string() }.getOrNull()
            trySend(ChatEvent.Failed("HTTP ${response.code()}: ${extractError(body)}"))
            close()
            return@callbackFlow
        }

        val stream = response.body()
        if (stream == null) {
            trySend(ChatEvent.Failed("服务端返回了空的响应体"))
            close()
            return@callbackFlow
        }

        val parser = SseParser(
            json = ServiceLocator.json,
            onDelta = { content, reasoning ->
                trySend(ChatEvent.Delta(content, reasoning))
            },
            onToolCall = { id, name, arguments ->
                trySend(ChatEvent.ToolCall(id, name, arguments))
            },
            onDone = {
                trySend(ChatEvent.Completed)
                close()
            },
            onError = { message -> trySend(ChatEvent.Failed(message)) },
        )

        // Reading blocks, so it runs on its own coroutine; closing the flow from
        // onDone unblocks the collector while this job unwinds naturally.
        val readerJob = launch(Dispatchers.IO) {
            runCatching {
                val reader = BufferedReader(InputStreamReader(stream.byteStream(), Charsets.UTF_8))
                parser.consume(reader)
            }.onFailure { throwable ->
                trySend(ChatEvent.Failed(describe(throwable)))
                close()
            }
        }

        awaitClose { readerJob.cancel() }
    }.flowOn(Dispatchers.IO)

    /** Non-streaming fallback, used when the user disables live typing. */
    suspend fun complete(
        history: List<ChatMessage>,
        model: String,
        systemPrompt: String,
        search: Boolean,
        thinking: Boolean,
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val request = ChatCompletionRequest(
                model = model,
                messages = buildWireMessages(history, systemPrompt),
                stream = false,
                search = search,
                reasoningEffort = if (thinking) "high" else null,
            )
            val response = api().chatCompletions(request)
            val choice = response.choices.firstOrNull()
            val text = choice?.message?.content?.let { jsonContentToString(it) }.orEmpty()
            ChatMessage(role = Role.ASSISTANT, content = text)
        }
    }

    /** Fetches the model catalogue, falling back to the bridge's default. */
    suspend fun models(): List<ModelOption> = withContext(Dispatchers.IO) {
        runCatching {
            api().models().data.map { info ->
                ModelOption(
                    id = info.id,
                    displayName = info.displayName ?: info.name ?: info.id,
                    contextWindow = info.contextWindow,
                    maxOutputTokens = info.maxOutputTokens,
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Converts local messages to the wire format.
     *
     * Reasoning text is intentionally dropped: it is a UI affordance and
     * re-sending it would inflate the prompt. Tool and system roles are kept.
     */
    private fun buildWireMessages(
        history: List<ChatMessage>,
        systemPrompt: String,
    ): List<ApiMessage> {
        val result = mutableListOf<ApiMessage>()
        if (systemPrompt.isNotBlank()) {
            result += ApiMessage(role = "system", content = JsonPrimitive(systemPrompt))
        }
        for (message in history) {
            if (message.error != null) continue
            if (message.content.isBlank() && message.role != Role.ASSISTANT) continue
            val role = when (message.role) {
                Role.SYSTEM -> "system"
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                Role.TOOL -> "tool"
            }
            result += ApiMessage(role = role, content = JsonPrimitive(message.content))
        }
        return result
    }

    private fun jsonContentToString(element: kotlinx.serialization.json.JsonElement): String =
        when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }

    private fun extractError(body: String?): String {
        if (body.isNullOrBlank()) return "未知错误"
        return runCatching {
            val root = ServiceLocator.json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
            root?.get("error")?.toString()?.trim('"')
                ?: root?.get("detail")?.toString()?.trim('"')
                ?: body.take(300)
        }.getOrDefault(body.take(300))
    }

    private fun describe(throwable: Throwable): String = when (throwable) {
        is java.net.UnknownHostException -> "无法解析服务器地址，请检查服务器配置"
        is java.net.ConnectException -> "无法连接到服务器，请确认服务已启动"
        is java.net.SocketTimeoutException -> "连接超时，请检查网络"
        else -> throwable.message ?: throwable.javaClass.simpleName
    }
}