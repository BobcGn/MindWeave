#include <napi/native_api.h>

#include <algorithm>
#include <chrono>
#include <cctype>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
#include "mindweave_bridge.h"
#endif

namespace {

using UnaryBridgeFn = char* (*)(const char*);
using NullaryBridgeFn = char* (*)();

struct DemoDiaryEntry {
    std::string id;
    std::string title;
    std::string content;
    std::string mood;
    std::string ai_summary;
    std::vector<std::string> tags;
    int64_t created_at_epoch_ms;
    int64_t updated_at_epoch_ms;
};

struct DemoScheduleEvent {
    std::string id;
    std::string title;
    std::string description;
    int64_t start_time_epoch_ms;
    int64_t end_time_epoch_ms;
    std::string type;
    int64_t created_at_epoch_ms;
    int64_t updated_at_epoch_ms;
};

struct DemoChatSession {
    std::string id;
    std::string title;
    int64_t created_at_epoch_ms;
    int64_t updated_at_epoch_ms;
};

struct DemoChatMessage {
    std::string id;
    std::string session_id;
    std::string role;
    std::string content;
    int64_t created_at_epoch_ms;
    int64_t updated_at_epoch_ms;
};

struct DemoBridgeState {
    bool bootstrapped = false;
    std::string platform_name = "HarmonyOS";
    std::string user_id = "local-user";
    std::string device_id = "harmony-phone";
    std::string device_name = "Harmony Phone";
    std::string username = "MindWeave";
    std::string password = "MindWeave";
    bool must_change_credentials = true;
    int64_t created_at_epoch_ms = 0;
    int64_t updated_at_epoch_ms = 0;
    int64_t credentials_updated_at_epoch_ms = 0;
    int64_t last_login_at_epoch_ms = 0;
    std::string ai_mode = "LOCAL_ONLY";
    std::string ai_mode_label = "仅本地";
    std::string cloud_enhancement_base_url;
    std::string local_lightweight_model_package_id = "mindweave-lite-core";
    std::string local_generative_model_package_id = "mindweave-gen-core";
    std::string model_download_policy = "PREBUNDLED";
    std::string model_download_policy_label = "预置模型";
    int64_t pending_changes = 0;
    int64_t last_sync_seq = 0;
    int64_t diary_counter = 0;
    int64_t schedule_counter = 0;
    int64_t chat_counter = 0;
    int64_t message_counter = 0;
    std::vector<DemoDiaryEntry> diaries;
    std::vector<DemoScheduleEvent> schedules;
    std::vector<DemoChatSession> chat_sessions;
    std::vector<DemoChatMessage> chat_messages;
};

std::mutex& DemoStateMutex() {
    static std::mutex mutex;
    return mutex;
}

DemoBridgeState& DemoState() {
    static DemoBridgeState state;
    return state;
}

int64_t NowEpochMs() {
    const auto now = std::chrono::system_clock::now().time_since_epoch();
    return std::chrono::duration_cast<std::chrono::milliseconds>(now).count();
}

std::string EscapeJson(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size() + 16);
    for (char ch : value) {
        switch (ch) {
            case '\\':
                escaped += "\\\\";
                break;
            case '"':
                escaped += "\\\"";
                break;
            case '\n':
                escaped += "\\n";
                break;
            case '\r':
                escaped += "\\r";
                break;
            case '\t':
                escaped += "\\t";
                break;
            default:
                escaped.push_back(ch);
                break;
        }
    }
    return escaped;
}

std::string Quote(const std::string& value) {
    return "\"" + EscapeJson(value) + "\"";
}

std::string Trim(const std::string& value) {
    size_t start = 0;
    size_t end = value.size();
    while (start < end && std::isspace(static_cast<unsigned char>(value[start])) != 0) {
        ++start;
    }
    while (end > start && std::isspace(static_cast<unsigned char>(value[end - 1])) != 0) {
        --end;
    }
    return value.substr(start, end - start);
}

