package com.deeperseeker.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deeperseeker.app.data.ChatMessage
import com.deeperseeker.app.data.ChatRepository
import com.deeperseeker.app.data.Conversation
import com.deeperseeker.app.data.ConversationStore
import com.deeperseeker.app.data.ModelOption
import com.deeperseeker.app.data.Role
import com.deeperseeker.app.data.SettingsStore
import com.deeperseeker.app.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the chat screen renders. */
data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeId: String? = null,
    val input: String = "",
    val isSending: Boolean = false,
    val searchEnabled: Boolean = false,
    val thinkingEnabled: Boolean = false,
    val model: String = SettingsStore.DEFAULT_MODEL,
    val models: List<ModelOption> = emptyList(),
    val systemPrompt: String = "",
    val error: String? = null,
    val showSettings: Boolean = false,
) {
    val active: Conversation?
        get() = conversations.firstOrNull { it.id == activeId }

    val messages: List<ChatMessage>
        get() = active?.messages.orEmpty()
}

/**
 * Drives the conversation screen: thread management, streaming replies and the
 * per-request switches (search, thinking, model).
 *
 * Streaming updates mutate the last message in place on every delta. That keeps
 * recomposition scoped to the final bubble and avoids rebuilding the whole list
 * per token, which matters on lower-end devices with long answers.
 */
