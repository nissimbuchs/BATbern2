#!/usr/bin/env bash
# Shared helpers for the pre-deploy gate scripts. Source, do not run.

# Decoded claims (JSON) of the ID token in ~/.batbern/staging-<role>.json.
gate_token_claims() {
    jq -r '.idToken | split(".")[1] | gsub("-"; "+") | gsub("_"; "/")
           | . + ("=" * ((4 - (length % 4)) % 4)) | @base64d | fromjson' \
        "$HOME/.batbern/staging-$1.json"
}

# Exports what the compose file needs, derived without any AWS call.
gate_export_env() {
    local claims
    claims=$(gate_token_claims organizer)
    GATE_COGNITO_USER_POOL_ID=$(jq -r '.iss | split("/") | last' <<<"$claims")
    GATE_COGNITO_CLIENT_ID=$(jq -r '.aud' <<<"$claims")
    GATE_JWT_SECRET=${GATE_JWT_SECRET:-$(head -c 48 /dev/urandom | base64 | tr -d '\n/+=')}
    export GATE_COGNITO_USER_POOL_ID GATE_COGNITO_CLIENT_ID GATE_JWT_SECRET
}

# docker compose for the gate, with every AWS credential source removed from the environment.
gate_compose() {
    env -u AWS_PROFILE -u AWS_DEFAULT_PROFILE -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY \
        -u AWS_SESSION_TOKEN -u AWS_SHARED_CREDENTIALS_FILE -u AWS_CONFIG_FILE \
        docker compose -f docker-compose.gate.yml "$@"
}
