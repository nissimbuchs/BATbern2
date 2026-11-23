#!/usr/bin/env bash
#
# Install the Anthropic API key of agent-service's model adapter (agent runtime, story 3, AD-14).
#
# The CDK SecretsStack creates the secret batbern/<env>/agent/anthropic-api-key holding the
# placeholder `none`: agent-service starts with the adapter off and hands every run to the
# organizers. This script writes the real key over the placeholder and restarts the agent service
# (ECS reads secrets at task start), then prints the log check: the start log must name the
# Anthropic adapter and the model, never the key.
#
# The key is read from stdin: typed at a hidden prompt on a terminal, or piped in (first line).
# It is never echoed, never passed on a command line: it is written to a umask-077 temp directory,
# handed to the AWS CLI as file://, and the directory is removed on exit.
#
# It refuses to overwrite a key that is already set unless --force is given.
#
# Usage:
#   scripts/deploy/put-anthropic-api-key.sh                  # prompt for the key, write, restart
#   pass show anthropic | scripts/deploy/put-anthropic-api-key.sh
#                                                            # key from a pipe
#   scripts/deploy/put-anthropic-api-key.sh --force          # replace a key that is already set
#   scripts/deploy/put-anthropic-api-key.sh --no-restart     # write only; restart yourself
#   scripts/deploy/put-anthropic-api-key.sh --dry-run        # read and check the key, no AWS call
#   scripts/deploy/put-anthropic-api-key.sh --env <name>     # another CDK envName
#
# To switch the adapter off again, write the placeholder back: echo none | ... --force
#
# Requires: (unless --dry-run) the AWS CLI v2 with credentials for the production account. The
# profile is forced to batbern-staging (the account that serves www.batbern.ch) rather than an
# ambient AWS_PROFILE; override only via ANTHROPIC_KEY_AWS_PROFILE.
# Runbook: docs/operations/runbooks.md -> "Anthropic API key".

set -euo pipefail

PLACEHOLDER="none"
PROFILE="${ANTHROPIC_KEY_AWS_PROFILE:-batbern-staging}"
REGION="eu-central-1"
ENV_NAME="staging"
DRY_RUN=0
FORCE=0
RESTART=1

usage() {
  awk '/^# Usage:/{on=1} /^# Requires:/{on=0} on' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

die() {
  echo "✗ $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      [[ $# -ge 2 ]] || die "--env needs a value"
      ENV_NAME="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --force)
      FORCE=1
      shift
      ;;
    --no-restart)
      RESTART=0
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "unknown argument: $1"
      ;;
  esac
done

[[ "$ENV_NAME" =~ ^[a-z][a-z0-9-]*$ ]] || die "--env must be a CDK envName such as staging"
if [[ "$DRY_RUN" -ne 1 ]]; then
  command -v aws >/dev/null 2>&1 || die "aws CLI not found; install AWS CLI v2 first"
fi

SECRET_ID="batbern/${ENV_NAME}/agent/anthropic-api-key"
CLUSTER="batbern-${ENV_NAME}"
LOG_GROUP="/aws/ecs/BATbern-${ENV_NAME}/agent"

awsp() {
  aws --profile "$PROFILE" --region "$REGION" "$@"
}

normalized() {
  printf '%s' "$1" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]'
}

is_placeholder() {
  local value
  value="$(normalized "$1")"
  [[ -z "$value" || "$value" == "$PLACEHOLDER" ]]
}

# --- Read the key, never echoed -------------------------------------------------------------
KEY=""
if [[ -t 0 ]]; then
  printf 'Anthropic API key (input hidden): ' >&2
  IFS= read -rs KEY || true
  printf '\n' >&2
else
  IFS= read -r KEY || true
