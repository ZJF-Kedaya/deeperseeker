package com.deeperseeker.app.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* ------------------------------------------------------------------ */
/*  Chat: OpenAI-compatible request/response                            */
/* ------------------------------------------------------------------ */

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement? = null,
    val name: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    /** Local-only: thinking text shown in a collapsed block, never sent upstream. */
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String = "function",
    val function: ToolCallFunction? = null,
)

@Serializable
data class ToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val search: Boolean = false,
    val tools: JsonElement? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val created: Long? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

/* ------------------------------------------------------------------ */
/*  Streaming chunk                                                     */
/* ------------------------------------------------------------------ */

@Serializable
data class StreamChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    /** Emitted by thinking-mode models ahead of the visible answer. */
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null,
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunctionDelta? = null,
)

@Serializable
data class ToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)

/* ------------------------------------------------------------------ */
/*  Models                                                              */
/* ------------------------------------------------------------------ */

@Serializable
data class ModelListResponse(
    val `object`: String? = null,
    val data: List<ModelInfo> = emptyList(),
)

@Serializable
data class ModelInfo(
    val id: String,
    val `object`: String? = null,
    val owned_by: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("context_window") val contextWindow: Long? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Long? = null,
)

/* ------------------------------------------------------------------ */
/*  Admin dashboard                                                     */
/* ------------------------------------------------------------------ */

@Serializable
data class TokenInfo(
    val id: Int,
    val alias: String? = null,
    val token: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("limited_at") val limitedAt: String? = null,
)

@Serializable
data class DashboardSummary(
    val total: Int = 0,
    val active: Int = 0,
    val rateLimited: Int = 0,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AddTokenRequest(
    val alias: String? = null,
    val authToken: String,
)

@Serializable
data class ApiResult(
    val ok: Boolean = true,
    val message: String? = null,
    val count: Int? = null,
)

/* ------------------------------------------------------------------ */
/*  Errors                                                              */
/* ------------------------------------------------------------------ */

@Serializable
data class ApiErrorBody(
    val error: JsonElement? = null,
    val detail: JsonElement? = null,
)