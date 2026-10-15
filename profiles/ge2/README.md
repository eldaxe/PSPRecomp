# God Eater 2 PSPRecomp profile

This profile is the starting point for a native Android ARM64 static-recompilation
port of God Eater 2 PSP (NPJH-50832). It uses PSPRecomp's reusable Allegrex,
ELF/PRX, memory, and AOT code-generation framework. It does not embed PPSSPP,
DyausPita, or PortableKit as a runtime.

## Status

The initial profile scaffold provides Android/ARM64 CMake integration and a
small JNI diagnostics entry point. It is **not yet a playable port**. No
commercial executable, ISO/CSO, game assets, decrypted ELF, or generated
game code is included.

Remaining work includes analyzing a user-provided decrypted ELF, validating
load addresses and relocations, generating and checking in reproducible AOT
units, implementing the GE2 bootstrap/HLE, and implementing PSP GE, audio,
input, threading, timers, and file-system behavior needed by this title.

## Local inputs

Keep the decrypted executable and extracted assets in the ignored local
directory `profiles/ge2/game/`. Analysis reports belong in
`profiles/ge2/analysis/`. Neither directory should be committed.

Use only game files you are authorized to use. Decryption and game-data
extraction are deliberately outside this repository.

## Android ARM64 build

Requirements: Android NDK with CMake/Ninja, CMake 3.20+, and a host C++20
toolchain for running the analyzer before cross-compiling.

From the repository root:

```bash
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/<installed-version>"
profiles/ge2/scripts/build_android.sh
```

The build produces `libge2_native.so` in the selected CMake build directory.
This first stage validates the native Android target and JNI loading surface;
it does not boot the game until the profile runtime and generated AOT corpus
are implemented.

For host-side analysis, build the generic tools on the host:

```bash
cmake -S . -B out/framework -DPSPRECOMP_PROFILE=""
cmake --build out/framework --target psp_analyze psp_recomp
```

Then analyze a local ELF/PRX with the helper script (it builds the host tools first):

```bash
# Default input: profiles/ge2/game/EBOOT.BIN
profiles/ge2/scripts/analyze_local.sh

# Or specify the local executable explicitly
profiles/ge2/scripts/analyze_local.sh /path/to/EBOOT_DECRYPTED.ELF

# Optional: also generate the automatic AOT corpus under ignored analysis/
profiles/ge2/scripts/analyze_local.sh --codegen /path/to/EBOOT_DECRYPTED.ELF
```

The helper keeps reports and generated scratch code in the ignored `profiles/ge2/analysis/` directory. The default load base is `0x08804000`, matching PSPRecomp core defaults; override it with `GE2_LOAD_BASE=0x...` only after confirming the correct base for that exact PRX.

Audit the analyzer output and generate an unresolved-NID backlog with:

```bash
python3 profiles/ge2/scripts/audit_report.py profiles/ge2/analysis/ge2_report.json \\
  --output profiles/ge2/analysis/unresolved_imports.csv
```

The audit reads the adjacent `ge2_report_imports.csv` emitted by `psp_analyze`. Use the report hash to ensure the analysis corresponds to the exact executable build before accepting generated AOT output.

Review the report's entry point, segment layout, relocation statistics,
function boundaries, unsupported instructions, and imports before attempting
code generation. Do not assume a report alone proves runtime compatibility.

**Executable identity warning:** the EBOOT previously supplied for analysis contains
strings such as `GodSaveGE2Trial`, `GodPlayerGE2Trial`, and `MODE:GE2TRIAL CONTINUOUS`.
That suggests a trial/demo build, but is not by itself definitive proof of the disc
revision. Do not label generated code as compatible with retail ULJS-00560 until the
executable identity is confirmed. The local static report recorded SHA-256
`8b48d9fb6470c221f94b0d852aa45061d043e92f497abbf2e243e4471f4c6efd`; keep the
binary and full reports out of the repository.

## Architecture contract

- `arm64-v8a` is the initial and only Android ABI.
- The guest remains 32-bit Allegrex/MIPS; only generated host code is AArch64.
- Title-specific addresses and HLE belong under this profile, not in
  `psprecomp_core`.
- Missing/unknown HLE must be logged and fail explicitly during development;
  do not silently return success for unimplemented kernel or graphics calls.
- Keep generator inputs and generated AOT output reproducible and document the
  exact executable hash expected by the profile.
