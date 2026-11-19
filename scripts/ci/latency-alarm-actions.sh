#!/usr/bin/env bash
# Mute and unmute batbern-<env>-alb-latency-p95 around a service rollout (#1144).
#
# The alarm flapped through every rolling deploy: each replaced task takes 10-15 minutes of
# multi-second responses to warm up, and four services roll one after another. Its actions are
# disabled for the rollout; state changes still happen, only the mails stop.
#
#   latency-alarm-actions.sh disable <env>
#   latency-alarm-actions.sh enable  <env>
#
# enable first resets an alarm that went to ALARM while muted back to OK. Alarm actions fire
# on state CHANGES only, so without the reset a breach that began during the rollout would
# never mail; after it, the next evaluation alarms again if the breach persists. The reset
# runs while the actions are still disabled, so it sends no OK mail of its own.
#
# Never fails: a denied or failed call prints a GitHub ::warning:: and exits 0, because a mute
# toggle must not turn a deploy or a rollback red. IAM: cloudwatch:DisableAlarmActions,
# EnableAlarmActions, SetAlarmState on that alarm only, DescribeAlarms (cicd-stack.ts).

set -o pipefail

ACTION="${1:-}"
ENVIRONMENT="${2:-}"
if [ -z "$ACTION" ] || [ -z "$ENVIRONMENT" ]; then
    echo "usage: $0 disable|enable <environment>" >&2
    exit 2
fi

ALARM="batbern-${ENVIRONMENT}-alb-latency-p95"
REGION_ARGS=()
if [ -n "${AWS_REGION:-}" ]; then
    REGION_ARGS=(--region "$AWS_REGION")
fi

case "$ACTION" in
    disable)
        if aws cloudwatch disable-alarm-actions --alarm-names "$ALARM" "${REGION_ARGS[@]}"; then
            echo "🔕 $ALARM actions disabled for the service rollout"
        else
            echo "::warning::Could not disable $ALARM actions (is the CICD stack grant deployed?). The alarm may mail during this rollout."
        fi
        ;;
    enable)
        STATE=$(aws cloudwatch describe-alarms --alarm-names "$ALARM" \
            --query 'MetricAlarms[0].StateValue' --output text "${REGION_ARGS[@]}") || STATE="UNKNOWN"
        if [ "$STATE" = "ALARM" ]; then
            if aws cloudwatch set-alarm-state --alarm-name "$ALARM" --state-value OK \
                --state-reason "reset after deploy (#1144)" "${REGION_ARGS[@]}"; then
                echo "↩️  $ALARM was in ALARM while muted; reset to OK so a persisting breach alarms again"
            else
                echo "::warning::$ALARM is in ALARM and could not be reset to OK; it will not mail until it recovers and breaches again."
            fi
        elif [ "$STATE" = "UNKNOWN" ]; then
            echo "::warning::Could not read the state of $ALARM; skipping the reset."
        fi
        if aws cloudwatch enable-alarm-actions --alarm-names "$ALARM" "${REGION_ARGS[@]}"; then
            echo "🔔 $ALARM actions enabled"
        else
            echo "::warning::Could not re-enable $ALARM actions; the alarm is MUTE. Run: aws cloudwatch enable-alarm-actions --alarm-names $ALARM"
        fi
        ;;
    *)
        echo "usage: $0 disable|enable <environment>" >&2
        exit 2
        ;;
esac
exit 0
