#!/usr/bin/env python3
"""i18n Unused Key Analyzer — Phase 4 of Story 10.9

Classifies translation keys into three buckets:
  - definitely_unused:   No t() call references this key in source files
  - possibly_dynamic:    Key prefix matches a dynamic t() template-literal pattern
  - needs_manual_check:  Indirect usage (local namespace inference, prop-drilling, partial match)

Usage (from web-frontend/ directory):
    python3 scripts/i18n/analyze-unused.py

Inputs:
    - public/locales/en/*.json       (EN locale files)
    - src/**/*.{ts,tsx}              (source files, test files excluded)
    - /tmp/unused_keys.txt           (optional: pre-generated flagged-key list)

Output:
    - docs/plans/i18n-unused-keys-report.md
"""

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

# ─── Configuration ────────────────────────────────────────────────────────────

SCRIPT_DIR = Path(__file__).parent
REPO_ROOT = SCRIPT_DIR.parent.parent  # web-frontend/
LOCALE_EN_DIR = REPO_ROOT / "public" / "locales" / "en"
SRC_DIR = REPO_ROOT / "src"
OUTPUT_REPORT = REPO_ROOT.parent / "docs" / "plans" / "i18n-unused-keys-report.md"
FLAGGED_KEYS_FILE = Path("/tmp/unused_keys.txt")

SOURCE_EXTENSIONS = {".tsx", ".ts"}

# Exclude test/spec files from "usage" detection — tests mock translations and
# referencing a key in a test does not prove it is used in production.
EXCLUDE_PATTERNS = [
    "test.tsx", "test.ts", "spec.ts", "spec.tsx",
    "/__tests__/", "/test/", "setup.ts",
]


# ─── Key Loading ──────────────────────────────────────────────────────────────

def flatten_json(obj: dict, prefix: str = "") -> dict:
    """Recursively flatten nested JSON to dot-separated key paths."""
    result = {}
    for k, v in obj.items():
        path = f"{prefix}.{k}" if prefix else k
        if isinstance(v, dict):
            result.update(flatten_json(v, path))
        else:
            result[path] = str(v)
    return result


def load_all_en_keys() -> dict:
    """Return {namespace:key.path -> EN value} for every key in the EN locale."""
    all_keys = {}
    for json_file in sorted(LOCALE_EN_DIR.glob("*.json")):
        ns = json_file.stem
        try:
            data = json.loads(json_file.read_text(encoding="utf-8"))
            for path, val in flatten_json(data).items():
                all_keys[f"{ns}:{path}"] = val
        except Exception as e:
            print(f"  WARNING: cannot parse {json_file.name}: {e}", file=sys.stderr)
    return all_keys


# ─── Source Scanning ──────────────────────────────────────────────────────────

def is_excluded(path: Path) -> bool:
    path_str = str(path)
    return any(exc in path_str for exc in EXCLUDE_PATTERNS)


def load_source_files() -> list:
    """Return list of (Path, content-str) for all non-test source files."""
    files = []
    for ext in SOURCE_EXTENSIONS:
        for f in SRC_DIR.rglob(f"*{ext}"):
            if not is_excluded(f):
                try:
                    files.append((f, f.read_text(encoding="utf-8")))
                except Exception:
                    pass
    return files


# 4.1 — Detect dynamic key patterns ───────────────────────────────────────────

