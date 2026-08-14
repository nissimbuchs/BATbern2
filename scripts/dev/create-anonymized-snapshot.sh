#!/usr/bin/env bash
# =============================================================================
# create-anonymized-snapshot.sh
#
# Produces an ANONYMIZED seed dump of the BATbern production database, suitable
# for loading into a local/dev database or copying to the NAS dev host.
#
# Pipeline:
#   prod (via SSM tunnel)  --pg_dump-->  local scratch DB
#   scratch DB             --anonymize-snapshot.sql-->  scrubbed
#   scratch DB             --pg_dump-->  batbern-anonymized-<date>.sql.gz
#   scratch DB             --dropped
#
# The raw production dump is NEVER written to disk -- it is streamed straight
# into the scratch database. Only the anonymized artifact is persisted.
# See CLAUDE.md:906-907 (anonymize before dev use; no prod dumps on laptops).
#
# Prerequisites:
#   * SSM tunnel to the prod RDS open on 127.0.0.1:$PROD_PORT
#       scripts/dev/start-db-tunnel.sh   (or the staging equivalent)
#   * local postgres running (docker compose -f docker-compose-dev.yml up -d)
#   * pg_dump >= 15 (brew install postgresql@15)
#   * AWS_PROFILE with read access to the DB secret
#
# Usage:
#   scripts/dev/create-anonymized-snapshot.sh
#   PROD_PORT=5433 OUT_DIR=~/snapshots scripts/dev/create-anonymized-snapshot.sh
# =============================================================================
set -euo pipefail

PROD_PORT="${PROD_PORT:-5433}"
PROD_DB="${PROD_DB:-batbern}"
SECRET_ID="${SECRET_ID:-RdsClusterInstanceSecret777-bt1SnbTuV6LN}"
AWS_PROFILE="${AWS_PROFILE:-batbern-staging}"
AWS_REGION="${AWS_REGION:-eu-central-1}"

LOCAL_HOST="${LOCAL_HOST:-127.0.0.1}"
LOCAL_PORT="${LOCAL_PORT:-5432}"
LOCAL_USER="${LOCAL_USER:-postgres}"
LOCAL_PASS="${LOCAL_PASS:-devpass123}"
SCRATCH_DB="${SCRATCH_DB:-batbern_scratch_anon}"

OUT_DIR="${OUT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.snapshots}"
STAMP="$(date +%Y%m%d)"
OUT_FILE="$OUT_DIR/batbern-anonymized-$STAMP.sql.gz"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANON_SQL="$SCRIPT_DIR/anonymize-snapshot.sql"

PG_DUMP="${PG_DUMP:-/usr/local/opt/postgresql@15/bin/pg_dump}"
PSQL="${PSQL:-/usr/local/opt/postgresql@15/bin/psql}"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# --- preflight --------------------------------------------------------------
[ -x "$PG_DUMP" ] || die "pg_dump >= 15 not found at $PG_DUMP (brew install postgresql@15)"
[ -f "$ANON_SQL" ] || die "missing $ANON_SQL"

PGDUMP_MAJOR="$("$PG_DUMP" --version | awk '{print $3}' | cut -d. -f1)"
[ "$PGDUMP_MAJOR" -ge 15 ] || die "pg_dump is $PGDUMP_MAJOR, need >= 15"

nc -z -G 3 127.0.0.1 "$PROD_PORT" 2>/dev/null \
  || die "no tunnel on 127.0.0.1:$PROD_PORT — run scripts/dev/start-db-tunnel.sh first"

log "Fetching production DB credentials from Secrets Manager"
CREDS="$(AWS_PROFILE="$AWS_PROFILE" AWS_REGION="$AWS_REGION" \
  aws secretsmanager get-secret-value --secret-id "$SECRET_ID" \
  --query SecretString --output text)" || die "could not read secret $SECRET_ID"
PROD_USER="$(echo "$CREDS" | jq -r .username)"
PROD_PASS="$(echo "$CREDS" | jq -r .password)"
[ -n "$PROD_USER" ] && [ -n "$PROD_PASS" ] || die "secret did not contain username/password"

mkdir -p "$OUT_DIR"

# The scratch DB holds RAW PRODUCTION DATA between steps 2 and 3. If anything
# fails in between, drop it rather than leaving real PII on the machine.
cleanup_scratch() {
  local rc=$?
  if [ $rc -ne 0 ]; then
    printf '\033[1;33m==>\033[0m Failure (rc=%s) — dropping scratch DB so no raw production data is left behind\n' "$rc" >&2
    PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" \
      -d postgres -q -c "DROP DATABASE IF EXISTS $SCRATCH_DB WITH (FORCE)" >/dev/null 2>&1 || true
  fi
}
trap cleanup_scratch EXIT

