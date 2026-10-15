# GE2 host implementation

The Android JNI entry point is only a loading/diagnostics smoke test.

Implement title-specific code in this directory as the profile develops:
- bootstrap and executable identity validation;
- PSP import/NID registrations and explicit unimplemented-call diagnostics;
- PSP thread, callback, alarm, timer, and interrupt behavior;
- file-system mapping for the user's extracted game data and save data;
- Graphics Engine command handling and Android rendering integration;
- audio and input bridges.

Do not register placeholder HLE functions that report success without matching
PSP behavior. Use the host-side PSPRecomp analyzer and tests to validate
executable layout and generated functions before wiring game execution.