def find_dynamic_prefixes(source_files: list) -> set:
    """
    Scan for dynamic t() patterns and return the static prefixes.

    Patterns detected:
      t(`common:actions.${action}`)  → prefix "common:actions"
      t(`${ns}:key`)                 → too dynamic, ignored
      t(someVar)                     → ignored (no prefix)
      t(KEY_PREFIX + suffix)         → prefix extraction attempted
    """
    prefixes = set()

    # Template literal: t(`prefix${...}`)
    tpl_pattern = re.compile(r"""t\(`([^`$]*)\$\{""")
    # Concatenation: t(SOME_PREFIX + key)
    concat_pattern = re.compile(r"""t\(([A-Z_]+)\s*\+""")
    # Variable with known prefix: const PREFIX = 'ns:path.'; t(PREFIX + ...)
    const_pattern = re.compile(r"""const\s+(\w+)\s*=\s*['"]([a-zA-Z]+:[a-zA-Z0-9_.]+)['"]""")

    const_map = {}
    for _path, content in source_files:
        for m in const_pattern.finditer(content):
            const_map[m.group(1)] = m.group(2)

    for _path, content in source_files:
        for m in tpl_pattern.finditer(content):
            raw = m.group(1).rstrip(".")
            if raw and len(raw) > 3 and not raw.startswith("${"):
                prefixes.add(raw)

        for m in concat_pattern.finditer(content):
            var_name = m.group(1)
            if var_name in const_map:
                prefixes.add(const_map[var_name].rstrip("."))

    return prefixes


# 4.2 — Cross-reference prop-drilling patterns ────────────────────────────────

def find_prop_drilling_refs(source_files: list) -> set:
    """
    Find t() calls inside config-array / object prop patterns like:
      { label: t('events:table.title') }
      columns.map(col => ({ header: t(col.key) }))

    Also detects config-object key patterns where a translation key string is
    stored in a property and later passed to t() dynamically:
      { labelKey: 'events:navigation.dashboard' }
      { tabKey: 'eventPage.tabs.venue' }

    Returns a set of key strings found in these patterns.
    """
    refs = set()
    # Property patterns where a t() call provides a string value
    prop_pattern = re.compile(
        r"""(?:label|header|title|name|text|placeholder|tooltip|ariaLabel|aria-label|description)"?\s*:\s*t\(['"]([^'"]+)['"]\)"""
    )
    # Config-object key patterns: labelKey, tabKey, columnKey, *Key, *key etc.
    # These store a translation key string that will later be passed to t(key)
    config_key_pattern = re.compile(
        r"""(?:\w*[Kk]ey)\s*:\s*['"]([a-zA-Z][\w:.-]+)['"]"""
    )
    for _path, content in source_files:
        for m in prop_pattern.finditer(content):
            refs.add(m.group(1))
        for m in config_key_pattern.finditer(content):
            refs.add(m.group(1))
    return refs


# ─── Key Reference Index ──────────────────────────────────────────────────────

def build_key_refs(source_files: list) -> dict:
    """
    Scan source files for all t() key references.

    Returns dict: key_ref -> [file_paths]

    Two forms are indexed:
      1. Explicit:  t('common:actions.cancel')  →  'common:actions.cancel'
      2. Local:     t('actions.cancel')          →  '__local:actions.cancel'
         (resolved later against known namespaces per file)
    """
    refs: dict = defaultdict(list)

    # Matches t('namespace:key.path') or t("...") or t(`...`) (static only)
    explicit_re = re.compile(
        r"""t\(['"`]([a-zA-Z][\w-]+:[a-zA-Z0-9_.]+)['"`]"""
    )
    # Matches t('key.without.namespace') — namespace-local keys
    local_re = re.compile(
        r"""t\(['"`]([a-zA-Z0-9][a-zA-Z0-9_.]*[a-zA-Z0-9])['"`]"""
    )
    # useTranslation('ns') or useTranslation(['ns1', 'ns2'])
    single_ns_re = re.compile(r"""useTranslation\(\s*['"]([a-zA-Z]+)['"]\s*\)""")
    multi_ns_re = re.compile(r"""useTranslation\(\s*\[([^\]]+)\]\s*\)""")
    ns_str_re = re.compile(r"""['"]([a-zA-Z]+)['"]""")

    for path, content in source_files:
        path_str = str(path)

        # Collect declared namespaces for this file.
        # Always include 'common' since it is the defaultNS in i18n/config.ts —
        # any t('key') call without a namespace prefix implicitly targets common.
        local_ns = ["common"]
        m = single_ns_re.search(content)
        if m:
            ns = m.group(1)
            if ns not in local_ns:
                local_ns.append(ns)
        for m in multi_ns_re.finditer(content):
            for ns_m in ns_str_re.finditer(m.group(1)):
                ns = ns_m.group(1)
                if ns not in local_ns:
                    local_ns.append(ns)

        # Explicit namespace:key references
        for m in explicit_re.finditer(content):
            refs[m.group(1)].append(path_str)

        # Local references (no namespace)
        for m in local_re.finditer(content):
            key = m.group(1)
            if ":" not in key:
                for ns in local_ns:
                    refs[f"{ns}:{key}"].append(path_str)
                refs[f"__local:{key}"].append(path_str)

    return refs