# --- 1. recreate scratch DB -------------------------------------------------
log "Recreating scratch database $SCRATCH_DB"
export PGPASSWORD="$LOCAL_PASS"
"$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" -d postgres -q \
  -c "DROP DATABASE IF EXISTS $SCRATCH_DB WITH (FORCE)" \
  -c "CREATE DATABASE $SCRATCH_DB"

# --- 2. stream prod -> scratch (no raw dump on disk) ------------------------
log "Streaming production database into scratch (no raw dump written to disk)"
set +e
PGPASSWORD="$PROD_PASS" "$PG_DUMP" \
    -h 127.0.0.1 -p "$PROD_PORT" -U "$PROD_USER" -d "$PROD_DB" \
    --no-owner --no-privileges --no-acl \
    --exclude-table='flyway_schema_history_backup_*' \
  | PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" \
    -U "$LOCAL_USER" -d "$SCRATCH_DB" -q -v ON_ERROR_STOP=0 > /dev/null
RC=("${PIPESTATUS[@]}")
set -e
[ "${RC[0]}" -eq 0 ] || die "pg_dump from production failed (rc=${RC[0]})"

ROWS="$(PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" \
        -d "$SCRATCH_DB" -tAc "select coalesce(sum(n_live_tup),0) from pg_stat_user_tables")"
log "Scratch loaded: $ROWS rows"
[ "$ROWS" -gt 0 ] || die "scratch database is empty — aborting before anonymization"

# --- 3. anonymize -----------------------------------------------------------
log "Anonymizing"
PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" \
  -d "$SCRATCH_DB" -v ON_ERROR_STOP=1 -f "$ANON_SQL"

# --- 4. hard verification ---------------------------------------------------
log "Verifying no real email addresses survive"
# Independent of the in-SQL check (belt and braces). The preserve-list is
# exempt in EVERY table -- those addresses legitimately appear in registrations
# and newsletter_recipients as well as user_profiles.
KEEP="'nissim@buchs.be','nissim.buchs@me.com','nissim.buchs@gmail.com','nissim.buchs@elca.ch'"
# Same definition of "an email address" as the in-SQL check. Without the regex
# this flagged bare usernames -- some email columns in production genuinely
# store usernames rather than addresses -- and the two checks disagreed.
RX='[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
LEAK_SQL="
    select src, e from (
      select 'user_profiles.email'                as src, email          as e from user_profiles
      union all select 'registrations.attendee_email',        attendee_email from registrations
      union all select 'newsletter_subscribers.email',        email          from newsletter_subscribers
      union all select 'newsletter_recipients.email',         email          from newsletter_recipients
      union all select 'user_additional_emails.email',        email          from user_additional_emails
      union all select 'partner_meeting_rsvps.attendee_email',attendee_email from partner_meeting_rsvps
      union all select 'speaker_reminder_log.email_address',  email_address  from speaker_reminder_log
    ) s
    where e is not null and e <> ''
      and e ~ '$RX'
      and e not like '%@example.invalid'
      and lower(e) not in ($KEEP)"

LEAKS="$(PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" \
  -d "$SCRATCH_DB" -tAc "select count(*) from ($LEAK_SQL) q")"
if [ "$LEAKS" -ne 0 ]; then
  echo "--- offending values (column | address) ---" >&2
  PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" \
    -d "$SCRATCH_DB" -tAc "select distinct src, e from ($LEAK_SQL) q limit 20" >&2
  die "$LEAKS real email addresses survived anonymization — snapshot NOT written"
fi
log "Verification passed: 0 real addresses remain"

# --- 5. export --------------------------------------------------------------
log "Writing $OUT_FILE"
PGPASSWORD="$LOCAL_PASS" "$PG_DUMP" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" \
  -d "$SCRATCH_DB" --no-owner --no-privileges | gzip -9 > "$OUT_FILE"

# --- 6. drop scratch --------------------------------------------------------
log "Dropping scratch database"
PGPASSWORD="$LOCAL_PASS" "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" -d postgres -q \
  -c "DROP DATABASE IF EXISTS $SCRATCH_DB WITH (FORCE)"

echo
log "Done: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"
echo
echo "Load it into local dev with:"
echo "  scripts/dev/load-anonymized-snapshot.sh $OUT_FILE"
