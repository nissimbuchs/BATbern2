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
    maxAzs: 3, // High availability across 3 AZs
    natGateways: 3,
  },
  rds: {
    instanceClass: ec2.InstanceClass.T3,
    instanceSize: ec2.InstanceSize.MEDIUM,
    multiAz: true, // Multi-AZ for production
    backupRetention: cdk.Duration.days(30),
    allocatedStorage: 100,
    deletionProtection: true,
  },
  elasticache: {
    nodeType: 'cache.t3.medium',
    numNodes: 3,
    automaticFailoverEnabled: true,
    snapshotRetentionLimit: 14,
  },
  ecs: {
    desiredCount: 3,
    cpu: 1024,
    memory: 2048,
    autoScaling: {
      minCapacity: 3,
      maxCapacity: 10,
      targetCpuUtilization: 50,
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
