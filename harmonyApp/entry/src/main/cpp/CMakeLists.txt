cmake_minimum_required(VERSION 3.5.0)
project(mindweave_bridge)

set(CMAKE_CXX_STANDARD 17)
set(NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR})
set(MINDWEAVE_ARCH ${CMAKE_OHOS_ARCH_ABI})
if(NOT MINDWEAVE_ARCH AND DEFINED OHOS_ARCH)
    set(MINDWEAVE_ARCH ${OHOS_ARCH})
endif()
if(NOT MINDWEAVE_ARCH)
    set(MINDWEAVE_ARCH arm64-v8a)
endif()

set(MINDWEAVE_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../libs/${MINDWEAVE_ARCH})
set(MINDWEAVE_SHARED_LIB ${MINDWEAVE_LIB_DIR}/libmindweave.so)
set(MINDWEAVE_BRIDGE_MODE_FILE ${MINDWEAVE_LIB_DIR}/mindweave_bridge_mode.txt)
set(MINDWEAVE_BRIDGE_MODE "auto")
set(MINDWEAVE_USE_KOTLIN_BRIDGE OFF)

include_directories(${NATIVE_ROOT}/thirdparty/mindweave)

add_library(mindweave_bridge SHARED napi_init.cpp)

if(EXISTS ${MINDWEAVE_BRIDGE_MODE_FILE})
    file(READ ${MINDWEAVE_BRIDGE_MODE_FILE} MINDWEAVE_BRIDGE_MODE)
    string(STRIP "${MINDWEAVE_BRIDGE_MODE}" MINDWEAVE_BRIDGE_MODE)
endif()

if(EXISTS ${MINDWEAVE_SHARED_LIB} AND MINDWEAVE_BRIDGE_MODE STREQUAL "kotlin")
    set(MINDWEAVE_USE_KOTLIN_BRIDGE ON)
elseif(NOT EXISTS ${MINDWEAVE_SHARED_LIB})
    message(WARNING
        "libmindweave.so is missing at ${MINDWEAVE_SHARED_LIB}. "
        "Building the Harmony NAPI shell only; runtime bridge calls will report that the real bridge is not ready."
    )
else()
    message(WARNING
        "mindweave_bridge_mode.txt is '${MINDWEAVE_BRIDGE_MODE}', not 'kotlin'. "
        "Building the Harmony NAPI shell only; republish the OHOS native outputs to enable the real bridge."
    )
endif()

if(MINDWEAVE_USE_KOTLIN_BRIDGE)
    add_library(mindweave SHARED IMPORTED)
    set_target_properties(mindweave PROPERTIES
        IMPORTED_LOCATION ${MINDWEAVE_SHARED_LIB}
    )
    target_compile_definitions(mindweave_bridge PRIVATE MINDWEAVE_USE_KOTLIN_BRIDGE=1)
    target_link_libraries(mindweave_bridge PUBLIC mindweave)
endif()

target_link_libraries(mindweave_bridge PUBLIC libace_napi.z.so libhilog_ndk.z.so)
