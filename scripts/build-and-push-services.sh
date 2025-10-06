#!/bin/bash
set -e

# Configuration
ENVIRONMENT=${1:-staging}
AWS_PROFILE="batbern-${ENVIRONMENT}"
AWS_REGION="eu-central-1"

# Get AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile ${AWS_PROFILE} --query Account --output text)

echo "=========================================="
echo "Building and pushing microservices"
echo "Environment: ${ENVIRONMENT}"
echo "AWS Account: ${AWS_ACCOUNT_ID}"
echo "AWS Region: ${AWS_REGION}"
echo "=========================================="

# Login to ECR
echo ""
echo "Logging in to ECR..."
aws ecr get-login-password --region ${AWS_REGION} --profile ${AWS_PROFILE} | \
    docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# Define services in services/ directory
DOMAIN_SERVICES=(
    "event-management-service"
    "speaker-coordination-service"
    "partner-coordination-service"
    "company-management-service"
    "attendee-experience-service"
)

# Build and push API Gateway (in root directory)
echo ""
echo "=========================================="
echo "Building api-gateway..."
echo "=========================================="

cd api-gateway

# Build Docker image
docker build -t api-gateway:latest .

# Tag for ECR
ECR_REPO="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/batbern/${ENVIRONMENT}/api-gateway"
docker tag api-gateway:latest ${ECR_REPO}:latest

# Push to ECR
echo "Pushing api-gateway to ECR..."
docker push ${ECR_REPO}:latest

cd ..

echo "✓ api-gateway built and pushed successfully"

# Build and push domain services
for SERVICE in "${DOMAIN_SERVICES[@]}"; do
    echo ""
    echo "=========================================="
    echo "Building ${SERVICE}..."
    echo "=========================================="

    # Navigate to service directory
    cd services/${SERVICE}

    # Build Docker image
    docker build -t ${SERVICE}:latest .

    # Tag for ECR
    ECR_REPO="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/batbern/${ENVIRONMENT}/${SERVICE}"
    docker tag ${SERVICE}:latest ${ECR_REPO}:latest

    # Push to ECR
    echo "Pushing ${SERVICE} to ECR..."
    docker push ${ECR_REPO}:latest

    # Navigate back to root
    cd ../..

    echo "✓ ${SERVICE} built and pushed successfully"
done

echo ""
echo "=========================================="
echo "All services built and pushed successfully!"
echo "=========================================="
