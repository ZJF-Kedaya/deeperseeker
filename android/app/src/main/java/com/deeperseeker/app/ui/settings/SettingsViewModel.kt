package com.deeperseeker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deeperseeker.app.data.ManageRepository
import com.deeperseeker.app.data.ServerStatus
import com.deeperseeker.app.data.SettingsStore
import com.deeperseeker.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Connection settings the user edits on the settings screen. */
data class SettingsUiState(
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = SettingsStore.DEFAULT_MODEL,
    val systemPrompt: String = "",
    val status: ServerStatus = ServerStatus.Unknown,
    val saved: Boolean = false,
)

/**
 * Owns the three values every other screen depends on: server address, API key
 * and the system prompt. A "test connection" action probes `/health` through
 * the admin client so the user can verify reachability before chatting.
 */
class SettingsViewModel(
    private val settings: SettingsStore = ServiceLocator.settings,
    private val manageRepository: ManageRepository = ServiceLocator.manageRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    baseUrl = settings.baseUrlFlow.first(),
                    apiKey = settings.apiKeyFlow.first(),
                    model = settings.modelFlow.first(),
                    systemPrompt = settings.systemPromptFlow.first(),
                )
            }
        }
    }

    fun onBaseUrlChange(value: String) {
        _state.update { it.copy(baseUrl = value, saved = false) }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value, saved = false) }
    }

    fun onModelChange(value: String) {
        _state.update { it.copy(model = value, saved = false) }
    }

    fun onSystemPromptChange(value: String) {
        _state.update { it.copy(systemPrompt = value, saved = false) }
    }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            settings.setBaseUrl(current.baseUrl)
            settings.setApiKey(current.apiKey)
            settings.setModel(current.model)
            settings.setSystemPrompt(current.systemPrompt)
            _state.update { it.copy(baseUrl = settings.baseUrlFlow.first(), saved = true) }
        }
    }

    fun testConnection() {
        val current = _state.value
        _state.update { it.copy(status = ServerStatus.Checking) }
        viewModelScope.launch {
            // Persist first so the repositories probe the address being tested.
            settings.setBaseUrl(current.baseUrl)
            settings.setApiKey(current.apiKey)
            manageRepository.ping()
                .onSuccess { latency ->
                    _state.update { it.copy(status = ServerStatus.Online(latency)) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(status = ServerStatus.Offline(throwable.message ?: "无法连接"))
                    }
                }
        }
    }
}