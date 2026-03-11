import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';
import { EnvironmentConfig } from './environment-config';

/**
 * @deprecated Production account (422940799530) is decommissioned as of March 2026.
 * Production traffic is now served by the staging account (188701360969) via staging-config.ts
 * with `isProduction: true`. This file is kept for reference only and is not used in deployments.
 */
export const prodConfig: EnvironmentConfig = {
  envName: 'production',
  region: 'eu-central-1',
  account: '422940799530', // BATbern Production account
  vpc: {
    cidr: '10.2.0.0/16',
    maxAzs: 2, // Minimum 2 AZs required for RDS subnet groups
    natGateways: 1, // Single NAT Gateway for cost savings
  },
  rds: {
    instanceClass: ec2.InstanceClass.T4G, // ARM-based for better price/performance
    instanceSize: ec2.InstanceSize.MICRO, // Sufficient for 1000 users
    multiAz: false, // Single-AZ for cost savings (low traffic use case)
    backupRetention: cdk.Duration.days(14), // 2-week grace window (staging is 7 days)
    allocatedStorage: 20, // Same as staging — 3 events/year, ~300 users
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
    cdnDomain: 'cdn.batbern.ch', // CloudFront CDN for static assets
    zoneName: 'batbern.ch', // Root domain zone
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
