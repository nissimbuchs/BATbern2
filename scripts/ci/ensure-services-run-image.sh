#!/usr/bin/env bash
# ensure-services-run-image.sh <env> <image-tag>
# After the service stacks are deployed, every ECS service must run <image-tag>. A service that
# does not is moved to the newest task definition of its family that carries that tag.
#
# Why: rollback-deployment.sh moves the services to a copy of their task definition with the
# staging-stable image, behind CloudFormation's back. A rerun of the same deploy (same commit,
# same image tag) then synthesizes an unchanged template, CloudFormation updates nothing, and the
# services stay on the rolled-back image while Bruno and Playwright test the old code. Seen on
# #1331, 2026-11-21: EMS, CUMS and PCS stayed on the previous image after a green rerun.
#
# Uses the same AWS actions as rollback-deployment.sh (ListServices, DescribeServices,
# DescribeTaskDefinition, ListTaskDefinitions, UpdateService, wait services-stable).
# Exits 1 when a service runs another tag and no task definition with <image-tag> exists, or a
# moved service does not become stable.

set -euo pipefail

ENV="${1:?usage: ensure-services-run-image.sh <env> <image-tag>}"
TAG="${2:?usage: ensure-services-run-image.sh <env> <image-tag>}"
CLUSTER="batbern-${ENV}"
FRAGMENTS=(EventManagement PartnerCoordination CompanyManagement ApiGatewayService)

image_tag_of() {
  aws ecs describe-task-definition --task-definition "$1" \
    --query 'taskDefinition.containerDefinitions[0].image' --output text | sed 's#.*:##'
}

failed=0
moved=()
for fragment in "${FRAGMENTS[@]}"; do
  svc=$(aws ecs list-services --cluster "$CLUSTER" \
    --query "serviceArns[?contains(@, '${fragment}-Service')] | [0]" --output text)
  if [[ -z "$svc" || "$svc" == "None" ]]; then
    echo "::error::No ECS service for $fragment in $CLUSTER"
    failed=1
    continue
  fi
  current=$(aws ecs describe-services --cluster "$CLUSTER" --services "$svc" \
    --query 'services[0].taskDefinition' --output text)
  if [[ "$(image_tag_of "$current")" == "$TAG" ]]; then
    echo "$fragment runs $TAG"
    continue
  fi
  family=$(echo "${current##*/}" | sed 's/:[0-9]*$//')
  target=""
  for td in $(aws ecs list-task-definitions --family-prefix "$family" --sort DESC \
      --max-items 10 --query 'taskDefinitionArns[]' --output text); do
    [[ "$td" == "None" ]] && continue
    if [[ "$(image_tag_of "$td")" == "$TAG" ]]; then target="$td"; break; fi
  done
  if [[ -z "$target" ]]; then
    echo "::error::$fragment runs $(image_tag_of "$current"), and no task definition of $family carries $TAG"
    failed=1
    continue
  fi
  echo "::warning::$fragment ran $(image_tag_of "$current") (${current##*/}); moving it to ${target##*/} ($TAG)"
  aws ecs update-service --cluster "$CLUSTER" --service "$svc" --task-definition "$target" \
    --query 'service.serviceName' --output text
  moved+=("$svc")
done

if [[ ${#moved[@]} -gt 0 ]]; then
  if ! aws ecs wait services-stable --cluster "$CLUSTER" --services "${moved[@]}"; then
    echo "::error::A moved service did not become stable"
    failed=1
  fi
fi

exit "$failed"
