#!/usr/bin/env python3
"""
doc-audit-retrospective.py
──────────────────────────
One-shot retrospective audit: compares documentation claims against the
current integration test suite to find stale or undocumented behaviour.

Uses the Anthropic Python SDK directly — no CLI subprocess required.
Each target runs as an isolated API session with file-system tools
(Read, Grep, Glob, Write). State is saved after each target so you can
interrupt and resume freely.

Usage:
  python3 scripts/dev/doc-audit-retrospective.py           # run all pending
  python3 scripts/dev/doc-audit-retrospective.py --list    # show status
  python3 scripts/dev/doc-audit-retrospective.py --reset   # reset all to pending
  python3 scripts/dev/doc-audit-retrospective.py --target state-machines
  python3 scripts/dev/doc-audit-retrospective.py --fix     # apply fixes from findings
  python3 scripts/dev/doc-audit-retrospective.py --fix --target state-machines
"""

import argparse
import datetime
import glob as glob_module
import json
import os
import pathlib
import re
import subprocess
import sys
import textwrap

import anthropic

# ── Paths ────────────────────────────────────────────────────────────────────
REPO_ROOT    = pathlib.Path(__file__).parent.parent.parent.resolve()
STATUS_FILE  = REPO_ROOT / "docs/plans/doc-audit/status.json"
FINDINGS_DIR = REPO_ROOT / "docs/plans/doc-audit"
MODEL        = "claude-sonnet-4-6"
MAX_TOKENS   = 8192

# ── Audit targets ─────────────────────────────────────────────────────────────
TARGETS = [
    {
        "id": "state-machines",
        "label": "Event state machine & lifecycle",
        "doc":   "docs/architecture/06a-workflow-state-machines.md",
        "tests": ["services/event-management-service/src/test/java"],
    },
    {
        "id": "backend-architecture",
        "label": "Backend architecture overview",
        "doc":   "docs/architecture/06-backend-architecture.md",
        "tests": [
            "services/event-management-service/src/test/java",
            "services/company-user-management-service/src/test/java",
            "api-gateway/src/test/java",
        ],
    },
    {
        "id": "epic-4-public-website",
        "label": "Public website & registration (Epic 4)",
        "doc":   "docs/prd/epic-4-public-website-content-discovery.md",
        "tests": ["services/event-management-service/src/test/java", "web-frontend/src"],
    },
    {
        "id": "epic-5-organizer-workflows",
        "label": "Organizer workflows & lifecycle automation (Epic 5)",
        "doc":   "docs/prd/epic-5-enhanced-organizer-workflows.md",
        "tests": ["services/event-management-service/src/test/java"],
    },
    {
        "id": "epic-6-speaker-portal",
        "label": "Speaker self-service portal (Epic 6)",
        "doc":   "docs/prd/epic-6-speaker-portal-support.md",
        "tests": ["services/speaker-coordination-service/src/test/java"],
    },
    {
        "id": "epic-8-partner",
        "label": "Partner coordination (Epic 8)",
        "doc":   "docs/prd/epic-8-partner-coordination.md",
        "tests": ["services/partner-coordination-service/src/test/java"],
    },
    {
        "id": "epic-9-speaker-auth",
        "label": "Speaker authentication (Epic 9)",
        "doc":   "docs/prd/epic-9-speaker-authentication.md",
        "tests": ["services/speaker-coordination-service/src/test/java"],
    },
    {
        "id": "user-lifecycle-sync",
        "label": "User lifecycle sync",
        "doc":   "docs/architecture/06b-user-lifecycle-sync.md",
        "tests": ["services/company-user-management-service/src/test/java"],
    },
    {
        "id": "notification-system",
        "label": "Notification system",
        "doc":   "docs/architecture/06d-notification-system.md",
        "tests": [
            "services/speaker-coordination-service/src/test/java",
            "services/event-management-service/src/test/java",
        ],
    },
    {
        "id": "user-guide-workflow",
        "label": "User guide — organiser workflow phases",
        "doc":   "docs/user-guide/workflow",
        "tests": ["services/event-management-service/src/test/java"],
    },
    {
        "id": "user-guide-speaker-portal",
        "label": "User guide — speaker portal",
        "doc":   "docs/user-guide/speaker-portal",
        "tests": ["services/speaker-coordination-service/src/test/java"],
    },
    {
        "id": "user-guide-partner-portal",
        "label": "User guide — partner portal",
        "doc":   "docs/user-guide/partner-portal",
        "tests": ["services/partner-coordination-service/src/test/java"],
    },
]

