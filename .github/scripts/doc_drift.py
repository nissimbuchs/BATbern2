#!/usr/bin/env python3
"""Doc drift rules, shared by the weekly audit and the PR check (#1214).

A change is in drift when it touches a mapped source path and changes none of the docs
mapped to that path. The rules:

- A commit or PR is exempt only when at least one doc MAPPED to its changed source paths
  changed. An unrelated doc change does not exempt it.
- docs/user-guide/appendix/changelog.md and docs/versions.json never count as a doc change
  and are never suggested: nearly every squash commit touches them.
- docs/api/ is source (OpenAPI specs), except its Markdown files. CLAUDE.md and any
  README.md count as docs and may be mapping targets.
- Mapping targets: a path ending in "/" covers everything under it, anything else is exact.
  Source patterns: a plain pattern is a path prefix; a pattern with * ? or [ is an fnmatch
  pattern over the whole path, and * crosses "/".
- Every commit type is audited except skip_commit_types, so perf and non-conventional
  ("unknown") subjects are audited too. [no-doc] anywhere in the message exempts.

Subcommands:
  audit  --since ISO --until ISO --gaps-file F   commits on HEAD with committer time in [since, until)
  pr     --base REF --title T --comment-file F   exit 1 and write a PR comment on drift
  window [--now ISO]                             print the fixed weekly window (previous Monday
                                                 00:00 UTC to this Monday 00:00 UTC)
"""

import argparse
import fnmatch
import re
import subprocess
import sys
from datetime import datetime, timedelta, timezone

import yaml

NEVER_COUNTS = {"docs/user-guide/appendix/changelog.md", "docs/versions.json"}
DOC_BASENAMES = {"README.md", "CLAUDE.md"}
GLOB_CHARS = re.compile(r"[*?\[]")


def load_config(path):
    with open(path) as f:
        data = yaml.safe_load(f)
    return {
        "mappings": data.get("mappings", []),
        "skip_types": set(data.get("skip_commit_types", [])),
        "skip_scopes": set(data.get("skip_commit_scopes", [])),
    }


def is_doc(path):
    """A documentation file, as opposed to a source file."""
    if path.rsplit("/", 1)[-1] in DOC_BASENAMES:
        return True
    if path.startswith("docs/api/"):
        return path.endswith(".md")
    return path.startswith("docs/")


def source_matches(pattern, path):
    if GLOB_CHARS.search(pattern):
        return fnmatch.fnmatchcase(path, pattern)
    return path.startswith(pattern)


def doc_covers(target, path):
    if target.endswith("/"):
        return path.startswith(target)
    return path == target


def parse_subject(subject):
    """(type, scope) of a conventional subject; ("unknown", "") otherwise."""
    # Dependabot writes two scope groups ("chore(deps)(deps-dev): ..."); the first one counts.
    m = re.match(r"^([a-z]+)(?:\(([^)]*)\))?(?:\([^)]*\))*!?:", subject)
    if not m:
        return "unknown", ""
    return m.group(1), m.group(2) or ""


def skip_reason(message, config):
    """Why this commit or PR is not audited, or None when it is audited."""
    if "[no-doc]" in message:
        return "[no-doc] flag present"
    subject = message.splitlines()[0] if message else ""
    ctype, scope = parse_subject(subject)
    if ctype in config["skip_types"]:
        return f"commit type '{ctype}' is in the skip list"
    scopes = {s.strip() for s in scope.split(",") if s.strip()}
    if scopes and scopes <= config["skip_scopes"]:
        return f"commit scope '{scope}' is in the skip list"
    return None


def classify(changed_files, config):
    """Return (sources, required_docs, satisfied_by).

    sources: changed source files that matched a mapping with at least one countable doc.
    required_docs: the mapped docs (sorted), changelog/versions.json removed.
    satisfied_by: changed files that cover one of the required docs; empty means drift.
    """
    sources, required = set(), set()
    for path in changed_files:
        if is_doc(path) or path in NEVER_COUNTS:
            continue
        for m in config["mappings"]:
            if source_matches(m["source_pattern"], path):
                docs = [d for d in m.get("docs", []) if d not in NEVER_COUNTS]
                if docs:
                    sources.add(path)
                    required.update(docs)
    satisfied = sorted(
        p for p in changed_files
        if p not in NEVER_COUNTS and any(doc_covers(d, p) for d in required)
    )
    return sorted(sources), sorted(required), satisfied


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def weekly_window(now):
    this_monday = (now - timedelta(days=now.weekday())).replace(
        hour=0, minute=0, second=0, microsecond=0)
    return this_monday - timedelta(days=7), this_monday


