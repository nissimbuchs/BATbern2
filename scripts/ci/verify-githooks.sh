#!/usr/bin/env bash
#
# BATbern Platform — git-hook install-path guard (#973)
#
# The defect this exists to prevent: the repo shipped a working installer
# (.githooks/install-hooks.sh) that NOTHING invoked, plus setup docs pointing at an
# `npm run prepare` script that had never existed. A fresh clone therefore committed and
# pushed with no Checkstyle, no ESLint and no conventional-commit check, while CLAUDE.md and
# project-context.md both asserted all three were enforced — and the `--no-verify` warning in
# those docs guarded a gate that was not there. The first feedback arrived from CI on a PR
# that had already deployed to production.
#
# So the failure mode is silent divergence between what the docs promise and what the setup
# path actually wires. This script asserts the wiring, not the prose:
#
#   1. every hook the installer claims to install exists and is executable
#   2. `make install` really depends on `install-hooks`
#   3. the root package.json `prepare` script really points at the installer
#   4. there is exactly ONE hook set (no resurrected web-frontend/.husky/)
#   5. commit-msg BEHAVIOURALLY rejects a message with no conventional-commit type
#
# (5) is the one that caught the second defect in #973: .commitlintrc.json had `type-empty`
# and `subject-empty` at severity 1, commitlint exits 0 on warnings, so the hook printed
# "✅ Commit message format valid" for `broken message no type`. A property assertion on the
# rc file would have missed it — only running the hook shows it. The check works whether
# commitlint is installed (rc path) or not (the hook's regex fallback), because it asserts
# the hook's exit code rather than which branch produced it.
#
# Usage: scripts/ci/verify-githooks.sh
# Exit 0 = install path intact. Exit 1 = it has rotted; the message says how.

set -uo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
FAILURES=0

fail() {
    echo -e "${RED}✗ $1${NC}"
    FAILURES=$((FAILURES + 1))
}
pass() { echo -e "${GREEN}✓ $1${NC}"; }

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
    echo "Not a git repository — nothing to verify."
    exit 0
}
cd "$REPO_ROOT" || exit 1

echo "🪝 Verifying the git-hook install path (#973)..."
echo ""

# ── 1. Hooks exist and are executable ───────────────────────────────────────────
# Derived from the installer itself, so adding an install_hook call without adding the
# file fails here rather than printing "⚠️ not found" during setup and carrying on.
INSTALLER='.githooks/install-hooks.sh'
if [ ! -x "$INSTALLER" ]; then
    fail "$INSTALLER is missing or not executable"
else
    pass "$INSTALLER is present and executable"

    CLAIMED_HOOKS=$(grep -oE '^install_hook "[a-z-]+"' "$INSTALLER" | sed 's/install_hook "//; s/"//')
    if [ -z "$CLAIMED_HOOKS" ]; then
        fail "$INSTALLER has no install_hook calls — it installs nothing"
    fi
    for hook in $CLAIMED_HOOKS; do
        if [ ! -f ".githooks/$hook" ]; then
            fail "$INSTALLER installs '$hook' but .githooks/$hook does not exist"
        elif [ ! -x ".githooks/$hook" ]; then
            fail ".githooks/$hook exists but is not executable (git will skip it silently)"
        else
            pass ".githooks/$hook present and executable"
        fi
    done
fi

# ── 2. `make install` wires the hooks ───────────────────────────────────────────
# The whole point of #973: the documented first command must install the hooks.
if ! grep -qE '^install:.*install-hooks' Makefile; then
    fail "'make install' does not depend on install-hooks — a fresh clone stays ungated"
else
    pass "'make install' depends on install-hooks"
fi
if ! grep -qE '^install-hooks:' Makefile; then
    fail "Makefile has no 'install-hooks' target"
else
    pass "Makefile defines the install-hooks target"
fi

# ── 3. The npm path wires them too ──────────────────────────────────────────────
if ! grep -q 'install-hooks.sh' package.json; then
    fail "root package.json 'prepare' does not reference install-hooks.sh"
else
    pass "root package.json prepare -> install-hooks.sh"
fi

# ── 4. Exactly one hook set ─────────────────────────────────────────────────────
# web-frontend/.husky/ was the second, competing set: husky v8 syntax
# (. "$(dirname -- "$0")/_/husky.sh") against a v9 pin that removed that shim, so those
# hooks could not source their own first line. Deleted in #974. If it comes back, two
# mechanisms fight over core.hooksPath and the loser is silent.
if [ -d 'web-frontend/.husky' ]; then
    fail "web-frontend/.husky/ exists again — two competing hook sets (see #973)"
else
    pass "no competing husky hook set"
fi

# ── 5. commit-msg actually rejects a message with no type ───────────────────────
if [ -x '.githooks/commit-msg' ]; then
    TMPMSG=$(mktemp)
    trap 'rm -f "$TMPMSG"' EXIT

    printf 'broken message no type\n' > "$TMPMSG"
    if ./.githooks/commit-msg "$TMPMSG" >/dev/null 2>&1; then
        fail "commit-msg ACCEPTED 'broken message no type' — check type-empty/subject-empty severity in web-frontend/.commitlintrc.json (must be 2, not 1)"
    else
        pass "commit-msg rejects a message with no type"
    fi

    printf 'fix(ci): a valid message\n' > "$TMPMSG"
    if ./.githooks/commit-msg "$TMPMSG" >/dev/null 2>&1; then
        pass "commit-msg accepts a valid conventional commit"
    else
        fail "commit-msg REJECTED 'fix(ci): a valid message' — the hook blocks every commit"
    fi
else
    fail ".githooks/commit-msg is missing or not executable"
fi

# ── 6. Both validation paths accept the same set of types ───────────────────────
# commit-msg has two branches: commitlint (when web-frontend/node_modules exists) and a
# hand-rolled regex fallback. If their type lists diverge, whether a commit is accepted
# depends on whether someone happens to have run `npm ci` — `review(x): ...` was accepted
# on one path and rejected on the other until this was aligned.
if [ -f 'web-frontend/.commitlintrc.json' ] && [ -f '.githooks/commit-msg' ]; then
    RC_TYPES=$(python3 -c "
import json, pathlib
d = json.loads(pathlib.Path('web-frontend/.commitlintrc.json').read_text())
print(' '.join(sorted(d['rules']['type-enum'][2])))" 2>/dev/null)
    HOOK_TYPES=$(grep -oE '\^\(feat\|[a-z|]+\)' .githooks/commit-msg \
        | sed 's/^\^(//; s/)$//' | tr '|' '\n' | sort | tr '\n' ' ' | sed 's/ $//')
    if [ -z "$RC_TYPES" ] || [ -z "$HOOK_TYPES" ]; then
        fail "could not extract the commit type list from both validation paths"
    elif [ "$RC_TYPES" != "$HOOK_TYPES" ]; then
        fail "commit type lists diverge — commitlintrc: [$RC_TYPES] vs commit-msg regex: [$HOOK_TYPES]"
    else
        pass "commitlint and the regex fallback accept the same types"
    fi
fi

echo ""
if [ "$FAILURES" -ne 0 ]; then
    echo -e "${RED}❌ $FAILURES check(s) failed — the git-hook install path has rotted.${NC}"
    echo -e "${YELLOW}   Fix the wiring, not the docs. See #973.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Git-hook install path intact.${NC}"
exit 0
