import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';

export interface EventManagementStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  contentBucket?: s3.IBucket;
  cloudFrontDistribution?: cloudfront.IDistribution;
}

/**
 * Event Management Service Stack
 *
 * Domain microservice for managing events, sessions, and schedules.
 * Handles /api/v1/events routes.
 */
export class EventManagementStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly serviceUrl: string;

  constructor(scope: Construct, id: string, props: EventManagementStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const serviceName = 'event-management';

    // Create domain service using reusable helper function
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'EventManagement-Service',
        routePattern: '/api/v1/events',
        cpu: 512,
        memoryLimitMiB: 1024,
        additionalEnvironment: {
          JPA_DDL_AUTO: 'none', // Let Flyway handle all schema management
          // S3 configuration for event theme images (Story 2.5.3a)
          ...(props.contentBucket && {
            AWS_S3_BUCKET_NAME: props.contentBucket.bucketName,
          }),
          ...(props.cloudFrontDistribution && {
            CLOUDFRONT_DOMAIN: `https://${props.cloudFrontDistribution.distributionDomainName}`,
          }),
        },
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

    // Grant S3 permissions for event theme images (Story 2.5.3a)
    if (props.contentBucket) {
      props.contentBucket.grantReadWrite(this.service.taskDefinition.taskRole);
    }

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.serviceUrl,
      description: 'Event Management Service internal URL',
      exportName: `${envName}-event-management-url`,
    });
  }
}
