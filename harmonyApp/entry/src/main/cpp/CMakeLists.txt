cmake_minimum_required(VERSION 3.5.0)
project(mindweave_bridge)

set(CMAKE_CXX_STANDARD 17)
set(NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR})
set(MINDWEAVE_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../libs/arm64-v8a)
set(MINDWEAVE_SHARED_LIB ${MINDWEAVE_LIB_DIR}/libmindweave.so)
set(MINDWEAVE_BRIDGE_MODE_FILE ${MINDWEAVE_LIB_DIR}/mindweave_bridge_mode.txt)
set(MINDWEAVE_BRIDGE_MODE "auto")

include_directories(${NATIVE_ROOT}/thirdparty/mindweave)

add_library(mindweave_bridge SHARED napi_init.cpp)

if(EXISTS ${MINDWEAVE_BRIDGE_MODE_FILE})
    file(READ ${MINDWEAVE_BRIDGE_MODE_FILE} MINDWEAVE_BRIDGE_MODE)
    string(STRIP "${MINDWEAVE_BRIDGE_MODE}" MINDWEAVE_BRIDGE_MODE)
endif()

if(NOT EXISTS ${MINDWEAVE_SHARED_LIB})
    message(FATAL_ERROR
        "Real Harmony bridge requires libmindweave.so at ${MINDWEAVE_SHARED_LIB}. "
        "Run ./2.0_ohos_mindweave_build.sh Debug kba or publishDebugBinariesToHarmonyApp first."
    )
endif()

if(NOT MINDWEAVE_BRIDGE_MODE STREQUAL "kotlin")
    message(FATAL_ERROR
        "Real Harmony bridge requires mindweave_bridge_mode.txt to be 'kotlin'. "
        "Current mode='${MINDWEAVE_BRIDGE_MODE}'. Republish the Harmony native outputs."
    )
endif()

add_library(mindweave SHARED IMPORTED)
set_target_properties(mindweave PROPERTIES
    IMPORTED_LOCATION ${MINDWEAVE_SHARED_LIB}
)
target_compile_definitions(mindweave_bridge PRIVATE MINDWEAVE_USE_KOTLIN_BRIDGE=1)
target_link_libraries(mindweave_bridge PUBLIC mindweave libace_napi.z.so libhilog_ndk.z.so)
