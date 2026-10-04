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
set(GGML_VULKAN_CHECK_RESULTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_VALIDATE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_RUN_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_DEBUG OFF CACHE BOOL "" FORCE)

# Android NDK ships a host glslc under shader-tools. Point llama.cpp at it explicitly
# so Vulkan shaders are generated during the Android cross-build.
if(ANDROID AND DEFINED ANDROID_NDK)
    file(GLOB NPCBRAIN_NDK_GLSLC_DIRS "${ANDROID_NDK}/shader-tools/*")
    find_program(
            NPCBRAIN_GLSLC
            NAMES glslc
            HINTS ${NPCBRAIN_NDK_GLSLC_DIRS}
            NO_CMAKE_FIND_ROOT_PATH)
    if(NPCBRAIN_GLSLC)
        set(Vulkan_GLSLC_EXECUTABLE "${NPCBRAIN_GLSLC}" CACHE FILEPATH "" FORCE)
    endif()
endif()

include(FetchContent)
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