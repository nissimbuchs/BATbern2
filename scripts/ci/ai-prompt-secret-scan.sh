#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Story 15.9 (OWASP-LLM, LLM06) — static secret/PII-in-prompt scan.
#
# Fails (exit 1) if any AI prompt-construction code interpolates a known-sensitive
# source (API key, env var, password, secret, jwt, token, credentials) into a
# prompt / chat-message string. The OpenAI API key belongs ONLY in the
# Authorization header — never in a prompt, message body, or generation log.
#
# This is a coarse, dependency-free guard (grep, no network) for the advisory
# `ai-security` CI job. It scans the prompt-construction sources only; it is a
# tripwire, not a proof.
#
# Usage:
#   scripts/ci/ai-prompt-secret-scan.sh            # scan the tree
#   scripts/ci/ai-prompt-secret-scan.sh --self-test # prove it catches a planted leak
# ─────────────────────────────────────────────────────────────────────────────
set -o pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Files that build prompts / chat-message bodies sent to the LLM.
# Add the 15.8 LinkedIn-draft generator here when it lands.
SCAN_FILES=(
  "services/event-management-service/src/main/java/ch/batbern/events/service/BatbernAiService.java"
  "services/event-management-service/src/main/java/ch/batbern/events/service/TrendingTopicsService.java"
  "services/event-management-service/src/main/java/ch/batbern/events/service/AiPromptService.java"
)

# Sensitive sources that must never be interpolated into a prompt string.
SENSITIVE_PATTERN='getApiKey|System\.getenv|apiKey\(\)|password|passwd|secret|credential|getPassword|jwt|bearer'

# Lines that indicate prompt / message construction context.
PROMPT_CONTEXT_PATTERN='prompt|message|"content"|buildMessages|buildChatBody|applyVariables'

# A line is a comment iff, after the "<lineno>:" prefix, its first non-space char is // or *.
# Excluding comment lines means doc prose ("never log the secret") can neither trip nor mask the scan.
COMMENT_LINE_PATTERN='^[0-9]+:[[:space:]]*(//|\*|/\*)'

MISSING=0

# A finding = a NON-COMMENT line that BOTH constructs a prompt/message AND references a sensitive
# source. The Authorization header lives in init() (not a prompt line), so "Bearer + apiKey" in the
# header builder is intentionally NOT matched by PROMPT_CONTEXT_PATTERN.
scan_file() {
  local file="$1"
  local path="$REPO_ROOT/$file"
  if [ ! -f "$path" ]; then
    echo "  ⚠️  WARNING: scan target missing (coverage gap — was it renamed?): $file" >&2
    MISSING=$((MISSING + 1))
    return 0
  fi

  grep -nE "$PROMPT_CONTEXT_PATTERN" "$path" \
    | grep -vE "$COMMENT_LINE_PATTERN" \
    | grep -iE "$SENSITIVE_PATTERN"
}

run_scan() {
  local hits=0
  echo "🔍 AI prompt secret/PII scan (LLM06)"
  for f in "${SCAN_FILES[@]}"; do
    echo "→ $f"
    local result
    result="$(scan_file "$f")"
    if [ -n "$result" ]; then
      echo "❌ POTENTIAL SECRET INTERPOLATED INTO PROMPT:"
      echo "$result" | sed 's/^/     /'
      hits=$((hits + 1))
    fi
  done
  if [ "$MISSING" -gt 0 ]; then
    echo ""
    echo "⚠️  $MISSING scan target(s) were missing — coverage may have eroded (update SCAN_FILES)."
  fi
  if [ "$hits" -gt 0 ]; then
    echo ""
    echo "❌ ai-prompt-secret-scan FAILED: $hits file(s) interpolate a sensitive source into a prompt."
    echo "   Secrets must stay in headers/config — never in prompt or message content."
    return 1
  fi
  echo "✅ ai-prompt-secret-scan clean — no secret/PII interpolation in prompt construction."
  return 0
}

self_test() {
  # Plant a leak in a temp file and prove the matcher catches it. Trap guarantees cleanup.
  local tmp
  tmp="$(mktemp)"
  trap 'rm -f "$tmp"' RETURN
  cat > "$tmp" <<'EOF'
String prompt = "Summarize using key " + aiConfig.getApiKey();
Map.of("content", "password=" + dbPassword);
// a comment line mentioning password must NOT be flagged
EOF
  local result
  result="$(grep -nE "$PROMPT_CONTEXT_PATTERN" "$tmp" | grep -vE "$COMMENT_LINE_PATTERN" | grep -iE "$SENSITIVE_PATTERN")"
  if [ -n "$result" ] && ! grep -q "comment line" <<<"$result"; then
    echo "✅ self-test passed — planted leaks detected, comment line correctly ignored:"
    echo "$result" | sed 's/^/     /'
    return 0
  fi
  echo "❌ self-test FAILED — detection broken (planted leak missed or comment line flagged)."
  echo "$result" | sed 's/^/     /'
  return 1
}

case "${1:-}" in
  --self-test) self_test ;;
  *)           run_scan ;;
esac
