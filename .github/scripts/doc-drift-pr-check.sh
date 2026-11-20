#!/usr/bin/env bash
# doc-drift-pr-check.sh
# Fails a PR that touches mapped source paths without changing any doc mapped to them.
# [no-doc] in the PR title or in any commit message on the branch skips the check.
# Rules: .github/scripts/doc_drift.py (#1214).
#
# Env:
#   MAPPINGS_FILE  mapping rules (default .github/doc-drift-mappings.yml)
#   BASE_REF       base branch, compared as origin/$BASE_REF (default develop)
#   PR_TITLE       PR title (the squash subject)
#   COMMENT_FILE   PR comment written on drift (default /tmp/drift-pr-comment.md)
# Exits 0 (pass) or 1 (drift).

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

exec python3 "$SCRIPT_DIR/doc_drift.py" --mappings "${MAPPINGS_FILE:-.github/doc-drift-mappings.yml}" \
  pr --base "${BASE_REF:-develop}" --title "${PR_TITLE:-}" \
  --comment-file "${COMMENT_FILE:-/tmp/drift-pr-comment.md}"
