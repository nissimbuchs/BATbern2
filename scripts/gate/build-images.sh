#!/usr/bin/env bash
# Pre-deploy gate spike (P5, step 1): build the five production images for this host's platform,
# one at a time, from the production Dockerfiles. Prints the wall time per image.
#
# Usage: scripts/gate/build-images.sh [service...]   (default: all five, sequentially)
#   services: api-gateway company-user-management event-management partner-coordination agent
# Env:    GATE_PLATFORM (default linux/amd64), GATE_TAG (default spike)
set -euo pipefail
cd "$(dirname "$0")/../.."

PLATFORM="${GATE_PLATFORM:-linux/amd64}"
TAG="${GATE_TAG:-spike}"

dockerfile_for() {
    case "$1" in
        api-gateway) echo "api-gateway/Dockerfile" ;;
        company-user-management|event-management|partner-coordination|agent)
            echo "services/$1-service/Dockerfile" ;;
        *) echo "unknown service: $1" >&2; return 1 ;;
    esac
}

services=("$@")
[ ${#services[@]} -gt 0 ] || services=(api-gateway company-user-management event-management partner-coordination agent)

for svc in "${services[@]}"; do
    file=$(dockerfile_for "$svc")
    start=$(date +%s)
    echo "=== build $svc ($file, $PLATFORM) started $(date -Is)"
    docker buildx build --platform "$PLATFORM" -f "$file" -t "batbern-gate/$svc:$TAG" --load .
    echo "=== build $svc took $(( $(date +%s) - start ))s"
done
