import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';

/**
 * Environment configuration interface for BATbern platform infrastructure
 */
export interface EnvironmentConfig {
  envName: string;
  region: string;
  account: string;
  vpc: VpcConfig;
  rds: RdsConfig;
  elasticache: ElastiCacheConfig;
  ecs: EcsConfig;
  tags: { [key: string]: string };
}

export interface VpcConfig {
  cidr: string;
  maxAzs: number;
  natGateways: number;
}

export interface RdsConfig {
  instanceClass: ec2.InstanceClass;
  instanceSize: ec2.InstanceSize;
  multiAz: boolean;
  backupRetention: cdk.Duration;
  allocatedStorage: number;
  deletionProtection: boolean;
}

export interface ElastiCacheConfig {
  nodeType: string;
  numNodes: number;
  automaticFailoverEnabled: boolean;
  snapshotRetentionLimit: number;
}

export interface EcsConfig {
  desiredCount: number;
  cpu: number;
  memory: number;
  autoScaling: AutoScalingConfig;
}

export interface AutoScalingConfig {
  minCapacity: number;
  maxCapacity: number;
  targetCpuUtilization: number;
}
