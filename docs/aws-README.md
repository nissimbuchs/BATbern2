# BATbern AWS Configuration Files

## Overview
This folder contains AWS configuration files for setting up the BATbern Event Management Platform infrastructure.

## Files

### Setup Guides
- **`aws-setup-guide.md`** - Complete AWS account setup with Route53 domain configuration
- **`claude-aws-setup.md`** - Quick setup commands for Claude automation access

### Policy Files
- **`claude-policy-enhanced.json`** - Enhanced IAM policy for Claude with comprehensive AWS permissions
- **`aws-cost-control-policy.json`** - Cost protection policy to prevent expensive service usage

### Documentation
- **`todo.md`** - Project todo list and infrastructure tasks

## Quick Start

1. Follow `aws-setup-guide.md` for complete AWS account setup
2. Use `claude-aws-setup.md` for automated Claude user configuration
3. Apply `aws-cost-control-policy.json` for cost protection
4. Use `claude-policy-enhanced.json` for Claude IAM permissions

## Domain Configuration

The platform uses `batbern.ch` domain managed by AWS Route53:
- Production: `www.batbern.ch` → `api.batbern.ch`
- Staging: `staging.batbern.ch` → `api-staging.batbern.ch`
- Development: `dev.batbern.ch` → `api-dev.batbern.ch`

## Architecture

The infrastructure follows the patterns defined in:
- `docs/architecture/02-infrastructure-deployment.md`
- `docs/architecture/01-system-overview.md`

## Cost Control

Apply the cost control policy to prevent accidental high-cost resource usage:
```bash
aws iam create-policy \
  --policy-name BATbernCostControl \
  --policy-document file://aws-cost-control-policy.json
```