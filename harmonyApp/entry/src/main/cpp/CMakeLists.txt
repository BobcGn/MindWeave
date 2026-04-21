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

if(EXISTS ${MINDWEAVE_SHARED_LIB} AND NOT MINDWEAVE_BRIDGE_MODE STREQUAL "demo")
    add_library(mindweave SHARED IMPORTED)
    set_target_properties(mindweave PROPERTIES
        IMPORTED_LOCATION ${MINDWEAVE_SHARED_LIB}
    )
    target_compile_definitions(mindweave_bridge PRIVATE MINDWEAVE_USE_KOTLIN_BRIDGE=1)
    target_link_libraries(mindweave_bridge PUBLIC mindweave libace_napi.z.so libhilog_ndk.z.so)
else()
    message(WARNING "Using demo bridge fallback. mode=${MINDWEAVE_BRIDGE_MODE}")
    target_link_libraries(mindweave_bridge PUBLIC libace_napi.z.so libhilog_ndk.z.so)
endif()
