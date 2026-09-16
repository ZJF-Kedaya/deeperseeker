package com.deeperseeker.app.data

import java.util.UUID

/** Who produced a message. Mirrors the OpenAI role vocabulary. */
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * One bubble in the conversation.
 *
 * [reasoning] holds the thinking-mode output. It is kept separate from
 * [content] so the UI can collapse it, and it is stripped before the history
 * is sent back upstream — the bridge only wants the visible answer.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String = "",
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val toolName: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

/** A file the user attached to a message, kept only for local rendering. */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUri: String? = null,
    val remoteFileId: String? = null,
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")
}

/** A conversation thread. */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Which model alias the user picked. The bridge serves all of them. */
data class ModelOption(
    val id: String,
    val displayName: String,
    val contextWindow: Long? = null,
    val maxOutputTokens: Long? = null,
)

/** Reachability of the configured server, shown as a status pill. */
sealed interface ServerStatus {
    data object Unknown : ServerStatus
    data object Checking : ServerStatus
    data class Online(val latencyMs: Long) : ServerStatus
    data class Offline(val reason: String) : ServerStatus
}