"""Minimal i18n for the DeeperSeeker dashboard (English + 简体中文).

Language selection priority per request:
  1. ``?lang=zh|en`` query parameter (also persisted as a cookie)
  2. ``ds_lang`` cookie
  3. ``Accept-Language`` header (auto-detect zh / others -> en)

The active language is kept in a ContextVar so the ``t()`` helper used inside
Jinja templates resolves to the request's language without extra plumbing.
"""

import re
from contextvars import ContextVar

AVAILABLE = ("en", "zh")
DEFAULT_LANG = "en"
LANG_COOKIE = "ds_lang"

TRANSLATIONS = {
    "en": {
        "nav_dashboard": "Dashboard",
        "nav_logout": "Logout",
        "lang_en": "EN",
        "lang_zh": "中文",
        "login_title": "Login",
        "username": "Username",
        "password": "Password",
        "login_button": "Login",
        "login_error_locked": "Too many attempts. Try again later.",
        "login_error_invalid": "Invalid username or password",
        "dashboard_title": "Dashboard",
        "add_token_title": "Add Auth Token",
        "steps": "Steps:",
        "step1": "Open an <strong>incognito/private</strong> window",
        "step2_prefix": "Go to ",
        "step2_suffix": " and log in",
        "step3": "Open DevTools (F12) → Console → paste:",
        "step4": "Copy the token and paste it below",
        "step5": "Remove any surrounding quotes — paste only the raw token string",
        "step6": "Close the incognito window to preserve the login",
        "token_warning": "If you log out of DeepSeek in the browser, this token will stop working.",
        "accounts_title": "Multi-account File",
        "accounts_desc": "Accounts are imported automatically at startup from <code>accounts.json</code> (browser-extension export format). Click reload after editing that file.",
        "accounts_path_label": "Current file path:",
        "accounts_not_found": "accounts.json not found at this path — create it to bulk-import accounts.",
        "accounts_reload_button": "Reload accounts.json",
        "alias_placeholder": "Alias (optional, e.g. personal, work)",
        "token_placeholder": "Paste auth token here (no quotes)",
        "add_token_button": "Add Token",
        "tokens_header": "Tokens",
        "col_id": "ID",
        "col_alias": "Alias",
        "col_token": "Token",
        "col_status": "Status",
        "col_actions": "Actions",
        "delete_button": "Delete",
        "confirm_delete": "Delete this token?",
        "no_tokens": "No tokens yet.",
        "api_title": "API",
        "api_openai": "OpenAI:",
        "api_anthropic": "Anthropic:",
        "api_model": "Model:",
        "api_model_note": "(single default DeepSeek model)",
    },
    "zh": {
        "nav_dashboard": "仪表盘",
        "nav_logout": "退出登录",
        "lang_en": "EN",
        "lang_zh": "中文",
        "login_title": "登录",
        "username": "用户名",
        "password": "密码",
        "login_button": "登录",
        "login_error_locked": "尝试次数过多，请稍后再试。",
        "login_error_invalid": "用户名或密码错误",
        "dashboard_title": "仪表盘",
        "add_token_title": "添加认证令牌",
        "steps": "操作步骤：",
        "step1": "打开一个<strong>无痕/隐私</strong>窗口",
        "step2_prefix": "访问 ",
        "step2_suffix": " 并登录",
        "step3": "打开开发者工具（F12）→ 控制台 → 粘贴：",
        "step4": "复制令牌并粘贴到下方",
        "step5": "去掉首尾的引号——只粘贴原始令牌字符串",
        "step6": "关闭无痕窗口以保留登录状态",
        "token_warning": "如果在浏览器中退出 DeepSeek 登录，该令牌将失效。",
        "accounts_title": "多账号配置文件",
        "accounts_desc": "启动时会自动从 <code>accounts.json</code>（浏览器扩展导出格式）导入账号。修改该文件后点击重新加载即可生效。",
        "accounts_path_label": "当前文件路径：",
        "accounts_not_found": "该路径下未找到 accounts.json——创建后即可批量导入账号。",
        "accounts_reload_button": "重新加载 accounts.json",
        "alias_placeholder": "别名（可选，例如：个人、工作）",
        "token_placeholder": "在此粘贴认证令牌（不要带引号）",
        "add_token_button": "添加令牌",
        "tokens_header": "令牌",
        "col_id": "ID",
        "col_alias": "别名",
        "col_token": "令牌",
        "col_status": "状态",
        "col_actions": "操作",
        "delete_button": "删除",
        "confirm_delete": "确定要删除该令牌吗？",
        "no_tokens": "暂无令牌。",
        "api_title": "API",
        "api_openai": "OpenAI：",
        "api_anthropic": "Anthropic：",
        "api_model": "模型：",
        "api_model_note": "（唯一的默认 DeepSeek 模型）",
    },
}

_lang = ContextVar("ds_lang", default=DEFAULT_LANG)


def set_lang(code):
    _lang.set(code if code in AVAILABLE else DEFAULT_LANG)


def get_lang():
    return _lang.get()


def html_lang():
    return "zh-CN" if _lang.get() == "zh" else "en"


def t(key, **kwargs):
    """Translate ``key`` for the active request language, falling back to en."""
    table = TRANSLATIONS.get(_lang.get(), TRANSLATIONS[DEFAULT_LANG])
    text = table.get(key, TRANSLATIONS[DEFAULT_LANG].get(key, key))
    return text.format(**kwargs) if kwargs else text


def choose_lang(request):
    """Decide language from query param → cookie → Accept-Language header."""
    q = request.query_params.get("lang")
    if q in AVAILABLE:
        return q
    cookie = request.cookies.get(LANG_COOKIE)
    if cookie in AVAILABLE:
        return cookie
    accept = request.headers.get("accept-language", "")
    return "zh" if re.search(r"zh|cmn|zh-cn|zh-hans", accept, re.IGNORECASE) else DEFAULT_LANG
