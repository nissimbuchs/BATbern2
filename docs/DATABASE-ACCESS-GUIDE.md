# Database Access Guide for Local Development

This guide explains how to access the AWS RDS database from your local Docker containers using AWS Systems Manager Session Manager.

## Architecture

The BATbern development environment uses **AWS RDS PostgreSQL** in the development account. Local Docker containers cannot directly access RDS because:

1. RDS is in a private subnet (10.0.2.0/24, 10.0.3.0/24)
2. Security groups only allow access from within the VPC
3. No public IP address is assigned to RDS

**Solution**: Use a bastion host with AWS Systems Manager (SSM) Session Manager to create a secure tunnel.

## Prerequisites

### 1. Install AWS Session Manager Plugin

**macOS:**
```bash
brew install --cask session-manager-plugin
```

**Linux:**
```bash
curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb" -o "session-manager-plugin.deb"
sudo dpkg -i session-manager-plugin.deb
```

**Windows:**
Download from: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html

### 2. AWS Credentials

Ensure you have AWS credentials configured for the development account:

```bash
aws configure --profile batbern-dev
# Or ensure AWS_PROFILE=batbern-dev is set
```

### 3. Bastion Host Deployed

The bastion stack should be deployed:

```bash
cd infrastructure
AWS_PROFILE=batbern-dev npx cdk deploy BATbern-development-Bastion
```

## Setup Steps

### Step 1: Get Bastion Instance ID

```bash
export AWS_PROFILE=batbern-dev
export BASTION_INSTANCE_ID=$(aws cloudformation describe-stacks \
  --stack-name BATbern-development-Bastion \
  --query 'Stacks[0].Outputs[?OutputKey==`BastionInstanceId`].OutputValue' \
  --output text \
  --region eu-central-1)

echo "Bastion Instance ID: $BASTION_INSTANCE_ID"
```

### Step 2: Get RDS Endpoint

```bash
export RDS_ENDPOINT=$(aws rds describe-db-instances \
  --region eu-central-1 \
  --query 'DBInstances[?DBInstanceIdentifier==`batbern-development-postgres`].Endpoint.Address' \
  --output text)

echo "RDS Endpoint: $RDS_ENDPOINT"
```

### Step 3: Create SSM Port Forward Tunnel

**Option A: Using Session Manager Plugin (Recommended)**

```bash
aws ssm start-session \
  --target $BASTION_INSTANCE_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"$RDS_ENDPOINT\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"5432\"]}" \
  --region eu-central-1
```

**Option B: Using One-Line Command**

```bash
aws ssm start-session \
  --target $BASTION_INSTANCE_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["batbern-development-postgres.c10iyqgumkwu.eu-central-1.rds.amazonaws.com"],"portNumber":["5432"],"localPortNumber":["5432"]}' \
  --region eu-central-1 \
  --profile batbern-dev
```

Keep this terminal open! The tunnel will remain active while this command runs.

### Step 4: Update .env for Local Docker

In a **new terminal**, update your `.env` file to use localhost:

```bash
# OLD (doesn't work from local Docker):
# DB_HOST=batbern-development-postgres.c10iyqgumkwu.eu-central-1.rds.amazonaws.com

# NEW (works through SSM tunnel):
DB_HOST=host.docker.internal  # macOS/Windows
# OR
DB_HOST=172.17.0.1  # Linux

DB_PORT=5432
DB_NAME=batbern
DB_USER=postgres
DB_PASSWORD=<from Secrets Manager>
DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/batbern?user=postgres&password=<password>
```

### Step 5: Restart Docker Services

```bash
docker compose down
docker compose up -d
```

## Verification

Test the connection from Docker:

```bash
# Check if services can connect
docker logs batbern-company-user-management --tail 50

# You should see successful database connection logs instead of UnknownHostException
```

Test from your host machine:

```bash
psql -h localhost -p 5432 -U postgres -d batbern
# Enter password from .env file
```

## Troubleshooting

### Problem: "Session Manager plugin is not found"

**Solution**: Install the Session Manager plugin (see Prerequisites)

```bash
# Verify installation
session-manager-plugin
```

### Problem: "TargetNotConnected" or instance not found

**Solution**: Check that bastion instance is running

```bash
aws ec2 describe-instances \
  --instance-ids $BASTION_INSTANCE_ID \
  --query 'Reservations[0].Instances[0].State.Name' \
  --region eu-central-1 \
  --profile batbern-dev
```

If stopped, start it:

```bash
aws ec2 start-instances \
  --instance-ids $BASTION_INSTANCE_ID \
  --region eu-central-1 \
  --profile batbern-dev
```

### Problem: "Port 5432 already in use"

**Solution**: Either kill existing process or use a different local port

