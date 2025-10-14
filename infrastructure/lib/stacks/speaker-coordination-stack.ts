import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { createDomainService } from '../constructs/domain-service-construct';

export interface SpeakerCoordinationStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
}

/**
 * Speaker Coordination Service Stack
 *
 * Domain microservice for managing speakers, profiles, and availability.
 * Handles /api/v1/speakers routes.
 */
export class SpeakerCoordinationStack extends cdk.Stack {
  public readonly service: ecsPatterns.ApplicationLoadBalancedFargateService;
  public readonly serviceUrl: string;

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
    this.serviceUrl = domainService.serviceUrl;

    // Outputs
    new cdk.CfnOutput(this, 'ServiceUrl', {
      value: this.serviceUrl,
      description: 'Speaker Coordination Service internal URL',
      exportName: `${envName}-speaker-coordination-url`,
    });
  }
}
