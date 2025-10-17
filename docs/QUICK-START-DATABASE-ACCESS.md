# Quick Start: Database Access for Local Development

## TL;DR

Your local Docker containers need to access AWS RDS, but it's in a private VPC. Solution: Use an AWS bastion host with SSM port forwarding.

## 5-Minute Setup

### 1. Wait for Bastion Deployment (One-Time)

The bastion stack is currently deploying. Wait for it to complete (~3-5 minutes).

Check status:
```bash
export AWS_PROFILE=batbern-dev
aws cloudformation describe-stacks \
  --stack-name BATbern-development-Bastion \
  --query 'Stacks[0].StackStatus' \
  --region eu-central-1
```

### 2. Add Security Group Rule (One-Time)

Once deployed, allow bastion to access the database:

```bash
export AWS_PROFILE=batbern-dev

# Get bastion security group ID
BASTION_SG=$(aws cloudformation describe-stacks \
  --stack-name BATbern-development-Bastion \
  --query 'Stacks[0].Outputs[?OutputKey==`BastionSecurityGroupId`].OutputValue' \
  --output text \
  --region eu-central-1)

# Get database security group ID
DB_SG=$(aws ec2 describe-security-groups \
  --filters "Name=tag:Name,Values=*DatabaseSG*" \
  --query 'SecurityGroups[0].GroupId' \
  --output text \
  --region eu-central-1)

# Add ingress rule
aws ec2 authorize-security-group-ingress \
  --group-id $DB_SG \
  --protocol tcp \
  --port 5432 \
  --source-group $BASTION_SG \
  --region eu-central-1

echo "✅ Bastion can now access database"
```

### 3. Install Session Manager Plugin (One-Time)

**macOS:**
```bash
brew install --cask session-manager-plugin
```

**Linux:**
```bash
curl "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb" -o "/tmp/session-manager-plugin.deb"
sudo dpkg -i /tmp/session-manager-plugin.deb
```

Verify:
```bash
session-manager-plugin
# Should show usage information
```

### 4. Start the Tunnel (Every Work Session)

Open a dedicated terminal and run:

```bash
./scripts/dev/start-db-tunnel.sh
```

Keep this terminal open! It maintains the connection.

### 5. Update .env (One-Time)

Edit `.env` file:

```bash
# Change DB_HOST from AWS endpoint to localhost
DB_HOST=host.docker.internal  # macOS/Windows
# OR
DB_HOST=172.17.0.1  # Linux

# Keep everything else the same
DB_PORT=5432
DB_NAME=batbern
DB_USER=postgres
DB_PASSWORD=<from .env>
DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/batbern?user=postgres&password=<password>
```

### 6. Start Docker (Every Work Session)

In a **new terminal**:

```bash
docker compose up -d
```

Done! Your services can now connect to AWS RDS through the tunnel.

## Daily Workflow

**Morning (start work):**
```bash
# Terminal 1: Start tunnel
./scripts/dev/start-db-tunnel.sh

# Terminal 2: Start Docker
docker compose up -d
```

**Evening (end work):**
```bash
# Terminal 2: Stop Docker
docker compose down

# Terminal 1: Stop tunnel
# Press Ctrl+C
```

## Troubleshooting

### "command not found: session-manager-plugin"

Install it (see step 3 above).

### "Port 5432 already in use"

Something else is using that port:

```bash
# Find what's using it
lsof -i :5432

# Kill it or use different port
./scripts/dev/start-db-tunnel.sh --port 15432
# Then update .env: DB_PORT=15432
```

### "TargetNotConnected"

Bastion instance stopped. Start it:

```bash
export AWS_PROFILE=batbern-dev
INSTANCE_ID=$(aws cloudformation describe-stacks \
  --stack-name BATbern-development-Bastion \
  --query 'Stacks[0].Outputs[?OutputKey==`BastionInstanceId`].OutputValue' \
  --output text \
  --region eu-central-1)

aws ec2 start-instances \
  --instance-ids $INSTANCE_ID \
  --region eu-central-1

# Wait 30 seconds for SSM agent
sleep 30

# Then run tunnel script again
./scripts/dev/start-db-tunnel.sh
```

### Docker containers can't connect

**Linux only**: Use Docker bridge IP instead of `host.docker.internal`:

```bash
# In .env:
DB_HOST=172.17.0.1
DATABASE_URL=jdbc:postgresql://172.17.0.1:5432/batbern?user=postgres&password=<password>
```

## Cost

The bastion instance (t4g.nano) costs ~$0.004/hour = ~$3/month if running 24/7.

**To save costs**, stop it when not working:

```bash
# scripts/dev/start-db-tunnel.sh automatically starts it
# Manually stop it:
aws ec2 stop-instances --instance-ids $INSTANCE_ID --region eu-central-1 --profile batbern-dev
```

## More Details

See full guide: [DATABASE-ACCESS-GUIDE.md](./DATABASE-ACCESS-GUIDE.md)

##What We Built

1. **Bastion Host**: Small EC2 instance (t4g.nano) in public subnet
2. **SSM Agent**: Pre-installed for secure access (no SSH keys!)
3. **Port Forward**: Tunnel from localhost:5432 → bastion → RDS:5432
4. **Docker Config**: Containers use `host.docker.internal` to reach tunnel

## Architecture

```
┌─────────────────┐
│ Your Computer   │
│                 │
│ Docker Compose  │
│   ↓ port 5432  │
│ localhost:5432  │────┐
└─────────────────┘    │
                       │ SSM Tunnel
                       ↓
            ┌───────────────────┐
            │ AWS VPC           │
            │                   │
            │ ┌──────────────┐  │
            │ │ Bastion      │  │
            │ │ (t4g.nano)   │  │
            │ └──────┬───────┘  │
            │        │           │
            │        ↓           │
            │ ┌──────────────┐  │
            │ │ RDS          │  │
            │ │ PostgreSQL   │  │
            │ └──────────────┘  │
            └───────────────────┘
```

The tunnel is secure:
- ✅ No SSH keys
- ✅ No open ports (SSM uses AWS API)
- ✅ IAM authentication
- ✅ Session logs in CloudWatch