# ── Tool definitions (passed to Claude API) ───────────────────────────────────
TOOLS = [
    {
        "name": "read_file",
        "description": "Read the full contents of a file in the repository.",
        "input_schema": {
            "type": "object",
            "properties": {
                "path": {"type": "string", "description": "Path relative to repo root"}
            },
            "required": ["path"],
        },
    },
    {
        "name": "glob_files",
        "description": "Find files matching a glob pattern.",
        "input_schema": {
            "type": "object",
            "properties": {
                "pattern": {"type": "string", "description": "Glob pattern relative to repo root (e.g. 'services/event-management-service/src/test/**/*.java')"},
                "max_results": {"type": "integer", "description": "Max files to return (default 50)"},
            },
            "required": ["pattern"],
        },
    },
    {
        "name": "grep_files",
        "description": "Search for a regex pattern in files under a directory.",
        "input_schema": {
            "type": "object",
            "properties": {
                "pattern":   {"type": "string", "description": "Regex pattern to search for"},
                "directory": {"type": "string", "description": "Directory to search in (relative to repo root)"},
                "glob":      {"type": "string", "description": "File glob filter (e.g. '*.java', '*.ts')"},
                "context_lines": {"type": "integer", "description": "Lines of context around each match (default 2)"},
            },
            "required": ["pattern", "directory"],
        },
    },
    {
        "name": "write_file",
        "description": "Write content to a file (creates or overwrites).",
        "input_schema": {
            "type": "object",
            "properties": {
                "path":    {"type": "string", "description": "Path relative to repo root"},
                "content": {"type": "string", "description": "File content to write"},
            },
            "required": ["path", "content"],
        },
    },
    {
        "name": "edit_file",
        "description": "Replace an exact string in a file.",
        "input_schema": {
            "type": "object",
            "properties": {
                "path":       {"type": "string", "description": "Path relative to repo root"},
                "old_string": {"type": "string", "description": "Exact text to find and replace"},
                "new_string": {"type": "string", "description": "Text to replace it with"},
            },
            "required": ["path", "old_string", "new_string"],
        },
    },
]


# ── Tool executor ─────────────────────────────────────────────────────────────
def execute_tool(name: str, inputs: dict) -> str:
    try:
        if name == "read_file":
            p = REPO_ROOT / inputs["path"]
            if not p.exists():
                return f"ERROR: File not found: {inputs['path']}"
            content = p.read_text(errors="replace")
            if len(content) > 40000:
                content = content[:40000] + f"\n... [truncated at 40000 chars]"
            return content

        elif name == "glob_files":
            pattern = str(REPO_ROOT / inputs["pattern"])
            max_r = inputs.get("max_results", 50)
            matches = glob_module.glob(pattern, recursive=True)[:max_r]
            rel = [str(pathlib.Path(m).relative_to(REPO_ROOT)) for m in sorted(matches)]
            return "\n".join(rel) if rel else "No files matched."

        elif name == "grep_files":
            directory = REPO_ROOT / inputs["directory"]
            if not directory.exists():
                return f"ERROR: Directory not found: {inputs['directory']}"
            ctx = inputs.get("context_lines", 2)
            file_glob = inputs.get("glob", "*")
            cmd = ["grep", "-r", "-n", f"-C{ctx}", "--include", file_glob,
                   inputs["pattern"], str(directory)]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            out = result.stdout
            if not out:
                return "No matches found."
            # Trim to 20000 chars
            if len(out) > 20000:
                out = out[:20000] + "\n... [truncated]"
            return out

        elif name == "write_file":
            p = REPO_ROOT / inputs["path"]
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(inputs["content"])
            return f"Written: {inputs['path']} ({len(inputs['content'])} chars)"

        elif name == "edit_file":
            p = REPO_ROOT / inputs["path"]
            if not p.exists():
                return f"ERROR: File not found: {inputs['path']}"
            content = p.read_text()
            if inputs["old_string"] not in content:
                return f"ERROR: old_string not found in {inputs['path']}"
            new_content = content.replace(inputs["old_string"], inputs["new_string"], 1)
            p.write_text(new_content)
            return f"Edited: {inputs['path']}"

        else:
            return f"ERROR: Unknown tool: {name}"

    except Exception as e:
        return f"ERROR: {e}"


