#include <napi/native_api.h>
#include <string>

#include "mindweave_bridge.h"

namespace {

using UnaryBridgeFn = char* (*)(const char*);
using NullaryBridgeFn = char* (*)();

std::string GetUtf8(napi_env env, napi_value value) {
    if (value == nullptr) {
        return "{}";
    }
    size_t length = 0;
    napi_get_value_string_utf8(env, value, nullptr, 0, &length);
    std::string buffer(length, '\0');
    napi_get_value_string_utf8(env, value, buffer.data(), length + 1, &length);
    return buffer;
}

napi_value NewUtf8(napi_env env, const std::string& value) {
    napi_value result = nullptr;
    napi_create_string_utf8(env, value.c_str(), value.size(), &result);
    return result;
}

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

napi_value ExecuteUnary(napi_env env, napi_callback_info info, UnaryBridgeFn bridge_fn) {
    size_t argc = 1;
    napi_value argv[1] = {nullptr};
    napi_get_cb_info(env, info, &argc, argv, nullptr, nullptr);
    const std::string payload = argc > 0 ? GetUtf8(env, argv[0]) : "{}";
    return NewUtf8(env, CallBridge(bridge_fn, payload));
}

napi_value Bootstrap(napi_env env, napi_callback_info info) {
    return ExecuteUnary(env, info, mindweave_bridge_bootstrap);
}

napi_value GetSnapshot(napi_env env, napi_callback_info info) {
    return ExecuteUnary(env, info, mindweave_bridge_get_snapshot);
}

napi_value CaptureDiary(napi_env env, napi_callback_info info) {
    return ExecuteUnary(env, info, mindweave_bridge_capture_diary);
}

napi_value CaptureSchedule(napi_env env, napi_callback_info info) {
    return ExecuteUnary(env, info, mindweave_bridge_capture_schedule);
}

napi_value SendChatMessage(napi_env env, napi_callback_info info) {
    return ExecuteUnary(env, info, mindweave_bridge_send_chat_message);
}

napi_value SavePreferences(napi_env env, napi_callback_info info) {
    return ExecuteUnary(env, info, mindweave_bridge_save_preferences);
}

napi_value RunSync(napi_env env, napi_callback_info info) {
    (void)info;
    return NewUtf8(env, CallBridge(mindweave_bridge_run_sync));
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
