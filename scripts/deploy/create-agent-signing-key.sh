#!/usr/bin/env bash
#
# Create the agent signing key pair once and write it over the deploy placeholders
# (agentic interaction 0.3).
#
# The CDK SecretsStack creates the secret batbern/<env>/agent/signing-private-key and the SSM
# String parameter /batbern/<env>/agent/jwt-public-key, both holding the placeholder `none`, so
# the first deploy starts with agent tokens off. This script generates an RSA-2048 pair,
# writes the private key (PKCS#8 PEM) into the secret and the public key (SPKI PEM) into the
# parameter, and prints the commands that restart the four services so they pick the key up.
#
# It refuses to overwrite a key that is already set unless --force is given.
#
# The private key is never printed and never passed on a command line: it is written to a
# umask-077 temp directory, handed to the AWS CLI as file://, and the directory is removed on
# exit.
#
# Usage:
#   scripts/deploy/create-agent-signing-key.sh                 # write the key (env staging)
#   scripts/deploy/create-agent-signing-key.sh --force         # overwrite an existing key
#   scripts/deploy/create-agent-signing-key.sh --dry-run [--out DIR]
#                                                              # generate only, no AWS call;
#                                                              # with --out keep the pair in DIR
#   scripts/deploy/create-agent-signing-key.sh --env <name>    # another CDK envName
#
# Requires: openssl, and (unless --dry-run) the AWS CLI v2 with credentials for the production
# account. The profile is forced to batbern-staging (the account that serves www.batbern.ch)
# rather than an ambient AWS_PROFILE; override only via AGENT_KEY_AWS_PROFILE.
# Runbook: docs/operations/runbooks.md -> "Agent signing key".

set -euo pipefail

