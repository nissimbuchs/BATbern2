#!/usr/bin/env bash
# doc-drift-pr-check.sh
# Checks a PR for documentation drift: feat/fix commits that touch monitored
# source paths without any corresponding change in docs/.
# Exits 0 (pass) or 1 (fail). Writes /tmp/drift-pr-comment.md on failure.

set -euo pipefail

MAPPINGS_FILE="${MAPPINGS_FILE:-.github/doc-drift-mappings.yml}"
BASE_REF="${BASE_REF:-main}"
PR_TITLE="${PR_TITLE:-}"

# ── 1. [no-doc] escape hatch ────────────────────────────────────────────────
if echo "$PR_TITLE" | grep -q "\[no-doc\]"; then
  echo "✅ [no-doc] flag present in PR title — doc drift check skipped."
  exit 0
fi

# ── 2. Commit type + scope filter ───────────────────────────────────────────
commit_type=$(echo "$PR_TITLE" | sed -E 's/^([a-z]+)[(:!].*/\1/' | grep -E '^[a-z]+$' || echo "unknown")
commit_scope=$(echo "$PR_TITLE" | sed -E 's/^[a-z]+\(([^)]+)\).*/\1/' | grep -vE '^[a-z]+[:!]' || echo "")

SKIP_TYPES=$(python3 -c "
import yaml
data = yaml.safe_load(open('$MAPPINGS_FILE'))
print('|'.join(data.get('skip_commit_types', [])))
")

SKIP_SCOPES=$(python3 -c "
import yaml
data = yaml.safe_load(open('$MAPPINGS_FILE'))
print('|'.join(data.get('skip_commit_scopes', [])))
")

if [[ -n "$SKIP_TYPES" ]] && echo "$commit_type" | grep -qE "^($SKIP_TYPES)$"; then
  echo "✅ Commit type '$commit_type' is in skip list — doc drift check skipped."
  exit 0
fi

if [[ -n "$SKIP_SCOPES" && -n "$commit_scope" ]] && echo "$commit_scope" | grep -qE "^($SKIP_SCOPES)$"; then
  echo "✅ Commit scope '$commit_scope' is in skip list — doc drift check skipped."
  exit 0
fi

echo "🔍 Auditing PR (type: '$commit_type') against base: origin/$BASE_REF"

# ── 3. Get changed files in this PR ─────────────────────────────────────────
changed_files=$(git diff --name-only "origin/$BASE_REF"...HEAD)

if [[ -z "$changed_files" ]]; then
  echo "✅ No changed files detected."
  exit 0
fi

# ── 4. Check if any docs/ files are already in the PR ───────────────────────
doc_changed=$(echo "$changed_files" | grep -E "^docs/" || true)
if [[ -n "$doc_changed" ]]; then
  echo "✅ PR includes doc changes:"
  echo "$doc_changed"
  exit 0
fi

# ── 5. Check changed files against mappings ─────────────────────────────────
matched_docs=()
matched_sources=()

# Load all source patterns once
all_patterns=$(python3 -c "
import yaml
data = yaml.safe_load(open('$MAPPINGS_FILE'))
for m in data['mappings']:
    print(m['source_pattern'])
")

while IFS= read -r file; do
  while IFS= read -r pattern; do
    if [[ "$file" == ${pattern}* ]]; then
      docs=$(python3 -c "
import yaml
data = yaml.safe_load(open('$MAPPINGS_FILE'))
for m in data['mappings']:
    if m['source_pattern'] == '$pattern':
        print('\n'.join(m.get('docs', [])))
" || true)
      if [[ -n "$docs" ]]; then
        matched_sources+=("$file")
        while IFS= read -r doc; do
          matched_docs+=("$doc")
        done <<< "$docs"
      fi
    fi
  done <<< "$all_patterns"
done <<< "$changed_files"

# Deduplicate
unique_docs=($(printf '%s\n' "${matched_docs[@]:-}" | sort -u | grep -v '^$' || true))
unique_sources=($(printf '%s\n' "${matched_sources[@]:-}" | sort -u || true))

if [[ ${#unique_docs[@]} -eq 0 ]]; then
  echo "✅ No monitored source paths changed — no drift."
  exit 0
fi

# ── 6. Drift detected — build PR comment ────────────────────────────────────
echo "❌ Doc drift detected in ${#unique_sources[@]} file(s)."

{
  echo "## ⚠️ Possible Documentation Drift"
  echo ""
  echo "This PR modifies business logic but includes no changes to \`docs/\`."
  echo ""
  echo "**Changed files that triggered this check:**"
  for src in "${unique_sources[@]}"; do
    echo "- \`$src\`"
  done
  echo ""
  echo "**Suggested docs to review and update:**"
  for doc in "${unique_docs[@]}"; do
    echo "- \`$doc\`"
  done
  echo ""
  echo "**To resolve, choose one:**"
  echo "1. Update the relevant doc(s) and include them in this PR"
  echo "2. Add \`[no-doc]\` to the PR title if no doc update is needed (e.g. internal refactor)"
  echo ""
  echo "_Checked by doc-drift-auditor · mapping rules: \`.github/doc-drift-mappings.yml\`_"
} > /tmp/drift-pr-comment.md

exit 1
