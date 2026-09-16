package com.deeperseeker.app.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deeperseeker.app.data.ManageRepository
import com.deeperseeker.app.di.ServiceLocator
import com.deeperseeker.app.net.TokenInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the admin/dashboard screen. */
data class ManageUiState(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val password: String = "",
    val tokens: List<TokenInfo> = emptyList(),
    val accountsPath: String? = null,
    val accountsExists: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val newAlias: String = "",
    val newToken: String = "",
)

/**
 * Wraps the server's admin dashboard.
 *
 * The session cookie is restored on construction so a returning user lands on
 * the token list instead of the login form.
 */
class ManageViewModel(
    private val repository: ManageRepository = ServiceLocator.manageRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ManageUiState())
    val state: StateFlow<ManageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.restoreSession()
            val saved = ServiceLocator.settings.adminUserFlow.let { flow ->
                runCatching { kotlinx.coroutines.flow.first(flow) }.getOrDefault("")
            }
            _state.update { it.copy(username = saved) }
            if (saved.isNotBlank()) refresh()
        }
    }

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun onNewAliasChange(value: String) {
        _state.update { it.copy(newAlias = value) }
    }

    fun onNewTokenChange(value: String) {
        _state.update { it.copy(newToken = value) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.login(current.username, current.password)
                .onSuccess {
                    _state.update { it.copy(isLoggedIn = true, password = "", isLoading = false) }
                    refresh()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "登录失败",
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _state.update { it.copy(isLoggedIn = false, tokens = emptyList()) }
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.dashboard()
                .onSuccess { dashboard ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = dashboard.isLoggedIn,
                            tokens = dashboard.tokens,
                            accountsPath = dashboard.accountsPath,
                            accountsExists = dashboard.accountsExists,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.message ?: "加载失败")
                    }
                }
        }
    }

    fun addToken() {
        val current = _state.value
        if (current.newToken.isBlank()) {
            _state.update { it.copy(error = "请输入 Token") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.addToken(current.newAlias, current.newToken)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            newAlias = "",
                            newToken = "",
                            message = "Token 已添加",
                        )
                    }
                    refresh()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.message ?: "添加失败")
                    }
                }
        }
    }

    fun deleteToken(id: Int) {
        viewModelScope.launch {
            repository.deleteToken(id)
                .onSuccess {
                    _state.update { it.copy(message = "Token 已删除") }
                    refresh()
                }
                .onFailure { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "删除失败") }
                }
        }
    }

    /** Re-imports accounts.json, matching the dashboard's reload button. */
    fun reloadAccounts() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.reloadAccounts()
                .onSuccess {
                    _state.update { it.copy(isLoading = false, message = "已重新加载 accounts.json") }
                    refresh()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.message ?: "重载失败")
                    }
                }
        }
    }
}