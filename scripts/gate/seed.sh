#!/usr/bin/env bash
# Seeds the gate through its own public API (real code paths, no SQL against the schema).
# No personal data: the only people are the three CI test users, created by JIT provisioning from
# their own tokens; the company and the event are synthetic.
#
#   1. The three test users' profiles, from their token claims (the only SQL step): JIT
#      provisioning would derive the username from the e-mail (success+batbern.speaker@... becomes
#      "user.success"), while production and the Bruno collections know them by custom:username.
#      Then GET /users/me with each token, through the real JIT/sync path.
#   2. Company "gatepartner" + a partnership, and the partner test user joins it (partner flows
#      resolve the caller's company from the profile).
#   3. Event 59: events/mails-*.bru address BATbern59 by name, a production event with a mail
#      history; the gate has an empty database. The speaker test user (an SES simulator address) is
#      added with notify=true, so the event has one sent mail (caught by the fake SES).
#
# Usage: scripts/gate/seed.sh <gateway base url, e.g. http://localhost:18000/api/v1>
# Needs: ORGANIZER_AUTH_TOKEN, SPEAKER_AUTH_TOKEN, PARTNER_AUTH_TOKEN exported.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/gate/lib.sh
base="${1:?gateway base url}"

call() { # method path token [json] -> prints "<status>"; fails on 5xx
    local method="$1" path="$2" token="$3" body="${4:-}" status
    status=$(curl -s -o /dev/null -w '%{http_code}' -X "$method" "$base$path" \
        -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        ${body:+--data "$body"})
    echo "  $method $path -> $status"
    [ "$status" -lt 500 ] || { echo "seed failed: $method $path answered $status" >&2; return 1; }
}

echo "seed: test users (profiles from token claims, then JIT sync)"
for role in organizer speaker partner; do
    gate_token_claims "$role" | jq -r '
        def q: "\u0027" + (gsub("\u0027"; "\u0027\u0027")) + "\u0027";
        (.["custom:username"]) as $u | (.["custom:role"] | split(",")) as $roles
        | "INSERT INTO user_profiles (username, cognito_user_id, email, first_name, last_name) VALUES ("
          + ($u | q) + ", " + (.sub | q) + ", " + (.email | q) + ", \u0027Gate\u0027, "
          + (($u | split(".") | last) | q) + ") ON CONFLICT DO NOTHING;",
          ($roles[] | "INSERT INTO role_assignments (user_id, role) SELECT id, " + q
            + " FROM user_profiles WHERE username = " + ($u | q) + " ON CONFLICT DO NOTHING;")'
done | gate_compose exec -T postgres psql -q -v ON_ERROR_STOP=1 -U batbern -d batbern
echo "  user_profiles: $(gate_compose exec -T postgres psql -tA -U batbern -d batbern \
    -c "select string_agg(username, ',' order by username) from user_profiles")"
call GET /users/me "$ORGANIZER_AUTH_TOKEN"
call GET /users/me "$SPEAKER_AUTH_TOKEN"
call GET /users/me "$PARTNER_AUTH_TOKEN"

echo "seed: partner company"
call POST /companies "$ORGANIZER_AUTH_TOKEN" \
    '{"name":"gatepartner","displayName":"Gate Partner Co","website":"https://gate-partner.example.com","industry":"Technology","description":"Synthetic partner company of the pre-deploy gate"}'
call POST /partners "$ORGANIZER_AUTH_TOKEN" \
    '{"companyName":"gatepartner","partnershipLevel":"GOLD","partnershipStartDate":"2026-01-01","partnershipEndDate":"2099-12-31"}'
partner_username=$(curl -s "$base/users/me" -H "Authorization: Bearer $PARTNER_AUTH_TOKEN" | jq -r '.id')
call PATCH "/users/$partner_username" "$ORGANIZER_AUTH_TOKEN" '{"companyId":"gatepartner"}'

echo "seed: event 59"
call POST /events "$ORGANIZER_AUTH_TOKEN" \
    '{"title":"Gate Event 59","eventNumber":59,"date":"2099-06-15T16:00:00Z","registrationDeadline":"2099-06-01T23:59:59Z","venueName":"Gate Venue","venueAddress":"Gate Street 1, 3000 Bern","venueCapacity":100,"organizerUsername":"batbern.organizer","description":"Synthetic event of the pre-deploy gate","eventType":"EVENING"}'
speaker_username=$(curl -s "$base/users/me" -H "Authorization: Bearer $SPEAKER_AUTH_TOKEN" | jq -r '.id')
call POST /events/BATbern59/participants "$ORGANIZER_AUTH_TOKEN" "{\"username\":\"$speaker_username\",\"notify\":true}"