def parse_iso(value):
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def cmd_window(args):
    now = parse_iso(args.now) if args.now else datetime.now(timezone.utc)
    since, until = weekly_window(now)
    print(f"{since.strftime('%Y-%m-%dT%H:%M:%SZ')} {until.strftime('%Y-%m-%dT%H:%M:%SZ')}")


def cmd_audit(args):
    config = load_config(args.mappings)
    since, until = parse_iso(args.since).timestamp(), parse_iso(args.until).timestamp()
    print(f"Scanning commits with committer time in [{args.since}, {args.until})")
    # Filter by committer time ourselves: `git log --since` stops walking at the first
    # commit older than the cutoff, which skips commits behind an out-of-order date.
    shas = []
    # Merge commits are skipped (as before): their changes are audited in the merged commits.
    for line in git("log", "--no-merges", "--format=%H %ct").splitlines():
        sha, ct = line.split()
        if since <= int(ct) < until:
            shas.append(sha)

    gaps = []
    for sha in shas:
        message = git("log", "-1", "--format=%B", sha).strip()
        subject = message.splitlines()[0] if message else ""
        if skip_reason(message, config):
            continue
        files = [f for f in git("diff-tree", "--no-commit-id", "-r", "--name-only", sha).splitlines() if f]
        sources, required, satisfied = classify(files, config)
        if required and not satisfied:
            gaps.append(f"{sha[:8]}|{subject.replace('|', '/')}|{','.join(required)}")
            print(f"  gap: {sha[:8]} {subject}")

    with open(args.gaps_file, "w") as f:
        for g in gaps:
            f.write(g + "\n")
        f.write(f"{len(gaps)}\n")  # last line = count sentinel
    print(f"GAPS_COUNT={len(gaps)}")


def cmd_pr(args):
    config = load_config(args.mappings)
    base = f"origin/{args.base}"
    if "[no-doc]" in args.title:
        print("[no-doc] flag present in PR title: doc drift check skipped.")
        return 0
    # CLAUDE.md documents "[no-doc] in the commit message", so honour any commit on the branch.
    if "[no-doc]" in git("log", f"{base}..HEAD", "--format=%B"):
        print("[no-doc] flag present in a commit message on this branch: doc drift check skipped.")
        return 0
    reason = skip_reason(args.title, config)
    if reason:
        print(f"{reason}: doc drift check skipped.")
        return 0

    files = [f for f in git("diff", "--name-only", f"{base}...HEAD").splitlines() if f]
    sources, required, satisfied = classify(files, config)
    if not required:
        print("No monitored source paths changed: no drift.")
        return 0
    if satisfied:
        print("PR changes a mapped doc:")
        print("\n".join(satisfied))
        return 0

    print(f"Doc drift: {len(sources)} monitored file(s) changed, none of their mapped docs did.")
    lines = [
        "## Possible documentation drift",
        "",
        "This PR changes monitored source paths but none of the docs mapped to them. "
        "The changelog and `docs/versions.json` do not count.",
        "",
        "**Changed files that triggered this check:**",
        *[f"- `{s}`" for s in sources],
        "",
        "**Docs mapped to them (one updated doc is enough):**",
        *[f"- `{d}`" for d in required],
        "",
        "**To resolve, choose one:**",
        "1. Update the relevant doc(s) in this PR",
        "2. Add `[no-doc]` to the PR title if no doc update is needed (e.g. internal refactor); "
        "editing the title re-runs this check",
        "",
        "_Checked by the Doc Drift Check · mapping rules: `.github/doc-drift-mappings.yml`_",
    ]
    with open(args.comment_file, "w") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    return 1


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--mappings", default=".github/doc-drift-mappings.yml")
    sub = p.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("audit")
    a.add_argument("--since", required=True)
    a.add_argument("--until", required=True)
    a.add_argument("--gaps-file", required=True)
    r = sub.add_parser("pr")
    r.add_argument("--base", required=True)
    r.add_argument("--title", default="")
    r.add_argument("--comment-file", required=True)
    w = sub.add_parser("window")
    w.add_argument("--now")
    args = p.parse_args()
    if args.cmd == "audit":
        cmd_audit(args)
        return 0
    if args.cmd == "window":
        cmd_window(args)
        return 0
    return cmd_pr(args)


if __name__ == "__main__":
    sys.exit(main())
