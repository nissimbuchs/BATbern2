import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as cdk from 'aws-cdk-lib';

/**
 * Deployment mode for different environments
 * - LOCAL: Development runs API Gateway and Frontend in Docker, uses AWS for infrastructure (RDS, Cognito)
 * - CLOUD: Staging/Production runs everything in AWS
 */
export enum DeploymentMode {
  LOCAL = 'LOCAL',   // Development: Docker + AWS infrastructure
  CLOUD = 'CLOUD',   // Staging/Production: Full AWS
}

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
  domain?: DomainConfig;
  tags: { [key: string]: string };
}

/**
 * Helper functions for environment-based deployment decisions
 */
export class EnvironmentHelper {
  /**
   * Get deployment mode for an environment
   */
  static getDeploymentMode(envName: string): DeploymentMode {
    return envName === 'development' ? DeploymentMode.LOCAL : DeploymentMode.CLOUD;
  }

  /**
   * Check if web infrastructure should be deployed (DNS, API Gateway, Frontend)
   * Only deploy for staging/production, not for development (runs locally in Docker)
   */
  static shouldDeployWebInfrastructure(envName: string): boolean {
    return this.getDeploymentMode(envName) === DeploymentMode.CLOUD;
  }

  /**
   * Check if this is development environment (local Docker mode)
   */
  static isLocalDevelopment(envName: string): boolean {
    return envName === 'development';
  }

  /**
   * Check if this is cloud environment (staging/production)
   */
  static isCloudEnvironment(envName: string): boolean {
    return envName === 'staging' || envName === 'production';
  }
}

export interface DomainConfig {
  frontendDomain: string;
  apiDomain: string;
  hostedZoneId?: string;
  frontendCertificateArn?: string; // us-east-1 for CloudFront
  apiCertificateArn?: string; // eu-central-1 for API Gateway
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
