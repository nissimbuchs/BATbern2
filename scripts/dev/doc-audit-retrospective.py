#!/usr/bin/env python3
"""
doc-audit-retrospective.py
──────────────────────────
One-shot retrospective audit: compares documentation claims against the
current integration test suite to find stale or undocumented behaviour.

Each doc target is audited in an isolated `claude` subagent session to
stay within context limits. State is saved to docs/plans/doc-audit/status.json
after each target, so you can interrupt and resume freely.

Usage:
  python3 scripts/dev/doc-audit-retrospective.py           # run all pending
  python3 scripts/dev/doc-audit-retrospective.py --list    # show status
  python3 scripts/dev/doc-audit-retrospective.py --reset   # reset all to pending
  python3 scripts/dev/doc-audit-retrospective.py --target state-machines  # one target only
"""

import argparse
import datetime
import json
import os
import pathlib
import subprocess
import sys
import textwrap

# ── Paths ────────────────────────────────────────────────────────────────────
REPO_ROOT   = pathlib.Path(__file__).parent.parent.parent.resolve()
STATUS_FILE = REPO_ROOT / "docs/plans/doc-audit/status.json"
FINDINGS_DIR = REPO_ROOT / "docs/plans/doc-audit"

# ── Audit targets ────────────────────────────────────────────────────────────
# Each target = one isolated claude subagent session.
# (doc, tests[]) pairs chosen by risk: architecture docs first, then PRDs,
# then user-guide sections. Add more targets here as needed.

TARGETS = [
    {
        "id": "state-machines",
        "label": "Event state machine & lifecycle",
        "doc":   "docs/architecture/06a-workflow-state-machines.md",
        "tests": [
            "services/event-management-service/src/test/java",
        ],
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
        "tests": [
            "services/event-management-service/src/test/java",
            "web-frontend/src",
        ],
    },
    {
        "id": "epic-5-organizer-workflows",
        "label": "Organizer workflows & lifecycle automation (Epic 5)",
        "doc":   "docs/prd/epic-5-enhanced-organizer-workflows.md",
        "tests": [
            "services/event-management-service/src/test/java",
        ],
    },
    {
        "id": "epic-6-speaker-portal",
        "label": "Speaker self-service portal (Epic 6)",
        "doc":   "docs/prd/epic-6-speaker-portal-support.md",
        "tests": [
            "services/speaker-coordination-service/src/test/java",
        ],
    },
    {
        "id": "epic-8-partner",
        "label": "Partner coordination (Epic 8)",
        "doc":   "docs/prd/epic-8-partner-coordination.md",
        "tests": [
            "services/partner-coordination-service/src/test/java",
        ],
    },
    {
        "id": "epic-9-speaker-auth",
        "label": "Speaker authentication (Epic 9)",
        "doc":   "docs/prd/epic-9-speaker-authentication.md",
        "tests": [
            "services/speaker-coordination-service/src/test/java",
        ],
    },
    {
        "id": "user-lifecycle-sync",
        "label": "User lifecycle sync",
        "doc":   "docs/architecture/06b-user-lifecycle-sync.md",
        "tests": [
            "services/company-user-management-service/src/test/java",
        ],
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
        "doc":   "docs/user-guide/workflow",   # directory — all .md files inside
        "tests": [
            "services/event-management-service/src/test/java",
        ],
    },
    {
        "id": "user-guide-speaker-portal",
        "label": "User guide — speaker portal",
        "doc":   "docs/user-guide/speaker-portal",
        "tests": [
            "services/speaker-coordination-service/src/test/java",
        ],
    },
    {
        "id": "user-guide-partner-portal",
        "label": "User guide — partner portal",
        "doc":   "docs/user-guide/partner-portal",
        "tests": [
            "services/partner-coordination-service/src/test/java",
        ],
    },
]

# ── Status helpers ────────────────────────────────────────────────────────────

def load_status() -> dict:
    if STATUS_FILE.exists():
        return json.loads(STATUS_FILE.read_text())
    # Initialise from TARGETS
    return {t["id"]: {"status": "pending", "findings_file": None, "completed_at": None}
            for t in TARGETS}


def save_status(status: dict):
    FINDINGS_DIR.mkdir(parents=True, exist_ok=True)
    STATUS_FILE.write_text(json.dumps(status, indent=2) + "\n")


# ── Prompt builder ────────────────────────────────────────────────────────────

