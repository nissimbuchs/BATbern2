#!/usr/bin/env bash
#
# Publish the current web-frontend build to the beta canary (https://beta.batbern.ch).
#
# Phase 4 of docs/plans/beta-frontend-canary.md — the lightweight, MANUAL-ONLY publish path:
# build the SPA, sync it to the beta S3 bucket, and invalidate the beta CloudFront
# distribution. Faster than a full `cdk deploy BATbern-staging-FrontendBeta` when only the
# frontend bundle changed (no infra change).
#
# ⚠️  Beta shares the PRODUCTION backend, Cognito pool, and database. It is a UI CANARY,
#     not a sandbox — use it only to preview frontend-only changes. Anything you do on beta
#     acts on live production data. Do not advertise the URL (it is public + noindex).
#
# Usage:
#   scripts/deploy/publish-beta-frontend.sh            # build + publish
#   SKIP_BUILD=1 scripts/deploy/publish-beta-frontend.sh   # publish the existing dist/ as-is
#
# Requires: AWS creds for the prod account (profile batbern-staging by default), node/npm.

set -euo pipefail

# Beta lives in the prod/staging account, so force the batbern-staging profile rather than
# inheriting an ambient AWS_PROFILE (a dev shell commonly exports AWS_PROFILE=batbern-dev,
# which points at the wrong account and makes the beta stack look "not found"). Override only
# via the dedicated BETA_AWS_PROFILE.
PROFILE="${BETA_AWS_PROFILE:-batbern-staging}"
# The beta CloudFormation stack lives in eu-central-1 (the CDK app region). Pin it so the
# describe-stacks lookup doesn't depend on ambient region resolution.
REGION="${AWS_REGION:-eu-central-1}"
STACK="BATbern-staging-FrontendBeta"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "▶ Resolving beta stack outputs ($STACK, $REGION)…"
BUCKET=$(AWS_PROFILE="$PROFILE" aws cloudformation describe-stacks --stack-name "$STACK" --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='WebsiteBucketName'].OutputValue" --output text)
DIST_ID=$(AWS_PROFILE="$PROFILE" aws cloudformation describe-stacks --stack-name "$STACK" --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='DistributionId'].OutputValue" --output text)

if [[ -z "$BUCKET" || -z "$DIST_ID" || "$BUCKET" == "None" || "$DIST_ID" == "None" ]]; then
  echo "✗ Could not resolve the beta bucket/distribution from $STACK." >&2
  echo "  Is the beta stack deployed? (cdk deploy $STACK --context betaFrontend=true)" >&2
  exit 1
fi
echo "  bucket=$BUCKET  distribution=$DIST_ID"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo "▶ Building web-frontend (with SSG prerender)…"
  # build:prerender = vite build + the Playwright SSG crawl (scripts/prerender.mjs).
  # Degrades gracefully to a CSR-only build if Chromium isn't installed locally.
  ( cd "$REPO_ROOT/web-frontend" && npm run build:prerender )
else
  echo "▶ SKIP_BUILD=1 — publishing existing dist/ as-is"
fi

DIST_DIR="$REPO_ROOT/web-frontend/dist"
if [[ ! -f "$DIST_DIR/index.html" ]]; then
  echo "✗ $DIST_DIR/index.html not found — build the frontend first." >&2
  exit 1
fi

echo "▶ Syncing dist/ → s3://$BUCKET (prune extras)…"
AWS_PROFILE="$PROFILE" aws s3 sync "$DIST_DIR" "s3://$BUCKET" --delete

echo "▶ Invalidating CloudFront $DIST_ID (/*)…"
INVALIDATION_ID=$(AWS_PROFILE="$PROFILE" aws cloudfront create-invalidation \
  --distribution-id "$DIST_ID" --paths '/*' --query "Invalidation.Id" --output text)

echo "✓ Published to https://beta.batbern.ch (invalidation $INVALIDATION_ID)"
echo "  Reminder: beta uses the PRODUCTION API / Cognito / data — canary only."
