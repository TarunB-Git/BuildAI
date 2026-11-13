cmake_minimum_required(VERSION 3.31.6)
# All llama.cpp shared libraries exchange C++ objects and exceptions. Select one STL before the NDK toolchain initializes.
set(ANDROID_STL c++_shared CACHE STRING "C++ runtime shared by all native libraries" FORCE)
project(buildai_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)

# Keep native compilation bounded on developer laptops. Optimize inference in debug APKs too.
set_property(GLOBAL PROPERTY JOB_POOLS buildai_compile=1 buildai_link=1)
set(CMAKE_JOB_POOL_COMPILE buildai_compile)
set(CMAKE_JOB_POOL_LINK buildai_link)
# Android's toolchain provides Debug flags in normal variables, overriding cache entries.
add_compile_options(-O3 "$<$<CONFIG:Debug>:-g1>")
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp
                 ${CMAKE_CURRENT_BINARY_DIR}/llama)
add_library(buildai_llama SHARED local_llm.cpp)
target_include_directories(buildai_llama PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/include
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/common)
target_link_libraries(buildai_llama PRIVATE llama llama-common log)