```bash
# Find what's using port 5432
lsof -i :5432

# Use different port
aws ssm start-session \
  --target $BASTION_INSTANCE_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"$RDS_ENDPOINT\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"15432\"]}" \
  --region eu-central-1

# Then update DB_PORT=15432 in .env
```

### Problem: Docker containers can't reach host.docker.internal

**Linux Only**: Use the Docker bridge IP

```bash
# Get Docker bridge IP
docker network inspect bridge | grep Gateway

# Use that IP (usually 172.17.0.1) in .env:
DB_HOST=172.17.0.1
```

### Problem: Connection times out

**Check**:
1. SSM tunnel is running (keep terminal open)
2. Bastion instance is in a public subnet
3. Bastion security group allows outbound traffic to RDS
4. Database security group allows inbound from bastion security group

```bash
# Verify security group rules
aws ec2 describe-security-groups \
  --group-ids sg-01c8271d35a36d3eb \
  --region eu-central-1 \
  --profile batbern-dev
```

## Alternative: Direct Connection (Not Recommended)

For quick testing only, you can temporarily enable public access:

⚠️ **WARNING**: This is a security risk! Only use for temporary testing.

```bash
# 1. Enable public accessibility
aws rds modify-db-instance \
  --db-instance-identifier batbern-development-postgres \
  --publicly-accessible \
  --region eu-central-1 \
  --profile batbern-dev

# 2. Add your IP to security group
MY_IP=$(curl -s ifconfig.me)
aws ec2 authorize-security-group-ingress \
  --group-id sg-01c8271d35a36d3eb \
  --protocol tcp \
  --port 5432 \
  --cidr $MY_IP/32 \
  --region eu-central-1 \
  --profile batbern-dev

# 3. Use RDS endpoint directly in .env
DB_HOST=batbern-development-postgres.c10iyqgumkwu.eu-central-1.rds.amazonaws.com

# 4. REVERT when done!
aws ec2 revoke-security-group-ingress \
  --group-id sg-01c8271d35a36d3eb \
  --protocol tcp \
  --port 5432 \
  --cidr $MY_IP/32 \
  --region eu-central-1 \
  --profile batbern-dev

aws rds modify-db-instance \
  --db-instance-identifier batbern-development-postgres \
  --no-publicly-accessible \
  --region eu-central-1 \
  --profile batbern-dev
```

## Daily Workflow

### Starting Work

1. Open terminal for SSM tunnel:
   ```bash
   ./scripts/dev/start-db-tunnel.sh
   ```

2. In another terminal, start Docker:
   ```bash
   docker compose up -d
   ```

### Stopping Work

1. Stop Docker:
   ```bash
   docker compose down
   ```

2. Stop SSM tunnel (Ctrl+C in tunnel terminal)

## Cost Optimization

The bastion instance is a **t4g.nano** (smallest ARM instance) costing ~$3/month if running 24/7.

**To save costs**, stop when not in use:

```bash
# Stop bastion
aws ec2 stop-instances \
  --instance-ids $BASTION_INSTANCE_ID \
  --region eu-central-1 \
  --profile batbern-dev

# Start when needed
aws ec2 start-instances \
  --instance-ids $BASTION_INSTANCE_ID \
  --region eu-central-1 \
  --profile batbern-dev
```

Or set up auto-stop/start with Lambda (advanced).

## Helper Scripts

Create `scripts/dev/start-db-tunnel.sh`:

```bash
#!/bin/bash
# Start SSM port forward tunnel to RDS

export AWS_PROFILE=batbern-dev
export AWS_REGION=eu-central-1

# Get bastion instance ID
BASTION_ID=$(aws cloudformation describe-stacks \
  --stack-name BATbern-development-Bastion \
  --query 'Stacks[0].Outputs[?OutputKey==`BastionInstanceId`].OutputValue' \
  --output text)

# Get RDS endpoint
RDS_ENDPOINT=$(aws rds describe-db-instances \
  --query 'DBInstances[?DBInstanceIdentifier==`batbern-development-postgres`].Endpoint.Address' \
  --output text)

echo "🔒 Starting secure tunnel to database..."
echo "  Bastion: $BASTION_ID"
echo "  RDS: $RDS_ENDPOINT"
echo "  Local port: 5432"
echo ""
echo "Keep this terminal open while working!"
echo "Press Ctrl+C to close tunnel"
echo ""

aws ssm start-session \
  --target $BASTION_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"$RDS_ENDPOINT\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"5432\"]}"
```

Make it executable:
```bash
chmod +x scripts/dev/start-db-tunnel.sh
```

## References

- [AWS Systems Manager Session Manager](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html)
- [Port Forwarding with Session Manager](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html#sessions-start-port-forwarding)
- [Docker host.docker.internal](https://docs.docker.com/desktop/networking/#i-want-to-connect-from-a-container-to-a-service-on-the-host)
