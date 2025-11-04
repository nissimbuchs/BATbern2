#!/bin/bash
# Start SSM port forward tunnel to RDS database
# This creates a secure tunnel allowing local Docker containers to access AWS RDS
#
# Usage:
#   ./scripts/dev/start-db-tunnel.sh              # Default port 5432
#   DB_TUNNEL_PORT=6432 ./scripts/dev/start-db-tunnel.sh  # Custom port 6432

set -e

export AWS_PROFILE=batbern-dev
export AWS_REGION=eu-central-1

# Instance-specific port (default 5432 for instance 1, 6432 for instance 2)
DB_TUNNEL_PORT="${DB_TUNNEL_PORT:-5432}"

echo "🔍 Fetching bastion and database information..."

# Get bastion instance ID from CloudFormation
BASTION_ID=$(aws cloudformation describe-stacks \
  --stack-name BATbern-development-Bastion \
  --query 'Stacks[0].Outputs[?OutputKey==`BastionInstanceId`].OutputValue' \
  --output text \
  --region $AWS_REGION 2>/dev/null)

if [ -z "$BASTION_ID" ] || [ "$BASTION_ID" == "None" ]; then
  echo "❌ Error: Bastion stack not found or not deployed"
  echo ""
  echo "Deploy it first:"
  echo "  cd infrastructure"
  echo "  AWS_PROFILE=batbern-dev npx cdk deploy BATbern-development-Bastion"
  exit 1
fi

# Get RDS endpoint
RDS_ENDPOINT=$(aws rds describe-db-instances \
  --query 'DBInstances[?DBInstanceIdentifier==`batbern-development-postgres`].Endpoint.Address' \
  --output text \
  --region $AWS_REGION 2>/dev/null)

if [ -z "$RDS_ENDPOINT" ]; then
  echo "❌ Error: RDS instance not found"
  echo ""
  echo "Deploy database stack first:"
  echo "  cd infrastructure"
  echo "  AWS_PROFILE=batbern-dev npx cdk deploy BATbern-development-Database"
  exit 1
fi

# Check if bastion instance is running
INSTANCE_STATE=$(aws ec2 describe-instances \
  --instance-ids $BASTION_ID \
  --query 'Reservations[0].Instances[0].State.Name' \
  --output text \
  --region $AWS_REGION 2>/dev/null)

if [ "$INSTANCE_STATE" != "running" ]; then
  echo "⚠️  Bastion instance is $INSTANCE_STATE"
  echo "   Starting instance..."

  aws ec2 start-instances \
    --instance-ids $BASTION_ID \
    --region $AWS_REGION >/dev/null

  echo "   Waiting for instance to start..."
  aws ec2 wait instance-running \
    --instance-ids $BASTION_ID \
    --region $AWS_REGION

  echo "   Waiting for SSM agent to be ready (30s)..."
  sleep 30
fi

echo ""
echo "✅ Configuration:"
echo "   Bastion Instance: $BASTION_ID"
echo "   RDS Endpoint: $RDS_ENDPOINT"
echo "   Local Port: $DB_TUNNEL_PORT"
echo "   Profile: $AWS_PROFILE"
echo "   Region: $AWS_REGION"
echo ""
echo "🔒 Starting secure tunnel to database..."
echo ""
echo "📝 IMPORTANT:"
echo "   1. Keep this terminal open while working"
echo "   2. In .env, set: DB_HOST=host.docker.internal (macOS/Windows)"
echo "   3. In .env, set: DB_HOST=172.17.0.1 (Linux)"
echo "   4. In .env.native, set: DB_HOST=localhost"
echo "   5. Press Ctrl+C to close tunnel when done"
echo ""
echo "🚀 Tunnel active! Database accessible at:"
echo "   localhost:$DB_TUNNEL_PORT"
echo ""

# Start the tunnel with instance-specific local port
aws ssm start-session \
  --target $BASTION_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"$RDS_ENDPOINT\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"$DB_TUNNEL_PORT\"]}" \
  --region $AWS_REGION
