# BATbern Deployment Documentation

This directory contains deployment guides and infrastructure documentation.

---

## Quick Links

### 📘 **[CI/CD Automated Setup Guide](./cicd-automated-setup.md)** ← START HERE

Complete automated setup for CI/CD pipeline using:
- AWS CDK for infrastructure
- GitHub CLI for configuration
- Shell scripts for automation

**Estimated time**: 15-20 minutes

---

## Available Guides

| Guide | Purpose | Status |
|-------|---------|--------|
| [CI/CD Automated Setup](./cicd-automated-setup.md) | **Primary guide** - Automated CI/CD setup | ✅ Current |
| [CI/CD Manual Setup](./cicd-setup-guide.md) | Legacy manual setup | ⚠️ Deprecated |

---

## What Gets Set Up

### AWS Infrastructure (via CDK)
- ✅ ECR repositories for all 7 services
- ✅ IAM roles for GitHub Actions (OIDC)
- ✅ CloudWatch log groups
- ✅ Lifecycle policies for image retention

### GitHub Configuration (via gh CLI)
- ✅ Repository secrets (AWS, databases, external services)
- ✅ Environments (staging, production)
- ✅ Environment protection rules

---

## Prerequisites

```bash
# Required tools
node --version      # v20+
aws --version       # AWS CLI v2
gh --version        # GitHub CLI 2.0+

# Authentication
aws configure
gh auth login
```

---

## Quick Start

```bash
# 1. Deploy AWS infrastructure
cd infrastructure
npm run deploy:dev -- --context githubRepository=nissimbuchs/BATbern2

# 2. Configure GitHub
cd ..
./scripts/ci/setup-github-config.sh

# 3. Test
git push origin develop
```

---

## Troubleshooting

See the [Automated Setup Guide](./cicd-automated-setup.md#troubleshooting) for common issues and solutions.

---

## Related Documentation

- **Story 1.4**: [CI/CD Pipeline Implementation](../stories/1.4.cicd-pipeline-implementation.md)
- **Architecture**: [Infrastructure & Deployment](../architecture/02-infrastructure-deployment.md)
- **CDK Source**: [`infrastructure/lib/stacks/cicd-stack.ts`](../../infrastructure/lib/stacks/cicd-stack.ts)

---

**Maintained By**: DevOps Team
**Last Updated**: 2025-10-01
