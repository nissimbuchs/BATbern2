# CI/CD Pipeline Automated Setup Guide

**Story**: 1.4 - CI/CD Pipeline Implementation
**Approach**: Infrastructure as Code (CDK) + GitHub CLI Automation

---

## Quick Start (4 Steps)

```bash
# 1. Deploy AWS infrastructure (Network, Database, Storage)
cd infrastructure
npm run deploy:dev

# 2. Deploy CI/CD infrastructure (ECR + IAM)
npm run deploy:dev -- BATbern-development-CICD --context githubRepository=YOUR_ORG/BATbern

# 3. Configure GitHub (secrets + environments)
cd ..
./scripts/ci/setup-github-config.sh

# 4. Test the pipeline
git push origin develop
```

---

## Overview

This guide uses **100% Infrastructure as Code** approach:
- ✅ **Databases** created by CDK with automatic credential management
- ✅ **ECR repositories** and IAM roles managed by CDK
- ✅ **Database credentials** retrieved from Secrets Manager at runtime
- ✅ **GitHub config** via gh CLI (no manual UI)
- ✅ **Zero manual secrets** for databases - all automatic!

### How Database Credentials Work

```
┌─────────────────┐
│  CDK Deploy     │ Creates RDS + stores credentials
│  Database Stack │ in AWS Secrets Manager automatically
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│ AWS Secrets Manager     │ Credentials: username, password
│ Secret: batbern-dev-db  │ Auto-rotated, encrypted
└────────┬────────────────┘
         │
         ▼
┌──────────────────────────┐
│ GitHub Actions Workflow  │ Retrieves credentials at runtime
│ (deploy-production.yml)  │ via AWS CLI + CloudFormation outputs
└──────────────────────────┘
```

**No manual database setup required!**

---

## Prerequisites

### Required Tools

```bash
# Check if installed
node --version    # v20+
npm --version     # v10+
aws --version     # AWS CLI v2
gh --version      # GitHub CLI 2.0+
```

### Install Missing Tools

```bash
# GitHub CLI
brew install gh         # macOS
# or: https://cli.github.com/

# AWS CLI
brew install awscli     # macOS
# or: https://aws.amazon.com/cli/
```

### Authentication

```bash
# AWS
aws configure

# GitHub
gh auth login
```

---

## Step 1: Deploy AWS Infrastructure with CDK

### What This Creates

- 7 ECR repositories (one per service)
- IAM role for GitHub Actions (OIDC)
- CloudWatch log groups for pipeline logs
- Lifecycle policies (keep last 10 images)

### Commands

```bash
cd infrastructure

# Install dependencies
npm install

# Set your GitHub repo (REQUIRED)
export GITHUB_REPO="YOUR_ORG/BATbern"

# Deploy to development
npm run deploy:dev -- --context githubRepository=$GITHUB_REPO

# Deploy to staging (when ready)
npm run deploy:staging -- --context githubRepository=$GITHUB_REPO

# Deploy to production (when ready)
npm run deploy:prod -- --context githubRepository=$GITHUB_REPO
```

### CDK Outputs

The stack will output:
- ECR repository URIs
- GitHub Actions IAM role ARN
- Log group names

**Save these outputs** - you'll need them for GitHub workflows.

---

## Step 2: One-Time AWS OIDC Setup

GitHub Actions uses OIDC to assume AWS roles without storing access keys.

```bash
# Run once per AWS account
./scripts/ci/setup-github-oidc-provider.sh
```

This creates the GitHub OIDC provider in IAM.

**Skip if already done** - Script will detect existing provider.

---

## Step 3: Configure GitHub (Automated)

### What This Sets Up

- Repository secrets (AWS credentials, DB credentials)
- GitHub environments (staging, production)
- Environment protection rules (reviewers)

### Run Setup Script

```bash
./scripts/ci/setup-github-config.sh
```

The script will prompt you for:

1. **AWS Credentials**
   - Account ID (auto-detected if AWS CLI configured)
   - Access Key ID
   - Secret Access Key

2. **Database Credentials**
   - ✅ NOT REQUIRED - Managed automatically by CDK!
   - Databases created by CDK Database stack
   - Credentials stored in AWS Secrets Manager
   - Workflows retrieve them at runtime

3. **External Services** (optional)
   - Snyk token
   - SonarQube URL + token
   - Codecov token
   - Slack webhook URL

### What Gets Created

**Repository Secrets:**
- `AWS_ACCOUNT_ID`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- External service tokens (optional):
  - `SNYK_TOKEN`
  - `SONAR_HOST_URL`, `SONAR_TOKEN`
  - `CODECOV_TOKEN`
  - `SLACK_WEBHOOK_URL`

**NOT in GitHub Secrets** (CDK-managed):
- ~~Database URLs~~ → Retrieved from CloudFormation outputs
- ~~Database credentials~~ → Retrieved from Secrets Manager

**Environments:**
- `staging`
- `production`

---

## Step 4: Manual Configuration (GitHub UI)

**One manual step** - Environment protection rules require GitHub UI:

```bash
# Script will open browser automatically, or visit:
# https://github.com/YOUR_ORG/BATbern/settings/environments
```

### Staging Environment

1. Click "staging"
2. Check "Required reviewers"
3. Add 1+ team leads
4. Deployment branches: `develop`, `main`
5. Save

### Production Environment

1. Click "production"
2. Check "Required reviewers"
3. Add 2+ senior engineers
4. (Optional) Wait timer: 10 minutes
5. Deployment branches: `main` only
6. Save

---

## Step 5: Verify Setup

### Check CDK Deployment

