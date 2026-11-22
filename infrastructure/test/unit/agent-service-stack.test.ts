import * as fs from 'fs';
import * as path from 'path';
import { App, Stack } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as sns from 'aws-cdk-lib/aws-sns';
import { stagingConfig } from '../../lib/config/staging-config';
import { AgentServiceStack } from '../../lib/stacks/agent-service-stack';

/**
 * agent-service (agentic interaction, epic agent runtime, story 1): its own ECS service behind the
 * Spring gateway, reached as `agent:8080` through Service Connect, image from the CDK-owned ECR
 * repository batbern/staging/agent-service, shipped dark (AGENT_ENABLED=false).
 */
const TEST_ACCOUNT = '123456789012';
const TEST_REGION = 'eu-central-1';
const ALARM_TOPIC = `arn:aws:sns:${TEST_REGION}:${TEST_ACCOUNT}:batbern-staging-alarms`;

function buildTemplate(): Template {
  const app = new App();
  const parent = new Stack(app, 'Parent', { env: { account: TEST_ACCOUNT, region: TEST_REGION } });
  const vpc = ec2.Vpc.fromVpcAttributes(parent, 'Vpc', {
    vpcId: 'vpc-test',
    vpcCidrBlock: '10.0.0.0/16',
    availabilityZones: ['eu-central-1a', 'eu-central-1b'],
    privateSubnetIds: ['subnet-aaa', 'subnet-bbb'],
    privateSubnetRouteTableIds: ['rtb-aaa', 'rtb-bbb'],
  });
  const cluster = ecs.Cluster.fromClusterAttributes(parent, 'Cluster', {
    clusterName: 'batbern-staging',
    vpc,
    securityGroups: [],
  });
  const stack = new AgentServiceStack(app, 'TestAgent', {
    config: { ...stagingConfig, region: TEST_REGION },
    cluster,
    vpc,
    databaseSecurityGroup: ec2.SecurityGroup.fromSecurityGroupId(parent, 'DbSg', 'sg-db12345'),
    databaseEndpoint: 'db.example.internal',
    userPool: cognito.UserPool.fromUserPoolId(parent, 'UP', `${TEST_REGION}_test`),
    userPoolClient: cognito.UserPoolClient.fromUserPoolClientId(parent, 'UPC', 'test-client-id'),
    alarmTopic: sns.Topic.fromTopicArn(parent, 'AlarmTopic', ALARM_TOPIC),
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  });
  return Template.fromStack(stack);
}

function containerEnv(template: Template): Record<string, string> {
  const taskDefs = template.findResources('AWS::ECS::TaskDefinition');
  const [taskDef] = Object.values(taskDefs) as Array<{
    Properties: {
      ContainerDefinitions: Array<{
        Name: string;
        Environment?: Array<{ Name: string; Value: unknown }>;
      }>;
    };
  }>;
  const container = taskDef.Properties.ContainerDefinitions.find((c) => c.Name === 'Container');
  return Object.fromEntries(
    (container?.Environment ?? []).map((e) => [e.Name, typeof e.Value === 'string' ? e.Value : ''])
  );
}

