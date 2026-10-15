#!/usr/bin/env python3
"""Audit a psp_analyze JSON report and produce a profile-local import backlog.

This tool never reads or embeds game assets. Reports and CSV output should stay
inside profiles/ge2/analysis/, which is ignored by Git.
"""
import argparse
import csv
import json
import sys
from collections import Counter
from pathlib import Path


def parse_int(value):
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        return int(value, 0)
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="JSON emitted by psp_analyze")
    parser.add_argument("--expected-sha256", help="Optional exact executable SHA-256")
    parser.add_argument("--output", type=Path, help="Optional unresolved-import CSV path")
    args = parser.parse_args()

    try:
        data = json.loads(args.report.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"error: cannot read analyzer report: {exc}", file=sys.stderr)
        return 2

    digest = str(data.get("sha256", "")).lower()
    elf_type = parse_int(data.get("elf_type"))
    is_prx = data.get("is_psp_prx")
    module = data.get("module") or {}
    reloc = data.get("relocations") or {}
    imports = data.get("imports", [])

    print("GE2 PSPRecomp report audit")
    print(f"  report:          {args.report}")
    print(f"  input:           {data.get('input', '<unknown>')}")
    print(f"  SHA-256:         {digest or '<missing>'}")
    print(f"  ELF type:        {hex(elf_type) if elf_type is not None else '<missing>'}")
    print(f"  PSP PRX:         {is_prx if is_prx is not None else '<missing>'}")
    print(f"  module:          {module.get('name', '<missing>')}")
    print(f"  runtime entry:   {data.get('entry_runtime', '<missing>')}")
    print(f"  import count:    {data.get('import_count', len(imports))}")
    print("  relocations:     " + ", ".join(
        f"{key}={reloc.get(key, '<missing>')}"
        for key in ("total", "r_mips_32", "r_mips_26", "r_mips_hi16",
                    "r_mips_lo16", "unsupported", "invalid")
    ))

    if args.expected_sha256 and digest != args.expected_sha256.lower():
        print("ERROR: executable SHA-256 does not match the expected identity.", file=sys.stderr)
        return 1
    if elf_type is not None and elf_type != 0xFFA0:
        print("WARNING: input is not marked as PSP PRX (ELF type 0xFFA0).")
    if is_prx is False:
        print("WARNING: analyzer reports that the input is not a PSP PRX.")
    for field in ("unsupported", "invalid"):
        count = parse_int(reloc.get(field))
        if count not in (None, 0):
            print(f"WARNING: relocation {field} count is {count}; inspect before codegen.")

    counts = Counter()
    unresolved = []
    for imp in imports:
        library = str(imp.get("library", ""))
        nid = str(imp.get("nid", ""))
        name = str(imp.get("name") or "").strip()
        counts[library] += 1
        if not name:
            unresolved.append({
                "library": library,
                "nid": nid,
                "stub_address": str(imp.get("stub_address", "")),
                "status": "unresolved_nid",
            })

    if counts:
        print("\nImports by library:")
        for library, count in sorted(counts.items()):
            print(f"  {library}: {count}")
    print(f"\nUnresolved NIDs: {len(unresolved)}")

    if args.output:
        try:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            with args.output.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.DictWriter(
                    stream, fieldnames=("library", "nid", "stub_address", "status")
                )
                writer.writeheader()
                writer.writerows(sorted(unresolved, key=lambda row: (row["library"], row["nid"])))
            print(f"Unresolved-import backlog: {args.output}")
        except OSError as exc:
            print(f"error: cannot write CSV: {exc}", file=sys.stderr)
            return 2

    print("\nThis is a static report audit only; it does not prove that the title boots.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