std::string ExtractJsonString(const std::string& json, const std::string& key) {
    const std::string pattern = "\"" + key + "\"";
    size_t key_pos = json.find(pattern);
    if (key_pos == std::string::npos) {
        return "";
    }
    size_t colon_pos = json.find(':', key_pos + pattern.size());
    if (colon_pos == std::string::npos) {
        return "";
    }
    size_t value_pos = colon_pos + 1;
    while (value_pos < json.size() && std::isspace(static_cast<unsigned char>(json[value_pos])) != 0) {
        ++value_pos;
    }
    if (value_pos >= json.size() || json[value_pos] != '"') {
        return "";
    }
    ++value_pos;
    std::string value;
    bool escaping = false;
    while (value_pos < json.size()) {
        const char ch = json[value_pos++];
        if (escaping) {
            switch (ch) {
                case '"':
                    value.push_back('"');
                    break;
                case '\\':
                    value.push_back('\\');
                    break;
                case 'n':
                    value.push_back('\n');
                    break;
                case 'r':
                    value.push_back('\r');
                    break;
                case 't':
                    value.push_back('\t');
                    break;
                default:
                    value.push_back(ch);
                    break;
            }
            escaping = false;
            continue;
        }
        if (ch == '\\') {
            escaping = true;
            continue;
        }
        if (ch == '"') {
            return value;
        }
        value.push_back(ch);
    }
    return "";
}

int64_t ExtractJsonInt64(const std::string& json, const std::string& key, int64_t default_value = 0) {
    const std::string pattern = "\"" + key + "\"";
    size_t key_pos = json.find(pattern);
    if (key_pos == std::string::npos) {
        return default_value;
    }
    size_t colon_pos = json.find(':', key_pos + pattern.size());
    if (colon_pos == std::string::npos) {
        return default_value;
    }
    size_t value_pos = colon_pos + 1;
    while (value_pos < json.size() && std::isspace(static_cast<unsigned char>(json[value_pos])) != 0) {
        ++value_pos;
    }
    size_t end_pos = value_pos;
    if (end_pos < json.size() && json[end_pos] == '-') {
        ++end_pos;
    }
    while (end_pos < json.size() && std::isdigit(static_cast<unsigned char>(json[end_pos])) != 0) {
        ++end_pos;
    }
    if (end_pos == value_pos) {
        return default_value;
    }
    return std::stoll(json.substr(value_pos, end_pos - value_pos));
}

std::vector<std::string> ExtractJsonStringArray(const std::string& json, const std::string& key) {
    const std::string pattern = "\"" + key + "\"";
    size_t key_pos = json.find(pattern);
    if (key_pos == std::string::npos) {
        return {};
    }
    size_t colon_pos = json.find(':', key_pos + pattern.size());
    if (colon_pos == std::string::npos) {
        return {};
    }
    size_t value_pos = json.find('[', colon_pos + 1);
    if (value_pos == std::string::npos) {
        return {};
    }
    ++value_pos;

    std::vector<std::string> values;
    while (value_pos < json.size()) {
        while (value_pos < json.size() && std::isspace(static_cast<unsigned char>(json[value_pos])) != 0) {
            ++value_pos;
        }
        if (value_pos >= json.size() || json[value_pos] == ']') {
            break;
        }
        if (json[value_pos] != '"') {
            ++value_pos;
            continue;
        }
        ++value_pos;

        std::string value;
        bool escaping = false;
        while (value_pos < json.size()) {
            const char ch = json[value_pos++];
            if (escaping) {
                switch (ch) {
                    case '"':
                        value.push_back('"');
                        break;
                    case '\\':
                        value.push_back('\\');
                        break;
                    case 'n':
                        value.push_back('\n');
                        break;
                    case 'r':
                        value.push_back('\r');
                        break;
                    case 't':
                        value.push_back('\t');
                        break;
                    default:
                        value.push_back(ch);
                        break;
                }
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                break;
            }
            value.push_back(ch);
        }
        values.push_back(value);
        while (value_pos < json.size() && json[value_pos] != ',' && json[value_pos] != ']') {
            ++value_pos;
        }
        if (value_pos < json.size() && json[value_pos] == ',') {
            ++value_pos;
        }
    }
    return values;
}

std::string BuildStringArrayJson(const std::vector<std::string>& values) {
    std::ostringstream out;
    out << "[";
    for (size_t index = 0; index < values.size(); ++index) {
        if (index > 0) {
            out << ",";
        }
        out << Quote(values[index]);
    }
    out << "]";
    return out.str();
}

std::string ResolveFocusSessionId(const DemoBridgeState& state, const std::string& requested_session_id) {
    if (!requested_session_id.empty()) {
        const auto it = std::find_if(
            state.chat_sessions.begin(),
            state.chat_sessions.end(),
            [&](const DemoChatSession& session) { return session.id == requested_session_id; }
        );
        if (it != state.chat_sessions.end()) {
            return requested_session_id;
        }
    }
    if (!state.chat_sessions.empty()) {
        return state.chat_sessions.front().id;
    }
    return "";
}

