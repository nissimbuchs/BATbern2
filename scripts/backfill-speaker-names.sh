#!/bin/bash
# Backfill speaker names for archive search
# This script fetches user data from the User Management API and updates session_users table

set -e

# Database connection
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="batbern_development"
DB_USER="postgres"
DB_PASS="devpass123"

# API endpoint (company-user-management-service)
USER_API="http://localhost:8001/api/v1/users"

echo "======================================"
echo "Speaker Name Backfill Script"
echo "======================================"

# Get list of usernames that need backfilling
USERNAMES=$(psql "postgresql://$DB_USER:$DB_PASS@$DB_HOST:$DB_PORT/$DB_NAME" -t -c \
  "SELECT DISTINCT username FROM session_users WHERE speaker_first_name IS NULL ORDER BY username;")

TOTAL=$(echo "$USERNAMES" | wc -l | tr -d ' ')
COUNT=0
SUCCESS=0
FAILED=0

echo "Found $TOTAL usernames to backfill"
echo ""

# Process each username
for username in $USERNAMES; do
  # Trim whitespace
  username=$(echo "$username" | tr -d ' ')

  if [ -z "$username" ]; then
    continue
  fi

  COUNT=$((COUNT + 1))
  echo "[$COUNT/$TOTAL] Processing: $username"

  # Fetch user data from API
  RESPONSE=$(curl -s "$USER_API/$username" 2>/dev/null || echo "ERROR")

  if [ "$RESPONSE" = "ERROR" ] || [ -z "$RESPONSE" ]; then
    echo "  ❌ Failed to fetch user data"
    FAILED=$((FAILED + 1))
    continue
  fi

  # Extract first and last names using jq
  FIRST_NAME=$(echo "$RESPONSE" | jq -r '.firstName // empty' 2>/dev/null)
  LAST_NAME=$(echo "$RESPONSE" | jq -r '.lastName // empty' 2>/dev/null)

  if [ -z "$FIRST_NAME" ] || [ -z "$LAST_NAME" ]; then
    echo "  ⚠️  Missing name data (firstName: '$FIRST_NAME', lastName: '$LAST_NAME')"
    FAILED=$((FAILED + 1))
    continue
  fi

  # Update database
  psql "postgresql://$DB_USER:$DB_PASS@$DB_HOST:$DB_PORT/$DB_NAME" -c \
    "UPDATE session_users SET speaker_first_name = '$FIRST_NAME', speaker_last_name = '$LAST_NAME' WHERE username = '$username';" \
    > /dev/null 2>&1

  if [ $? -eq 0 ]; then
    echo "  ✅ Updated: $FIRST_NAME $LAST_NAME"
    SUCCESS=$((SUCCESS + 1))
  else
    echo "  ❌ Database update failed"
    FAILED=$((FAILED + 1))
  fi
done

echo ""
echo "======================================"
echo "Backfill Complete!"
echo "======================================"
echo "Total processed: $COUNT"
echo "Successful: $SUCCESS"
echo "Failed: $FAILED"
echo "======================================"

# Verify results
echo ""
echo "Current status:"
psql "postgresql://$DB_USER:$DB_PASS@$DB_HOST:$DB_PORT/$DB_NAME" -c \
  "SELECT COUNT(*) as total_speakers, COUNT(speaker_first_name) as with_names, COUNT(*) - COUNT(speaker_first_name) as missing_names FROM session_users;"
