#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
BUILD_DIR="${GE2_HOST_BUILD_DIR:-$ROOT_DIR/out/ge2-host-tools}"
ANALYSIS_DIR="${GE2_ANALYSIS_DIR:-$ROOT_DIR/profiles/ge2/analysis}"
LOAD_BASE="${GE2_LOAD_BASE:-0x08800000}"
CODEGEN=0

usage() {
    echo "Usage: $0 [--codegen] [path/to/EBOOT.BIN-or-decrypted-ELF]" >&2
    echo "Environment: GE2_HOST_BUILD_DIR, GE2_ANALYSIS_DIR, GE2_LOAD_BASE" >&2
}

INPUT="$ROOT_DIR/profiles/ge2/game/EBOOT.BIN"
while [ "$#" -gt 0 ]; do
    case "$1" in
        --codegen) CODEGEN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        -*)
            echo "Unknown option: $1" >&2
            usage
            exit 2
            ;;
        *)
            INPUT="$1"
            case "$INPUT" in
                /*) ;;
                *) INPUT="$PWD/$INPUT" ;;
            esac
            shift
            ;;
    esac
done

if [ ! -f "$INPUT" ]; then
    echo "Input executable not found: $INPUT" >&2
    echo "Place your own dumped/decrypted ELF/PRX in profiles/ge2/game/ or pass its path." >&2
    exit 2
fi

command -v cmake >/dev/null 2>&1 || { echo "cmake is required." >&2; exit 2; }
command -v c++ >/dev/null 2>&1 || { echo "A host C++20 compiler is required." >&2; exit 2; }

mkdir -p "$ANALYSIS_DIR"
cmake -S "$ROOT_DIR" -B "$BUILD_DIR" \
    -DPSPRECOMP_PROFILE="" \
    -DPSPRECOMP_BUILD_TESTS=OFF \
    -DPSPRECOMP_BUILD_PROFILE_TESTS=OFF \
    -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD_DIR" --target psp_analyze psp_recomp --parallel

REPORT="$ANALYSIS_DIR/ge2_report.json"
"$BUILD_DIR/psp_analyze" "$INPUT" "$REPORT" "$LOAD_BASE"

echo
echo "Analysis complete:"
echo "  input:      $INPUT"
echo "  report:     $REPORT"
echo "  imports:    ${REPORT%.json}_imports.csv"
echo "  seeds:      ${REPORT%.json}_function_seeds.csv"
echo "  functions:  ${REPORT%.json}_functions_auto.csv"
echo "  load base:  $LOAD_BASE"

if [ "$CODEGEN" -eq 1 ]; then
    GENERATED_DIR="$ANALYSIS_DIR/generated"
    "$BUILD_DIR/psp_recomp" "$INPUT" --auto "$GENERATED_DIR" "$LOAD_BASE"
    echo
    echo "AOT code generation completed into the ignored local directory:"
    echo "  $GENERATED_DIR"
    echo "This is generated code, not proof that the game can boot."
fi