void EnsureDemoBootstrapped(DemoBridgeState& state) {
    if (state.bootstrapped) {
        return;
    }

    state.bootstrapped = true;
    const int64_t now = NowEpochMs();
    state.created_at_epoch_ms = now;
    state.updated_at_epoch_ms = now;
    state.credentials_updated_at_epoch_ms = now;
    state.last_login_at_epoch_ms = 0;

    state.diary_counter = 1;
    state.diaries.push_back(DemoDiaryEntry{
        .id = "diary-1",
        .title = "欢迎来到 MindWeave",
        .content = "该占位桥接不应在正式构建中启用，请检查 Kotlin/Native 发布流程。",
        .mood = "CALM",
        .ai_summary = "如果看到这条数据，说明真实桥接没有正确接管。",
        .tags = {"HarmonyOS", "Demo"},
        .created_at_epoch_ms = now - 60 * 60 * 1000,
        .updated_at_epoch_ms = now - 60 * 60 * 1000
    });

    state.schedule_counter = 1;
    state.schedules.push_back(DemoScheduleEvent{
        .id = "schedule-1",
        .title = "检查 Harmony 首页",
        .description = "确认日记、日程、聊天和同步按钮都能正常操作。",
        .start_time_epoch_ms = now + 60 * 60 * 1000,
        .end_time_epoch_ms = now + 2 * 60 * 60 * 1000,
        .type = "WORK",
        .created_at_epoch_ms = now,
        .updated_at_epoch_ms = now
    });

    state.chat_counter = 1;
    state.chat_sessions.push_back(DemoChatSession{
        .id = "chat-1",
        .title = "Harmony Demo",
        .created_at_epoch_ms = now,
        .updated_at_epoch_ms = now
    });

    state.message_counter = 1;
    state.chat_messages.push_back(DemoChatMessage{
        .id = "message-1",
        .session_id = "chat-1",
        .role = "ASSISTANT",
        .content = "该占位桥接仅用于提示配置错误，请切换到真实 Kotlin/Native 桥接。",
        .created_at_epoch_ms = now,
        .updated_at_epoch_ms = now
    });
}

