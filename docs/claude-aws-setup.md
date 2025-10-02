# Claude AWS User Setup Instructions

## Prerequisites

You should have already completed:
- ✅ AWS Organization with Development/Staging/Production accounts
- ✅ Route53 hosted zone for `batbern.ch`
- ✅ `claude-automation` IAM user created
- ✅ `BATbernClaudePolicy` and `BATbernCostControl` policies created

## Required Manual Steps

### 1. Attach Policies to claude-automation User

```bash
# Attach Claude policy to user
aws iam attach-user-policy \
  --user-name claude-automation \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/BATbernClaudePolicy

# Create cost control group and attach policy
aws iam create-group --group-name BATbernCostControlledUsers
aws iam attach-group-policy \
  --group-name BATbernCostControlledUsers \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/BATbernCostControl

# Add claude-automation to cost control group
aws iam add-user-to-group \
  --group-name BATbernCostControlledUsers \
  --user-name claude-automation
```

### 2. Configure AWS Profile for Claude

```bash
# Configure AWS CLI with Claude's credentials
aws configure --profile claude-automation
# Enter Access Key ID and Secret Access Key when prompted
# Region: eu-central-1
# Output format: json

# Set as default profile
export AWS_PROFILE=claude-automation
echo 'export AWS_PROFILE=claude-automation' >> ~/.bashrc  # or ~/.zshrc
```

### 3. Test Access

```bash
# Verify identity
aws sts get-caller-identity

# Test basic permissions
aws s3 ls
aws route53 list-hosted-zones
```

## CDK Will Handle

All infrastructure provisioning is automated via CDK:
- Cross-account IAM roles
- VPC and networking setup
- S3 buckets and policies
- CloudFormation stacks
- Route53 DNS records
- Security groups and NACLs
- Application load balancers
- ECS clusters and services

## Security Notes

- Cost control policy prevents expensive service usage
- All infrastructure changes tracked via CloudTrail
- Cross-account roles will be created by CDK with proper trust policies
- Regular credential rotation recommended (90 days)

---

**Next Steps**: Run the manual commands above, then proceed with CDK deployment.