#!/usr/bin/env bash
# Pre-deploy gate (P5) spike: one full, timed run on this host.
#   up (clean) -> health of all four services -> seed -> every Bruno collection -> mail check
# Images must exist (scripts/gate/build-images.sh). Containers get no AWS credentials.
#
# Usage: scripts/gate/run-gate.sh [--keep]      (--keep leaves the stack running afterwards)
# Exit:  0 only if the stack got healthy, the seed worked, every Bruno collection passed, the fake
#        AWS received at least one SendRawEmail (proof that mail reaches the fake), and no service
#        log holds an AWS credentials error (proof that no AWS client went past the overrides).
#        The stack is torn down on every exit unless --keep.
# Output: ~/.cache/batbern-gate/run-<timestamp>/ (bruno results, failures.tsv, mails.json, logs)
set -uo pipefail
cd "$(dirname "$0")/../.."
source scripts/gate/lib.sh

keep=0; [ "${1:-}" = "--keep" ] && keep=1
run_dir="$HOME/.cache/batbern-gate/run-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$run_dir"
base="http://localhost:${GATE_GATEWAY_PORT:-18000}/api/v1"
fake="http://localhost:18025"
t() { date +%s; }
log() { echo "[gate $(date +%T)] $*"; }

# Test-user tokens: the existing CI identities only (refreshed on the host, never in a container).
for role in organizer speaker partner; do
    ./scripts/auth/refresh-token.sh staging "$role" >/dev/null 2>&1 || true
done
ORGANIZER_AUTH_TOKEN=$(jq -r .idToken "$HOME/.batbern/staging-organizer.json")
SPEAKER_AUTH_TOKEN=$(jq -r .idToken "$HOME/.batbern/staging-speaker.json")
PARTNER_AUTH_TOKEN=$(jq -r .idToken "$HOME/.batbern/staging-partner.json")
export ORGANIZER_AUTH_TOKEN SPEAKER_AUTH_TOKEN PARTNER_AUTH_TOKEN
gate_export_env

cleanup() {
    if [ "$keep" = 0 ]; then
        log "compose down -v"
        gate_compose down -v --remove-orphans >/dev/null 2>&1
    fi
    log "results in $run_dir"
}
trap cleanup EXIT

t0=$(t)
log "compose down -v (clean start)"
gate_compose down -v --remove-orphans >/dev/null 2>&1
log "compose up"
if ! gate_compose up -d --wait > "$run_dir/compose-up.log" 2>&1; then
    log "stack did not become healthy, see $run_dir/compose-up.log"
    gate_compose ps -a > "$run_dir/ps.txt"; gate_compose logs --no-color > "$run_dir/stack.log" 2>&1
    exit 1
fi
t_up=$(t)
log "healthy after $((t_up - t0))s"
gate_compose ps --format '{{.Service}}\t{{.Status}}' | tee "$run_dir/ps.txt"

# Clear the fake BEFORE the seed: the seed's registration mail is sent asynchronously, so a clear
# after it could race with that send. The mail list then holds the seed's mail and Bruno's.
curl -s -X DELETE "$fake/_gate/messages" >/dev/null
log "seed"
scripts/gate/seed.sh "$base" | tee "$run_dir/seed.log" || { log "seed failed"; exit 1; }
t_seed=$(t)

log "bruno"
scripts/gate/run-bruno.sh "$run_dir/bruno" | tee "$run_dir/bruno-summary.txt"
bruno_rc=${PIPESTATUS[0]}
t_bruno=$(t)
scripts/gate/bruno-failures.sh "$run_dir/bruno" > "$run_dir/failures.tsv"

log "mail check"
curl -s "$fake/_gate/messages" > "$run_dir/mails.json"
curl -s "$fake/_gate/requests" > "$run_dir/fake-aws-requests.json"
jq -r '.[] | "\(.action)\t\(.to | join(","))\t\(.subject)"' "$run_dir/mails.json" | tee "$run_dir/mails.tsv"
t_end=$(t)

gate_compose logs --no-color > "$run_dir/stack.log" 2>&1
grep -E "SdkClientException|Unable to load credentials|AwsCredentialsProviderChain" "$run_dir/stack.log" > "$run_dir/aws-credential-failures.log" || true

{
    echo "start->healthy   $((t_up - t0))s"
    echo "seed             $((t_seed - t_up))s"
    echo "bruno            $((t_bruno - t_seed))s"
    echo "mail check       $((t_end - t_bruno))s"
    echo "total            $((t_end - t0))s"
    echo "mails caught     $(jq length "$run_dir/mails.json")"
    echo "failed checks    $(wc -l < "$run_dir/failures.tsv")"
    echo "credential errors $(wc -l < "$run_dir/aws-credential-failures.log")"
} | tee "$run_dir/timings.txt"

rc=0
[ "$bruno_rc" = 0 ] || { log "FAIL: Bruno had failures (see $run_dir/failures.tsv)"; rc=1; }
raw_mails=$(jq '[.[] | select(.action == "SendRawEmail")] | length' "$run_dir/mails.json" 2>/dev/null || echo 0)
[ "$raw_mails" -ge 1 ] || { log "FAIL: the fake AWS received no SendRawEmail"; rc=1; }
[ ! -s "$run_dir/aws-credential-failures.log" ] \
    || { log "FAIL: AWS credentials errors in the service logs"; rc=1; }
exit "$rc"
