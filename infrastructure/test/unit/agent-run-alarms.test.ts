/**
 * Alarms on agent runs (agentic interaction, epic agent runtime, story 10, R10).
 *
 * agent-service's `RunAlarms` writes a marker line per run end an alarm watches. The alarms are log
 * based like the domain-event and scheduled-job alarms: a literal metric filter per marker on
 * agent-service's own log group, an alarm on the filter's metric, both actions on the shared alarm
 * topic. The markers are read from the Java source, and every alarm's namespace and metric name are
 * checked against the filter that matches its marker, so a rename on either side fails this test
 * instead of leaving an alarm on a metric nothing emits.
 *
 * Built through the real AgentServiceStack, so the filters are proven to sit on the log group the
 * agent containers write to.
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
import { AgentServiceStack } from '../../lib/stacks/agent-service-stack';
import {
  AGENT_CREDENTIALS_REFUSED_MARKER,
  AGENT_DAILY_BUDGET_BREACHED_MARKER,
  AGENT_RUN_CAP_REACHED_MARKER,
  AGENT_RUN_FAILED_MARKER,
  AGENT_RUNS_NAMESPACE,
} from '../../lib/constructs/agent-run-alarms';

const TEST_ACCOUNT = '123456789012';
const TEST_REGION = 'eu-central-1';
const ALARM_TOPIC_ARN = `arn:aws:sns:${TEST_REGION}:${TEST_ACCOUNT}:batbern-staging-alarms`;
const LOG_GROUP_NAME = '/aws/ecs/BATbern-staging/agent';
const RUN_ALARMS_SOURCE = path.resolve(
  __dirname,
  '../../../services/agent-service/src/main/java/ch/batbern/agent/run/RunAlarms.java'
);

/** The marker exactly as RunAlarms declares the constant, so a rename in Java fails this test. */
function javaMarker(constant: string): string {
  const src = fs.readFileSync(RUN_ALARMS_SOURCE, 'utf8');
  const match = src.match(new RegExp(`String\\s+${constant}\\s*=\\s*"([^"]+)"`));
  if (!match) {
    throw new Error(`${constant} not found in RunAlarms.java`);
  }
  return match[1];
}

function buildTemplate(withTopic: boolean): Template {
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
    userPool: cognito.UserPool.fromUserPoolId(parent, 'UP', `${TEST_REGION}_test`),
    userPoolClient: cognito.UserPoolClient.fromUserPoolClientId(parent, 'UPC', 'test-client-id'),
    alarmTopic: withTopic
      ? sns.Topic.fromTopicArn(parent, 'AlarmTopic', ALARM_TOPIC_ARN)
      : undefined,
    env: { account: TEST_ACCOUNT, region: TEST_REGION },
  });
  return Template.fromStack(stack);
}

/** The metric (namespace, name) of the filter matching `marker` on the agent log group. */
function metricOfFilter(template: Template, marker: string): { namespace: string; name: string } {
  const logGroups = template.findResources('AWS::Logs::LogGroup', {
    Properties: { LogGroupName: LOG_GROUP_NAME },
  });
  const [logGroupId] = Object.keys(logGroups);
  expect(logGroupId).toBeDefined();
  const filters = Object.values(
    template.findResources('AWS::Logs::MetricFilter', {
      Properties: { FilterPattern: `"${marker}"`, LogGroupName: { Ref: logGroupId } },
    })
  );
  expect(filters).toHaveLength(1);
  const [transformation] = filters[0].Properties.MetricTransformations;
  expect(transformation.MetricValue).toBe('1');
  expect(transformation.DefaultValue).toBe(0);
  return { namespace: transformation.MetricNamespace, name: transformation.MetricName };
}