# ─── Classification ────────────────────────────────────────────────────────────

def classify_keys(
    flagged_keys: list,
    all_en_keys: dict,
    key_refs: dict,
    dynamic_prefixes: set,
    prop_refs: set,
) -> dict:
    """Classify each flagged key into one of three buckets."""
    buckets: dict = {
        "definitely_unused": [],
        "possibly_dynamic": [],
        "needs_manual_check": [],
        "found_used": [],  # internal — not emitted in report
    }

    for key in flagged_keys:
        # key format: "namespace:path.to.leaf"
        local_part = key.split(":", 1)[1] if ":" in key else key

        # ── Check 1: Direct explicit reference ────────────────────────────────
        if key in key_refs and key_refs[key]:
            buckets["found_used"].append(key)
            continue

        # ── Check 2: Local namespace resolution matched ────────────────────────
        local_key = f"__local:{local_part}"
        if local_key in key_refs and key_refs[local_key]:
            buckets["needs_manual_check"].append(key)
            continue

        # ── Check 3: Dynamic prefix match ─────────────────────────────────────
        matched_dynamic = any(
            key.startswith(prefix) or local_part.startswith(prefix.split(":", 1)[-1])
            for prefix in dynamic_prefixes
            if len(prefix) > 3
        )
        if matched_dynamic:
            buckets["possibly_dynamic"].append(key)
            continue

        # ── Check 4: Prop-drilling reference ──────────────────────────────────
        if local_part in prop_refs or key in prop_refs:
            buckets["needs_manual_check"].append(key)
            continue

        # ── Check 5: Partial match — ref is the DIRECT parent of flagged key ──
        # e.g., t('actions.delete') used → needs_manual_check for 'actions.delete.tooltip'
        # Restrict to direct parent only (one level up) to avoid false positives from
        # broad refs like '__local:company' (created by t('company.foo')) matching the
        # unrelated key 'company.industries.cloudComputing' via ancestor startswith().
        local_parent = local_part.rsplit(".", 1)[0] if "." in local_part else ""
        partial = bool(local_parent) and any(
            ref.split(":", 1)[-1] == local_parent
            for ref in key_refs
            if ":" not in ref.split("__local:")[-1] and ref != local_key
        )
        if partial:
            buckets["needs_manual_check"].append(key)
            continue

        # ── No reference found ────────────────────────────────────────────────
        buckets["definitely_unused"].append(key)

    return buckets


# ─── Deletion (Task 4.4) ──────────────────────────────────────────────────────

def delete_nested_key(obj: dict, path: str) -> bool:
    """Delete a dot-separated nested key from dict. Returns True if deleted."""
    parts = path.split(".")
    current = obj
    for part in parts[:-1]:
        if not isinstance(current, dict) or part not in current:
            return False
        current = current[part]
    if isinstance(current, dict) and parts[-1] in current:
        del current[parts[-1]]
        return True
    return False


def prune_empty_objects(obj: dict) -> dict:
    """Recursively remove empty nested dicts left after key deletions."""
    if not isinstance(obj, dict):
        return obj
    result = {}
    for k, v in obj.items():
        if isinstance(v, dict):
            pruned = prune_empty_objects(v)
            if pruned:
                result[k] = pruned
        else:
            result[k] = v
    return result


