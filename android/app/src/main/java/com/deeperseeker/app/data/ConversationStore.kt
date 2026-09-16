package com.deeperseeker.app.data

import android.content.Context
import com.deeperseeker.app.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Persists conversation threads as JSON files in the app's private storage.
 *
 * A flat-file store is deliberate: the app has no database dependency, the
 * volume is tiny (a handful of threads), and keeping the format human-readable
 * makes it easy to inspect or migrate. Writes are atomic via a temp file rename
 * so a crash mid-save cannot corrupt an existing thread.
 */
class ConversationStore(private val context: Context) {

    @Serializable
    private data class PersistedMessage(
        val role: String,
        val content: String,
        val reasoning: String = "",
        val timestamp: Long = 0L,
    )

    @Serializable
    private data class PersistedConversation(
        val id: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val messages: List<PersistedMessage>,
    )

    private val dir: File
        get() = File(context.filesDir, "conversations").apply { if (!exists()) mkdirs() }

    suspend fun loadAll(): List<Conversation> = withContext(Dispatchers.IO) {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            runCatching {
                val persisted = ServiceLocator.json.decodeFromString<PersistedConversation>(file.readText())
                Conversation(
                    id = persisted.id,
                    title = persisted.title,
                    createdAt = persisted.createdAt,
                    updatedAt = persisted.updatedAt,
                    messages = persisted.messages.map { msg ->
                        ChatMessage(
                            role = runCatching { Role.valueOf(msg.role) }.getOrDefault(Role.USER),
                            content = msg.content,
                            reasoning = msg.reasoning,
                            timestamp = msg.timestamp,
                        )
                    },
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun save(conversation: Conversation) = withContext(Dispatchers.IO) {
        val persisted = PersistedConversation(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            messages = conversation.messages
                .filter { it.error == null }
                .map { msg ->
                    PersistedMessage(
                        role = msg.role.name,
                        content = msg.content,
                        reasoning = msg.reasoning,
                        timestamp = msg.timestamp,
                    )
                },
        )
        val target = File(dir, "${conversation.id}.json")
        val temp = File(dir, "${conversation.id}.json.tmp")
        temp.writeText(ServiceLocator.json.encodeToString(persisted))
        if (target.exists()) target.delete()
        temp.renameTo(target)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        Unit
    }
}