describe('AgentServiceStack', () => {
  let template: Template;

  beforeAll(() => {
    // ECR image path instead of fromAsset (no Dockerfile hashing in unit tests).
    process.env.IMAGE_TAG = 'test-image-tag';
    template = buildTemplate();
  });

  afterAll(() => {
    delete process.env.IMAGE_TAG;
  });

  test('should_runOneArm64Task_with256CpuAnd1024Mib', () => {
    template.hasResourceProperties('AWS::ECS::TaskDefinition', {
      Cpu: '256',
      Memory: '1024',
      RuntimePlatform: { CpuArchitecture: 'ARM64', OperatingSystemFamily: 'LINUX' },
    });
    template.hasResourceProperties('AWS::ECS::Service', { DesiredCount: 1 });
    template.hasResourceProperties('AWS::ApplicationAutoScaling::ScalableTarget', {
      MinCapacity: 1,
      MaxCapacity: 2,
    });
  });

  test('should_pullTheImage_fromTheCdkOwnedAgentServiceRepository', () => {
    const taskDefs = JSON.stringify(template.findResources('AWS::ECS::TaskDefinition'));
    expect(taskDefs).toContain('batbern/staging/agent-service');
    expect(taskDefs).toContain('test-image-tag');
  });

  test('should_shipDark_with_agentSwitchOff', () => {
    expect(containerEnv(template).AGENT_ENABLED).toBe('false');
  });

  test('should_carryNoAgentOrMailSecrets_inStoryOne', () => {
    // Cognito only (no agent public key), no caller secret (story 4), no Anthropic key (story 3)
    const taskDefs = JSON.stringify(template.findResources('AWS::ECS::TaskDefinition'));
    expect(taskDefs).not.toContain('AGENT_JWT_PUBLIC_KEY');
    expect(taskDefs).not.toContain('CALLER_SECRET');
    expect(taskDefs).not.toContain('ANTHROPIC');
    expect(JSON.stringify(template.toJSON())).not.toMatch(/ses:Send/);
  });

  test('should_registerServiceConnect_as_agent8080', () => {
    template.hasResourceProperties('AWS::ECS::Service', {
      ServiceConnectConfiguration: Match.objectLike({
        Enabled: true,
        Services: [
          Match.objectLike({
            PortName: 'agent-port',
            DiscoveryName: 'agent',
            ClientAliases: [Match.objectLike({ DnsName: 'agent', Port: 8080 })],
          }),
        ],
      }),
    });
  });

  test('should_logTo_awsEcsBatbernStagingAgent', () => {
    template.hasResourceProperties('AWS::Logs::LogGroup', {
      LogGroupName: '/aws/ecs/BATbern-staging/agent',
    });
  });

  test('should_alarmLikeTheOtherServices_onTheSharedTopic', () => {
    const alarms = template.findResources('AWS::CloudWatch::Alarm');
    const names = Object.values(alarms).map(
      (a) => (a as { Properties: { AlarmName?: string } }).Properties.AlarmName ?? ''
    );
    expect(names.some((n) => n.includes('Agent'))).toBe(true);
    for (const alarm of Object.values(alarms)) {
      expect(JSON.stringify(alarm)).toContain(ALARM_TOPIC);
    }
  });
});

describe('agent-service wiring', () => {
  const root = path.resolve(__dirname, '../../..');
  const read = (file: string) => fs.readFileSync(path.join(root, file), 'utf8');

  test('should_pointTheGateway_atServiceConnectAgent8080', () => {
    expect(read('infrastructure/lib/stacks/api-gateway-service-stack.ts')).toContain(
      "AGENT_SERVICE_URL: 'http://agent:8080'"
    );
  });

  test('should_deployTheAgentStack_inLayer4AndOnItsOwn', () => {
    const scripts = JSON.parse(read('infrastructure/package.json')).scripts as Record<
      string,
      string
    >;
    expect(scripts['deploy:staging:layer4-services']).toContain('BATbern-staging-AgentService');
    expect(scripts['deploy:staging:microservices']).toContain('BATbern-staging-AgentService');
    expect(scripts['deploy:staging:layer4-agent-service']).toMatch(
      /cdk deploy BATbern-staging-AgentService --exclusively/
    );
  });

  test('should_wireTheStack_inTheApp_withItsDependencies', () => {
    const app = read('infrastructure/bin/batbern-infrastructure.ts');
    expect(app).toContain('new AgentServiceStack(app, `${stackPrefix}-AgentService`');
    for (const dep of [
      'clusterStack',
      'databaseStack',
      'cicdStack',
      'cognitoStack',
      'monitoringStack',
    ]) {
      expect(app).toContain(`agentServiceStack.addDependency(${dep})`);
    }
    expect(app).not.toContain('agentServiceStack.addDependency(sesStack)');
  });
});
