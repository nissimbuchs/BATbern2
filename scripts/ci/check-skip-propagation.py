#!/usr/bin/env python3
"""Fails for a workflow job that GitHub would skip silently.

A job without a status function in its `if:` gets an implicit `success()`, which is false when ANY
ancestor in the `needs` chain was skipped, not only a direct one. A job downstream of a conditional
job (e.g. the frontend test shards, skipped when no frontend file changed) therefore needs an
explicit `!cancelled()` / `always()` plus its own result checks. Found 2026-11-03: after the frontend
shard split (#1074) `deploy-to-staging` was skipped on every PR without frontend changes, and #1201
auto-merged without deploying.

Usage: check-skip-propagation.py .github/workflows/build.yml [...]
"""
import sys

import yaml

STATUS = ("always()", "!cancelled()", "cancelled()", "failure()")


def ancestors(jobs, name, seen=None):
    seen = set() if seen is None else seen
    needs = jobs[name].get("needs") or []
    for n in [needs] if isinstance(needs, str) else needs:
        if n not in seen:
            seen.add(n)
            ancestors(jobs, n, seen)
    return seen


def problems(path):
    jobs = (yaml.safe_load(open(path)) or {}).get("jobs") or {}
    found = []
    for name, job in jobs.items():
        if job.get("if") is False:
            continue
        cond = str(job.get("if", "")).strip()
        if cond in ("false", "${{ false }}"):
            continue
        conditional = sorted(a for a in ancestors(jobs, name) if "if" in jobs[a])
        if conditional and not any(s in cond for s in STATUS):
            found.append(f"{path}: job '{name}' is skipped whenever {conditional} is skipped "
                         f"(implicit success()); add !cancelled() and check needs.<job>.result")
    return found


if __name__ == "__main__":
    out = [p for f in sys.argv[1:] for p in problems(f)]
    print("\n".join(out) if out else "ok: no job is skipped by a skipped ancestor")
    sys.exit(1 if out else 0)