```bash
cd infrastructure

# List all ECR repositories
aws ecr describe-repositories \
  --region eu-central-1 \
  --query 'repositories[?contains(repositoryName, `batbern`)].{Name:repositoryName,URI:repositoryUri}' \
  --output table

# Verify IAM role exists
aws iam get-role --role-name batbern-development-github-actions-role
```

### Check GitHub Configuration

```bash
# List secrets
gh secret list

# List environments
gh api repos/{owner}/{repo}/environments | jq '.environments[].name'

# Test authentication
gh auth status
```

### Test ECR Push

```bash
# Login to ECR
aws ecr get-login-password --region eu-central-1 | \
  docker login --username AWS --password-stdin \
  $(aws sts get-caller-identity --query Account --output text).dkr.ecr.eu-central-1.amazonaws.com

# Pull and tag a test image
docker pull alpine:latest
docker tag alpine:latest \
  $(aws sts get-caller-identity --query Account --output text).dkr.ecr.eu-central-1.amazonaws.com/batbern/development/api-gateway:test

# Push
docker push \
  $(aws sts get-caller-identity --query Account --output text).dkr.ecr.eu-central-1.amazonaws.com/batbern/development/api-gateway:test
```

---

## Step 6: Test the Pipeline

### Trigger Build

```bash
# Push to develop branch
git checkout develop
git commit --allow-empty -m "test: Trigger CI/CD pipeline"
git push origin develop
```

### Watch Workflow

```bash
# View running workflows
gh run list

# Watch specific run
gh run watch

# View logs
gh run view --log
```

### Expected Results

✅ **Build Pipeline** runs:
- Builds shared-kernel
- Builds api-gateway
- Runs tests with coverage checks
- Pushes Docker images to ECR
- Quality gates pass

---

## Updating GitHub Workflows (Optional - OIDC)

If you want to use OIDC instead of access keys:

### Replace in workflows:

**Before** (using access keys):
```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
    aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    aws-region: eu-central-1
```

**After** (using OIDC):
```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/batbern-development-github-actions-role
    aws-region: eu-central-1
```

Get the role ARN from CDK outputs:
```bash
cd infrastructure
npm run synth:dev | grep GitHubActionsRoleArn
```

---

## Troubleshooting

### "Repository does not exist" (ECR)

**Cause**: CDK CICD stack not deployed
**Solution**: `cd infrastructure && npm run deploy:dev`

### "Secrets not found" (GitHub Actions)

**Cause**: Secrets not set in GitHub
**Solution**: Run `./scripts/ci/setup-github-config.sh` again

### "IAM role cannot be assumed" (OIDC)

**Cause**: OIDC provider not created or wrong repo name
**Solution**:
1. Run `./scripts/ci/setup-github-oidc-provider.sh`
2. Verify `githubRepository` context matches your repo

### "Coverage below 90%" (Build fails)

**Expected**: JaCoCo enforces 90% coverage
**Solution**: Add tests or request coverage threshold adjustment

### "Environment protection rules not working"

**Cause**: Manual step skipped
**Solution**: Visit GitHub Settings → Environments → Configure reviewers

---

## File Reference

### CDK Files
- `infrastructure/lib/stacks/cicd-stack.ts` - ECR + IAM definitions
- `infrastructure/bin/batbern-infrastructure.ts` - Main entry point

### Scripts
- `scripts/ci/setup-github-oidc-provider.sh` - AWS OIDC setup
- `scripts/ci/setup-github-config.sh` - GitHub automation
- `scripts/ci/setup-ecr-repositories.sh` - Deprecated (use CDK)

### Workflows
- `.github/workflows/build.yml` - Main build pipeline
- `.github/workflows/deploy-production.yml` - Production deployment

---

## Architecture: Infrastructure as Code

```
┌─────────────────────────────────────────────┐
│  Developer                                  │
│  └─ Runs scripts                            │
└──────────────┬──────────────────────────────┘
               │
               ├──────────────────┐
               │                  │
        ┌──────▼──────┐    ┌─────▼─────┐
        │ CDK Deploy  │    │  gh CLI   │
        │ (AWS)       │    │ (GitHub)  │
        └──────┬──────┘    └─────┬─────┘
               │                  │
     ┌─────────▼──────────┐       │
     │ ECR Repositories   │       │
     │ IAM Roles (OIDC)   │       │
     │ CloudWatch Logs    │       │
     └────────────────────┘       │
                          ┌───────▼────────┐
                          │ Secrets        │
                          │ Environments   │
                          └────────────────┘
                                  │
                          ┌───────▼────────┐
                          │ GitHub Actions │
                          │ Workflows      │
                          └────────────────┘
```

---

## Security Best Practices

1. ✅ **OIDC over Access Keys**: Use OIDC for GitHub Actions when possible
2. ✅ **Least Privilege IAM**: Roles have minimal required permissions
3. ✅ **Environment Protection**: Staging/prod require approvals
4. ✅ **Secrets Encryption**: All secrets encrypted at rest by GitHub
5. ✅ **Image Scanning**: ECR scans images on push
6. ✅ **Short-lived Sessions**: OIDC sessions expire after 1 hour

---

## Next Steps

1. ✅ **Deploy all environments**: dev, staging, production
2. ✅ **Configure external services**: SonarQube, Snyk (optional)
3. ✅ **Set up notifications**: Slack webhook for deployments
4. ✅ **Create version tags**: `git tag v0.1.0 && git push --tags`
5. ✅ **Team training**: Share this guide with developers

---

## Support & Resources

- **CDK Documentation**: https://docs.aws.amazon.com/cdk/
- **GitHub CLI**: https://cli.github.com/manual/
- **GitHub OIDC**: https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- **Story 1.4**: `docs/stories/1.4.cicd-pipeline-implementation.md`

---

**Last Updated**: 2025-10-01
**Maintained By**: DevOps Team
