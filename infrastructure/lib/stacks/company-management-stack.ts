import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as events from 'aws-cdk-lib/aws-events';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';
import { EcsServiceAlarms } from '../constructs/ecs-service-alarms';

export interface CompanyManagementStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  cacheEndpoint?: string;
  userPool: cognito.UserPool;
  userPoolClient: cognito.IUserPoolClient;
  contentBucket?: s3.IBucket;
  cloudFrontDistribution?: cloudfront.IDistribution;
  eventBus?: events.IEventBus;
  alarmTopic?: sns.ITopic;
  watchJwtSecret?: secretsmanager.ISecret;
  /**
   * Transactional SES Configuration Set name — shared EmailService default so the
   * additional-email verification mails are delivery-tracked (no suppression).
   * See spec-transactional-ses-config-set.md.
   */
  sesTransactionalConfigurationSetName?: string;
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
 * Uses Service Connect for direct service-to-service communication (no ALB).
 */
export class CompanyManagementStack extends cdk.Stack {
  public readonly service: ecs.FargateService;

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
      // CloudFront distribution for serving uploaded content via CDN
      // Use custom CDN domain if configured, otherwise fall back to CloudFront domain
      ...(props.cloudFrontDistribution && {
        CLOUDFRONT_DOMAIN: props.config.domain?.cdnDomain
          ? `https://${props.config.domain.cdnDomain}`
          : `https://${props.cloudFrontDistribution.distributionDomainName}`,
      }),
      // EventBridge for domain events
      ...(props.eventBus && {
        EVENT_BUS_NAME: props.eventBus.eventBusName,
      }),
      // Transactional SES config set → shared EmailService default (delivery tracking)
      ...(props.sesTransactionalConfigurationSetName && {
        BATBERN_SES_CONFIGURATION_SET_NAME: props.sesTransactionalConfigurationSetName,
      }),
      // Additional-email verification (v2): public base URL used to build the
      // {{baseUrl}}/verify-email?token= link in verification emails. Must match
      // the frontend domain so the link resolves to the SPA verify page.
      ...(props.config.domain && {
        APP_BASE_URL: `https://${props.config.domain.frontendDomain}`,
      }),
    };

    // Secrets from Secrets Manager
    const additionalSecrets: Record<string, ecs.Secret> = {};
    if (props.watchJwtSecret) {
      additionalSecrets.WATCH_JWT_SECRET = ecs.Secret.fromSecretsManager(props.watchJwtSecret);
      // JWT_SECRET provides a stable HMAC key for AdditionalEmailVerificationTokenService
      // (additional-email verification links). Without it the service generates a random
      // key on each start, invalidating all in-flight verification tokens whenever ECS
      // replaces a Fargate Spot task or a new deployment lands. Reuses the WATCH_JWT_SECRET
      // secret (same pattern as event-management-stack).
      additionalSecrets.JWT_SECRET = ecs.Secret.fromSecretsManager(props.watchJwtSecret);
    }

    // Create domain service using reusable helper function
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'CompanyUserManagement-Service',
        routePattern: '/api/v1/companies,/api/v1/users',
        cpu: 256,
        memoryLimitMiB: 1024, // Increased from 512 MB (Priority 4: ECS Right-Sizing - was at 93-99% utilization)
        healthCheckStartPeriodSeconds: 300, // DB + Flyway + JPA needs more startup time than 120s default
        additionalEnvironment,
        additionalSecrets,
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

    // Grant execution role read access to Watch JWT secret.
    // secretsKey uses trustAccountIdentities:true so grantRead() only adds an IAM
    // policy on this role — it does NOT modify the KMS key policy in SecretsStack,
    // avoiding the cyclic cross-stack dependency.
    if (props.watchJwtSecret) {
      props.watchJwtSecret.grantRead(domainService.service.taskDefinition.executionRole!);
    }

    // Platform Stability Improvements (Phase 3): Add ECS Service Alarms
    if (props.alarmTopic) {
      new EcsServiceAlarms(this, 'ServiceAlarms', {
        environment: envName,
        clusterName: props.cluster.clusterName,
        serviceName: this.service.serviceName,
        serviceDisplayName: 'CompanyManagement',
        alarmTopic: props.alarmTopic,
        thresholds: {
          memoryUtilization: 85, // JVM heap capped via MaxRAMPercentage; 85% gives headroom for non-heap
          oomKillCount: envName === 'production' ? 1 : 3,
          taskFailureCount: envName === 'production' ? 2 : 5,
          eventBridgePublishingFailures: envName === 'production' ? 5 : 10,
        },
      });
    }

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

    // Additional-email verification (v2): grant SES send permissions so the
    // shared-kernel EmailService can dispatch verification emails to additional
    // addresses. Mirrors the partner-coordination-stack SES grant (same
    // isProdTraffic domain selection + scoped identity ARNs).
    const isProdTraffic = props.config.isProduction ?? (envName === 'production');
    const sesFromDomain = isProdTraffic ? 'batbern.ch' : 'batbern.ch';
    this.service.taskDefinition.taskRole.addToPrincipalPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['ses:SendEmail', 'ses:SendRawEmail'],
        resources: [
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:identity/${sesFromDomain}`,
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:identity/*@${sesFromDomain}`,
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:identity/*`,
          // Configuration set — required when configurationSetName is attached to SendRawEmail
          `arn:aws:ses:${props.config.region}:${cdk.Stack.of(this).account}:configuration-set/batbern-${envName}-*`,
        ],
      }),
    );

    // Story 11.E.1 / AR30 / cherry-pick d5cf0fcc: Grant Cognito admin perms for speaker provisioning (Story 11.E.2).
    // Scope: this service's task role only. Resource: the BATbern User Pool ARN (no wildcard).
    // AdminAddUserToGroup is intentionally NOT granted (Resolved Q#1, PM 2026-05-17): roles live in
    // PostgreSQL user_roles per ADR-001; no Cognito groups exist; granting the permission would be a useless
    // least-privilege violation. ADR-009 §Decision 3, PRD AR30, and PRD NFR5 are updated in the same commit.
    // ListUsers + ResendConfirmationCode added for the daily unconfirmed-signup nudge
    // (CognitoConfirmationResendJob) and the nightly reconciliation's missing-user scan
    // (UserReconciliationService), which both page through the pool. ResendConfirmationCode is a
    // non-admin API but is still IAM-scoped to this pool; the SPA client has no secret so no
    // SecretHash is required.
    this.service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cognito-idp:AdminCreateUser',
        'cognito-idp:AdminSetUserPassword',
        'cognito-idp:AdminInitiateAuth',
        'cognito-idp:AdminGetUser',
        'cognito-idp:ListUsers',
        'cognito-idp:ResendConfirmationCode',
      ],
      resources: [props.userPool.userPoolArn],
    }));

    // Note: Cognito Lambda triggers (Story 1.2.5) are now created in CognitoStack
    // to avoid cyclic dependencies. Database tables are created by Flyway migrations
    // when this service starts, and triggers work when users sign up at runtime.

    // Apply additional tags specific to this service
    cdk.Tags.of(this).add('Consolidation', 'Companies+Users');
  }
}