std::string BuildSnapshotJson(const DemoBridgeState& state, const std::string& selected_session_id) {
    const std::string focus_session_id = ResolveFocusSessionId(state, selected_session_id);

    std::ostringstream out;
    out << "{";
    out << "\"platformName\":" << Quote(state.platform_name) << ",";
    out << "\"session\":{";
    out << "\"userId\":" << Quote(state.user_id) << ",";
    out << "\"deviceId\":" << Quote(state.device_id) << ",";
    out << "\"deviceName\":" << Quote(state.device_name);
    out << "},";
    out << "\"account\":{";
    out << "\"username\":" << Quote(state.username) << ",";
    out << "\"mustChangeCredentials\":" << (state.must_change_credentials ? "true" : "false") << ",";
    out << "\"createdAtEpochMs\":" << state.created_at_epoch_ms << ",";
    out << "\"updatedAtEpochMs\":" << state.updated_at_epoch_ms << ",";
    out << "\"credentialsUpdatedAtEpochMs\":" << state.credentials_updated_at_epoch_ms << ",";
    out << "\"lastLoginAtEpochMs\":" << state.last_login_at_epoch_ms;
    out << "},";
    out << "\"preferences\":{";
    out << "\"aiMode\":" << Quote(state.ai_mode) << ",";
    out << "\"aiModeLabel\":" << Quote(state.ai_mode_label) << ",";
    out << "\"cloudEnhancementBaseUrl\":" << Quote(state.cloud_enhancement_base_url) << ",";
    out << "\"localLightweightModelPackageId\":" << Quote(state.local_lightweight_model_package_id) << ",";
    out << "\"localGenerativeModelPackageId\":" << Quote(state.local_generative_model_package_id) << ",";
    out << "\"modelDownloadPolicy\":" << Quote(state.model_download_policy) << ",";
    out << "\"modelDownloadPolicyLabel\":" << Quote(state.model_download_policy_label);
    out << "},";

    out << "\"timeline\":[";
    for (size_t index = 0; index < state.diaries.size(); ++index) {
        if (index > 0) {
            out << ",";
        }
        const DemoDiaryEntry& diary = state.diaries[index];
        out << "{";
        out << "\"entry\":{";
        out << "\"id\":" << Quote(diary.id) << ",";
        out << "\"title\":" << Quote(diary.title) << ",";
        out << "\"content\":" << Quote(diary.content) << ",";
        out << "\"mood\":" << Quote(diary.mood) << ",";
        out << "\"aiSummary\":" << Quote(diary.ai_summary) << ",";
        out << "\"createdAtEpochMs\":" << diary.created_at_epoch_ms << ",";
        out << "\"updatedAtEpochMs\":" << diary.updated_at_epoch_ms;
        out << "},";
        out << "\"tags\":" << BuildStringArrayJson(diary.tags);
        out << "}";
    }
    out << "],";

    out << "\"schedules\":[";
    for (size_t index = 0; index < state.schedules.size(); ++index) {
        if (index > 0) {
            out << ",";
        }
        const DemoScheduleEvent& schedule = state.schedules[index];
        out << "{";
        out << "\"id\":" << Quote(schedule.id) << ",";
        out << "\"title\":" << Quote(schedule.title) << ",";
        out << "\"description\":" << Quote(schedule.description) << ",";
        out << "\"startTimeEpochMs\":" << schedule.start_time_epoch_ms << ",";
        out << "\"endTimeEpochMs\":" << schedule.end_time_epoch_ms << ",";
        out << "\"type\":" << Quote(schedule.type) << ",";
        out << "\"createdAtEpochMs\":" << schedule.created_at_epoch_ms << ",";
        out << "\"updatedAtEpochMs\":" << schedule.updated_at_epoch_ms;
        out << "}";
    }
    out << "],";

    out << "\"chatSessions\":[";
    for (size_t index = 0; index < state.chat_sessions.size(); ++index) {
        if (index > 0) {
            out << ",";
        }
        const DemoChatSession& session = state.chat_sessions[index];
        out << "{";
        out << "\"id\":" << Quote(session.id) << ",";
        out << "\"title\":" << Quote(session.title) << ",";
        out << "\"createdAtEpochMs\":" << session.created_at_epoch_ms << ",";
        out << "\"updatedAtEpochMs\":" << session.updated_at_epoch_ms;
        out << "}";
    }
    out << "],";

    out << "\"conversation\":[";
    bool first_message = true;
    for (const DemoChatMessage& message : state.chat_messages) {
        if (!focus_session_id.empty() && message.session_id != focus_session_id) {
            continue;
        }
        if (!first_message) {
            out << ",";
        }
        first_message = false;
        out << "{";
        out << "\"id\":" << Quote(message.id) << ",";
        out << "\"sessionId\":" << Quote(message.session_id) << ",";
        out << "\"role\":" << Quote(message.role) << ",";
        out << "\"content\":" << Quote(message.content) << ",";
        out << "\"createdAtEpochMs\":" << message.created_at_epoch_ms << ",";
        out << "\"updatedAtEpochMs\":" << message.updated_at_epoch_ms;
        out << "}";
    }
    out << "],";

    out << "\"syncState\":{";
    out << "\"pendingChanges\":" << state.pending_changes << ",";
    out << "\"lastSyncSeq\":" << state.last_sync_seq;
    out << "}";
    out << "}";
    return out.str();
}

std::string BuildResponseJson(
    const DemoBridgeState& state,
    const std::string& message,
    const std::string& selected_session_id = "",
    const std::string& focus_session_id = "",
    bool ok = true
) {
    const std::string resolved_focus_session_id = ResolveFocusSessionId(state, focus_session_id.empty() ? selected_session_id : focus_session_id);

    std::ostringstream out;
    out << "{";
    out << "\"ok\":" << (ok ? "true" : "false") << ",";
    out << "\"message\":" << Quote(message) << ",";
    if (!resolved_focus_session_id.empty()) {
        out << "\"focusSessionId\":" << Quote(resolved_focus_session_id) << ",";
    }
    out << "\"snapshot\":" << BuildSnapshotJson(state, resolved_focus_session_id);
    out << "}";
    return out.str();
}

std::string TruncateTitle(const std::string& value) {
    const std::string trimmed = Trim(value);
    if (trimmed.empty()) {
        return "新会话";
    }
    if (trimmed.size() <= 18) {
        return trimmed;
    }
    return trimmed.substr(0, 18);
}

std::string BuildAssistantReply(const std::string& prompt) {
    return "已收到你的消息：" + Trim(prompt) + "。当前构建应当使用真实 Kotlin/Native 桥接。";
}