describe('Agent run alarms', () => {
  beforeAll(() => {
    // ECR image path instead of fromAsset (no Dockerfile hashing in unit tests).
    process.env.IMAGE_TAG = 'test-image-tag';
  });

  afterAll(() => {
    delete process.env.IMAGE_TAG;
  });

  test('should_useTheJavaMarkers_when_constructDeclaresThem', () => {
    expect(AGENT_RUN_FAILED_MARKER).toBe(javaMarker('RUN_FAILED'));
    expect(AGENT_RUN_CAP_REACHED_MARKER).toBe(javaMarker('RUN_CAP_REACHED'));
    expect(AGENT_DAILY_BUDGET_BREACHED_MARKER).toBe(javaMarker('DAILY_BUDGET_BREACHED'));
    expect(AGENT_CREDENTIALS_REFUSED_MARKER).toBe(javaMarker('CREDENTIALS_REFUSED'));
  });

  describe('with the shared alarm topic', () => {
    let template: Template;

    beforeAll(() => {
      template = buildTemplate(true);
    });

    test.each([
      ['Agent-Run-Failures', 'RUN_FAILED', 300, 1, undefined],
      ['Agent-Refused-Mints', 'CREDENTIALS_REFUSED', 300, 1, undefined],
      // The budget is per day: one breach keeps the alarm on for 24 hours (1 of 24, not 24 of 24)
      ['Agent-Daily-Budget-Breaches', 'DAILY_BUDGET_BREACHED', 3600, 24, 1],
    ])(
      'should_alarmOnTheCodesMetric_when_%s',
      (alarmSuffix, constant, period, evaluationPeriods, datapointsToAlarm) => {
        const metric = metricOfFilter(template, javaMarker(constant));
        expect(metric.namespace).toBe(AGENT_RUNS_NAMESPACE);

        template.hasResourceProperties('AWS::CloudWatch::Alarm', {
          AlarmName: `batbern-staging-${alarmSuffix}`,
          Namespace: metric.namespace,
          MetricName: metric.name,
          Dimensions: Match.absent(),
          Statistic: 'Sum',
          Period: period,
          EvaluationPeriods: evaluationPeriods,
          DatapointsToAlarm: datapointsToAlarm ?? Match.absent(),
          Threshold: 0,
          ComparisonOperator: 'GreaterThanThreshold',
          TreatMissingData: 'notBreaching',
          // The shared topic feeds the github-issues Lambda; the OK action closes the issue (#956)
          AlarmActions: [ALARM_TOPIC_ARN],
          OKActions: [ALARM_TOPIC_ARN],
        });
      }
    );

    test('should_countPerRunCapsWithoutAnAlarm_when_capReached', () => {
      const metric = metricOfFilter(template, javaMarker('RUN_CAP_REACHED'));
      expect(metric).toEqual({ namespace: AGENT_RUNS_NAMESPACE, name: 'RunCapsReached-Agent' });
      template.resourcePropertiesCountIs(
        'AWS::CloudWatch::Alarm',
        { MetricName: 'RunCapsReached-Agent' },
        0
      );
    });

    test('should_haveExactlyThreeRunAlarms_when_synthesized', () => {
      const runAlarms = template.findResources('AWS::CloudWatch::Alarm', {
        Properties: { Namespace: AGENT_RUNS_NAMESPACE },
      });
      expect(Object.keys(runAlarms)).toHaveLength(3);
    });

    test('should_nameEveryAlarmForTheAgentLogGroup_when_issueIsFiled', () => {
      // github-issues-integration maps '-Agent-' in the alarm name to /aws/ecs/BATbern-{env}/agent
      const runAlarms = template.findResources('AWS::CloudWatch::Alarm', {
        Properties: { Namespace: AGENT_RUNS_NAMESPACE },
      });
      for (const alarm of Object.values(runAlarms)) {
        expect(alarm.Properties.AlarmName).toContain('-Agent-');
      }
    });
  });

  test('should_createNoRunAlarm_when_noAlarmTopic', () => {
    const template = buildTemplate(false);
    template.resourcePropertiesCountIs(
      'AWS::CloudWatch::Alarm',
      { Namespace: AGENT_RUNS_NAMESPACE },
      0
    );
  });
});