def build_prompt(target: dict) -> str:
    doc_path  = target["doc"]
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
    3. **UNDOCUMENTED** — a test asserts a business rule that is NOT mentioned in the doc

    ## Doc to audit
    `{doc_path}`
    (If this is a directory, read all .md files inside it)

    ## Test directories to search
    {test_paths_str}

    ## Instructions

    Step 1 — Read the doc(s) completely. Extract every:
      - State machine transition or business rule
      - User-facing behaviour or API contract
      - Timing rule, threshold, or constraint (e.g. "14 days", "30 days", "02:00")
      - Named workflow step or process

    Step 2 — For each claim, search the test directories:
      - Use Grep to find relevant test methods by keyword
      - Read matching test files to understand what they actually assert
      - Classify: VALIDATED / MISMATCH / UNTESTED

    Step 3 — Scan test files for business-rule assertions NOT covered by the doc:
      - Look for test method names that describe behaviour (`should_`, `when_`, `given_`)
      - If the tested behaviour is not mentioned in the doc → UNDOCUMENTED

    Step 4 — Write your findings to `{findings_file}` using this format:

    ```markdown
    # Doc Audit Findings — {target['label']}
    **Audited:** {datetime.date.today().isoformat()}
    **Doc:** `{doc_path}`
    **Tests searched:** {', '.join(f'`{p}`' for p in test_paths)}

    ## Summary
    - VALIDATED: n
    - MISMATCH: n
    - UNTESTED: n
    - UNDOCUMENTED: n

    ## MISMATCH
    ### M1 — <short title>
    **Doc claims:** "exact quote from doc"
    **Test asserts:** `TestClass#methodName` — description of what test actually checks
    **Action:** Update doc to say: "suggested correction"

    ## UNTESTED
    ### U1 — <short title>
    **Doc claims:** "exact quote"
    **Test found:** none
    **Risk:** high / medium / low
    **Action:** Write a test, or remove/update the claim if it's stale

    ## UNDOCUMENTED
    ### N1 — <short title>
    **Test:** `TestClass#methodName` — what it asserts
    **Missing from doc:** which doc section should mention this
    **Action:** Add a sentence to doc section X

    ## VALIDATED (list only — no detail needed)
    - "claim text" → `TestClass#methodName`
    ```

    ## Rules
    - Be surgical — only flag real discrepancies, not stylistic differences
    - Quote the doc and the test precisely
    - If a test exists but only partially validates a claim, mark as UNTESTED with a note
    - Do not modify any source files or doc files — output findings only
    - If everything checks out and there are no findings, write a findings file that says so
    """).strip()


# ── Runner ────────────────────────────────────────────────────────────────────

def run_target(target: dict, status: dict, dry_run: bool = False) -> bool:
    tid = target["id"]
    label = target["label"]
    findings_file = FINDINGS_DIR / f"findings-{tid}.md"

    print(f"\n{'='*60}")
    print(f"▶  Auditing: {label}")
    print(f"   Doc:   {target['doc']}")
    print(f"   Tests: {', '.join(target['tests'])}")
    print(f"{'='*60}")

    if dry_run:
        print("   [dry-run] Skipping claude invocation.")
        return True

    prompt = build_prompt(target)
    prompt_file = FINDINGS_DIR / f"prompt-{tid}.txt"
    FINDINGS_DIR.mkdir(parents=True, exist_ok=True)
    prompt_file.write_text(prompt)

    print(f"   Launching claude subagent session…")

    result = subprocess.run(
        [
            "claude",
            "--print",
            "--allowedTools", "Read,Edit,Write,Glob,Grep",
            "--model", "claude-sonnet-4-6",
            prompt,
        ],
        cwd=str(REPO_ROOT),
        capture_output=False,   # stream output so user can watch progress
        text=True,
    )

    if result.returncode != 0:
        print(f"   ❌ claude exited with code {result.returncode}")
        status[tid]["status"] = "error"
        save_status(status)
        return False

    # Mark complete
    status[tid]["status"] = "done"
    status[tid]["findings_file"] = str(findings_file.relative_to(REPO_ROOT))
    status[tid]["completed_at"] = datetime.datetime.now().isoformat()
    save_status(status)

    if findings_file.exists():
        print(f"   ✅ Findings written to {findings_file.relative_to(REPO_ROOT)}")
    else:
        print(f"   ⚠️  No findings file written — check claude output above.")

    return True


# ── Aggregate report ──────────────────────────────────────────────────────────

def build_report(status: dict):
    report_file = FINDINGS_DIR / "report.md"
    lines = [
        "# Doc Audit Retrospective Report",
        f"**Generated:** {datetime.date.today().isoformat()}",
        "",
        "## Status",
        "",
        "| Target | Label | Status | Findings |",
        "|--------|-------|--------|----------|",
    ]

    for t in TARGETS:
        s = status.get(t["id"], {})
        st = s.get("status", "pending")
        ff = s.get("findings_file", "")
        link = f"[view]({pathlib.Path(ff).name})" if ff else "—"
        icon = {"done": "✅", "pending": "⏳", "error": "❌", "skipped": "⏭"}.get(st, "?")
        lines.append(f"| `{t['id']}` | {t['label']} | {icon} {st} | {link} |")

    lines += ["", "## Findings", ""]

    for t in TARGETS:
        s = status.get(t["id"], {})
        ff = s.get("findings_file")
        if ff and (REPO_ROOT / ff).exists():
            lines.append(f"---")
            lines.append((REPO_ROOT / ff).read_text())

    report_file.write_text("\n".join(lines) + "\n")
    print(f"\n📄 Report written to {report_file.relative_to(REPO_ROOT)}")


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--list",   action="store_true", help="Show current status and exit")
    parser.add_argument("--reset",  action="store_true", help="Reset all targets to pending")
    parser.add_argument("--target", metavar="ID",        help="Run a single target by ID")
    parser.add_argument("--dry-run",action="store_true", help="Show what would run, skip claude")
    parser.add_argument("--report", action="store_true", help="Regenerate aggregate report and exit")
    args = parser.parse_args()

    status = load_status()

    # Ensure all targets exist in status (for newly added targets)
    for t in TARGETS:
        if t["id"] not in status:
            status[t["id"]] = {"status": "pending", "findings_file": None, "completed_at": None}
    save_status(status)

    if args.list:
        print(f"\nDoc Audit Status  ({STATUS_FILE.relative_to(REPO_ROOT)})\n")
        for t in TARGETS:
            s = status[t["id"]]
            icon = {"done": "✅", "pending": "⏳", "error": "❌"}.get(s["status"], "?")
            done_at = s.get("completed_at", "")[:10] if s.get("completed_at") else ""
            print(f"  {icon} [{s['status']:<8}] {t['id']:<30} {t['label']}  {done_at}")
        pending = sum(1 for t in TARGETS if status[t["id"]]["status"] == "pending")
        done    = sum(1 for t in TARGETS if status[t["id"]]["status"] == "done")
        print(f"\n  {done}/{len(TARGETS)} complete, {pending} pending\n")
        return

    if args.reset:
        for t in TARGETS:
            status[t["id"]] = {"status": "pending", "findings_file": None, "completed_at": None}
        save_status(status)
        print("✅ All targets reset to pending.")
        return

    if args.report:
        build_report(status)
        return

    # Determine targets to run
    if args.target:
        ids = [t["id"] for t in TARGETS]
        if args.target not in ids:
            print(f"❌ Unknown target '{args.target}'. Known: {', '.join(ids)}")
            sys.exit(1)
        targets_to_run = [t for t in TARGETS if t["id"] == args.target]
    else:
        targets_to_run = [t for t in TARGETS if status[t["id"]]["status"] == "pending"]

    if not targets_to_run:
        print("✅ All targets already complete. Use --reset to re-run, or --list to view status.")
        return

    print(f"\n🔍 Doc Audit Retrospective")
    print(f"   Targets to run: {len(targets_to_run)}")
    print(f"   State file:     {STATUS_FILE.relative_to(REPO_ROOT)}")
    print(f"   Findings dir:   {FINDINGS_DIR.relative_to(REPO_ROOT)}/")
    print(f"\n   Interrupt at any time — completed targets are saved and will be skipped on resume.\n")

    errors = 0
    for target in targets_to_run:
        ok = run_target(target, status, dry_run=args.dry_run)
        if not ok:
            errors += 1
            print(f"   ⚠️  Continuing to next target despite error…")

    # Build aggregate report after all targets
    build_report(status)

    print(f"\n{'='*60}")
    done = sum(1 for t in TARGETS if status[t["id"]]["status"] == "done")
    print(f"✅ {done}/{len(TARGETS)} targets complete. {errors} error(s).")
    if errors:
        print(f"   Re-run to retry errored targets (they remain 'pending').")
    print(f"{'='*60}\n")


if __name__ == "__main__":
    main()
