#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
BUILD_DIR="${GE2_BUILD_DIR:-$ROOT_DIR/out/ge2-android-arm64}"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -n "${ANDROID_NDK_ROOT:-}" ]; then
        ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
    elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
        # Pick the lexicographically last installed NDK directory. Set
        # ANDROID_NDK_HOME explicitly if you need a particular version.
        ANDROID_NDK_HOME="$(find "$ANDROID_SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
    fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]; then
    echo "Set ANDROID_NDK_HOME to an installed Android NDK directory." >&2
    exit 2
fi

cmake -S "$ROOT_DIR" -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DPSPRECOMP_PROFILE=ge2 \
    -DPSPRECOMP_BUILD_TESTS=OFF \
    -DPSPRECOMP_BUILD_PROFILE_TESTS=OFF \
    -DCMAKE_BUILD_TYPE=Release

cmake --build "$BUILD_DIR" --target ge2_native
printf '\nNative library output: %s/libge2_native.so\n' "$BUILD_DIR"
printf 'Note: this scaffold does not boot the game yet.\n'
