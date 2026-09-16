package com.deeperseeker.app.data

import com.deeperseeker.app.di.ServiceLocator
import com.deeperseeker.app.net.ApiClient
import com.deeperseeker.app.net.TokenInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Drives the server's admin dashboard from the phone.
 *
 * The dashboard is server-rendered HTML, and there is no JSON API for token
 * management. Rather than change the server, this repository logs in exactly
 * like the browser does (capturing the `session_id` cookie), then scrapes the
 * two things the app needs — the token rows and the account-file status — out
 * of the returned markup. Parsing is defensive: a missing fragment degrades to
 * an empty list instead of crashing.
 */
class ManageRepository(private val settings: SettingsStore) {

    private fun api() = ServiceLocator.manageApi()

    /** Result of a dashboard refresh. */
    data class Dashboard(
        val tokens: List<TokenInfo>,
        val accountsPath: String?,
        val accountsExists: Boolean,
        val isLoggedIn: Boolean,
    )

    /** Signing in stores the session cookie for all later dashboard calls. */
    suspend fun login(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api().login(username, password)
                val setCookie = response.headers()["Set-Cookie"].orEmpty()
                val sessionId = extractSessionCookie(setCookie)
                if (sessionId == null) {
                    // FastAPI returns 200 with the login page on bad credentials,
                    // so a missing cookie is the authoritative failure signal.
                    error("用户名或密码错误")
                }
                ApiClient.sessionCookie.cookie = sessionId
                settings.setSessionCookie(sessionId)
                settings.setAdminUser(username)
                Unit
            }
        }

    /** Restores a previously captured cookie after an app restart. */
    suspend fun restoreSession() {
        val saved = settings.currentSessionCookie()
        if (saved.isNotBlank()) {
            ApiClient.sessionCookie.cookie = saved
        }
    }

    suspend fun logout() {
        ApiClient.sessionCookie.cookie = null
        settings.setSessionCookie("")
    }

    /** Loads and parses the dashboard page. */
    suspend fun dashboard(): Result<Dashboard> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api().dashboard()
            val html = response.body()?.string().orEmpty()
            val loggedIn = !html.contains("name=\"password\"") && html.contains("tokens")
            Dashboard(
                tokens = parseTokens(html),
                accountsPath = parseAccountsPath(html),
                accountsExists = !html.contains("accounts.json not found", ignoreCase = true) &&
                    !html.contains("未找到 accounts.json"),
                isLoggedIn = loggedIn,
            )
        }
    }

    suspend fun addToken(alias: String, authToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                api().addToken(alias, authToken)
                Unit
            }
        }

    suspend fun deleteToken(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            api().deleteToken(id)
            Unit
        }
    }

    /** Mirrors the "Reload accounts.json" button on the web dashboard. */
    suspend fun reloadAccounts(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            api().reloadAccounts()
            Unit
        }
    }

    /** Quick reachability probe for the connection settings screen. */
    suspend fun ping(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = api().health()
            if (!response.isSuccessful) error("HTTP ${response.code()}")
            System.currentTimeMillis() - start
        }
    }

    /* ---------------------------------------------------------------- */
    /*  HTML parsing                                                     */
    /* ---------------------------------------------------------------- */

    /**
     * Pulls token rows out of the dashboard table.
     *
     * The web template renders each token as a `<tr>` containing the alias,
     * the (truncated) token value and a status cell. The pattern is anchored on
     * the delete form action, which carries the numeric id and is the only
     * stable, unique marker in each row.
     */
    private fun parseTokens(html: String): List<TokenInfo> {
        val tokens = mutableListOf<TokenInfo>()
        val rowPattern = Pattern.compile(
            "<tr[^>]*>(.*?)</tr>",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
        val idPattern = Pattern.compile("/tokens/(\\d+)/delete")
        val cellPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

        val rowMatcher = rowPattern.matcher(html)
        while (rowMatcher.find()) {
            val row = rowMatcher.group(1) ?: continue
            val idMatcher = idPattern.matcher(row)
            if (!idMatcher.find()) continue
            val id = idMatcher.group(1)?.toIntOrNull() ?: continue

            val cells = mutableListOf<String>()
            val cellMatcher = cellPattern.matcher(row)
            while (cellMatcher.find()) {
                cells += stripTags(cellMatcher.group(1).orEmpty())
            }

            val status = cells.firstOrNull { it.contains("ACTIVE", true) || it.contains("RATE", true) }
            tokens += TokenInfo(
                id = id,
                alias = cells.getOrNull(0)?.takeIf { it.isNotBlank() },
                token = cells.getOrNull(1)?.takeIf { it.isNotBlank() },
                status = when {
                    status == null -> null
                    status.contains("RATE", true) -> "RATE_LIMITED"
                    else -> "ACTIVE"
                },
            )
        }
        return tokens
    }

    /** Extracts the `<code>` holding the resolved accounts.json path. */
    private fun parseAccountsPath(html: String): String? {
        val pattern = Pattern.compile(
            "accounts[^<]*<code>(.*?)</code>",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return stripTags(matcher.group(1).orEmpty()).trim()
        }
        // Fall back to any path-looking <code> that ends in accounts.json.
        val generic = Pattern.compile("<code>(.*?accounts\\.json.*?)</code>", Pattern.CASE_INSENSITIVE)
        val genericMatcher = generic.matcher(html)
        return if (genericMatcher.find()) stripTags(genericMatcher.group(1).orEmpty()).trim() else null
    }

    private fun stripTags(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractSessionCookie(setCookie: String): String? {
        // Set-Cookie: session_id=<uuid>; HttpOnly; SameSite=lax
        val match = Regex("session_id=([^;]+)").find(setCookie) ?: return null
        return "session_id=${match.groupValues[1]}"
    }
}