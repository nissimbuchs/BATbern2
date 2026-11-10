/**
 * Alarm on scheduled-job failures (Story 4.9b, assertion rule "no silent catch in schedulers
 * without a metric").
 *
 * EMS's `ScheduledJobFailures` records an item a scheduled job could not process (today: the
 * daily auto-publish) in a Micrometer counter and an ERROR line behind a fixed marker, then the
 * job goes on. No service exports Micrometer to CloudWatch, so the alarm is log based, as the
 * domain-event listener alarm (#1279): a metric filter on EMS's own log group matches the marker.
 * The marker is read from the Java source, so renaming it fails this test instead of silencing
 * the alarm. Only EMS has the recorder; CUMS and PCS get no such alarm.
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
const RECORDER_SOURCE = path.resolve(
  __dirname,
  '../../../services/event-management-service/src/main/java/ch/batbern/events/scheduled/ScheduledJobFailures.java'
);

/** The marker exactly as the recorder declares it, so a rename in Java fails this test. */
function recorderLogMarker(): string {
  const src = fs.readFileSync(RECORDER_SOURCE, 'utf8');
  const match = src.match(/LOG_MARKER\s*=\s*"([^"]+)"/);
  if (!match) {
    throw new Error('LOG_MARKER not found in ScheduledJobFailures.java');
  }
  return match[1];
}

type Build = (app: App, common: Record<string, unknown>) => Stack;

function buildTemplate(build: (app: App, common: Record<string, unknown>) => Stack): Template {
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
  return Template.fromStack(build(app, common));
}

const ALARM_TOPIC_ARN = `arn:aws:sns:${TEST_REGION}:${TEST_ACCOUNT}:batbern-staging-alarms`;

describe('Scheduled-job failure alarm', () => {
  beforeAll(() => {
    process.env.IMAGE_TAG = 'test-image-tag';
  });

  afterAll(() => {
    delete process.env.IMAGE_TAG;
  });

  test('should_readTheMarkerFromTheRecorder_when_testRuns', () => {
    expect(recorderLogMarker()).toBe('SCHEDULED_JOB_FAILED');
  });

  describe('EventManagement', () => {
    let template: Template;

    beforeAll(() => {
      template = buildTemplate(
        (app, common) => new EventManagementStack(app, 'TestEMS', common as any)
      );
    });

    test('should_filterTheRecorderMarker_when_serviceLogGroupCreated', () => {
      const logGroups = template.findResources('AWS::Logs::LogGroup', {
        Properties: { LogGroupName: '/aws/ecs/BATbern-staging/event-management' },
      });
      const [logGroupId] = Object.keys(logGroups);
      expect(logGroupId).toBeDefined();

      template.hasResourceProperties('AWS::Logs::MetricFilter', {
        LogGroupName: { Ref: logGroupId },
        FilterPattern: `"${recorderLogMarker()}"`,
        MetricTransformations: [
          {
            MetricNamespace: 'BATbern/ScheduledJobs',
            MetricName: 'ScheduledJobFailures-EventManagement',
            MetricValue: '1',
            DefaultValue: 0,
          },
        ],
      });
    });

    test('should_alarmOnAnyFailureInFiveMinutes_when_metricFilterMatches', () => {
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-EventManagement-Scheduled-Job-Failures',
        Namespace: 'BATbern/ScheduledJobs',
        MetricName: 'ScheduledJobFailures-EventManagement',
        Dimensions: Match.absent(),
        Statistic: 'Sum',
        Period: 300,
        EvaluationPeriods: 1,
        Threshold: 0,
        ComparisonOperator: 'GreaterThanThreshold',
        TreatMissingData: 'notBreaching',
        AlarmActions: [ALARM_TOPIC_ARN],
        OKActions: [ALARM_TOPIC_ARN],
      });
    });
  });

  test.each<[string, Build]>([
    [
      'CompanyManagement',
      (app, common) => new CompanyManagementStack(app, 'TestCUMS', common as any),
    ],
    [
      'PartnerCoordination',
      (app, common) => new PartnerCoordinationStack(app, 'TestPCS', common as any),
    ],
  ])('should_haveNoScheduledJobAlarm_when_serviceIs%s', (_name, build) => {
    const template = buildTemplate(build);
    template.resourcePropertiesCountIs(
      'AWS::Logs::MetricFilter',
      { FilterPattern: '"SCHEDULED_JOB_FAILED"' },
      0
    );
  });
});
