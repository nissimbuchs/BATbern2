import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';
import { EcsServiceAlarms } from '../constructs/ecs-service-alarms';

export interface SpeakerCoordinationStackProps extends cdk.StackProps {
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
 * Speaker Coordination Service Stack
 *
 * Domain microservice for managing speakers, profiles, and availability.
 * Handles /api/v1/speakers routes.
 * Uses Service Connect for direct service-to-service communication (no ALB).
 */
export class SpeakerCoordinationStack extends cdk.Stack {
  public readonly service: ecs.FargateService;

  constructor(scope: Construct, id: string, props: SpeakerCoordinationStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const serviceName = 'speaker-coordination';

    // Create domain service using reusable helper function
    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName,
        componentTag: 'SpeakerCoordination-Service',
        routePattern: '/api/v1/speakers',
        cpu: 256,
        memoryLimitMiB: 512,
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
        serviceDisplayName: 'SpeakerCoordination',
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
