import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';
import { EnvironmentConfig } from './environment-config';

/**
 * Development environment configuration
 */
export const devConfig: EnvironmentConfig = {
  envName: 'development',
  region: 'eu-central-1',
  account: '954163570305', // BATbern Development account
  vpc: {
    cidr: '10.0.0.0/16',
    maxAzs: 2,
    natGateways: 1, // Cost optimization for dev
  },
  rds: {
    instanceClass: ec2.InstanceClass.T3,
    instanceSize: ec2.InstanceSize.MICRO,
    multiAz: false, // Single AZ for dev
    backupRetention: cdk.Duration.days(7),
    allocatedStorage: 20,
    deletionProtection: false,
  },
  elasticache: {
    nodeType: 'cache.t3.micro',
    numNodes: 1,
    automaticFailoverEnabled: false,
    snapshotRetentionLimit: 1,
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
  tags: {
    Environment: 'development',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Engineering',
  },
};
