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
    // Centralized DNS in management account (510187933511)
    hostedZoneId: 'Z04921951F6B818JF0POD',
    frontendCertificateArn: 'arn:aws:acm:us-east-1:510187933511:certificate/3644eb35-b649-45e7-a198-05b01611ff09',
    apiCertificateArn: 'arn:aws:acm:eu-central-1:510187933511:certificate/1033ffde-72a7-46c3-94cc-d68124fb1588',
  },
  tags: {
    Environment: 'production',
    Project: 'BATbern',
    ManagedBy: 'CDK',
    CostCenter: 'Operations',
    Compliance: 'GDPR',
  },
};
