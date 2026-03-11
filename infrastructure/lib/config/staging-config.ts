import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';
import { EnvironmentConfig } from './environment-config';

/**
 * Staging account configuration — serves as the PRODUCTION environment.
 *
 * The staging AWS account (188701360969) has been promoted to production.
 * envName remains 'staging' to preserve existing CloudFormation stack names
 * (BATbern-staging-*) and avoid resource recreation. The isProduction flag
 * controls production-grade behaviour (domains, email, CORS, retention, etc.).
 */
export const stagingConfig: EnvironmentConfig = {
  envName: 'staging',
  isProduction: true,
  region: 'eu-central-1',
  account: '188701360969', // BATbern Staging account (now serving production)
  vpc: {
    cidr: '10.1.0.0/16',
    maxAzs: 2, // Minimum 2 AZs required for RDS subnet groups
    natGateways: 1, // Single NAT for Lambda internet access (Cognito, etc) - VPC endpoints reduce data transfer
  },
  rds: {
    instanceClass: ec2.InstanceClass.T4G, // ARM-based for better price/performance
    instanceSize: ec2.InstanceSize.MICRO, // Sufficient for ~300 users, 3 events/year
    multiAz: false, // Single-AZ for cost savings (low traffic use case)
    backupRetention: cdk.Duration.days(14), // 2-week grace window (production)
    allocatedStorage: 20, // Sufficient for current data volume
    deletionProtection: true, // Production data protection
  },
  elasticache: {
    // Redis disabled for cost optimization
    nodeType: 'cache.t3.micro',
    numNodes: 0, // Redis disabled
    automaticFailoverEnabled: false,
    snapshotRetentionLimit: 0,
  },
  ecs: {
    desiredCount: 1,
    cpu: 256,
    memory: 512,
    autoScaling: {
      minCapacity: 1,
      maxCapacity: 2,
      targetCpuUtilization: 70,
    },
  },
  domain: {
    frontendDomain: 'www.batbern.ch',
    apiDomain: 'api.batbern.ch',
    cdnDomain: 'cdn.batbern.ch',
    zoneName: 'batbern.ch',
    hostedZoneId: 'Z08825557YYLWVHISLPY', // New batbern.ch zone in staging account
    frontendCertificateArn: 'arn:aws:acm:us-east-1:188701360969:certificate/ef93631c-8241-4a2c-b84b-74615d5394cd', // Pre-created for www.batbern.ch
    cdnCertificateArn: 'arn:aws:acm:us-east-1:188701360969:certificate/4aece095-d46f-4266-ac3a-c25736d59c08', // Pre-created for cdn.batbern.ch
    apiCertificateArn: 'arn:aws:acm:eu-central-1:188701360969:certificate/d0f9a634-86a9-4bf4-b8b8-d2e9503e3cec', // Pre-created for api.batbern.ch
  },
  tags: {
    Environment: 'production',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Operations',
    Compliance: 'GDPR',
  },
};
