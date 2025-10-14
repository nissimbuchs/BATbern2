import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';

export interface CompanyManagementStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  cacheEndpoint?: string;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
}

/**
 * Company & User Management Service Stack
 *
 * Consolidated master data microservice for managing:
 * - Company profiles, employees, and corporate data (/api/v1/companies)
 * - User profiles, preferences, settings, and roles (/api/v1/users)
 * - User-company relationships
 * - AWS Cognito integration
 * - File storage (logos, profile pictures) with S3 presigned URLs
 *
 * This service consolidates Company Management and User Management into a single
 * service because both are tightly coupled master data concerns used by all domain services.
 */
export class CompanyManagementStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly serviceUrl: string;

  constructor(scope: Construct, id: string, props: CompanyManagementStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const serviceName = 'company-user-management';

    // Create domain service using reusable helper function with additional environment variables
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'CompanyUserManagement-Service',
        routePattern: '/api/v1/companies,/api/v1/users',
        cpu: 256,
        memoryLimitMiB: 512,
        additionalEnvironment: {
          SERVICE_SCOPE: 'companies-and-users',
          HANDLES_COMPANIES: 'true',
          HANDLES_USERS: 'true',
        },
      },
      cluster: props.cluster,
      vpc: props.vpc,
      databaseEndpoint: props.databaseEndpoint,
      databaseSecret: props.databaseSecret,
      cacheEndpoint: props.cacheEndpoint,
      userPool: props.userPool,
      userPoolClient: props.userPoolClient,
    });

    this.service = domainService.service;
    this.serviceUrl = domainService.serviceUrl;

    // Apply additional tags for consolidated service
    cdk.Tags.of(this).add('Consolidation', 'Companies+Users');

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.serviceUrl,
      description: 'Company & User Management Service internal URL (consolidated)',
      exportName: `${envName}-company-user-management-url`,
    });
  }
}
