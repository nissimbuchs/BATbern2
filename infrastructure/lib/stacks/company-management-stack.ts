import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as events from 'aws-cdk-lib/aws-events';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';

export interface CompanyManagementStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  cacheEndpoint?: string;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  contentBucket?: s3.IBucket;
  eventBus?: events.IEventBus;
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

    // Build additional environment variables specific to this service
    const additionalEnvironment: Record<string, string> = {
      // Service scope - consolidated master data
      SERVICE_SCOPE: 'companies-and-users',
      HANDLES_COMPANIES: 'true',
      HANDLES_USERS: 'true',
      // S3 bucket for company logos and user profile pictures
      ...(props.contentBucket && {
        S3_CONTENT_BUCKET_NAME: props.contentBucket.bucketName,
      }),
      // EventBridge for domain events
      ...(props.eventBus && {
        EVENT_BUS_NAME: props.eventBus.eventBusName,
      }),
    };

    // Create domain service using reusable helper function
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'CompanyUserManagement-Service',
        routePattern: '/api/v1/companies,/api/v1/users',
        cpu: 256,
        memoryLimitMiB: 512,
        additionalEnvironment,
      },
      cluster: props.cluster,
      vpc: props.vpc,
      databaseSecurityGroup: props.databaseSecurityGroup,
      databaseEndpoint: props.databaseEndpoint,
      databaseSecret: props.databaseSecret,
      userPool: props.userPool,
      userPoolClient: props.userPoolClient,
    });

    this.service = domainService.service;
    this.serviceUrl = domainService.serviceUrl;

    // Grant S3 permissions for company logos and user profile pictures
    if (props.contentBucket) {
      props.contentBucket.grantReadWrite(this.service.taskDefinition.taskRole);
      // Grant permissions for presigned URL generation
      this.service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          's3:PutObject',
          's3:GetObject',
          's3:DeleteObject',
        ],
        resources: [`${props.contentBucket.bucketArn}/*`],
      }));
    }

    // Grant EventBridge permissions for domain events
    if (props.eventBus) {
      this.service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'events:PutEvents',
        ],
        resources: [props.eventBus.eventBusArn],
      }));
    }

    // Apply additional tags specific to this service
    cdk.Tags.of(this).add('Consolidation', 'Companies+Users');

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.serviceUrl,
      description: 'Company & User Management Service internal URL (consolidated)',
      exportName: `${envName}-company-user-management-url`,
    });
  }
}
