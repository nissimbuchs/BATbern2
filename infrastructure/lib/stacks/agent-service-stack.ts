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

export interface AgentServiceStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  cluster: ecs.ICluster;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  databaseEndpoint?: string;
  databaseSecret?: secretsmanager.ISecret;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
  alarmTopic?: sns.ITopic;
  /** Anthropic API key of the model adapter (story 3); `none` until the owner installs it */
  anthropicApiKey?: secretsmanager.ISecret;
  /** agent-service's caller secret at CUMS (story 4): grants and mints of the run's agent token */
  cumsCallerSecret?: secretsmanager.ISecret;
}

/**
 * Agent Service Stack (agentic interaction, epic agent runtime, story 1)
 *
 * agent-service runs the agent loop and keeps its decision log in its own `agent_*` tables
 * (AD-9). Handles /api/v1/agent/** behind the Spring gateway, reached through Service Connect as
 * `agent:8080`. Shipped dark: AGENT_ENABLED stays false until the owner switches it on.
 *
 * It accepts Cognito tokens only, so it needs no agent public key. The Anthropic key secret
 * (story 3) is injected as AGENT_ANTHROPIC_API_KEY; agent-service's caller secret at CUMS (story 4,
 * the same secret CUMS reads as AGENT_CALLERS_AGENT_SERVICE_SECRET) as AGENT_CUMS_CALLER_SECRET.
 * A run obtains its grant and mints its agent token at CUMS (`company-user-management:8080`,
 * /internal/agent-grants and /internal/agent-tokens) and reaches the public API only through the
 * Spring gateway (AGENT_GATEWAY_URL, `api-gateway:8080`). No SES: the agent never sends mail
 * itself (AD-5). Egress to api.anthropic.com goes through the domain-service security group (443 to
 * any IPv4, via NAT).
 */
export class AgentServiceStack extends cdk.Stack {
  public readonly service: ecs.FargateService;

  constructor(scope: Construct, id: string, props: AgentServiceStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // ecs.Secret grants the execution role read access to the secret; no other service gets it
    const additionalSecrets: Record<string, ecs.Secret> = {};
    if (props.anthropicApiKey) {
      additionalSecrets.AGENT_ANTHROPIC_API_KEY = ecs.Secret.fromSecretsManager(
        props.anthropicApiKey
      );
    }
    if (props.cumsCallerSecret) {
      additionalSecrets.AGENT_CUMS_CALLER_SECRET = ecs.Secret.fromSecretsManager(
        props.cumsCallerSecret
      );
    }

    const domainService = createDomainService(this, {
      config: props.config,
      serviceConfig: {
        serviceName: 'agent',
        componentTag: 'Agent-Service',
        routePattern: '/api/v1/agent',
        cpu: 256,
        memoryLimitMiB: 1024,
        // One steady-state task, as every domain service (docs/plans/aws-cost-reduction.md, tier 2)
        desiredCount: 1,
        minCapacity: 1,
        maxCapacity: 2,
        healthCheckStartPeriodSeconds: 300, // DB + Flyway + JPA
        additionalEnvironment: {
          // The service's agent switch; the owner turns it on, never a deploy (C1)
          AGENT_ENABLED: 'false',
          // CUMS grants runs and mints their tokens (story 4); tools reach the public API only
          // through the Spring gateway, with the run's token
          COMPANY_USER_MANAGEMENT_SERVICE_URL: 'http://company-user-management:8080',
          AGENT_GATEWAY_URL: 'http://api-gateway:8080',
        },
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

    if (props.alarmTopic) {
      new EcsServiceAlarms(this, 'ServiceAlarms', {
        environment: envName,
        clusterName: props.cluster.clusterName,
        serviceName: this.service.serviceName,
        serviceDisplayName: 'Agent',
        alarmTopic: props.alarmTopic,
        serviceLogGroup: domainService.logGroup,
        thresholds: {
          memoryUtilization: 85,
          oomKillCount: envName === 'production' ? 1 : 3,
          taskFailureCount: envName === 'production' ? 2 : 5,
        },
      });
    }
  }
}