fi
# Trim surrounding whitespace (a pasted newline or space), keep the key itself untouched
KEY="${KEY#"${KEY%%[![:space:]]*}"}"
KEY="${KEY%"${KEY##*[![:space:]]}"}"

[[ -n "$KEY" ]] || die "no key read from stdin; nothing written"
if [[ "$KEY" =~ [[:space:]] ]]; then
  KEY=""
  die "the key contains whitespace; nothing written"
fi
WRITING_PLACEHOLDER=0
if is_placeholder "$KEY"; then
  WRITING_PLACEHOLDER=1
  KEY="$PLACEHOLDER"
  [[ "$FORCE" -eq 1 ]] || die "'$PLACEHOLDER' switches the adapter off; pass --force to write it"
elif [[ "$KEY" != sk-ant-* ]]; then
  echo "⚠ The key does not start with sk-ant-; writing it anyway." >&2
fi

umask 077
KEY_DIR="$(mktemp -d)"
trap 'rm -rf "$KEY_DIR"' EXIT
KEY_FILE="$KEY_DIR/anthropic-api-key"
# printf is a shell builtin: the key never appears in a process list
printf '%s' "$KEY" >"$KEY_FILE"
unset KEY

print_log_check() {
  cat <<EOF

Check the start log of the new task (it takes a few minutes to start):

  aws --profile $PROFILE --region $REGION logs tail $LOG_GROUP --since 30m \\
    --filter-pattern '"Model adapter"'

  Expected: Model adapter anthropic, model claude-sonnet-5-5, effort medium
  ("Model adapter none" means the task still reads the placeholder.)
EOF
}

restart_command() {
  cat <<EOF
  aws --profile $PROFILE --region $REGION ecs update-service --cluster $CLUSTER \\
    --service <agent service> --force-new-deployment
EOF
}

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "▶ Dry run: no AWS call is made. The key was read and checked; it is not shown."
  cat <<EOF
  A real run would, with profile $PROFILE in $REGION:
    1. check that secret $SECRET_ID holds '$PLACEHOLDER' (or pass with --force)
    2. aws secretsmanager put-secret-value --secret-id $SECRET_ID --secret-string file://<key file>
EOF
  if [[ "$RESTART" -eq 1 ]]; then
    echo "    3. force a new deployment of the agent service in cluster $CLUSTER:"
    restart_command
  fi
  print_log_check
  exit 0
fi

echo "▶ Checking the secret (profile $PROFILE, $REGION)…"
current="$(awsp secretsmanager get-secret-value --secret-id "$SECRET_ID" \
  --query SecretString --output text 2>/dev/null)" \
  || die "cannot read secret $SECRET_ID. Deploy the SecretsStack first (it creates the placeholder), and check the credentials of profile $PROFILE."
if ! is_placeholder "$current"; then
  unset current
  [[ "$FORCE" -eq 1 ]] || die "secret $SECRET_ID already holds a key, not '$PLACEHOLDER'. Nothing written. Use --force to replace it."
  echo "⚠ --force: replacing the key in $SECRET_ID." >&2
fi
unset current

echo "▶ Writing secret $SECRET_ID…"
awsp secretsmanager put-secret-value --secret-id "$SECRET_ID" \
  --secret-string "file://$KEY_FILE" --query VersionId --output text >/dev/null \
  || die "writing the secret failed; nothing was written"
if [[ "$WRITING_PLACEHOLDER" -eq 1 ]]; then
  echo "✓ Placeholder '$PLACEHOLDER' written: the adapter is off after the restart."
else
  echo "✓ Key written."
fi

if [[ "$RESTART" -ne 1 ]]; then
  echo "▶ --no-restart: the running task keeps the old value until it restarts:"
  restart_command
  print_log_check
  exit 0
fi

echo "▶ Restarting the agent service (cluster $CLUSTER)…"
services="$(awsp ecs list-services --cluster "$CLUSTER" \
  --query "serviceArns[?contains(@, 'AgentService')]" --output text)" \
  || die "listing the services of $CLUSTER failed. The key is written; restart the agent service yourself."
read -r -a service_arns <<<"$services"
if [[ ${#service_arns[@]} -ne 1 ]]; then
  die "expected one agent service in $CLUSTER, found ${#service_arns[@]}. The key is written; restart it yourself:
$(restart_command)"
fi
awsp ecs update-service --cluster "$CLUSTER" --service "${service_arns[0]}" \
  --force-new-deployment --query service.serviceName --output text \
  || die "the restart failed. The key is written; restart the agent service yourself."
echo "✓ New deployment started."
print_log_check