# ── Claude session runner ─────────────────────────────────────────────────────
def run_claude_session(prompt: str, label: str) -> str:
    """Run a full Claude tool-use loop. Returns final text response."""
    client = anthropic.Anthropic()
    messages = [{"role": "user", "content": prompt}]
    turn = 0

    while True:
        turn += 1
        print(f"   [turn {turn}] Calling Claude API…", flush=True)

        response = client.messages.create(
            model=MODEL,
            max_tokens=MAX_TOKENS,
            tools=TOOLS,
            messages=messages,
        )

        # Collect any text output from this turn
        text_parts = [b.text for b in response.content if hasattr(b, "text") and b.text]
        tool_uses  = [b for b in response.content if b.type == "tool_use"]

        if text_parts:
            print(f"   [turn {turn}] Claude: {text_parts[0][:120]}…" if len(text_parts[0]) > 120 else f"   [turn {turn}] Claude: {text_parts[0]}", flush=True)

        if tool_uses:
            print(f"   [turn {turn}] Tools: {', '.join(f'{t.name}({list(t.input.values())[0]!r:.50})' for t in tool_uses)}", flush=True)

        # Append assistant message
        messages.append({"role": "assistant", "content": response.content})

        if response.stop_reason == "end_turn":
            return "\n".join(text_parts)

        if response.stop_reason == "tool_use":
            # Execute all tools and collect results
            tool_results = []
            for tool_use in tool_uses:
                print(f"   [tool] {tool_use.name}: {json.dumps(tool_use.input)[:100]}", flush=True)
                result = execute_tool(tool_use.name, tool_use.input)
                tool_results.append({
                    "type": "tool_result",
                    "tool_use_id": tool_use.id,
                    "content": result,
                })
            messages.append({"role": "user", "content": tool_results})
            continue

        # Unexpected stop reason
        print(f"   ⚠️  Unexpected stop_reason: {response.stop_reason}")
        break

    return ""


# ── Status helpers ─────────────────────────────────────────────────────────────
def load_status() -> dict:
    if STATUS_FILE.exists():
        return json.loads(STATUS_FILE.read_text())
    return {t["id"]: {"status": "pending", "findings_file": None, "completed_at": None}
            for t in TARGETS}


def save_status(status: dict):
    FINDINGS_DIR.mkdir(parents=True, exist_ok=True)
    STATUS_FILE.write_text(json.dumps(status, indent=2) + "\n")


# ── Prompt builders ────────────────────────────────────────────────────────────
def build_audit_prompt(target: dict) -> str:
    doc_path   = target["doc"]
    test_paths = target["tests"]
    findings_file = f"docs/plans/doc-audit/findings-{target['id']}.md"
    test_paths_str = "\n".join(f"  - {p}" for p in test_paths)

    return textwrap.dedent(f"""
    You are performing a retrospective documentation accuracy audit for BATbern.

    ## Your task

    Compare the documentation claims in the doc file(s) below against the current
    integration and unit test suite. Identify:

    1. **MISMATCH** — doc states X, but a test asserts Y (doc is wrong or stale)
    2. **UNTESTED** — doc makes a claim but no test validates it (claim may be stale)
    3. **UNDOCUMENTED** — a test asserts a business rule NOT mentioned in the doc

    ## Doc to audit
    `{doc_path}`
    (If this is a directory, use glob_files to find all .md files inside it and read each)

    ## Test directories to search
    {test_paths_str}

    ## Steps

    1. Read the doc file(s) completely using read_file
    2. Extract every business rule, state transition, API behaviour, timing constraint
    3. For each claim, use grep_files to find relevant test methods, then read_file to inspect them
    4. Classify each claim: VALIDATED / MISMATCH / UNTESTED
    5. Use glob_files + grep_files to find test methods not covered by the doc (UNDOCUMENTED)
    6. Write your findings using write_file to `{findings_file}` in this format:

    ```markdown
    # Doc Audit Findings — {target['label']}
    **Audited:** {datetime.date.today().isoformat()}
    **Doc:** `{doc_path}`

    ## Summary
    - VALIDATED: n  |  MISMATCH: n  |  UNTESTED: n  |  UNDOCUMENTED: n

    ## MISMATCH
    ### M1 — <title>
    **Doc claims:** "quote"
    **Test asserts:** `TestClass#method` — what it actually checks
    **Action:** Update doc to say: "correction"

    ## UNTESTED
    ### U1 — <title>
    **Doc claims:** "quote"
    **Risk:** high / medium / low

    ## UNDOCUMENTED
    ### N1 — <title>
    **Test:** `TestClass#method` — what it asserts
    **Action:** Add to doc section: X

    ## VALIDATED
    - "claim" → `TestClass#method`
    ```

    Rules: surgical findings only — quote doc and test precisely.
    If no gaps found, write a findings file that says so.
    """).strip()