def delete_keys_from_locales(definitely_unused: list) -> None:
    """Delete all definitely_unused keys from every locale JSON file."""
    by_ns: dict = defaultdict(list)
    for key in definitely_unused:
        ns, path = key.split(":", 1)
        by_ns[ns].append(path)

    locales_root = REPO_ROOT / "public" / "locales"
    locale_dirs = sorted(d for d in locales_root.iterdir() if d.is_dir())

    total_deleted = 0
    for locale_dir in locale_dirs:
        locale = locale_dir.name
        for ns, paths in sorted(by_ns.items()):
            json_file = locale_dir / f"{ns}.json"
            if not json_file.exists():
                continue
            try:
                data = json.loads(json_file.read_text(encoding="utf-8"))
                deleted = sum(1 for p in paths if delete_nested_key(data, p))
                if deleted > 0:
                    data = prune_empty_objects(data)
                    json_file.write_text(
                        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8",
                    )
                    total_deleted += deleted
                    print(f"  [{locale}/{ns}.json] deleted {deleted} keys")
            except Exception as e:
                print(f"  WARNING: error processing {json_file}: {e}", file=sys.stderr)

    print(f"\n  Total deletions across all locales: {total_deleted}")


# ─── Report Generation ────────────────────────────────────────────────────────

def generate_report(
    buckets: dict,
    all_en_keys: dict,
    dynamic_prefixes: set,
    total_flagged: int,
) -> str:
    du = buckets["definitely_unused"]
    pd = buckets["possibly_dynamic"]
    nm = buckets["needs_manual_check"]
    fu = buckets["found_used"]

    lines = [
        "# i18n Unused Key Analysis Report",
        "",
        "> **Generated by:** `web-frontend/scripts/i18n/analyze-unused.py`  ",
        f"> **Total EN keys:** {len(all_en_keys)}  ",
        f"> **Flagged keys analyzed:** {total_flagged}  ",
        "",
        "## Summary",
        "",
        "| Bucket | Count | Action |",
        "|--------|-------|--------|",
        f"| ✅ Definitely unused | {len(du)} | **DELETE** from all locale files |",
        f"| ⚠️  Possibly dynamic | {len(pd)} | Keep — used via template literal |",
        f"| 🔍 Needs manual check | {len(nm)} | Verify before deleting |",
        f"| ✓ Found used (excluded) | {len(fu)} | No action needed |",
        "",
        "## Dynamic Prefixes Detected (4.1)",
        "",
        "Keys whose prefix matches a dynamic `t()` call are classified as *possibly dynamic*.",
        "",
    ]
    for p in sorted(dynamic_prefixes):
        lines.append(f"- `{p}`")
    lines += [""]

    # ── Definitely Unused ────────────────────────────────────────────────────
    lines += [
        "## ✅ Definitely Unused Keys",
        "",
        "No direct or indirect reference found in production source files.",
        "Safe to delete from all locale files (EN + all 8 non-EN locales).",
        "",
        "### Delete Commands",
        "",
        "Use `jq` to delete keys. Example:",
        "```bash",
        "# From web-frontend/ directory:",
        "# jq 'del(.path.to.key)' public/locales/en/namespace.json > /tmp/ns.json",
        "# mv /tmp/ns.json public/locales/en/namespace.json",
        "# Repeat for all 9 locales.",
        "```",
        "",
    ]
    current_ns = None
    for key in sorted(du):
        ns, path = (key.split(":", 1) if ":" in key else ("?", key))
        if ns != current_ns:
            lines += [f"### {ns}.json", ""]
            current_ns = ns
        val = all_en_keys.get(key, "")
        lines.append(f"- `{path}` — *\"{val[:80]}{'...' if len(val) > 80 else ''}\"*")
    lines += [""]

    # ── Possibly Dynamic ─────────────────────────────────────────────────────
    lines += [
        "## ⚠️  Possibly Used Dynamically",
        "",
        "Key prefix matches a dynamic `t()` template literal. Do **NOT** delete",
        "without confirming the dynamic pattern no longer references this key.",
        "",
    ]
    for key in sorted(pd):
        lines.append(f"- `{key}`")
    lines += [""]

    # ── Needs Manual Check ───────────────────────────────────────────────────
    lines += [
        "## 🔍 Needs Manual Check",
        "",
        "Found via indirect patterns (local namespace inference, prop-drilling,",
        "or partial key match). Verify each before deleting.",
        "",
    ]
    for key in sorted(nm):
        lines.append(f"- `{key}`")
    lines += [""]

    return "\n".join(lines)


