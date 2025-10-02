# BATbern Platform Infrastructure

AWS CDK infrastructure as code for the BATbern conference platform with multi-environment support.

## Architecture Overview

This infrastructure implements a complete AWS-based multi-tier architecture with:

- **Network Layer**: Isolated VPCs with public, private, and isolated subnets
- **Database Layer**: RDS PostgreSQL and ElastiCache Redis with Multi-AZ support
- **Storage Layer**: S3 buckets with CloudFront CDN
- **Security Layer**: Secrets Manager, KMS encryption, IAM roles
- **Monitoring Layer**: CloudWatch dashboards, alarms, and log aggregation

## Environments

Three environments are supported with different configurations:

- **Development**: Cost-optimized for development and testing
- **Staging**: Production-like for integration testing
- **Production**: High-availability with Multi-AZ deployments

## Prerequisites

- Node.js 20+ and npm
- AWS CLI v2 configured with appropriate credentials
- AWS CDK v2.110+ (`npm install -g aws-cdk`)
- TypeScript 5.3+

## Quick Start

### Install Dependencies

```bash
npm install
```

### Build TypeScript

```bash
npm run build
```

### Synthesize CloudFormation Templates

```bash
# Development
npm run synth:dev

# Staging
npm run synth:staging

# Production
npm run synth:prod
```

### Deploy Infrastructure

```bash
# Deploy all stacks to development
npm run deploy:dev

# Deploy all stacks to staging
npm run deploy:staging

# Deploy all stacks to production (requires approval)
npm run deploy:prod
```

### View Infrastructure Differences

```bash
# Compare current deployment with code
npm run diff:dev
npm run diff:staging
npm run diff:prod
```

## Stack Architecture

### 1. Network Stack

Creates isolated VPC with:
- Public subnets for load balancers
- Private subnets with NAT for application tier
- Isolated subnets for databases
- Security groups for each tier

**Resources**: VPC, Subnets, NAT Gateways, Security Groups

### 2. Secrets Stack

Manages sensitive credentials with:
- Database credentials with automatic rotation (production)
- Redis authentication tokens
- JWT signing keys
- KMS encryption for all secrets

**Resources**: Secrets Manager, KMS Keys, SSM Parameters

### 3. Database Stack

Provides data persistence with:
- RDS PostgreSQL with automated backups
- ElastiCache Redis for caching
- Multi-AZ deployment for production
- Automated snapshots and retention policies

**Resources**: RDS Instances, ElastiCache Clusters, DB Subnet Groups

### 4. Storage Stack

Content delivery infrastructure:
- S3 buckets for content, logs, and backups
- CloudFront CDN distribution
- Lifecycle policies for cost optimization
- Server-side encryption for all buckets

**Resources**: S3 Buckets, CloudFront Distributions

### 5. Monitoring Stack

Observability and alerting:
- CloudWatch dashboards per environment
- Alarms for CPU, errors, and latency
- Centralized log aggregation
- SNS notifications for production alerts

**Resources**: CloudWatch Dashboards, Alarms, Log Groups, SNS Topics

## Environment Configurations

### Development
- **VPC CIDR**: 10.0.0.0/16
- **RDS**: t3.micro, Single-AZ
- **Redis**: t3.micro, 1 node
- **NAT Gateways**: 1 (cost optimization)
- **Backup Retention**: 7 days
- **Log Retention**: 30 days

### Staging
- **VPC CIDR**: 10.1.0.0/16
- **RDS**: t3.small, Multi-AZ
- **Redis**: t3.small, 2 nodes with failover
- **NAT Gateways**: 2 (high availability)
- **Backup Retention**: 14 days
- **Log Retention**: 90 days

### Production
- **VPC CIDR**: 10.2.0.0/16
- **RDS**: t3.medium, Multi-AZ
- **Redis**: t3.medium, 3 nodes with failover
- **NAT Gateways**: 3 (across 3 AZs)
- **Backup Retention**: 30 days
- **Log Retention**: 180 days
- **Deletion Protection**: Enabled
- **Secret Rotation**: Enabled (30 days)

## Testing

Run the comprehensive test suite:

```bash
# Run all tests
npm test

# Run specific stack tests
npm test -- network-stack.test.ts
npm test -- database-stack.test.ts
npm test -- storage-stack.test.ts
npm test -- secrets-stack.test.ts
npm test -- monitoring-stack.test.ts

# Watch mode
npm run test:watch
```

## Stack Outputs

After deployment, each stack exports important values:

### Network Stack
- VPC ID
- Security Group IDs
- Subnet IDs

### Secrets Stack
- Database Secret ARN
- Redis Secret ARN
- JWT Secret ARN
- KMS Key ID

### Database Stack
- RDS Endpoint and Port
- Redis Endpoint

### Storage Stack
- Content Bucket Name
- CloudFront Distribution Domain
- Logs Bucket Name

### Monitoring Stack
- Dashboard URL
- Application Log Group
- Alarm Topic ARN (production)

## Security

- All database credentials stored in Secrets Manager with KMS encryption
- Secrets rotation enabled for production (30-day cycle)
- VPC isolation with security groups enforcing least-privilege access
- S3 buckets encrypted at rest with AES256
- CloudFront requires HTTPS
- IAM roles follow least-privilege principle

## Cost Optimization

Development environment optimized for cost:
- Single NAT Gateway instead of per-AZ
- Smaller instance types
- Shorter backup retention
- No deletion protection
- Single-AZ databases

Production environment prioritizes availability and durability.

## Cleanup

To destroy all infrastructure (development/staging only):

```bash
# CAUTION: This will delete all resources
cdk destroy --all --context environment=development
cdk destroy --all --context environment=staging

# Production has deletion protection enabled
# Must disable protection first before destroying
```

## Troubleshooting

### CDK Bootstrap Required

If you see "This stack uses assets, so the toolkit stack must be deployed":

```bash
cdk bootstrap aws://ACCOUNT-ID/eu-central-1
```

### Insufficient Permissions

Ensure your AWS credentials have permissions for:
- VPC and EC2
- RDS and ElastiCache
- S3 and CloudFront
- Secrets Manager and KMS
- CloudWatch and SNS
- IAM role creation

### Stack Dependencies

Stacks must be deployed in order:
1. Network
2. Secrets
3. Database (depends on Network + Secrets)
4. Storage (independent)
5. Monitoring (independent)

CDK automatically handles dependencies.

## Contributing

Follow the TDD workflow:
1. Write tests first (RED phase)
2. Implement infrastructure (GREEN phase)
3. Refactor for optimization (REFACTOR phase)

All infrastructure changes must:
- Include comprehensive tests
- Pass `npm test` with >90% coverage
- Successfully synthesize with `npm run synth:dev`
- Follow AWS CDK best practices

## Related Documentation

- [BATbern Architecture Documentation](../docs/architecture/)
- [AWS CDK Documentation](https://docs.aws.amazon.com/cdk/)
- [Story 1.3: Multi-Environment CDK Infrastructure](../docs/stories/1.3.multi-environment-cdk-infrastructure.md)
