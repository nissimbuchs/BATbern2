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
    maxAzs: 1, // Single AZ for development (services run locally)
    natGateways: 1, // Single NAT Gateway for cost optimization
  },
  rds: {
    instanceClass: ec2.InstanceClass.T4G, // ARM-based for better price/performance
    instanceSize: ec2.InstanceSize.MICRO,
    multiAz: false, // Single AZ for development
    backupRetention: cdk.Duration.days(7),
    allocatedStorage: 20,
    deletionProtection: false, // Allow deletion in development
  },
  elasticache: {
    // Redis disabled for development (services run locally in Docker Compose)
    nodeType: 'cache.t3.micro',
    numNodes: 0, // Disabled - local Docker services don't need AWS Redis
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
  tags: {
    Environment: 'development',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Engineering',
  },
};
