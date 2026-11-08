# Reachable-only CFG discovery

## Why this mode exists

A PSP PRX may put code, constants, strings, jump tables and metadata in one
file-backed PT_LOAD segment carrying PF_X. PF_X is a memory protection
attribute; it does not prove that every aligned word in the segment is an
instruction.

The legacy automatic_global_cfg mode linearly scans every executable range
for JAL instructions and materialized pointer patterns. On mixed code/data RX
segments, data words can resemble Allegrex instructions and become false
function or basic-block roots.

## Opt-in mode

The AOT generator accepts an optional --reachable argument:

```sh
psp_recomp module.sprx --auto generated-reachable 0x08E00000 0x4000 --reachable
```

The mode starts with:
- the ELF/PRX entry point;
- code pointers at valid R_MIPS_32 relocation sites whose relocated value
  falls inside an executable range.

It then analyzes reachable basic blocks, follows conditional branch edges,
direct jumps, and direct JAL calls, and adds direct call targets as function
seeds. It deliberately does not sweep the entire RX segment for JAL opcodes,
LUI/ADDIU pointer patterns, or unrelocated raw words.

The report identifies this mode as reachable_global_cfg. Without
--reachable, the existing automatic_global_cfg behavior remains the
default for compatibility.

## Important limitations

This is a conservative discovery mode, not proof of complete function
coverage. Indirect calls, callbacks and function pointers that are not covered
by an R_MIPS_32 relocation may be missed. Add verified roots through a
profile-specific seed list or a future explicit seed-input option before
shipping a corpus generated in this mode.

Compare the new report against the legacy report:
- function seed count;
- unique emitted instruction count;
- registered block entry count;
- unsupported instruction locations;
- known module entry points and runtime-observed indirect targets.

Do not replace the existing corpus solely because the new corpus is smaller.
Boot and exercise the module with interpreter fallback enabled, then compare
coverage and runtime traces. The mode should be enabled per module only after
its root coverage is validated.