class ChatViewModel(
    private val settings: SettingsStore = ServiceLocator.settings,
    private val store: ConversationStore = ServiceLocator.conversations,
    private val repository: ChatRepository = ServiceLocator.chatRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = store.loadAll()
            val models = repository.models()
            val savedModel = settings.currentModel()
            _state.update {
                it.copy(
                    conversations = loaded,
                    activeId = loaded.firstOrNull()?.id,
                    models = models,
                    model = savedModel,
                    systemPrompt = settings.currentSystemPrompt(),
                )
            }
        }
    }

    /* ---------------------------------------------------------------- */
    /*  Thread management                                                */
    /* ---------------------------------------------------------------- */

    fun newConversation() {
        val conversation = Conversation()
        _state.update {
            it.copy(
                conversations = listOf(conversation) + it.conversations,
                activeId = conversation.id,
                error = null,
            )
        }
        viewModelScope.launch { store.save(conversation) }
    }

    fun selectConversation(id: String) {
        _state.update { it.copy(activeId = id, error = null) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch { store.delete(id) }
        _state.update { current ->
            val remaining = current.conversations.filterNot { it.id == id }
            current.copy(
                conversations = remaining,
                activeId = if (current.activeId == id) remaining.firstOrNull()?.id else current.activeId,
            )
        }
    }

    fun renameConversation(id: String, title: String) {
        updateConversation(id) { it.copy(title = title, updatedAt = System.currentTimeMillis()) }
    }

    fun clearMessages() {
        val id = _state.value.activeId ?: return
        updateConversation(id) { it.copy(messages = emptyList(), updatedAt = System.currentTimeMillis()) }
    }

    /* ---------------------------------------------------------------- */
    /*  Composer                                                         */
    /* ---------------------------------------------------------------- */

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun toggleSearch() {
        _state.update { it.copy(searchEnabled = !it.searchEnabled) }
    }

    fun toggleThinking() {
        _state.update { it.copy(thinkingEnabled = !it.thinkingEnabled) }
    }

    fun setModel(model: String) {
        _state.update { it.copy(model = model) }
        viewModelScope.launch { settings.setModel(model) }
    }

    fun setSystemPrompt(prompt: String) {
        _state.update { it.copy(systemPrompt = prompt) }
        viewModelScope.launch { settings.setSystemPrompt(prompt) }
    }

    fun toggleSettings() {
        _state.update { it.copy(showSettings = !it.showSettings) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /* ---------------------------------------------------------------- */
    /*  Sending                                                          */
    /* ---------------------------------------------------------------- */

    fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() || current.isSending) return

        val conversationId = current.activeId
            ?: run {
                newConversation()
                _state.value.activeId
            }
            ?: return

        val userMessage = ChatMessage(role = Role.USER, content = text)
        // The placeholder assistant bubble is what streaming writes into.
        val placeholder = ChatMessage(role = Role.ASSISTANT, content = "", isStreaming = true)

        _state.update { it.copy(input = "", isSending = true, error = null) }
        appendMessages(conversationId, listOf(userMessage, placeholder))

        // Auto-title from the first user turn, mirroring the web UI.
        val conversation = _state.value.conversations.firstOrNull { it.id == conversationId }
        if (conversation != null && conversation.messages.none { it.role == Role.USER } == false &&
            conversation.title == "新对话"
        ) {
            renameConversation(conversationId, text.take(30))
        }

        val historyForRequest = _state.value.conversations
            .firstOrNull { it.id == conversationId }
            ?.messages
            ?.filterNot { it.isStreaming }
            .orEmpty()

        sendJob = viewModelScope.launch {
            repository.stream(
                history = historyForRequest,
                model = current.model,
                systemPrompt = current.systemPrompt,
                search = current.searchEnabled,
                thinking = current.thinkingEnabled,
            ).collect { event ->
                when (event) {
                    is ChatRepository.ChatEvent.Delta -> {
                        updateMessage(conversationId, placeholder.id) { message ->
                            message.copy(
                                content = message.content + (event.content ?: ""),
                                reasoning = message.reasoning + (event.reasoning ?: ""),
                            )
                        }
                    }

                    is ChatRepository.ChatEvent.ToolCall -> {
                        updateMessage(conversationId, placeholder.id) { message ->
                            val label = buildString {
                                append(message.toolName?.let { "$it\n" }.orEmpty())
                                append("调用工具 ${event.name ?: "unknown"}")
                                if (!event.arguments.isNullOrBlank()) {
                                    append("(")
                                    append(event.arguments.take(200))
                                    append(")")
                                }
                            }
                            message.copy(toolName = label)
                        }
                    }

                    is ChatRepository.ChatEvent.Failed -> {
                        updateMessage(conversationId, placeholder.id) { message ->
                            message.copy(
                                error = event.message,
                                isStreaming = false,
                                content = message.content.ifBlank { "" },
                            )
                        }
                        _state.update { it.copy(isSending = false, error = event.message) }
                    }

                    ChatRepository.ChatEvent.Completed -> {
                        updateMessage(conversationId, placeholder.id) { message ->
                            message.copy(isStreaming = false)
                        }
                        _state.update { it.copy(isSending = false) }
                    }
                }
            }
            // Persist the finished thread once the flow terminates.
            _state.value.conversations.firstOrNull { it.id == conversationId }?.let { store.save(it) }
            _state.update { it.copy(isSending = false) }
        }
    }

    /** Cancels an in-flight answer, keeping whatever text already arrived. */
    fun stop() {
        sendJob?.cancel()
        sendJob = null
        val id = _state.value.activeId ?: return
        _state.update { current ->
            current.copy(
                isSending = false,
                conversations = current.conversations.map { conversation ->
                    if (conversation.id != id) return@map conversation
                    conversation.copy(
                        messages = conversation.messages.map { message ->
                            if (message.isStreaming) message.copy(isStreaming = false) else message
                        },
                    )
                },
            )
        }
    }

    fun retryLast() {
        val conversation = _state.value.active ?: return
        val lastUser = conversation.messages.lastOrNull { it.role == Role.USER } ?: return
        // Drop the failed assistant turn so the retry starts from the user turn.
        val trimmed = conversation.messages.dropLastWhile { it.role == Role.ASSISTANT }
        updateConversation(conversation.id) { it.copy(messages = trimmed) }
        onInputChange(lastUser.content)
        send()
    }

    /* ---------------------------------------------------------------- */
    /*  Internals                                                        */
    /* ---------------------------------------------------------------- */

    private fun appendMessages(conversationId: String, messages: List<ChatMessage>) {
        updateConversation(conversationId) {
            it.copy(messages = it.messages + messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun updateMessage(
        conversationId: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        updateConversation(conversationId) { conversation ->
            conversation.copy(
                messages = conversation.messages.map { message ->
                    if (message.id == messageId) transform(message) else message
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun updateConversation(conversationId: String, transform: (Conversation) -> Conversation) {
        _state.update { current ->
            current.copy(
                conversations = current.conversations.map { conversation ->
                    if (conversation.id == conversationId) transform(conversation) else conversation
                },
            )
        }
    }

    override fun onCleared() {
        sendJob?.cancel()
        super.onCleared()
    }
}