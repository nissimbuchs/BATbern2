# BATbern AWS Account Setup Guide

## Overview
This guide covers the **manual organizational setup** for AWS accounts. Most infrastructure provisioning is automated via CDK - this guide only covers what must be done manually before CDK deployment.

## Phase 1: Management Account Setup

### Prerequisites
- Valid email address for BATbern organization
- Business phone number
- Credit/debit card for billing
- Organization details (name, address)
- Domain `batbern.ch` registered and transferred to AWS Route53

### Step 1: Create AWS Management Account
1. **Go to**: [aws.amazon.com](https://aws.amazon.com)
2. **Click**: "Create an AWS Account"
3. **Enter Account Details**:
   - **Email**: `awsroot@batbern.ch`
   - **Password**: Use strong password (store in password manager)
   - **AWS Account Name**: `BATbern Event Platform`
4. **Contact Information**:
   - **Account Type**: Business
   - **Company Name**: BATbern
   - **Phone Number**: Your business phone
   - **Address**: Your organization's address
5. **Payment Information**: Add credit/debit card
6. **Identity Verification**: Complete phone verification
7. **Support Plan**: Start with Basic (free)

### Step 2: Secure Root Account
1. **Enable MFA** on root account immediately
2. **Create strong password** and store securely
3. **Note down Account ID** (12-digit number)
4. **Verify email** address

## Phase 2: AWS Organizations Setup

### Step 3: Create AWS Organization
1. **Navigate to**: AWS Organizations console
2. **Click**: "Create organization"
3. **Choose**: "All features" (recommended)
4. **Confirm**: Organization creation

### Step 4: Create Organizational Units (OUs)
```
Root
├── Security
├── Infrastructure
├── Workloads
│   ├── Development
│   ├── Staging
│   └── Production
└── Suspended
```

Create OUs in this order:
1. Security
2. Infrastructure
3. Workloads
4. Development (under Workloads)
5. Staging (under Workloads)
6. Production (under Workloads)
7. Suspended

### Step 5: Create Member Accounts
Create accounts for each environment:

#### Development Account
- **Email**: `admin+aws-dev@berner-architekten-treffen.ch`
- **Account Name**: `BATbern Development`
- **OU**: Development

#### Staging Account
- **Email**: `admin+aws-staging@berner-architekten-treffen.ch`
- **Account Name**: `BATbern Staging`
- **OU**: Staging

#### Production Account
- **Email**: `admin+aws-prod@berner-architekten-treffen.ch`
- **Account Name**: `BATbern Production`
- **OU**: Production

#### Security Account
- **Email**: `admin+aws-security@berner-architekten-treffen.ch`
- **Account Name**: `BATbern Security`
- **OU**: Security

## Phase 3: Security Configuration

### Step 6: Enable AWS CloudTrail
1. **Create Organization Trail**:
   - Trail name: `BATbern-Organization-Trail`
   - Apply to all accounts: Yes
   - Log file validation: Enable
   - S3 bucket: Create new bucket `batbern-cloudtrail-logs`

### Step 7: Configure Service Control Policies (SCPs)
Create and attach these SCPs:

#### 1. Deny Root User Access (except for specific actions)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Deny",
      "Principal": {
        "AWS": "*"
      },
      "Action": "*",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws:PrincipalType": "Root"
        },
        "StringNotEquals": {
          "aws:RequestedRegion": [
            "eu-central-1",
            "eu-west-1"
          ]
        }
      }
    }
  ]
}
```

#### 2. Enforce MFA for Console Access
#### 3. Restrict to EU Regions Only
#### 4. Deny High-Cost Services

### Step 8: Set Up Billing Alerts
1. **Enable**: Billing preferences
2. **Create**: Budget for monthly spend limit
3. **Set**: Alerts at 50%, 80%, 100% of budget

## Phase 4: Route53 Domain Setup

### Step 9: Configure batbern.ch Domain in Route53
1. **Navigate to**: Route53 console
2. **Create Hosted Zone**:
   - **Domain Name**: `batbern.ch`
   - **Type**: Public hosted zone
   - **Comment**: `BATbern Event Management Platform`
3. **Note Name Servers**: Copy the 4 NS records provided
4. **Update Domain Registrar**: Point domain to AWS name servers
5. **Verify Domain**: Wait for DNS propagation (up to 48 hours)

### Step 10: Create SSL Certificate (ACM)
1. **Navigate to**: Certificate Manager (us-east-1 region)
2. **Request Certificate**:
   - **Domain**: `*.batbern.ch`
   - **Additional domains**: `batbern.ch`
   - **Validation**: DNS validation
3. **Add CNAME records** to Route53 for validation
4. **Wait for validation** (usually 5-10 minutes)

## Phase 5: IAM Setup for Claude

### Step 11: Create IAM User for Claude (in Management Account)
1. **Navigate to**: IAM Console
2. **Create User**:
   - **Username**: `claude-automation`
   - **Access type**: Programmatic access only
   - **Permissions**: Custom policy (see below)

### Step 10: Claude IAM Policy
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:CreateBucket",
        "s3:DeleteBucket",
        "s3:PutBucketPolicy",
        "s3:PutBucketWebsite",
        "s3:PutBucketVersioning"
      ],
      "Resource": [
        "arn:aws:s3:::batbern-*",
        "arn:aws:s3:::batbern-*/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "cloudformation:CreateStack",
        "cloudformation:UpdateStack",
        "cloudformation:DeleteStack",
        "cloudformation:DescribeStacks",
        "cloudformation:DescribeStackEvents",
        "cloudformation:ListStacks"
      ],
      "Resource": "arn:aws:cloudformation:*:*:stack/batbern-*/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:PassRole"
      ],
      "Resource": "arn:aws:iam::*:role/batbern-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances",
        "ec2:DescribeImages",
        "ec2:DescribeVpcs",
        "ec2:DescribeSubnets",
        "ec2:DescribeSecurityGroups"
      ],
      "Resource": "*"
    }
  ]
}
```

## Phase 5: Cross-Account Access Setup

### Step 11: Create Cross-Account Roles
For each member account, create an assumable role:

#### Development Account Role
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::MANAGEMENT-ACCOUNT-ID:user/claude-automation"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

## CDK-Managed Infrastructure

The following will be automatically provisioned by CDK:
- S3 buckets for all environments
- Route53 DNS records and health checks
- VPC networking and security groups
- ECS clusters and services
- CloudWatch logging and monitoring
- AWS Config compliance rules
- Cross-account IAM roles

## Security Checklist

- [ ] Root account MFA enabled
- [ ] IAM password policy configured
- [ ] CloudTrail logging enabled
- [ ] VPC Flow Logs enabled
- [ ] AWS Config enabled
- [ ] GuardDuty enabled
- [ ] Security Hub enabled
- [ ] Billing alerts configured
- [ ] SCPs attached to OUs
- [ ] Cross-account roles configured

## Next Steps

1. **Complete manual account setup** following this guide
2. **Save credentials** securely for Claude user
3. **Test access** from each account
4. **Configure CI/CD** pipelines
5. **Set up monitoring** and alerting

## Support Resources

- **AWS Documentation**: [docs.aws.amazon.com](https://docs.aws.amazon.com)
- **AWS Support**: Create support case if needed
- **Cost Calculator**: [calculator.aws](https://calculator.aws)

---

**Estimated Setup Time**: 2-4 hours
**Estimated Monthly Cost**: $20-50 for basic setup
**Security Rating**: Enterprise-ready with proper configurations