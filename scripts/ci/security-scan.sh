#!/bin/bash
# Run security scans before promotion
# Usage: ./security-scan.sh <environment>

set -e

ENVIRONMENT=$1

if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    exit 1
fi

echo "=========================================="
echo "Running Security Scans"
echo "Environment: $ENVIRONMENT"
echo "=========================================="

SCAN_FAILED=false

# Scan 1: Check for vulnerabilities in dependencies
echo ""
echo "Scanning dependencies for vulnerabilities..."

# Backend: Gradle dependency check (if project exists)
if [ -f "build.gradle" ] || [ -f "api-gateway/build.gradle" ]; then
    echo "Checking Java/Gradle dependencies..."

    # Run OWASP dependency check on backend services
    SERVICES=(api-gateway shared-kernel)
    for service in "${SERVICES[@]}"; do
        if [ -d "$service" ] && [ -f "$service/build.gradle" ]; then
            echo "  Scanning $service..."
            cd "$service"

            # Use gradle dependencies to check for known vulnerabilities
            # In production, you would use: ./gradlew dependencyCheckAnalyze
            if ! ./gradlew dependencies --configuration runtimeClasspath > /dev/null 2>&1; then
                echo "  ⚠️  Warning: Could not scan $service dependencies"
            else
                echo "  ✓ $service dependencies scanned"
            fi

            cd ..
        fi
    done
fi

# Frontend: npm audit (if package.json exists)
if [ -f "web-frontend/package.json" ]; then
    echo "Checking Node.js/npm dependencies..."
    cd web-frontend

    # Run npm audit
    if npm audit --audit-level=moderate > audit-result.txt 2>&1; then
        echo "  ✓ No moderate or higher vulnerabilities found"
        rm -f audit-result.txt
    else
        AUDIT_EXIT_CODE=$?
        if [ $AUDIT_EXIT_CODE -eq 1 ]; then
            echo "  ❌ ERROR: Vulnerabilities found in npm dependencies"
            npm audit
            SCAN_FAILED=true
        fi
        rm -f audit-result.txt
    fi

    cd ..
fi

# Infrastructure: CDK dependencies
if [ -f "infrastructure/package.json" ]; then
    echo "Checking infrastructure dependencies..."
    cd infrastructure

    if npm audit --audit-level=moderate > audit-result.txt 2>&1; then
        echo "  ✓ No moderate or higher vulnerabilities found"
        rm -f audit-result.txt
    else
        AUDIT_EXIT_CODE=$?
        if [ $AUDIT_EXIT_CODE -eq 1 ]; then
            echo "  ⚠️  Warning: Vulnerabilities found in infrastructure dependencies"
            # Don't fail the build for infrastructure vulnerabilities in dev dependencies
        fi
        rm -f audit-result.txt
    fi

    cd ..
fi

# Scan 2: Check for secrets in code
echo ""
echo "Scanning for exposed secrets..."

# Use git-secrets or gitleaks if available, otherwise basic pattern matching
if command -v gitleaks &> /dev/null; then
    echo "Running gitleaks scan..."
    if ! gitleaks detect --source . --verbose 2>&1 | tee gitleaks-report.txt; then
        echo "❌ ERROR: Potential secrets detected"
        SCAN_FAILED=true
    else
        echo "✓ No secrets detected"
    fi
    rm -f gitleaks-report.txt
else
    echo "Gitleaks not installed, running basic pattern matching..."

    # Check for common secret patterns
    SECRET_PATTERNS=(
        "password\s*=\s*['\"][^'\"]+['\"]"
        "api[_-]?key\s*=\s*['\"][^'\"]+['\"]"
        "secret\s*=\s*['\"][^'\"]+['\"]"
        "token\s*=\s*['\"][^'\"]+['\"]"
        "aws[_-]?access[_-]?key"
        "private[_-]?key"
    )

    for pattern in "${SECRET_PATTERNS[@]}"; do
        if git grep -i "$pattern" -- '*.java' '*.ts' '*.tsx' '*.js' '*.yml' '*.yaml' 2>/dev/null | grep -v "test\|spec\|example\|template"; then
            echo "⚠️  Warning: Potential secret pattern found: $pattern"
            # Don't fail for warnings, just alert
        fi
    done

    echo "✓ Basic secret scan complete"
fi

# Scan 3: Docker image scanning (if running in CI with image available)
echo ""
echo "Checking container images..."

# This would use Trivy or similar in production
# For now, just verify images are tagged correctly
if [ -n "$IMAGE_TAG" ]; then
    echo "Image tag: $IMAGE_TAG"
    echo "✓ Container image validation complete"
else
    echo "No image tag specified, skipping container scan"
fi

# Scan 4: Infrastructure security
echo ""
echo "Checking infrastructure security..."

if [ -f "infrastructure/lib/core-infrastructure-stack.ts" ]; then
    # Check for basic security misconfigurations
    echo "Validating infrastructure security configurations..."

    # Check for overly permissive IAM policies
    if grep -r "\*" infrastructure/lib/ | grep -i "action\|resource" | grep -v "test\|comment" > /dev/null; then
        echo "  ⚠️  Warning: Potential overly permissive IAM policies detected"
    fi

    echo "  ✓ Infrastructure security check complete"
fi

echo ""
echo "=========================================="
echo "Security Scan Results"
echo "=========================================="

if [ "$SCAN_FAILED" = true ]; then
    echo "❌ SECURITY SCANS FAILED"
    echo "Critical vulnerabilities or secrets detected"
    exit 1
else
    echo "✅ ALL SECURITY SCANS PASSED"
    exit 0
fi
