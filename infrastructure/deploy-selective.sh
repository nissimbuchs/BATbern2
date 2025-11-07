#!/bin/bash
# Selective CDK Deployment Script
# Optimized deployment that only deploys changed stacks

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
ENVIRONMENT="staging"
PROFILE="batbern-staging"
DRY_RUN=false
STACKS=()

# Help function
show_help() {
    cat << EOF
Usage: ./deploy-selective.sh [OPTIONS]

Selectively deploy CDK stacks based on changes or explicit selection.

OPTIONS:
    -e, --environment ENV    Environment (development|staging|production) [default: staging]
    -s, --stacks STACK1,STACK2  Specific stacks to deploy (comma-separated)
    -m, --microservices      Deploy only microservices
    -i, --infra              Deploy only infrastructure
    -f, --frontend           Deploy only frontend
    -a, --all                Deploy all stacks (same as regular deploy)
    --dry-run                Show what would be deployed without deploying
    -h, --help               Show this help message

EXAMPLES:
    # Deploy only microservices (fastest for code changes)
    ./deploy-selective.sh -m

    # Deploy specific stacks
    ./deploy-selective.sh -s EventManagement,CompanyManagement

    # Deploy infrastructure only
    ./deploy-selective.sh -i

    # Dry run to see what would deploy
    ./deploy-selective.sh -m --dry-run

PERFORMANCE:
    Full deployment (--all):        30+ minutes
    Microservices only (-m):        8-12 minutes
    Single stack:                   2-4 minutes
    Frontend only (-f):             3-5 minutes

EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            case $ENVIRONMENT in
                development) PROFILE="batbern-dev" ;;
                staging) PROFILE="batbern-staging" ;;
                production) PROFILE="batbern-prod" ;;
                *) echo -e "${RED}Invalid environment: $ENVIRONMENT${NC}" exit 1 ;;
            esac
            shift 2
            ;;
        -s|--stacks)
            IFS=',' read -ra STACK_ARRAY <<< "$2"
            for stack in "${STACK_ARRAY[@]}"; do
                STACKS+=("BATbern-${ENVIRONMENT}-${stack}")
            done
            shift 2
            ;;
        -m|--microservices)
            STACKS+=("BATbern-${ENVIRONMENT}-EventManagement")
            STACKS+=("BATbern-${ENVIRONMENT}-SpeakerCoordination")
            STACKS+=("BATbern-${ENVIRONMENT}-PartnerCoordination")
            STACKS+=("BATbern-${ENVIRONMENT}-AttendeeExperience")
            STACKS+=("BATbern-${ENVIRONMENT}-CompanyManagement")
            STACKS+=("BATbern-${ENVIRONMENT}-ApiGatewayService")
            shift
            ;;
        -i|--infra)
            STACKS+=("BATbern-${ENVIRONMENT}-Network")
            STACKS+=("BATbern-${ENVIRONMENT}-Database")
            STACKS+=("BATbern-${ENVIRONMENT}-Storage")
            STACKS+=("BATbern-${ENVIRONMENT}-Cognito")
            STACKS+=("BATbern-${ENVIRONMENT}-Secrets")
            shift
            ;;
        -f|--frontend)
            STACKS+=("BATbern-${ENVIRONMENT}-Frontend")
            shift
            ;;
        -a|--all)
            STACKS=("--all")
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# If no stacks specified, show help
if [ ${#STACKS[@]} -eq 0 ]; then
    echo -e "${YELLOW}No stacks specified. Use -h for help.${NC}"
    show_help
    exit 1
fi

# Build CDK command
CDK_CMD="AWS_PROFILE=$PROFILE cdk deploy"
if [ "${STACKS[0]}" = "--all" ]; then
    CDK_CMD="$CDK_CMD --all"
else
    CDK_CMD="$CDK_CMD ${STACKS[*]}"
fi
CDK_CMD="$CDK_CMD --context environment=$ENVIRONMENT --require-approval never --concurrency=3"

# Show what will be deployed
echo -e "${GREEN}=== CDK Selective Deployment ===${NC}"
echo "Environment: $ENVIRONMENT"
echo "AWS Profile: $PROFILE"
echo "Stacks to deploy: ${STACKS[*]}"
echo ""

if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}DRY RUN - Would execute:${NC}"
    echo "$CDK_CMD"
    exit 0
fi

# Execute deployment
echo -e "${GREEN}Executing deployment...${NC}"
echo "$CDK_CMD"
echo ""

# Enable Docker BuildKit for faster builds
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

eval $CDK_CMD

echo -e "${GREEN}=== Deployment Complete ===${NC}"