# ─── Main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    import argparse
    parser = argparse.ArgumentParser(description="i18n Unused Key Analyzer — Phase 4")
    parser.add_argument(
        "--delete",
        action="store_true",
        help="After analysis, delete definitely_unused keys from all locale files",
    )
    args = parser.parse_args()

    print("═" * 60)
    print("i18n Unused Key Analyzer — Phase 4")
    print("═" * 60)

    print("\n[1/6] Loading EN locale keys…")
    all_en_keys = load_all_en_keys()
    print(f"      {len(all_en_keys)} total keys in EN locale")

    print("\n[2/6] Loading flagged keys list…")
    if FLAGGED_KEYS_FILE.exists():
        raw = FLAGGED_KEYS_FILE.read_text().splitlines()
        flagged_keys = [l.strip() for l in raw if l.strip()]
        print(f"      {len(flagged_keys)} keys from {FLAGGED_KEYS_FILE}")
    else:
        flagged_keys = list(all_en_keys.keys())
        print(f"      {FLAGGED_KEYS_FILE} not found — using all {len(flagged_keys)} EN keys")

    print("\n[3/6] Scanning source files…")
    source_files = load_source_files()
    print(f"      {len(source_files)} files scanned (tests excluded)")

    print("\n[4/6] Detecting dynamic key patterns (Task 4.1)…")
    dynamic_prefixes = find_dynamic_prefixes(source_files)
    print(f"      {len(dynamic_prefixes)} dynamic prefixes found")
    for p in sorted(dynamic_prefixes)[:10]:
        print(f"        {p}")
    if len(dynamic_prefixes) > 10:
        print(f"        … ({len(dynamic_prefixes) - 10} more)")

    print("\n[5/6] Cross-referencing prop-drilling patterns (Task 4.2)…")
    prop_refs = find_prop_drilling_refs(source_files)
    print(f"      {len(prop_refs)} prop-drilling refs found")

    print("\n[5b/6] Building key reference index…")
    key_refs = build_key_refs(source_files)
    print(f"       {len(key_refs)} unique key references indexed")

    print("\n[6/6] Classifying keys (Task 4.3)…")
    buckets = classify_keys(flagged_keys, all_en_keys, key_refs, dynamic_prefixes, prop_refs)

    du = len(buckets["definitely_unused"])
    pd = len(buckets["possibly_dynamic"])
    nm = len(buckets["needs_manual_check"])
    fu = len(buckets["found_used"])
    total = du + pd + nm + fu

    print()
    print("═" * 60)
    print("RESULTS")
    print("═" * 60)
    print(f"  Definitely unused:       {du:4d}  ← safe to delete")
    print(f"  Possibly dynamic:        {pd:4d}  ← keep (template literals)")
    print(f"  Needs manual check:      {nm:4d}  ← verify before deleting")
    print(f"  Found used (skipped):    {fu:4d}")
    print(f"  ─────────────────────── ────")
    print(f"  Total analyzed:          {total:4d}")
    print("═" * 60)

    if nm > 50:
        print(f"\n  ⚠️  WARNING: 'Needs manual check' has {nm} keys (AC#26 target ≤ 50)")

    print(f"\nWriting report → {OUTPUT_REPORT}")
    OUTPUT_REPORT.parent.mkdir(parents=True, exist_ok=True)
    report = generate_report(buckets, all_en_keys, dynamic_prefixes, len(flagged_keys))
    OUTPUT_REPORT.write_text(report, encoding="utf-8")
    print("Done. ✓")

    if args.delete:
        print(f"\n[Task 4.4] Deleting {du} definitely-unused keys from all locale files…")
        delete_keys_from_locales(buckets["definitely_unused"])
        print("Deletion complete. ✓")

    return 0 if nm <= 50 else 1


if __name__ == "__main__":
    sys.exit(main())
