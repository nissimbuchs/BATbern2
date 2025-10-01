import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';
import { EnvironmentConfig } from './environment-config';

/**
 * Staging environment configuration
 */
export const stagingConfig: EnvironmentConfig = {
  envName: 'staging',
  region: 'eu-central-1',
  account: process.env.CDK_DEFAULT_ACCOUNT || '',
  vpc: {
    cidr: '10.1.0.0/16',
    maxAzs: 2,
    natGateways: 2, // High availability for staging
  },
  rds: {
    instanceClass: ec2.InstanceClass.T3,
    instanceSize: ec2.InstanceSize.SMALL,
    multiAz: true, // Multi-AZ for staging reliability
    backupRetention: cdk.Duration.days(14),
    allocatedStorage: 50,
    deletionProtection: true,
  },
  elasticache: {
    nodeType: 'cache.t3.small',
    numNodes: 2,
    automaticFailoverEnabled: true,
    snapshotRetentionLimit: 5,
  },
  ecs: {
    desiredCount: 2,
    cpu: 512,
    memory: 1024,
    autoScaling: {
      minCapacity: 2,
      maxCapacity: 4,
      targetCpuUtilization: 60,
    },
  },
  tags: {
    Environment: 'staging',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Engineering',
  },
};
