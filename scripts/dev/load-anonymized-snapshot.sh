#!/usr/bin/env bash
# =============================================================================
# load-anonymized-snapshot.sh
#
# Loads an anonymized snapshot (produced by create-anonymized-snapshot.sh) into
# a local development database, replacing its current contents.
#
# Works on the Mac and on the NAS dev host -- the only requirement is a reachable
# PostgreSQL and psql >= 15.
#
# Usage:
#   scripts/dev/load-anonymized-snapshot.sh .snapshots/batbern-anonymized-20260808.sql.gz
#   TARGET_DB=batbern_development scripts/dev/load-anonymized-snapshot.sh <file>
# =============================================================================
set -euo pipefail

SNAPSHOT="${1:-}"
[ -n "$SNAPSHOT" ] || { echo "usage: $0 <snapshot.sql.gz>" >&2; exit 1; }
[ -f "$SNAPSHOT" ] || { echo "ERROR: no such file: $SNAPSHOT" >&2; exit 1; }

LOCAL_HOST="${LOCAL_HOST:-127.0.0.1}"
LOCAL_PORT="${LOCAL_PORT:-5432}"
LOCAL_USER="${LOCAL_USER:-postgres}"
LOCAL_PASS="${LOCAL_PASS:-devpass123}"
TARGET_DB="${TARGET_DB:-batbern_development}"

PSQL="${PSQL:-$(command -v psql)}"
[ -n "$PSQL" ] || { echo "ERROR: psql not found" >&2; exit 1; }

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

export PGPASSWORD="$LOCAL_PASS"

# Safety: never let this point at something that isn't a dev database.
case "$TARGET_DB" in
  *development*|*dev*|*local*|*test*) ;;
  *) echo "ERROR: refusing to load into '$TARGET_DB' — name must look like a dev database" >&2; exit 1 ;;
esac

log "Recreating $TARGET_DB on $LOCAL_HOST:$LOCAL_PORT"
"$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" -d postgres -q \
  -c "DROP DATABASE IF EXISTS $TARGET_DB WITH (FORCE)" \
  -c "CREATE DATABASE $TARGET_DB"

log "Loading $SNAPSHOT"
gunzip -c "$SNAPSHOT" \
  | "$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" -d "$TARGET_DB" \
      -q -v ON_ERROR_STOP=0 > /dev/null
[ "${PIPESTATUS[0]}" -eq 0 ] || { echo "ERROR: gunzip failed" >&2; exit 1; }

log "Row counts after load:"
"$PSQL" -h "$LOCAL_HOST" -p "$LOCAL_PORT" -U "$LOCAL_USER" -d "$TARGET_DB" -c "
  SELECT relname AS table, n_live_tup AS rows
  FROM pg_stat_user_tables
  WHERE n_live_tup > 0
  ORDER BY n_live_tup DESC
  LIMIT 12"

log "Done. Restart services so they pick up the new data:"
echo "  make dev-native-down && make dev-native-up"
