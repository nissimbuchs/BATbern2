import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as events from 'aws-cdk-lib/aws-events';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';
import { EcsServiceAlarms } from '../constructs/ecs-service-alarms';

export interface PartnerCoordinationStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  eventBus?: events.IEventBus;
  alarmTopic?: sns.ITopic;
}

/**
 * Partner Coordination Service Stack
 *
 * Domain microservice for managing partners, sponsors, and exhibitors.
 * Handles /api/v1/partners routes.
 * Uses Service Connect for direct service-to-service communication (no ALB).
 */
export class PartnerCoordinationStack extends cdk.Stack {
  public readonly service: ecs.FargateService;

  constructor(scope: Construct, id: string, props: PartnerCoordinationStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const serviceName = 'partner-coordination';

    // Build additional environment variables specific to this service
    const additionalEnvironment: Record<string, string> = {
      // EventBridge for domain events (PartnerCreatedEvent, TopicVoteSubmittedEvent, etc.)
      ...(props.eventBus && {
        EVENT_BUS_NAME: props.eventBus.eventBusName,
      }),
    };

    // Create domain service using reusable helper function
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'PartnerCoordination-Service',
        routePattern: '/api/v1/partners',
        cpu: 256,
        memoryLimitMiB: 1024, // Increased from 512 MB (Priority 4: ECS Right-Sizing - was at 87-90% utilization)
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

    // Platform Stability Improvements (Phase 3): Add ECS Service Alarms
    if (props.alarmTopic) {
      new EcsServiceAlarms(this, 'ServiceAlarms', {
        environment: envName,
        clusterName: props.cluster.clusterName,
        serviceName: this.service.serviceName,
        alarmTopic: props.alarmTopic,
        thresholds: {
          memoryUtilization: 80,
          oomKillCount: envName === 'production' ? 1 : 3,
          taskFailureCount: envName === 'production' ? 2 : 5,
          eventBridgePublishingFailures: envName === 'production' ? 5 : 10,
        },
      });
    }

    // Grant EventBridge permissions for domain events
    // Service publishes: PartnerCreatedEvent, PartnerUpdatedEvent, TopicVoteSubmittedEvent, TopicSuggestionSubmittedEvent
    if (props.eventBus) {
      this.service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'events:PutEvents',
        ],
        resources: [props.eventBus.eventBusArn],
      }));
    }
  }
}
