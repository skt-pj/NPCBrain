cmake_minimum_required(VERSION 3.22.1)
project(npcbrain_clef LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)

if (ANDROID AND DEFINED ANDROID_NDK AND NOT Vulkan_GLSLC_EXECUTABLE)
    if (CMAKE_HOST_SYSTEM_NAME STREQUAL "Linux")
        set(_npcbrain_shader_host "linux-x86_64")
        set(_npcbrain_glslc_name "glslc")
    elseif (CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
        set(_npcbrain_shader_host "darwin-x86_64")
        set(_npcbrain_glslc_name "glslc")
    elseif (CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows")
        set(_npcbrain_shader_host "windows-x86_64")
        set(_npcbrain_glslc_name "glslc.exe")
    endif()

    if (DEFINED _npcbrain_shader_host)
        set(_npcbrain_glslc
                "${ANDROID_NDK}/shader-tools/${_npcbrain_shader_host}/${_npcbrain_glslc_name}")
        if (EXISTS "${_npcbrain_glslc}")
            set(Vulkan_GLSLC_EXECUTABLE "${_npcbrain_glslc}" CACHE FILEPATH "" FORCE)
        endif()
    endif()
endif()

include(FetchContent)

FetchContent_Declare(
        SPIRV-Headers
        GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers.git
        GIT_TAG 86f980c731e62ae4eaf383d320449d71687936bf
        GIT_SHALLOW FALSE
        OVERRIDE_FIND_PACKAGE
)
set(SPIRV_HEADERS_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(SPIRV_HEADERS_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(SPIRV-Headers)
FetchContent_GetProperties(SPIRV-Headers SOURCE_DIR spirv_headers_source_dir)
include_directories("${spirv_headers_source_dir}/include")

FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG 99b95488cac0f00ce3f05af113a8c1e287753f87
        GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(npcbrain_clef SHARED clef_jni.cpp)

target_include_directories(npcbrain_clef PRIVATE
        ${llama_cpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}/src
        ${llama_cpp_SOURCE_DIR}/ggml/include
)

target_link_libraries(npcbrain_clef
        llama
        android
        log
)
