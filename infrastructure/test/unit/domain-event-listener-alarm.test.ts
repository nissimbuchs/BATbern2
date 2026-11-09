/**
 * Alarm on swallowed domain-event listener failures (owner go 2026-11-09).
 *
 * `SpringDomainEventPublisher` (shared-kernel) catches an exception thrown by a synchronous
 * listener, logs it at ERROR behind a fixed marker and lets the caller continue. It also counts
 * the failure in Micrometer, but no service exports metrics to CloudWatch, so until this alarm
 * nobody was told. The alarm is log based: a metric filter on each service's own log group
 * matches the marker, and any occurrence in five minutes opens an issue through the shared
 * alarm topic.
 *
 * Built through the real service stacks rather than the construct alone, so the test proves the
 * filter sits on the log group the containers actually write to.
 */
import * as fs from 'fs';
import * as path from 'path';
import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as sns from 'aws-cdk-lib/aws-sns';

import { stagingConfig } from '../../lib/config/staging-config';
import { CompanyManagementStack } from '../../lib/stacks/company-management-stack';
import { EventManagementStack } from '../../lib/stacks/event-management-stack';
import { PartnerCoordinationStack } from '../../lib/stacks/partner-coordination-stack';

const TEST_ACCOUNT = '123456789012';
const TEST_REGION = 'eu-central-1';
const PUBLISHER_SOURCE = path.resolve(
  __dirname,
  '../../../shared-kernel/src/main/java/ch/batbern/shared/events/SpringDomainEventPublisher.java'
);

/** The marker exactly as the publisher declares it, so a rename in Java fails this test. */
function publisherLogMarker(): string {
  const src = fs.readFileSync(PUBLISHER_SOURCE, 'utf8');
  const match = src.match(/LOG_MARKER\s*=\s*"([^"]+)"/);
  if (!match) {
    throw new Error('LOG_MARKER not found in SpringDomainEventPublisher.java');
  }
  return match[1];
}

type ServiceCase = {
  displayName: string;
  logGroupName: string;
  build: (app: App, common: Record<string, unknown>) => Stack;
};

const SERVICES: ServiceCase[] = [
  {
    displayName: 'EventManagement',
    logGroupName: '/aws/ecs/BATbern-staging/event-management',
    build: (app, common) => new EventManagementStack(app, 'TestEMS', common as any),
  },
  {
    displayName: 'CompanyManagement',
    logGroupName: '/aws/ecs/BATbern-staging/company-user-management',
    build: (app, common) => new CompanyManagementStack(app, 'TestCUMS', common as any),
  },
  {
    displayName: 'PartnerCoordination',
    logGroupName: '/aws/ecs/BATbern-staging/partner-coordination',
    build: (app, common) => new PartnerCoordinationStack(app, 'TestPCS', common as any),
  },
];

function buildTemplate(service: ServiceCase): Template {
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
  const common = {
    config: { ...stagingConfig, region: TEST_REGION },
    cluster,
    vpc,
    databaseSecurityGroup: ec2.SecurityGroup.fromSecurityGroupId(parent, 'DbSg', 'sg-db12345'),
    userPool: cognito.UserPool.fromUserPoolId(parent, 'UP', `${TEST_REGION}_test`),
    userPoolClient: cognito.UserPoolClient.fromUserPoolClientId(parent, 'UPC', 'test-client-id'),
    alarmTopic: sns.Topic.fromTopicArn(
      parent,
      'AlarmTopic',
      `arn:aws:sns:${TEST_REGION}:${TEST_ACCOUNT}:batbern-staging-alarms`
    ),
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  };
  return Template.fromStack(service.build(app, common));
}

describe('Domain-event listener failure alarm', () => {
  beforeAll(() => {
    // ECR image path instead of fromAsset (no Dockerfile hashing in unit tests).
    process.env.IMAGE_TAG = 'test-image-tag';
  });

  afterAll(() => {
    delete process.env.IMAGE_TAG;
  });

  test('should_readTheMarkerFromThePublisher_when_testRuns', () => {
    // Guards the guard: the pattern below is only meaningful if it is the publisher's marker.
    expect(publisherLogMarker()).toBe('DOMAIN_EVENT_LISTENER_FAILED');
  });

  describe.each(SERVICES)('$displayName', (service) => {
    let template: Template;

    beforeAll(() => {
      template = buildTemplate(service);
    });

    test('should_filterThePublisherMarker_when_serviceLogGroupCreated', () => {
      const logGroups = template.findResources('AWS::Logs::LogGroup', {
        Properties: { LogGroupName: service.logGroupName },
      });
      const [logGroupId] = Object.keys(logGroups);
      expect(logGroupId).toBeDefined();

      template.hasResourceProperties('AWS::Logs::MetricFilter', {
        LogGroupName: { Ref: logGroupId },
        FilterPattern: `"${publisherLogMarker()}"`,
        MetricTransformations: [
          {
            MetricNamespace: 'BATbern/DomainEvents',
            MetricName: `ListenerFailures-${service.displayName}`,
            MetricValue: '1',
            DefaultValue: 0,
          },
        ],
      });
    });

    test('should_alarmOnAnyFailureInFiveMinutes_when_metricFilterMatches', () => {
      const alarmTopicArn = `arn:aws:sns:${TEST_REGION}:${TEST_ACCOUNT}:batbern-staging-alarms`;
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: `batbern-staging-${service.displayName}-Event-Listener-Failures`,
        Namespace: 'BATbern/DomainEvents',
        MetricName: `ListenerFailures-${service.displayName}`,
        Dimensions: Match.absent(),
        Statistic: 'Sum',
        Period: 300,
        EvaluationPeriods: 1,
        Threshold: 0,
        ComparisonOperator: 'GreaterThanThreshold',
        TreatMissingData: 'notBreaching',
        // The shared topic feeds the github-issues Lambda; the OK action closes the issue (#956).
        AlarmActions: [alarmTopicArn],
        OKActions: [alarmTopicArn],
      });
    });
  });
});
