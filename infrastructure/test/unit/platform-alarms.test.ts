import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as sns from 'aws-cdk-lib/aws-sns';
import { EcsServiceAlarms } from '../../lib/constructs/ecs-service-alarms';
import { AlbAlarms } from '../../lib/constructs/alb-alarms';

/**
 * Issue #970: the nine alarms produced by AlarmConstruct had never evaluated a single
 * datapoint. They pointed at AWS/ApiGateway (traffic is served by an ALB), at AWS/ECS
 * without ClusterName, at DBClusterIdentifier for a single RDS instance, at AWS/EBS with
 * no volumes, and at AWS/Billing outside us-east-1.
 *
 * These tests pin the dimensions, because a wrong dimension is exactly what
 * `resourceCountIs` and a threshold assertion cannot see: CloudFormation happily accepts
 * an alarm whose dimensions match no resource on earth, and the old E2E specs asserted
 * `alarm.Threshold === 99.9` on an alarm that was permanently INSUFFICIENT_DATA.
 */
describe('Platform alarms (#970)', () => {
  const makeStack = () => {
    const app = new App();
    const stack = new Stack(app, 'TestPlatformAlarms', {
      env: { account: '123456789012', region: 'eu-central-1' },
    });
    const topic = new sns.Topic(stack, 'AlarmTopic');
    return { stack, topic };
  };

  describe('ECS CPU (previously batbern-*-high-cpu, dead)', () => {
    // The old alarm used dimensionsMap { ServiceName: 'batbern-staging' } — that value is
    // the CLUSTER name, and AWS/ECS needs BOTH ClusterName and ServiceName to resolve a
    // datapoint. It reported "Insufficient Data: 2 datapoints were unknown" for its whole
    // life. CPU is now per-service, alongside the memory alarm that always worked.
    test('should_createPerServiceCpuAlarm_when_ecsServiceAlarmsCreated', () => {
      const { stack, topic } = makeStack();

      new EcsServiceAlarms(stack, 'ServiceAlarms', {
        environment: 'staging',
        clusterName: 'batbern-staging',
        serviceName: 'BATbern-staging-EventManagement-ServiceD69D759B-abc',
        serviceDisplayName: 'EventManagement',
        alarmTopic: topic,
      });

      Template.fromStack(stack).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-EventManagement-High-CPU',
        Namespace: 'AWS/ECS',
        MetricName: 'CPUUtilization',
        // Both dimensions, or the metric resolves to nothing. This is the whole bug.
        Dimensions: Match.arrayWith([
          { Name: 'ClusterName', Value: 'batbern-staging' },
          {
            Name: 'ServiceName',
            Value: 'BATbern-staging-EventManagement-ServiceD69D759B-abc',
          },
        ]),
        Threshold: 80,
        ComparisonOperator: 'GreaterThanThreshold',
        TreatMissingData: 'notBreaching',
      });
    });

    test('should_notifyAndRecoverOnCpuAlarm_when_topicProvided', () => {
      const { stack, topic } = makeStack();

      new EcsServiceAlarms(stack, 'ServiceAlarms', {
        environment: 'staging',
        clusterName: 'batbern-staging',
        serviceName: 'svc',
        serviceDisplayName: 'EventManagement',
        alarmTopic: topic,
      });

      // #956: OK actions matter as much as ALARM actions — the github-issues Lambda can
      // only close an alarm's issue if the alarm publishes on recovery.
      Template.fromStack(stack).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-EventManagement-High-CPU',
        AlarmActions: Match.anyValue(),
        OKActions: Match.anyValue(),
      });
    });

    test('should_respectCpuThresholdOverride_when_provided', () => {
      const { stack, topic } = makeStack();

      new EcsServiceAlarms(stack, 'ServiceAlarms', {
        environment: 'production',
        clusterName: 'c',
        serviceName: 's',
        serviceDisplayName: 'ApiGatewayService',
        alarmTopic: topic,
        thresholds: { cpuUtilization: 65 },
      });

      Template.fromStack(stack).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-production-ApiGatewayService-High-CPU',
        Threshold: 65,
      });
    });
  });

  describe('ALB alarms (replacing the dead AWS/ApiGateway alarms)', () => {
    const albFullName = 'app/BATber-Servi-XlmJ3ScSHcvA/1234567890abcdef';
    const tgFullName = 'targetgroup/BATbe-Targe-ABCDEF/abcdef1234567890';

    const withAlb = () => {
      const { stack, topic } = makeStack();
      new AlbAlarms(stack, 'AlbAlarms', {
        environment: 'staging',
        loadBalancerFullName: albFullName,
        targetGroupFullName: tgFullName,
        alarmTopic: topic,
      });
      return Template.fromStack(stack);
    };

    test('should_alarmOnServerErrors_when_albAlarmsCreated', () => {
      // Old: AWS/ApiGateway 5XXError with ApiName=batbern-staging. There is no REST API
      // Gateway in this account; traffic is served by an ALB.
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-5xx',
        Metrics: Match.arrayWith([
          Match.objectLike({
            MetricStat: Match.objectLike({
              Metric: Match.objectLike({
                Namespace: 'AWS/ApplicationELB',
                MetricName: 'HTTPCode_Target_5XX_Count',
                Dimensions: [{ Name: 'LoadBalancer', Value: albFullName }],
              }),
            }),
          }),
        ]),
        TreatMissingData: 'notBreaching',
      });
    });

    test('should_alarmOnClientErrors_when_albAlarmsCreated', () => {
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-4xx',
        Namespace: 'AWS/ApplicationELB',
        MetricName: 'HTTPCode_Target_4XX_Count',
        Dimensions: [{ Name: 'LoadBalancer', Value: albFullName }],
        TreatMissingData: 'notBreaching',
      });
    });

    test('should_alarmOnP95LatencyInSeconds_when_albAlarmsCreated', () => {
      // The old alarm used threshold 500 against AWS/ApiGateway Latency, which reports
      // MILLISECONDS. ALB TargetResponseTime reports SECONDS, so the same number would
      // have meant a 500-second SLA. coding-standards.md targets P95 < 200ms.
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-latency-p95',
        Threshold: 0.5,
        ComparisonOperator: 'GreaterThanThreshold',
        TreatMissingData: 'notBreaching',
        Metrics: Match.arrayWith([
          Match.objectLike({
            MetricStat: Match.objectLike({
              Stat: 'p95',
              Metric: Match.objectLike({
                Namespace: 'AWS/ApplicationELB',
                MetricName: 'TargetResponseTime',
              }),
            }),
          }),
        ]),
      });
    });

    test('should_requireSustainedBreach_when_latencyAlarmCreated', () => {
      // The breach that actually occurs here is a deploy. Measured 2026-08-19 across two
      // ECS task replacements, p95 by 5-minute window with request counts:
      //
      //   25 req/3.71s, 116/1.99s, 256/0.84s, 386/0.53s, 45/0.006s
      //   35 req/4.71s, 220/1.17s, 533/0.43s
      //
      // Volume ramps while latency decays — a JVM warming under real load. Those warmups
      // breached for 4 and 2 consecutive periods, so requiring 5 of 6 (25 minutes) keeps
      // the alarm silent for them while still catching latency that is genuinely stuck.
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-latency-p95',
        EvaluationPeriods: 6,
        DatapointsToAlarm: 5,
      });
    });

    test('should_ignoreLatencyBelowMinimumTraffic_when_albAlarmsCreated', () => {
      // A percentile computed over a handful of requests is not a percentile: quiet windows
      // carry 1-6 requests, where p95 is essentially a single sample.
      //
      // NOTE: this guard is NOT what silences the deploy-warmup breaches. Every window in
      // those episodes carried 25-533 requests and clears this floor — see
      // should_requireSustainedBreach_when_latencyAlarmCreated, which is the control that
      // actually does that work. This one is retained because it is independently correct,
      // not because it fixed the 2026-08-19 notifications.
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-latency-p95',
        Metrics: Match.arrayWith([
          Match.objectLike({
            Expression: Match.stringLikeRegexp('IF\\(requests >= \\d+'),
          }),
        ]),
      });
    });

    test('should_computeAvailabilityAsAPercentage_when_albAlarmsCreated', () => {
      // The old alarm compared Sum(Count) — a REQUEST COUNT — against 99.9 and called it
      // "availability below 99.9% SLA". Any 5-minute window with under 100 requests would
      // have tripped it. It also used treatMissingData BREACHING, which is why it sat in
      // ALARM continuously from 2025-10-04. Availability must be a ratio.
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-availability',
        Threshold: 99.9,
        ComparisonOperator: 'LessThanThreshold',
        TreatMissingData: 'notBreaching',
        Metrics: Match.arrayWith([
          Match.objectLike({
            Expression: Match.stringLikeRegexp('IF\\(.*requests.*'),
          }),
        ]),
      });
    });

    test('should_alarmOnUnhealthyTargets_when_albAlarmsCreated', () => {
      withAlb().hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: 'batbern-staging-alb-unhealthy-targets',
        Namespace: 'AWS/ApplicationELB',
        MetricName: 'UnHealthyHostCount',
        Dimensions: Match.arrayWith([
          { Name: 'LoadBalancer', Value: albFullName },
          { Name: 'TargetGroup', Value: tgFullName },
        ]),
      });
    });

    test('should_neverUseBreachingMissingData_when_albAlarmsCreated', () => {
      // No ALB alarm may treat missing data as breaching. That setting is what turned
      // high-availability into a permanently red alarm nobody looked at any more.
      const alarms = withAlb().findResources('AWS::CloudWatch::Alarm');
      const breaching = Object.entries(alarms).filter(
        ([, r]) => r.Properties?.TreatMissingData === 'breaching'
      );
      expect(breaching).toEqual([]);
    });

    test('should_attachAlarmAndOkActionsToEveryAlarm_when_topicProvided', () => {
      const alarms = withAlb().findResources('AWS::CloudWatch::Alarm');
      expect(Object.keys(alarms).length).toBeGreaterThan(0);
      const missing = Object.entries(alarms)
        .filter(([, r]) => !r.Properties?.AlarmActions || !r.Properties?.OKActions)
        .map(([logicalId]) => logicalId);
      expect(missing).toEqual([]);
    });
  });
});