def build_fix_prompt(target: dict, findings_text: str) -> str:
    fix_summary_file = f"docs/plans/doc-audit/fix-summary-{target['id']}.md"

    return textwrap.dedent(f"""
    You are applying documentation fixes for BATbern based on a completed audit.

    ## Findings to action

    ```
    {findings_text}
    ```

    ## What to fix

    **MISMATCH** — use edit_file to correct the doc to match what the test asserts.
    **UNDOCUMENTED** — use edit_file or read_file + write_file to add the missing behaviour.
    **UNTESTED** — do NOT auto-fix. Note them in the fix summary only.

    ## Rules
    - Only edit files under `docs/` — never touch source or test files
    - Surgical edits only — fix the specific gap, don't rewrite sections
    - After all edits, write a fix summary to `{fix_summary_file}`:

    ```markdown
    # Fix Summary — {target['label']}
    **Fixed:** {datetime.date.today().isoformat()}

    ## Changes made
    - M1: <what changed and why>
    - N1: <what was added and where>

    ## Skipped — needs manual decision
    - U1: "<claim>" — no test exists, may need test or doc removal
    ```
    """).strip()


# ── Target runners ─────────────────────────────────────────────────────────────
def run_audit_target(target: dict, status: dict) -> bool:
    tid = target["id"]
    findings_file = FINDINGS_DIR / f"findings-{tid}.md"

    print(f"\n{'='*60}")
    print(f"▶  Auditing: {target['label']}")
    print(f"   Doc:   {target['doc']}")
    print(f"   Tests: {', '.join(target['tests'])}")
    print(f"{'='*60}")

    prompt = build_audit_prompt(target)
    run_claude_session(prompt, target["label"])

    status[tid]["status"] = "done" if findings_file.exists() else "error"
    status[tid]["findings_file"] = str(findings_file.relative_to(REPO_ROOT)) if findings_file.exists() else None
    status[tid]["completed_at"] = datetime.datetime.now().isoformat()
    save_status(status)

    if findings_file.exists():
        print(f"   ✅ Findings: {findings_file.relative_to(REPO_ROOT)}")
        return True
    else:
        print(f"   ⚠️  No findings file written — check output above")
        status[tid]["status"] = "error"
        save_status(status)
        return False


def run_fix_target(target: dict, status: dict) -> bool:
    tid = target["id"]
    findings_file = FINDINGS_DIR / f"findings-{tid}.md"
    fix_summary   = FINDINGS_DIR / f"fix-summary-{tid}.md"

    s = status.get(tid, {})
    if s.get("status") != "done" or not findings_file.exists():
        print(f"   ⏭  {target['label']} — no findings yet. Run audit first.")
        return True
    if s.get("fix_status") == "done":
        print(f"   ✅ {target['label']} — already fixed.")
        return True

    print(f"\n{'='*60}")
    print(f"🔧 Fixing: {target['label']}")
    print(f"{'='*60}")

    findings_text = findings_file.read_text()
    if "MISMATCH" not in findings_text and "UNDOCUMENTED" not in findings_text:
        print(f"   ℹ️  No actionable findings — nothing to fix.")
        status[tid]["fix_status"] = "done"
        save_status(status)
        return True

    prompt = build_fix_prompt(target, findings_text)
    run_claude_session(prompt, target["label"])

    status[tid]["fix_status"] = "done"
    status[tid]["fix_summary_file"] = str(fix_summary.relative_to(REPO_ROOT)) if fix_summary.exists() else None
    status[tid]["fixed_at"] = datetime.datetime.now().isoformat()
    save_status(status)

    print(f"   ✅ Done. Review with: git diff docs/")
    return True


