#!/usr/bin/env bash
# test-doc-drift.sh
# Tests the doc-drift weekly audit and PR check end to end against a throwaway git repo
# with synthetic commits. Uses the real mappings file. Needs git, python3 and pyyaml.
#
#   .github/scripts/test-doc-drift.sh
#
# Temp files go under $DRIFT_TEST_TMP (default ~/.cache), not /tmp (a tmpfs on the dev host).

set -uo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SCRIPTS="${DRIFT_SCRIPTS_DIR:-$REPO_ROOT/.github/scripts}"
export MAPPINGS_FILE="$REPO_ROOT/.github/doc-drift-mappings.yml"

TMP_PARENT="${DRIFT_TEST_TMP:-$HOME/.cache}"
mkdir -p "$TMP_PARENT"
WORK=$(mktemp -d "$TMP_PARENT/doc-drift-test.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

failures=0
passes=0
pass() { passes=$((passes + 1)); echo "  ok   $1"; }
fail() { failures=$((failures + 1)); echo "  FAIL $1"; }

EMS=services/event-management-service/src/main/java/ch/batbern/events
CHANGELOG=docs/user-guide/appendix/changelog.md

# commit <date> <subject> <file>... : touch each file (append a line) and commit at <date>
commit() {
  local date="$1" subject="$2"
  shift 2
  local f
  for f in "$@"; do
    mkdir -p "$(dirname "$f")"
    echo "$RANDOM $subject" >> "$f"
    git add "$f"
  done
  GIT_AUTHOR_DATE="$date" GIT_COMMITTER_DATE="$date" git commit -q -m "$subject"
}

cd "$WORK"
git init -q -b develop repo
cd repo
git config user.email "test@example.com"
git config user.name "Doc Drift Test"
git config commit.gpgsign false

commit "2026-11-01T12:00:00Z" "chore: initial" README.md

# ── Weekly audit ────────────────────────────────────────────────────────────
# DRIFT_NOW is Wednesday 2026-11-18, so the window is Mon 2026-11-09 00:00 UTC to Mon 2026-11-16 00:00 UTC.
IN="2026-11-10T12:00:00Z"
commit "$IN" "feat(events): W-changelog-only" "$EMS/service/EventService.java" "$CHANGELOG"
commit "$IN" "feat(events): W-versions-only" "$EMS/service/EventService.java" docs/versions.json
commit "$IN" "feat(events): W-mapped-doc" "$EMS/service/EventService.java" docs/architecture/06-backend-architecture.md
commit "$IN" "feat(events): W-unmapped-doc" "$EMS/service/EventService.java" docs/guides/some-other-guide.md
commit "$IN" "fix(security): W-scope-security" api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java
commit "$IN" "fix(auth): W-scope-auth" api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java
commit "$IN" "security(gateway): W-type-security" api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java
commit "$IN" "perf(web): W-type-perf" web-frontend/src/pages/Home.tsx
commit "$IN" "Epic 7 attendee touchpoints W-unknown (#776)" "$EMS/service/EventService.java"
commit "$IN" "feat(api): W-openapi-only" docs/api/events-api.openapi.yml
commit "$IN" "fix(ci): W-workflow-with-claude-md" .github/workflows/build.yml CLAUDE.md
commit "$IN" "feat(dev): W-compose-with-readme-only" docker-compose.yml README.md
commit "$IN" "chore(events): W-chore" "$EMS/service/EventService.java"
commit "$IN" "chore(deps)(deps-dev): W-dependabot-double-scope" web-frontend/package.json web-frontend/src/x.ts
commit "$IN" "feat!: W-breaking-no-scope" "$EMS/service/EventService.java"
commit "$IN" "fix(events): W-no-doc [no-doc]" "$EMS/service/EventService.java"
commit "$IN" "feat(speakers): W-speaker-ems" "$EMS/service/SpeakerInvitationService.java"
commit "$IN" "feat(speakers): W-speaker-ems-with-guide" "$EMS/service/SpeakerInvitationService.java" docs/user-guide/speaker-portal/dashboard.md
commit "2026-11-08T23:59:00Z" "feat(events): W-before-window" "$EMS/service/EventService.java"
commit "2026-11-16T00:00:01Z" "feat(events): W-after-window" "$EMS/service/EventService.java"

echo "Weekly audit"
GAPS="$WORK/gaps.txt"
if DRIFT_NOW="2026-11-18T10:00:00Z" GAPS_FILE="$GAPS" "$SCRIPTS/doc-drift-weekly-audit.sh" > "$WORK/weekly.log" 2>&1; then
  pass "weekly audit exits 0"
else
  fail "weekly audit exits 0 (log follows)"
  cat "$WORK/weekly.log"
fi

expect_flagged() {
  if grep -q -- "$1" "$GAPS" 2>/dev/null; then pass "flagged: $1"; else fail "flagged: $1"; fi
}
expect_clean() {
  if grep -q -- "$1" "$GAPS" 2>/dev/null; then fail "not flagged: $1"; else pass "not flagged: $1"; fi
}

expect_flagged "W-changelog-only"
expect_flagged "W-versions-only"
expect_clean   "W-mapped-doc"
expect_flagged "W-unmapped-doc"
expect_flagged "W-scope-security"
expect_flagged "W-scope-auth"
expect_flagged "W-type-security"
expect_flagged "W-type-perf"
expect_flagged "W-unknown"
expect_flagged "W-openapi-only"
expect_clean   "W-workflow-with-claude-md"
expect_flagged "W-compose-with-readme-only"
expect_clean   "W-chore"
expect_clean   "W-dependabot-double-scope"
expect_flagged "W-breaking-no-scope"
expect_clean   "W-no-doc"
expect_flagged "W-speaker-ems"
expect_clean   "W-speaker-ems-with-guide"
expect_clean   "W-before-window"
expect_clean   "W-after-window"
# The changelog is never suggested as the doc to update.
if grep -q "changelog.md" "$GAPS" 2>/dev/null; then fail "changelog never suggested"; else pass "changelog never suggested"; fi
if [[ "$(tail -1 "$GAPS" 2>/dev/null)" == "12" ]]; then pass "count sentinel is 12"; else fail "count sentinel is 12 (got '$(tail -1 "$GAPS" 2>/dev/null)')"; fi

# ── PR check ────────────────────────────────────────────────────────────────
echo "PR check"
git update-ref refs/remotes/origin/develop HEAD

# pr_case <name> <title> <expected exit> <file>...
pr_case() {
  local name="$1" title="$2" expected="$3"
  shift 3
  git checkout -q -b "case-$name" origin/develop
  commit "2026-11-18T09:00:00Z" "$title" "$@"
  local rc=0
  BASE_REF=develop PR_TITLE="$title" COMMENT_FILE="$WORK/comment-$name.md" \
    "$SCRIPTS/doc-drift-pr-check.sh" > "$WORK/pr-$name.log" 2>&1 || rc=$?
  if [[ "$rc" == "$expected" ]]; then
    pass "PR $name exits $expected"
  else
    fail "PR $name exits $expected (got $rc, log follows)"
    cat "$WORK/pr-$name.log"
  fi
  git checkout -q develop
}

pr_case changelog-only "feat(events): add a thing" 1 "$EMS/service/EventService.java" "$CHANGELOG"
pr_case mapped-doc     "feat(events): add a thing" 0 "$EMS/service/EventService.java" docs/architecture/06a-workflow-state-machines.md
pr_case security-scope "fix(security): tighten" 1 services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java
pr_case perf-type      "perf(web): lazy load" 1 web-frontend/src/pages/Home.tsx
pr_case unknown-type   "Make the homepage faster" 1 web-frontend/src/pages/Home.tsx
pr_case no-doc-title   "fix(events): internal [no-doc]" 0 "$EMS/service/EventService.java"
pr_case chore-type     "chore(events): tidy" 0 "$EMS/service/EventService.java"
pr_case unmonitored    "feat(x): unmapped path" 0 some/unmapped/file.txt

# pr_two <name> <expected exit> <msg1> <file1> <msg2> <file2>: a branch of two commits, PR title = msg2.
# [no-doc] exempts only the files of its own commit, not every other commit on the branch.
pr_two() {
  local name="$1" expected="$2" m1="$3" f1="$4" m2="$5" f2="$6"
  git checkout -q -b "case-$name" origin/develop
  commit "2026-11-18T09:00:00Z" "$m1" "$f1"
  commit "2026-11-18T09:05:00Z" "$m2" "$f2"
  local rc=0
  BASE_REF=develop PR_TITLE="$m2" COMMENT_FILE="$WORK/comment-$name.md" \
    "$SCRIPTS/doc-drift-pr-check.sh" > "$WORK/pr-$name.log" 2>&1 || rc=$?
  if [[ "$rc" == "$expected" ]]; then
    pass "PR $name exits $expected"
  else
    fail "PR $name exits $expected (got $rc, log follows)"
    cat "$WORK/pr-$name.log"
  fi
  git checkout -q develop
}

pr_two no-doc-commit-does-not-exempt-others 1 \
  "test(web): fixture [no-doc]" web-frontend/src/pages/Home.tsx \
  "fix(events): change the workflow" "$EMS/service/EventService.java"
pr_two no-doc-commit-exempts-its-own-files 0 \
  "fix(events): internal [no-doc]" "$EMS/service/EventService.java" \
  "feat(x): unmapped path" some/unmapped/other.txt

if grep -q "changelog.md" "$WORK/comment-changelog-only.md" 2>/dev/null; then
  fail "PR comment does not suggest the changelog"
else
  pass "PR comment does not suggest the changelog"
fi

# ── Mapping file integrity ──────────────────────────────────────────────────
# Every target exists, and every source pattern matches at least one tracked file, so a
# renamed or deleted path cannot leave a mapping that silently never fires.
echo "Mappings"
if (cd "$REPO_ROOT" && git ls-files > "$WORK/tracked.txt" && python3 - "$MAPPINGS_FILE" "$WORK/tracked.txt" "$SCRIPTS" <<'PY'
import os, sys
sys.path.insert(0, sys.argv[3])
import doc_drift
config = doc_drift.load_config(sys.argv[1])
tracked = [l.rstrip("\n") for l in open(sys.argv[2])]
bad = []
for m in config["mappings"]:
    pattern = m["source_pattern"]
    if not any(doc_drift.source_matches(pattern, f) for f in tracked):
        bad.append(f"source matches no tracked file: {pattern}")
    for d in m.get("docs", []):
        if not os.path.exists(d):
            bad.append(f"target does not exist: {d} (from {pattern})")
print("\n".join(bad))
sys.exit(1 if bad else 0)
PY
); then
  pass "every source pattern matches a tracked file and every target exists"
else
  fail "every source pattern matches a tracked file and every target exists"
fi

echo
echo "$passes passed, $failures failed"
[[ $failures -eq 0 ]]
