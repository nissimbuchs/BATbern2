#!/bin/bash

# Trivy Security Scanning Script
# Scans all Java modules and web-frontend for vulnerabilities
# Generates SARIF reports for the test dashboard

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
REPORTS_DIR="$PROJECT_ROOT/security-reports"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "================================================"
echo "🔒 BATbern Security Scanning with Trivy"
echo "================================================"
echo ""

# Create reports directory
mkdir -p "$REPORTS_DIR"

# Java modules to scan
JAVA_MODULES=(
  "shared-kernel"
  "api-gateway"
  "services/event-management-service"
  "services/speaker-coordination-service"
  "services/partner-coordination-service"
  "services/attendee-experience-service"
  "services/company-user-management-service"
)

# Function to scan a Java module
scan_java_module() {
  local module=$1
  local module_name=$(basename "$module")
  local module_dir="$PROJECT_ROOT/$module"

  if [ ! -d "$module_dir" ]; then
    echo -e "${YELLOW}⚠️  Skipping $module_name (directory not found)${NC}"
    return
  fi

  echo -e "${GREEN}📦 Scanning $module_name...${NC}"

  # Scan filesystem for vulnerabilities in dependencies
  trivy fs \
    --scanners vuln,secret,misconfig \
    --format sarif \
    --output "$REPORTS_DIR/${module_name}-trivy.sarif" \
    --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL \
    "$module_dir"

  echo -e "${GREEN}✅ $module_name scan complete${NC}"
}

# Function to scan web-frontend
scan_web_frontend() {
  local module_dir="$PROJECT_ROOT/web-frontend"

  if [ ! -d "$module_dir" ]; then
    echo -e "${YELLOW}⚠️  Skipping web-frontend (directory not found)${NC}"
    return
  fi

  echo -e "${GREEN}📦 Scanning web-frontend...${NC}"

  # Scan npm dependencies
  trivy fs \
    --scanners vuln,secret,misconfig \
    --format sarif \
    --output "$REPORTS_DIR/web-frontend-trivy.sarif" \
    --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL \
    "$module_dir"

  echo -e "${GREEN}✅ web-frontend scan complete${NC}"
}

# Scan all Java modules
echo ""
echo "Scanning Java modules..."
echo "------------------------"
for module in "${JAVA_MODULES[@]}"; do
  scan_java_module "$module"
done

# Scan web-frontend
echo ""
echo "Scanning frontend..."
echo "--------------------"
scan_web_frontend

# Summary
echo ""
echo "================================================"
echo "✅ Security scanning complete!"
echo "================================================"
echo ""
echo "SARIF reports generated in: $REPORTS_DIR"
echo ""
echo "Files created:"
ls -lh "$REPORTS_DIR"/*.sarif 2>/dev/null | awk '{print "  - " $9 " (" $5 ")"}'
echo ""
echo "To view security findings, run:"
echo "  cd apps/projectdoc && npm run build:reports"
echo ""
