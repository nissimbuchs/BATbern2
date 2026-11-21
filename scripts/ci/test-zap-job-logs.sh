#!/usr/bin/env bash
# Tests for zap-job-logs.sh (#1228) with a fake `gh` on PATH.
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/zap-job-logs.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
pass=0; fail=0
ok() { echo "  PASS $1"; pass=$((pass + 1)); }
ko() { echo "  FAIL $1"; fail=$((fail + 1)); }

mkdir -p "$WORK/bin"
cat > "$WORK/bin/gh" <<'EOF'
#!/usr/bin/env bash
# fake gh: "api --paginate .../jobs --jq ..." lists jobs; "api .../jobs/<id>/logs" prints a log.
args="$*"
if [[ "$args" == *"/runs/"*"/jobs"* ]]; then
  printf '11\tZAP API — events-core\n12\tZAP Frontend Baseline\n13\tSecurity Scan Summary\n'
  exit 0
fi
case "$args" in
  *"/jobs/11/logs"*)
    printf '2026-11-23T02:50:01.1234567Z Total of 120 URLs\n'
    printf '2026-11-23T02:50:02.1234567Z PASS: Vulnerable JS Library [10003]\n'
    printf '2026-11-23T02:50:02.2234567Z WARN-NEW: Full Path Disclosure [110009] x 1 \n'
    printf '2026-11-23T02:50:02.3234567Z \thttps://api.batbern.ch/api/v1/x (404 Not Found)\n' ;;
  *"/jobs/12/logs"*)
    [[ "${FAKE_FRONTEND:-ok}" == "empty" ]] && { printf '2026-11-23T02:50:02Z nothing\n'; exit 0; }
    [[ "${FAKE_FRONTEND:-ok}" == "error" ]] && exit 1
    printf '2026-11-23T02:51:02.1234567Z PASS: Cookie No HttpOnly Flag [10010]\n' ;;
  *) exit 1 ;;
esac
EOF
chmod +x "$WORK/bin/gh"
export PATH="$WORK/bin:$PATH" REPO=o/r RUN_ID=1 GH_TOKEN=x

echo "zap-job-logs"
if bash "$SCRIPT" "$WORK/out" > "$WORK/log" 2>&1; then ok "exits 0 when every scan log has rule lines"; else ko "exits 0 (log: $(cat "$WORK/log"))"; fi
[[ -f "$WORK/out/zap-events-core.log" ]] && ok "API job → zap-events-core.log" || ko "API job → zap-events-core.log"
[[ -f "$WORK/out/zap-frontend.log" ]] && ok "frontend job → zap-frontend.log" || ko "frontend job → zap-frontend.log"
[[ ! -e "$WORK/out/zap-Security Scan Summary.log" && $(ls "$WORK/out" | wc -l) -eq 2 ]] && ok "non-ZAP jobs skipped" || ko "non-ZAP jobs skipped"
grep -qx 'WARN-NEW: Full Path Disclosure \[110009\] x 1 ' "$WORK/out/zap-events-core.log" && ok "timestamp prefix stripped" || ko "timestamp prefix stripped"
FAKE_FRONTEND=empty bash "$SCRIPT" "$WORK/out2" > "$WORK/log2" 2>&1 && ko "a log without rule lines fails" || ok "a log without rule lines fails"
FAKE_FRONTEND=error bash "$SCRIPT" "$WORK/out3" > "$WORK/log3" 2>&1 && ko "an unfetchable log fails" || ok "an unfetchable log fails"

echo "$pass passed, $fail failed"
[[ "$fail" -eq 0 ]]
