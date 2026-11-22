#!/usr/bin/env bash
# Tests for ensure-services-run-image.sh with a fake `aws` on PATH.
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ensure-services-run-image.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
pass=0; fail=0
ok() { echo "  PASS $1"; pass=$((pass + 1)); }
ko() { echo "  FAIL $1"; fail=$((fail + 1)); }

mkdir -p "$WORK/bin"
# State: which task definition each service runs (FAKE_RUN_<Fragment>), and the images per revision.
cat > "$WORK/bin/aws" <<'EOF'
#!/usr/bin/env bash
args="$*"
log="${FAKE_LOG:?}"
echo "$args" >> "$log"
image() { # task def arn → image
  case "$1" in
    *:493) echo "repo/ems:old-tag" ;;
    *:492) echo "repo/ems:new-tag" ;;
    *:491) echo "repo/ems:older-tag" ;;
    *:900) echo "repo/other:${FAKE_OTHER_TAG:-new-tag}" ;;
    *) echo "repo/x:unknown" ;;
  esac
}
case "$args" in
  "ecs list-services"*)
    for f in EventManagement PartnerCoordination CompanyManagement AgentService ApiGatewayService; do
      [[ "$args" == *"'${f}-Service'"* ]] && { echo "arn:aws:ecs:svc/BATbern-staging-${f}-ServiceABC"; exit 0; }
    done ;;
  "ecs describe-services"*)
    [[ "$args" == *EventManagement* ]] && { echo "arn:task-definition/EmsTaskDef:${FAKE_EMS_RUNS:-492}"; exit 0; }
    echo "arn:task-definition/OtherTaskDef:900" ;;
  "ecs describe-task-definition"*)
    td=$(echo "$args" | sed -E 's/.*--task-definition ([^ ]+).*/\1/'); image "$td" ;;
  "ecs list-task-definitions"*)
    [[ "${FAKE_NO_MATCH:-}" == 1 ]] && { echo "arn:task-definition/EmsTaskDef:493	arn:task-definition/EmsTaskDef:491"; exit 0; }
    echo "arn:task-definition/EmsTaskDef:493	arn:task-definition/EmsTaskDef:492	arn:task-definition/EmsTaskDef:491" ;;
  "ecs update-service"*) echo "BATbern-staging-EventManagement-ServiceABC" ;;
  "ecs wait services-stable"*) exit "${FAKE_WAIT_RC:-0}" ;;
  *) echo "unexpected: $args" >&2; exit 9 ;;
esac
EOF
chmod +x "$WORK/bin/aws"
export PATH="$WORK/bin:$PATH"

run() { FAKE_LOG="$WORK/aws-$1.log" bash "$SCRIPT" staging new-tag > "$WORK/out-$1.log" 2>&1; }

echo "ensure-services-run-image"
: > "$WORK/aws-ok.log"
if run ok; then ok "all services on the tag: exits 0"; else ko "all services on the tag: exits 0 ($(cat "$WORK/out-ok.log"))"; fi
grep -q "update-service" "$WORK/aws-ok.log" && ko "nothing moved when all run the tag" || ok "nothing moved when all run the tag"

: > "$WORK/aws-drift.log"
if FAKE_EMS_RUNS=493 run drift; then ok "rolled-back service: exits 0 after the move"; else ko "rolled-back service: exits 0 ($(cat "$WORK/out-drift.log"))"; fi
grep -q "update-service .*EventManagement.* --task-definition arn:task-definition/EmsTaskDef:492" "$WORK/aws-drift.log" \
  && ok "moves EMS to the newest revision with the tag (:492, not :493)" || ko "moves EMS to :492 ($(grep update-service "$WORK/aws-drift.log"))"
[[ $(grep -c "update-service" "$WORK/aws-drift.log") -eq 1 ]] && ok "only the drifted service is moved" || ko "only the drifted service is moved"
grep -q "wait services-stable" "$WORK/aws-drift.log" && ok "waits for the moved service" || ko "waits for the moved service"

: > "$WORK/aws-nomatch.log"
FAKE_EMS_RUNS=493 FAKE_NO_MATCH=1 run nomatch && ko "no revision with the tag: fails" || ok "no revision with the tag: fails"

: > "$WORK/aws-unstable.log"
FAKE_EMS_RUNS=493 FAKE_WAIT_RC=255 run unstable && ko "moved service not stable: fails" || ok "moved service not stable: fails"

echo "$pass passed, $fail failed"
[[ "$fail" -eq 0 ]]
