#!/bin/bash
# Promote one ECR image tag to another across every BATbern service repo.
#
# Used by .github/workflows/deploy-staging.yml to maintain two mutable tags on
# top of the immutable per-build SHA tag (`<sha7>-staging.<run>`):
#
#   - staging-current  : what was just deployed (set after CDK + ECS settle)
#   - staging-stable   : last Bruno-green deploy (set after bruno-tests passes)
#
# `scripts/ci/rollback-deployment.sh` resolves `staging-stable` → image digest
# to find the last known good image when a Bruno failure trips auto-rollback.
#
# Usage:
#   ./scripts/ci/promote-ecr-tag.sh <env> <source-tag> <target-tag>
#
# Examples:
#   # After successful deploy:
#   ./scripts/ci/promote-ecr-tag.sh staging abc1234-staging.55 staging-current
#
#   # After successful Bruno run:
#   ./scripts/ci/promote-ecr-tag.sh staging staging-current staging-stable
#
# Behavior:
#   - Iterates over all 6 BATbern service ECR repos (5 services + api-gateway).
#   - If the source tag is missing in a repo, prints a warning and continues
#     (so a per-service deploy that updates only one repo doesn't fail the
#     whole promotion).
#   - If the target tag already points to the same manifest in a repo, that's
#     a no-op success.
#   - Exits non-zero if any repo encountered an unexpected error; exit 0 if
#     every repo was either promoted or harmlessly skipped.
#
# Refs: docs/plans/bruno-staging-hardening.md §A4

set -u  # do NOT set -e — we want every repo to be attempted

ENV="${1:-}"
SOURCE_TAG="${2:-}"
TARGET_TAG="${3:-}"

if [ -z "$ENV" ] || [ -z "$SOURCE_TAG" ] || [ -z "$TARGET_TAG" ]; then
  echo "Usage: $0 <env> <source-tag> <target-tag>" >&2
  echo "Example: $0 staging abc1234-staging.55 staging-current" >&2
  exit 2
fi

# Active ECR repos. Mirrors the matrix in .github/workflows/build.yml and the
# validation list in deploy-staging.yml. Keep in sync if the matrix changes.
REPOS=(
  "event-management-service"
  "speaker-coordination-service"
  "partner-coordination-service"
  "attendee-experience-service"
  "company-user-management-service"
  "api-gateway"
)

OK=0
SKIPPED=0
FAILED=0

echo "===================================="
echo "Promote ECR tag"
echo "===================================="
echo "Environment: ${ENV}"
echo "Source tag:  ${SOURCE_TAG}"
echo "Target tag:  ${TARGET_TAG}"
echo ""

for SERVICE in "${REPOS[@]}"; do
  REPO="batbern/${ENV}/${SERVICE}"

  echo "→ ${REPO}: ${SOURCE_TAG} → ${TARGET_TAG}"

  # Fetch the manifest of the source tag. batch-get-image returns an empty
  # `images` array (not an error) when the tag doesn't exist — so check for
  # an empty/None manifest, not a non-zero exit code.
  MANIFEST=$(aws ecr batch-get-image \
    --repository-name "$REPO" \
    --image-ids imageTag="$SOURCE_TAG" \
    --query 'images[0].imageManifest' \
    --output text 2>/dev/null)

  if [ -z "$MANIFEST" ] || [ "$MANIFEST" = "None" ]; then
    echo "  ⚠️  source tag '${SOURCE_TAG}' not found in ${REPO} — skipping"
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  # put-image with an existing mutable tag moves it to the new manifest.
  # If the tag already points to *this* manifest, AWS returns
  # ImageAlreadyExistsException, which is harmless for our purpose.
  PUT_ERR=$(aws ecr put-image \
    --repository-name "$REPO" \
    --image-tag "$TARGET_TAG" \
    --image-manifest "$MANIFEST" \
    --output text 2>&1 >/dev/null)
  PUT_EXIT=$?

  if [ "$PUT_EXIT" -eq 0 ]; then
    echo "  ✅ ${REPO}:${TARGET_TAG} now points to ${SOURCE_TAG}"
    OK=$((OK + 1))
  elif echo "$PUT_ERR" | grep -q "ImageAlreadyExistsException"; then
    echo "  ✅ ${REPO}:${TARGET_TAG} already points to this manifest (no-op)"
    OK=$((OK + 1))
  else
    echo "  ❌ failed to tag ${REPO}:${TARGET_TAG}"
    echo "     $PUT_ERR"
    FAILED=$((FAILED + 1))
  fi
done

echo ""
echo "===================================="
echo "Summary"
echo "===================================="
echo "Promoted: ${OK}"
echo "Skipped (source missing): ${SKIPPED}"
echo "Failed:   ${FAILED}"

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi

exit 0
