#!/usr/bin/env bash
# doc-drift-weekly-audit.sh
# Scans commits from the past N days and identifies feat/fix commits that
# touched monitored source paths without a corresponding docs/ change.
# Writes results to /tmp/doc-drift-gaps.txt and exports GAPS_COUNT.

set -euo pipefail

MAPPINGS_FILE="${MAPPINGS_FILE:-.github/doc-drift-mappings.yml}"
SINCE="${SINCE:-7 days ago}"

echo "🔍 Scanning commits since: $SINCE"

# Load skip types
SKIP_TYPES=$(python3 -c "
import yaml
data = yaml.safe_load(open('$MAPPINGS_FILE'))
print('|'.join(data.get('skip_commit_types', [])))
")

# Load all source patterns
all_patterns=$(python3 -c "
import yaml
data = yaml.safe_load(open('$MAPPINGS_FILE'))
for m in data['mappings']:
    print(m['source_pattern'])
")

gaps=()

while IFS= read -r sha; do
  [[ -z "$sha" ]] && continue

  msg=$(git log -1 --format="%s" "$sha")
  commit_type=$(echo "$msg" | grep -oP '^[a-z]+(?=[\(:!])' || echo "unknown")

  # Skip [no-doc] commits
  if echo "$msg" | grep -q "\[no-doc\]"; then
    continue
  fi

  # Skip non-auditable types
  if [[ -n "$SKIP_TYPES" ]] && echo "$commit_type" | grep -qE "^($SKIP_TYPES)$"; then
    continue
  fi

  # Get files changed in this commit
  changed_files=$(git diff-tree --no-commit-id -r --name-only "$sha" 2>/dev/null || true)
  [[ -z "$changed_files" ]] && continue

  # Skip if docs/ was also changed in this commit
  doc_changed=$(echo "$changed_files" | grep -E "^docs/" || true)
  [[ -n "$doc_changed" ]] && continue

  # Check against mappings
  matched_docs=()
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
          while IFS= read -r doc; do
            matched_docs+=("$doc")
          done <<< "$docs"
        fi
      fi
    done <<< "$all_patterns"
  done <<< "$changed_files"

  unique_docs=($(printf '%s\n' "${matched_docs[@]:-}" | sort -u | grep -v '^$' || true))

  if [[ ${#unique_docs[@]} -gt 0 ]]; then
    short_sha="${sha:0:8}"
    docs_csv=$(printf '%s,' "${unique_docs[@]}" | sed 's/,$//')
    gaps+=("$short_sha|$msg|$docs_csv")
    echo "  ⚠️  $short_sha — $msg"
  fi

done < <(git log --since="$SINCE" --format="%H")

GAPS_COUNT=${#gaps[@]}
echo "GAPS_COUNT=$GAPS_COUNT"

# Write gaps file
printf '%s\n' "${gaps[@]:-}" > /tmp/doc-drift-gaps.txt
echo "$GAPS_COUNT" >> /tmp/doc-drift-gaps.txt  # last line = count sentinel

echo "✅ Scan complete. $GAPS_COUNT gap(s) found."
