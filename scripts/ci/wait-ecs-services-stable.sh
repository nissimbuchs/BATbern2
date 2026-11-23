#!/usr/bin/env bash
# Wait until every ECS service of the platform is stable after a rollout.
#
#   wait-ecs-services-stable.sh <env>
#
# Services are looked up by their real names, BATbern-<env>-<Fragment>-Service<hash> (the
# fragment is the PascalCase CDK construct id), the same way update-ecs-task.sh and
# rollback-deployment.sh find them. The inline step this replaces asked ECS for
# `event-management-service` etc., which ECS reports as MISSING, so it waited for nothing,
# and it never included the API gateway.
#
# A name that resolves to nothing is an error (exit 1): the list below is wrong and the wait
# would silently cover less than it claims. A service that does not settle in ~30 minutes is a
# warning, as before: the deploy steps already report their own failures.

set -o pipefail

ENVIRONMENT="${1:-}"
if [ -z "$ENVIRONMENT" ]; then
    echo "usage: $0 <environment>" >&2
    exit 2
fi
CLUSTER="batbern-${ENVIRONMENT}"
REGION_ARGS=()
if [ -n "${AWS_REGION:-}" ]; then
    REGION_ARGS=(--region "$AWS_REGION")
fi

FRAGMENTS=(CompanyManagement EventManagement PartnerCoordination AgentService ApiGatewayService)
MISSING=""

for FRAGMENT in "${FRAGMENTS[@]}"; do
    PATTERN="BATbern-${ENVIRONMENT}-${FRAGMENT}-Service"
    ARN=$(aws ecs list-services --cluster "$CLUSTER" "${REGION_ARGS[@]}" \
        --query "serviceArns[?contains(@, '${PATTERN}')] | [0]" --output text) || ARN=""
    if [ -z "$ARN" ] || [ "$ARN" = "None" ]; then
        echo "::error::No ECS service in $CLUSTER matches ${PATTERN}*"
        MISSING="$MISSING $FRAGMENT"
        continue
    fi
    SERVICE=$(basename "$ARN")
    echo "Waiting for $SERVICE to stabilize..."
    # The CLI waiter gives up after 10 minutes (40 x 15 s); three rounds allow ~30.
    STABLE=false
    for _ in 1 2 3; do
        if aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE" "${REGION_ARGS[@]}"; then
            STABLE=true
            break
        fi
    done
    if [ "$STABLE" = true ]; then
        echo "✅ $SERVICE stable"
    else
        echo "::warning::$SERVICE not stable after ~30 minutes"
    fi
done

if [ -n "$MISSING" ]; then
    echo "::error::Could not resolve:$MISSING"
    exit 1
fi
exit 0
