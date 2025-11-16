#!/bin/bash
# Start MinIO (S3-compatible object storage) for local development
#
# MinIO provides S3-compatible API for local development
# Default credentials: minioadmin / minioadmin
#
# Usage:
#   ./scripts/dev/start-minio.sh                                      # Default (port 9000/9001, instance 1)
#   MINIO_API_PORT=9100 MINIO_CONSOLE_PORT=9101 INSTANCE=2 ./scripts/dev/start-minio.sh  # Instance 2

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Instance-specific configuration
INSTANCE="${INSTANCE:-1}"
MINIO_API_PORT="${MINIO_API_PORT:-8450}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-8451}"

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# MinIO is shared across all instances and all BATbern projects - use /tmp
MINIO_DATA_DIR="/tmp/.minio/data"
MINIO_PID_FILE="/tmp/batbern-shared-minio.pid"
MINIO_LOG_FILE="/tmp/batbern-shared-minio.log"

# MinIO credentials (default for local dev)
export MINIO_ROOT_USER="minioadmin"
export MINIO_ROOT_PASSWORD="minioadmin"

# Enable CORS for browser access (all frontend instances)
export MINIO_API_CORS_ALLOW_ORIGIN="http://localhost:3000,http://localhost:3001,http://localhost:4000,http://localhost:4001"

# Check if MinIO is already running
if [ -f "$MINIO_PID_FILE" ]; then
    PID=$(cat "$MINIO_PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠ MinIO already running (PID: $PID)${NC}"
        exit 0
    else
        rm -f "$MINIO_PID_FILE"
    fi
fi

# Create data directory if it doesn't exist
mkdir -p "$MINIO_DATA_DIR"

echo -e "${CYAN}→ Starting MinIO server (Instance ${INSTANCE})...${NC}"
echo -e "${CYAN}  Data directory: ${MINIO_DATA_DIR}${NC}"
echo -e "${CYAN}  API endpoint:   http://localhost:${MINIO_API_PORT}${NC}"
echo -e "${CYAN}  Console:        http://localhost:${MINIO_CONSOLE_PORT}${NC}"
echo -e "${CYAN}  Credentials:    minioadmin / minioadmin${NC}"

# Start MinIO server in background with instance-specific ports
nohup minio server "$MINIO_DATA_DIR" \
    --address ":${MINIO_API_PORT}" \
    --console-address ":${MINIO_CONSOLE_PORT}" \
    > "$MINIO_LOG_FILE" 2>&1 &

PID=$!
echo $PID > "$MINIO_PID_FILE"
disown

echo -e "${GREEN}✓ MinIO started (PID: $PID)${NC}"

# Wait for MinIO to be ready
echo -e "${CYAN}→ Waiting for MinIO to be ready...${NC}"
MAX_ATTEMPTS=30
ATTEMPT=1

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    if curl -sf http://localhost:${MINIO_API_PORT}/minio/health/live > /dev/null 2>&1; then
        echo -e "${GREEN}✓ MinIO is ready${NC}"
        break
    fi
    sleep 1
    ATTEMPT=$((ATTEMPT + 1))
done

if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
    echo -e "${RED}✗ MinIO failed to start within 30 seconds${NC}"
    echo -e "${YELLOW}Check logs: tail -f ${MINIO_LOG_FILE}${NC}"
    exit 1
fi

# Configure MinIO client alias (instance-specific)
echo -e "${CYAN}→ Configuring MinIO client...${NC}"
mc alias set local-${INSTANCE} http://localhost:${MINIO_API_PORT} minioadmin minioadmin > /dev/null 2>&1 || true

# Create batbern-development-company-logos bucket if it doesn't exist
echo -e "${CYAN}→ Creating batbern-development-company-logos bucket...${NC}"
if mc ls local-${INSTANCE}/batbern-development-company-logos > /dev/null 2>&1; then
    echo -e "${YELLOW}  ⚠ Bucket 'batbern-development-company-logos' already exists${NC}"
else
    mc mb local-${INSTANCE}/batbern-development-company-logos > /dev/null 2>&1
    echo -e "${GREEN}  ✓ Created bucket 'batbern-development-company-logos'${NC}"
fi

# Set bucket policy to allow public read (for logo access)
echo -e "${CYAN}→ Setting bucket policy (public read)...${NC}"
cat > /tmp/minio-policy-${INSTANCE}.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {"AWS": ["*"]},
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::batbern-development-company-logos/*"]
    }
  ]
}
EOF
mc anonymous set-json /tmp/minio-policy-${INSTANCE}.json local-${INSTANCE}/batbern-development-company-logos > /dev/null 2>&1
rm -f /tmp/minio-policy-${INSTANCE}.json
echo -e "${GREEN}  ✓ Bucket policy configured (public read)${NC}"
echo -e "${GREEN}  ✓ CORS enabled via environment variable (MINIO_API_CORS_ALLOW_ORIGIN)${NC}"

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          MinIO Started Successfully! 🪣                     ║${NC}"
echo -e "${GREEN}║              Instance ${INSTANCE} (Ports ${MINIO_API_PORT}/${MINIO_CONSOLE_PORT})              ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}MinIO Console: ${GREEN}http://localhost:${MINIO_CONSOLE_PORT}${NC}"
echo -e "${CYAN}API Endpoint:  ${GREEN}http://localhost:${MINIO_API_PORT}${NC}"
echo -e "${CYAN}Access Key:    ${GREEN}minioadmin${NC}"
echo -e "${CYAN}Secret Key:    ${GREEN}minioadmin${NC}"
echo ""
