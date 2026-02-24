import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';
import { EcsServiceAlarms } from '../constructs/ecs-service-alarms';

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
  alarmTopic?: sns.ITopic;
}

/**
 * Event Management Service Stack
 *
 * Domain microservice for managing events, sessions, and schedules.
 * Handles /api/v1/events routes.
 * Uses Service Connect for direct service-to-service communication (no ALB).
 */
export class EventManagementStack extends cdk.Stack {
  public readonly service: ecs.FargateService;

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
        cpu: 256,
        memoryLimitMiB: 2048, // Increased from 1024 MB: JVM non-heap was consuming headroom, memory alarm firing (2026-02-24)
        minCapacity: 2, // Hard floor: at least 2 tasks always running (EMS is the most critical service)
        maxCapacity: 6, // Scale up to 2x baseline under load
        additionalEnvironment: {
          JPA_DDL_AUTO: 'none', // Let Flyway handle all schema management
          // S3 configuration for event theme images (Story 2.5.3a)
          ...(props.contentBucket && {
            AWS_S3_BUCKET_NAME: props.contentBucket.bucketName,
          }),
          ...(props.cloudFrontDistribution && {
            CLOUDFRONT_DOMAIN: `https://${props.cloudFrontDistribution.distributionDomainName}`,
            CLOUDFRONT_DISTRIBUTION_ID: props.cloudFrontDistribution.distributionId,
          }),
          // Service Connect URL for company-user-management service (ADR-004)
          // Uses DNS-based service discovery: http://<service-name>:8080
          COMPANY_USER_MANAGEMENT_SERVICE_URL: 'http://company-user-management:8080',
          // Story 4.1.5: Base URL for email confirmation links
          // Must match the frontend domain for correct email link generation
          ...(props.config.domain && {
            APP_BASE_URL: `https://${props.config.domain.frontendDomain}`,
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

    // Override desiredCount for Event Management specifically (3 tasks for HA + load capacity)
    // 2048 MiB / 256 CPU per task; auto-scaling floor is 2, ceiling is 6
    const cfnService = this.service.node.defaultChild as ecs.CfnService;
    cfnService.addPropertyOverride('DesiredCount', 3);

    // Platform Stability Improvements (Phase 3): Add ECS Service Alarms
    if (props.alarmTopic) {
      new EcsServiceAlarms(this, 'ServiceAlarms', {
        environment: envName,
        clusterName: props.cluster.clusterName,
        serviceName: this.service.serviceName,
        serviceDisplayName: 'EventManagement',
        alarmTopic: props.alarmTopic,
        thresholds: {
          memoryUtilization: 85, // JVM heap capped via MaxRAMPercentage; 85% gives headroom for non-heap
          oomKillCount: envName === 'production' ? 1 : 3,
          taskFailureCount: envName === 'production' ? 2 : 5,
          eventBridgePublishingFailures: envName === 'production' ? 5 : 10,
        },
      });
    }

    // Grant S3 permissions for event theme images (Story 2.5.3a)
    if (props.contentBucket) {
      props.contentBucket.grantReadWrite(this.service.taskDefinition.taskRole);
    }

    // Grant SES permissions for sending registration confirmation emails (Story 4.1.5)
    // Note: In SES sandbox mode, permissions are required for BOTH sender (FROM) and recipient (TO) identities
    // Using wildcard (*) for recipient identities to support any verified email in sandbox mode
    // In production (out of sandbox), only FROM identity permissions are needed
    this.service.taskDefinition.taskRole.addToPrincipalPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['ses:SendEmail', 'ses:SendRawEmail'],
        resources: [
          // FROM identity - batbern.ch domain (verified domain)
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:identity/batbern.ch`,
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:identity/*@batbern.ch`,
          // TO identities - all verified emails (required for sandbox mode)
          // This allows sending to any verified recipient in sandbox mode
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:identity/*`,
        ],
      })
    );

    // Grant CloudFront cache invalidation permissions for publishing engine (Story 5.7 - BAT-11)
    if (props.cloudFrontDistribution) {
      this.service.taskDefinition.taskRole.addToPrincipalPolicy(
        new iam.PolicyStatement({
          effect: iam.Effect.ALLOW,
          actions: [
            'cloudfront:CreateInvalidation',
            'cloudfront:GetInvalidation',
            'cloudfront:ListInvalidations',
          ],
          resources: [
            `arn:aws:cloudfront::${cdk.Stack.of(this).account}:distribution/${
              props.cloudFrontDistribution.distributionId
            }`,
          ],
        })
      );
    }

    // Grant EventBridge permissions for auto-publish scheduling (Story 5.7 - BAT-11)
    this.service.taskDefinition.taskRole.addToPrincipalPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'events:PutRule',
          'events:PutTargets',
          'events:DeleteRule',
          'events:RemoveTargets',
          'events:DescribeRule',
          'events:ListTargetsByRule',
        ],
        resources: [
          `arn:aws:events:${props.config.region}:${cdk.Stack.of(this).account}:rule/batbern-auto-publish-*`,
        ],
      })
    );
  }
}