PLACEHOLDER="none"
PROFILE="${AGENT_KEY_AWS_PROFILE:-batbern-staging}"
REGION="eu-central-1"
ENV_NAME="staging"
DRY_RUN=0
FORCE=0
OUT_DIR=""

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
    --out)
      [[ $# -ge 2 ]] || die "--out needs a directory"
      OUT_DIR="$2"
      shift 2
      ;;
    --force)
      FORCE=1
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
if [[ -n "$OUT_DIR" && "$DRY_RUN" -ne 1 ]]; then
  die "--out is only allowed with --dry-run (a real run keeps no copy of the private key)"
fi

command -v openssl >/dev/null 2>&1 || die "openssl not found; install it first"
if [[ "$DRY_RUN" -ne 1 ]]; then
  command -v aws >/dev/null 2>&1 || die "aws CLI not found; install AWS CLI v2 first"
fi

SECRET_ID="batbern/${ENV_NAME}/agent/signing-private-key"
PARAMETER_NAME="/batbern/${ENV_NAME}/agent/jwt-public-key"
CLUSTER="batbern-${ENV_NAME}"
LOG_PREFIX="/aws/ecs/BATbern-${ENV_NAME}"

awsp() {
  aws --profile "$PROFILE" --region "$REGION" "$@"
}

is_placeholder() {
  local value
  value="$(printf '%s' "$1" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
  [[ "$value" == "$PLACEHOLDER" ]]
}

# Key material goes into a private directory: the --out directory for a dry run, else a temp
# directory that is removed on exit.
umask 077
if [[ -n "$OUT_DIR" ]]; then
  mkdir -p "$OUT_DIR"
  if [[ -e "$OUT_DIR/agent-signing-private.pem" || -e "$OUT_DIR/agent-signing-public.pem" ]]; then
    die "$OUT_DIR already holds a key; choose an empty directory"
  fi
  KEY_DIR="$OUT_DIR"
else
  KEY_DIR="$(mktemp -d)"
  trap 'rm -rf "$KEY_DIR"' EXIT
fi
PRIVATE_PEM="$KEY_DIR/agent-signing-private.pem"
PUBLIC_PEM="$KEY_DIR/agent-signing-public.pem"

generate_pair() {
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_PEM" 2>/dev/null \
    || die "openssl could not generate the key"
  chmod 600 "$PRIVATE_PEM"
  openssl pkey -in "$PRIVATE_PEM" -pubout -out "$PUBLIC_PEM" 2>/dev/null \
    || die "openssl could not derive the public key"
}

print_restart_steps() {
  cat <<EOF

Next: restart the four services so they read the key (ECS reads secrets at task start).

  for s in \$(aws --profile $PROFILE --region $REGION ecs list-services --cluster $CLUSTER \\
      --query "serviceArns[?contains(@, 'CompanyManagement') || contains(@, 'EventManagement') || contains(@, 'PartnerCoordination') || contains(@, 'ApiGatewayService')]" \\
      --output text); do
    aws --profile $PROFILE --region $REGION ecs update-service --cluster $CLUSTER --service "\$s" \\
      --force-new-deployment --query service.serviceName --output text
  done

Then check each service's start log for the decoder line:

  aws --profile $PROFILE --region $REGION logs tail $LOG_PREFIX/<service> --since 30m \\
    --filter-pattern '"JWT decoder accepts token kinds"'

  company-user-management, event-management, api-gateway: [COGNITO, WATCH, AGENT]
  partner-coordination:                                     [COGNITO, AGENT]
EOF
}

if [[ "$DRY_RUN" -eq 1 ]]; then
  generate_pair
  echo "▶ Dry run: no AWS call is made."
  if [[ -n "$OUT_DIR" ]]; then
    echo "  Key pair written to $OUT_DIR (private key mode 600)."
  fi
  echo "  Public key (SPKI PEM):"
  cat "$PUBLIC_PEM"
  cat <<EOF
  A real run would, with profile $PROFILE in $REGION:
    1. check that secret $SECRET_ID and parameter $PARAMETER_NAME hold '$PLACEHOLDER'
    2. aws secretsmanager put-secret-value --secret-id $SECRET_ID --secret-string file://<private key>
    3. aws ssm put-parameter --name $PARAMETER_NAME --type String --overwrite --value file://<public key>
    4. read the parameter back and compare it with the public key
EOF
  print_restart_steps
  exit 0
fi

echo "▶ Checking the placeholders (profile $PROFILE, $REGION)…"
current_secret="$(awsp secretsmanager get-secret-value --secret-id "$SECRET_ID" \
  --query SecretString --output text 2>/dev/null)" \
  || die "cannot read secret $SECRET_ID. Deploy the SecretsStack first (it creates the placeholders), and check the credentials of profile $PROFILE."
current_parameter="$(awsp ssm get-parameter --name "$PARAMETER_NAME" \
  --query Parameter.Value --output text 2>/dev/null)" \
  || die "cannot read parameter $PARAMETER_NAME. Deploy the SecretsStack first (it creates the placeholders)."

already_set=()
is_placeholder "$current_secret" || already_set+=("secret $SECRET_ID")
is_placeholder "$current_parameter" || already_set+=("parameter $PARAMETER_NAME")
unset current_secret
if [[ ${#already_set[@]} -gt 0 ]]; then
  if [[ "$FORCE" -ne 1 ]]; then
    die "already set, not '$PLACEHOLDER': ${already_set[*]}. Nothing written. Use --force to replace the key (every agent token signed with the old key stops verifying)."
  fi
  echo "⚠ --force: replacing an existing value in ${already_set[*]}." >&2
fi

echo "▶ Generating an RSA-2048 key pair…"
generate_pair

echo "▶ Writing the private key to secret $SECRET_ID…"
awsp secretsmanager put-secret-value --secret-id "$SECRET_ID" \
  --secret-string "file://$PRIVATE_PEM" --query VersionId --output text >/dev/null \
  || die "writing the secret failed; nothing else was written"

echo "▶ Writing the public key to parameter $PARAMETER_NAME…"
awsp ssm put-parameter --name "$PARAMETER_NAME" --type String --overwrite \
  --value "file://$PUBLIC_PEM" --query Version --output text >/dev/null \
  || die "writing the parameter failed after the secret was written. Re-run with --force to write a fresh pair."

written="$(awsp ssm get-parameter --name "$PARAMETER_NAME" --query Parameter.Value --output text)" \
  || die "could not read the parameter back"
if [[ "$written" != "$(cat "$PUBLIC_PEM")" ]]; then
  die "parameter $PARAMETER_NAME does not hold the public key just written. Re-run with --force."
fi

echo "✓ Agent signing key written. Public key (SPKI PEM):"
cat "$PUBLIC_PEM"
print_restart_steps