# ── Aggregate report ───────────────────────────────────────────────────────────
def build_report(status: dict):
    report_file = FINDINGS_DIR / "report.md"
    lines = [
        "# Doc Audit Retrospective Report",
        f"**Generated:** {datetime.date.today().isoformat()}",
        "", "## Status", "",
        "| Target | Label | Audit | Fix | Findings |",
        "|--------|-------|-------|-----|----------|",
    ]
    for t in TARGETS:
        s  = status.get(t["id"], {})
        ai = {"done": "✅", "pending": "⏳", "error": "❌"}.get(s.get("status", "pending"), "?")
        fi = {"done": "✅", "error": "❌"}.get(s.get("fix_status", ""), "—")
        ff = s.get("findings_file", "")
        link = f"[view]({pathlib.Path(ff).name})" if ff else "—"
        lines.append(f"| `{t['id']}` | {t['label']} | {ai} | {fi} | {link} |")

    lines += ["", "## Findings", ""]
    for t in TARGETS:
        ff = status.get(t["id"], {}).get("findings_file")
        if ff and (REPO_ROOT / ff).exists():
            lines += ["---", (REPO_ROOT / ff).read_text()]

    report_file.write_text("\n".join(lines) + "\n")
    print(f"\n📄 Report: {report_file.relative_to(REPO_ROOT)}")


# ── CLI ────────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--list",      action="store_true")
    parser.add_argument("--reset",     action="store_true")
    parser.add_argument("--reset-fix", action="store_true")
    parser.add_argument("--target",    metavar="ID")
    parser.add_argument("--report",    action="store_true")
    parser.add_argument("--fix",       action="store_true")
    args = parser.parse_args()

    status = load_status()
    for t in TARGETS:
        if t["id"] not in status:
            status[t["id"]] = {"status": "pending", "findings_file": None, "completed_at": None}
    save_status(status)

    if args.list:
        print(f"\nDoc Audit Status\n")
        print(f"  {'ID':<32} {'AUDIT':<10} {'FIX':<8}  Label")
        print(f"  {'-'*31} {'-'*9} {'-'*7}  {'-'*35}")
        for t in TARGETS:
            s  = status[t["id"]]
            ai = {"done": "✅ done", "pending": "⏳ pend", "error": "❌ err"}.get(s["status"], "?")
            fi = {"done": "✅ done", "error": "❌ err"}.get(s.get("fix_status", ""), "—")
            print(f"  {t['id']:<32} {ai:<10} {fi:<8}  {t['label']}")
        done  = sum(1 for t in TARGETS if status[t["id"]]["status"] == "done")
        fixed = sum(1 for t in TARGETS if status[t["id"]].get("fix_status") == "done")
        print(f"\n  Audit: {done}/{len(TARGETS)} done  |  Fix: {fixed}/{done} done\n")
        return

    if args.reset:
        for t in TARGETS:
            status[t["id"]] = {"status": "pending", "findings_file": None, "completed_at": None}
        save_status(status); print("✅ Reset."); return

    if args.reset_fix:
        for t in TARGETS:
            for k in ("fix_status", "fix_summary_file", "fixed_at"):
                status[t["id"]].pop(k, None)
        save_status(status); print("✅ Fix status reset."); return

    if args.report:
        build_report(status); return

    # Resolve single target if given
    def get_targets(pool):
        if args.target:
            ids = [t["id"] for t in TARGETS]
            if args.target not in ids:
                print(f"❌ Unknown target '{args.target}'. Known: {', '.join(ids)}")
                sys.exit(1)
            return [t for t in TARGETS if t["id"] == args.target]
        return pool

    if args.fix:
        targets = get_targets([t for t in TARGETS
                               if status[t["id"]]["status"] == "done"
                               and status[t["id"]].get("fix_status") != "done"])
        if not targets:
            print("✅ Nothing to fix — run audit first, or use --reset-fix to redo."); return
        print(f"\n🔧 Fix mode — {len(targets)} target(s)\n")
        for t in targets:
            run_fix_target(t, status)
        print(f"\n   git diff docs/   ← review changes")
        print(f"   git add docs/ && git commit -m 'docs(audit): retrospective fixes'")
        return

    # Default: audit
    targets = get_targets([t for t in TARGETS if status[t["id"]]["status"] == "pending"])
    if not targets:
        print("✅ All done. Run --fix to apply fixes, or --reset to re-audit."); return

    print(f"\n🔍 Doc Audit — {len(targets)} target(s)  (interrupt anytime, state is saved)\n")
    for t in targets:
        run_audit_target(t, status)
    build_report(status)
    done = sum(1 for t in TARGETS if status[t["id"]]["status"] == "done")
    print(f"\n✅ {done}/{len(TARGETS)} done. Run --fix to apply corrections.\n")


if __name__ == "__main__":
    main()