std::string GetUtf8(napi_env env, napi_value value) {
    if (value == nullptr) {
        return "{}";
    }
    size_t length = 0;
    napi_get_value_string_utf8(env, value, nullptr, 0, &length);
    std::string buffer(length + 1, '\0');
    napi_get_value_string_utf8(env, value, buffer.data(), length + 1, &length);
    buffer.resize(length);
    return buffer;
}

napi_value NewUtf8(napi_env env, const std::string& value) {
    napi_value result = nullptr;
    napi_create_string_utf8(env, value.c_str(), value.size(), &result);
    return result;
}

#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
std::string CallBridge(UnaryBridgeFn bridge_fn, const std::string& payload) {
    char* raw = bridge_fn(payload.c_str());
    if (raw == nullptr) {
        return R"({"ok":false,"message":"Kotlin bridge returned null."})";
    }
    std::string response(raw);
    mindweave_bridge_dispose_string(raw);
    return response;
}

std::string CallBridge(NullaryBridgeFn bridge_fn) {
    char* raw = bridge_fn();
    if (raw == nullptr) {
        return R"({"ok":false,"message":"Kotlin bridge returned null."})";
    }
    std::string response(raw);
    mindweave_bridge_dispose_string(raw);
    return response;
}
#else
std::string MissingRealBridgeResponse() {
    return R"({"ok":false,"message":"Harmony 真实桥接未就绪。请先发布 libmindweave.so，并以 kotlin 模式重新构建 entry 模块。"})";
}

std::string DemoBootstrap(const std::string&) {
    return MissingRealBridgeResponse();
}

std::string DemoGetSnapshot(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoCaptureDiary(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoCaptureSchedule(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoSendChatMessage(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoRunSync() {
    return MissingRealBridgeResponse();
}

std::string DemoSavePreferences(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoAuthenticate(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoForceResetCredentials(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}

std::string DemoChangeCredentials(const std::string& payload) {
    (void)payload;
    return MissingRealBridgeResponse();
}
#endif

napi_value Bootstrap(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_bootstrap, payload));
#else
    return NewUtf8(env, DemoBootstrap(payload));
#endif
}

napi_value GetSnapshot(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_get_snapshot, payload));
#else
    return NewUtf8(env, DemoGetSnapshot(payload));
#endif
}

napi_value CaptureDiary(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_capture_diary, payload));
#else
    return NewUtf8(env, DemoCaptureDiary(payload));
#endif
}

napi_value CaptureSchedule(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_capture_schedule, payload));
#else
    return NewUtf8(env, DemoCaptureSchedule(payload));
#endif
}

napi_value SendChatMessage(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_send_chat_message, payload));
#else
    return NewUtf8(env, DemoSendChatMessage(payload));
#endif
}

napi_value SavePreferences(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_save_preferences, payload));
#else
    return NewUtf8(env, DemoSavePreferences(payload));
#endif
}

napi_value Authenticate(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_authenticate, payload));
#else
    return NewUtf8(env, DemoAuthenticate(payload));
#endif
}

napi_value ForceResetCredentials(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_force_reset_credentials, payload));
#else
    return NewUtf8(env, DemoForceResetCredentials(payload));
#endif
}

napi_value ChangeCredentials(napi_env env, napi_callback_info info) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_change_credentials, payload));
#else
    return NewUtf8(env, DemoChangeCredentials(payload));
#endif
}

napi_value RunSync(napi_env env, napi_callback_info info) {
    (void)info;
#ifdef MINDWEAVE_USE_KOTLIN_BRIDGE
    return NewUtf8(env, CallBridge(mindweave_bridge_run_sync));
#else
    return NewUtf8(env, DemoRunSync());
#endif
}

}  // namespace

EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor descriptors[] = {
        {"bootstrap", nullptr, Bootstrap, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"getSnapshot", nullptr, GetSnapshot, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"captureDiary", nullptr, CaptureDiary, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"captureSchedule", nullptr, CaptureSchedule, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"sendChatMessage", nullptr, SendChatMessage, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"runSync", nullptr, RunSync, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"savePreferences", nullptr, SavePreferences, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"authenticate", nullptr, Authenticate, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"forceResetCredentials", nullptr, ForceResetCredentials, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"changeCredentials", nullptr, ChangeCredentials, nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(descriptors) / sizeof(descriptors[0]), descriptors);
    return exports;
}
EXTERN_C_END

static napi_module mindweave_module = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "mindweave_bridge",
    .nm_priv = nullptr,
    .reserved = {nullptr},
};

extern "C" __attribute__((constructor)) void RegisterMindWeaveBridgeModule() {
    napi_module_register(&mindweave_module);
}
