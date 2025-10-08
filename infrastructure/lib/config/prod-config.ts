import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';
import { EnvironmentConfig } from './environment-config';

/**
 * Production environment configuration
 */
export const prodConfig: EnvironmentConfig = {
  envName: 'production',
  region: 'eu-central-1',
  account: '422940799530', // BATbern Production account
  vpc: {
    cidr: '10.2.0.0/16',
    maxAzs: 1, // Single AZ for cost optimization (low traffic: 1000 users/month)
    natGateways: 1, // Single NAT Gateway for cost savings
  },
  rds: {
    instanceClass: ec2.InstanceClass.T4G, // ARM-based for better price/performance
    instanceSize: ec2.InstanceSize.MICRO, // Sufficient for 1000 users
    multiAz: false, // Single-AZ for cost savings (low traffic use case)
    backupRetention: cdk.Duration.days(30),
    allocatedStorage: 50, // Reduced storage (was 100GB)
    deletionProtection: true,
  },
  elasticache: {
    // Redis cluster removed for cost optimization
    // Application will use in-memory caching instead
    nodeType: 'cache.t3.micro', // Placeholder (not deployed)
    numNodes: 0, // Disabled
    automaticFailoverEnabled: false,
    snapshotRetentionLimit: 0,
  },
  ecs: {
    // Legacy ECS config - replaced by App Runner
    desiredCount: 1,
    cpu: 512,
    memory: 1024,
    autoScaling: {
      minCapacity: 1,
      maxCapacity: 3,
      targetCpuUtilization: 70,
    },
  },
  domain: {
    frontendDomain: 'www.batbern.ch',
    apiDomain: 'api.batbern.ch',
    // Production account owns batbern.ch hosted zone
    hostedZoneId: 'Z003987919RPX23XXEU48',
    // Certificates created via DNS stack with automatic validation
    // frontendCertificateArn: undefined, // Created by DNS stack
    // apiCertificateArn: undefined, // Created by Network stack
  },
  tags: {
    Environment: 'production',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Operations',
    Compliance: 'GDPR',
  },
};
