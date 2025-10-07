import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';
import { EnvironmentConfig } from './environment-config';

/**
 * Staging environment configuration
 */
export const stagingConfig: EnvironmentConfig = {
  envName: 'staging',
  region: 'eu-central-1',
  account: '188701360969', // BATbern Staging account
  vpc: {
    cidr: '10.1.0.0/16',
    maxAzs: 1, // Single AZ for cost optimization (test environment)
    natGateways: 1, // Single NAT Gateway for cost savings
  },
  rds: {
    instanceClass: ec2.InstanceClass.T4G, // ARM-based for better price/performance
    instanceSize: ec2.InstanceSize.MICRO, // Sufficient for testing
    multiAz: false, // Single-AZ for cost savings (test environment)
    backupRetention: cdk.Duration.days(7), // Reduced backup retention
    allocatedStorage: 20, // Reduced storage (test environment)
    deletionProtection: false, // Allow deletion in staging
  },
  elasticache: {
    // Redis disabled for cost optimization (test environment)
    nodeType: 'cache.t3.micro',
    numNodes: 0, // Redis disabled
    automaticFailoverEnabled: false,
    snapshotRetentionLimit: 0,
  },
  ecs: {
    // Minimal ECS config (will be replaced by App Runner)
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
    frontendDomain: 'staging.batbern.ch',
    apiDomain: 'api.staging.batbern.ch',
    // Staging account owns staging.batbern.ch hosted zone (delegated subdomain)
    hostedZoneId: 'Z00395322M4O1QCL0M7UA',
    // Certificates created via DNS stack with automatic validation
    // frontendCertificateArn: undefined, // Created by DNS stack
    // apiCertificateArn: undefined, // Created by Network stack
  },
  tags: {
    Environment: 'staging',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Engineering',
  },
};
