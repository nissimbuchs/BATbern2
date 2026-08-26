import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';
import { EcsServiceAlarms } from '../constructs/ecs-service-alarms';

export interface AttendeeExperienceStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  alarmTopic?: sns.ITopic;
}

/**
 * Attendee Experience Service Stack
 *
 * Domain microservice for managing attendee content, personalization, and engagement.
 * Handles /api/v1/content routes.
 * Uses Service Connect for direct service-to-service communication (no ALB).
 */
export class AttendeeExperienceStack extends cdk.Stack {
  public readonly service: ecs.FargateService;

  constructor(scope: Construct, id: string, props: AttendeeExperienceStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const serviceName = 'attendee-experience';

    // Create domain service using reusable helper function
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'AttendeeExperience-Service',
        routePattern: '/api/v1/content',
        cpu: 256,
        memoryLimitMiB: 1024, // Increased from 512 MB: 80% mem max, 120s startup caused health check failures
        healthCheckStartPeriodSeconds: 300, // Spring Boot on constrained resources needs headroom
        // PARKED AT ZERO TASKS — docs/plans/aws-cost-reduction.md, tier 1.
        //
        // This service is an empty Spring Boot application: src/main/java contains exactly
        // one file, AttendeeExperienceApplication.java, with no controllers and no
        // endpoints. The only gateway route pointing at it, /api/v1/content, resolves to no
        // handler in any service. It ran 2 Fargate tasks 24/7 for ~$17/month.
        //
        // Parked rather than deleted: Epic 7 will need this service, and restoring a
        // desiredCount is one deploy where recreating a stack is not. disableAutoScaling is
        // required, or the scaler registers a target and CPU policy for a service that is off.
        //
        // To bring it back: set desiredCount to 1 and remove disableAutoScaling.
        desiredCount: 0,
        disableAutoScaling: true,
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

    // Platform Stability Improvements (Phase 3): Add ECS Service Alarms
    if (props.alarmTopic) {
      new EcsServiceAlarms(this, 'ServiceAlarms', {
        environment: envName,
        clusterName: props.cluster.clusterName,
        serviceName: this.service.serviceName,
        serviceDisplayName: 'AttendeeExperience',
        alarmTopic: props.alarmTopic,
        thresholds: {
          memoryUtilization: 85, // JVM heap capped via MaxRAMPercentage; 85% gives headroom for non-heap
          oomKillCount: envName === 'production' ? 1 : 3,
          taskFailureCount: envName === 'production' ? 2 : 5,
          eventBridgePublishingFailures: envName === 'production' ? 5 : 10,
        },
      });
    }
  }
}
