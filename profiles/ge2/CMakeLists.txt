if(NOT ANDROID)
    message(FATAL_ERROR "The ge2 profile currently targets Android NDK builds only.")
endif()

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "God Eater 2 profile currently supports only arm64-v8a; got: ${ANDROID_ABI}")
endif()

add_library(ge2_native SHARED
    host/ge2_android.cpp
)

target_include_directories(ge2_native PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/host"
)

target_link_libraries(ge2_native PRIVATE
    psprecomp_core
    android
    log
)

target_compile_definitions(ge2_native PRIVATE
    GE2_PROFILE_ID="ge2"
    GE2_TARGET_ABI="arm64-v8a"
)

target_compile_options(ge2_native PRIVATE
    -Wall -Wextra -Wpedantic -Wconversion -Wshadow
)

set_target_properties(ge2_native PROPERTIES
    OUTPUT_NAME "ge2_native"
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)
