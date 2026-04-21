cmake_minimum_required(VERSION 3.5.0)
project(mindweave_bridge)

set(CMAKE_CXX_STANDARD 17)
set(NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR})
set(MINDWEAVE_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../libs/arm64-v8a)

include_directories(${NATIVE_ROOT}/thirdparty/mindweave)

add_library(mindweave SHARED IMPORTED)
set_target_properties(mindweave PROPERTIES
    IMPORTED_LOCATION ${MINDWEAVE_LIB_DIR}/libmindweave.so
)

add_library(mindweave_bridge SHARED napi_init.cpp)

target_link_libraries(mindweave_bridge PUBLIC mindweave libace_napi.z.so libhilog_ndk.z.so)
