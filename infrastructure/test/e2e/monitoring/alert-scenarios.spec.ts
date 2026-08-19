import {
  CloudWatchClient,
  DescribeAlarmsCommand,
  AlarmType,
  MetricAlarm,
} from '@aws-sdk/client-cloudwatch';
import { SNSClient } from '@aws-sdk/client-sns';

/**
 * E2E checks for the deployed alarm estate.
 *
 * ## Why this file was rewritten (issue #970)
 *
 * The previous version asserted that alarms **existed** with the right threshold:
 *
 * ```ts
 * const alarm = response.MetricAlarms![0];
 * expect(alarm.Threshold).toBe(99.9);
 * expect(alarm.ComparisonOperator).toBe('LessThanThreshold');
 * ```
 *
 * Every one of those assertions passed for nine alarms that had **never evaluated a single
 * datapoint** — they queried `AWS/ApiGateway` when traffic is served by an ALB, `AWS/ECS`
 * without `ClusterName`, `DBClusterIdentifier` against a single RDS instance, `AWS/EBS`
 * with no volumes, and `AWS/Billing` outside us-east-1. A name and a threshold say nothing
 * about whether the metric resolves to anything.
 *
 * The check that actually catches it is `StateReason`. CloudWatch reports
 * `"Unchecked: Initial alarm creation"` for an alarm that has never evaluated, so any alarm
 * still saying that well after deploy is watching nothing. That is the primary test below.
 *
 * ## These tests do not currently run anywhere
 *
 * They are `describe.skip` unless `TEST_E2E=true`, and CI runs only `test/unit`
 * (`build.yml`: `npm test -- --ci test/unit`). So this suite is dormant twice over, which
 * is a large part of why #970 survived ten months. Wiring it into the post-deploy job is
 * tracked separately — until then, run it by hand:
 *
 * ```sh
 * TEST_E2E=true TEST_ENVIRONMENT=staging AWS_PROFILE=batbern-staging \
 *   npx jest test/e2e/monitoring
 * ```
 */
const describeE2E = process.env.TEST_E2E === 'true' ? describe : describe.skip;

describeE2E('Alert Scenarios E2E Tests', () => {
  let cloudwatchClient: CloudWatchClient;
  const environment = process.env.TEST_ENVIRONMENT || 'dev';
  const alarmPrefix = `batbern-${environment}`;

  /** CloudWatch's marker for an alarm that has never evaluated a datapoint. */
  const NEVER_EVALUATED = 'Unchecked: Initial alarm creation';

  const allAlarms = async (): Promise<MetricAlarm[]> => {
    const collected: MetricAlarm[] = [];
    let nextToken: string | undefined;
    do {
      const response = await cloudwatchClient.send(
        new DescribeAlarmsCommand({
          AlarmNamePrefix: alarmPrefix,
          AlarmTypes: [AlarmType.MetricAlarm],
          NextToken: nextToken,
        })
      );
      collected.push(...(response.MetricAlarms ?? []));
      nextToken = response.NextToken;
    } while (nextToken);
    return collected;
  };

  const byName = async (name: string): Promise<MetricAlarm | undefined> => {
    const response = await cloudwatchClient.send(
      new DescribeAlarmsCommand({ AlarmNames: [name], AlarmTypes: [AlarmType.MetricAlarm] })
    );
    return response.MetricAlarms?.[0];
  };

  beforeAll(() => {
    cloudwatchClient = new CloudWatchClient({
      region: process.env.AWS_REGION || 'eu-central-1',
    });
    // Constructed for parity with the previous suite's setup; SNS wiring is asserted
    // through the alarms' own actions below.
    new SNSClient({ region: process.env.AWS_REGION || 'eu-central-1' });
  });

  describe('Alarms are actually watching something (#970)', () => {
    test('should_haveEvaluatedAtLeastOnce_when_alarmDeployed', async () => {
      // THE check. An alarm reporting "Unchecked: Initial alarm creation" long after
      // deploy has never once evaluated — its dimensions match no resource. Nine alarms
      // sat in exactly this state for months while every other assertion passed.
      const alarms = await allAlarms();
      expect(alarms.length).toBeGreaterThan(0);

      const neverEvaluated = alarms
        .filter((a) => a.StateReason?.includes(NEVER_EVALUATED))
        .map((a) => `${a.AlarmName} (${a.Namespace}/${a.MetricName})`);

      expect(neverEvaluated).toEqual([]);
    });

    test('should_notSitInAlarmIndefinitely_when_alarmDeployed', async () => {
      // batbern-staging-high-availability was ALARM continuously from 2025-10-04 — about
      // ten months — because it treated its (never-arriving) missing data as breaching.
      // A permanently red alarm is worse than no alarm.
      const alarms = await allAlarms();
      const stale = alarms
        .filter((a) => a.StateValue === 'ALARM')
        .filter((a) => {
          const updated = a.StateUpdatedTimestamp;
          if (!updated) return false;
          const daysRed = (Date.now() - new Date(updated).getTime()) / 86_400_000;
          return daysRed > 7;
        })
        .map((a) => `${a.AlarmName} (red since ${a.StateUpdatedTimestamp?.toISOString()})`);

      expect(stale).toEqual([]);
    });

    test('should_notifyOnAlarmAndRecovery_when_alarmDeployed', async () => {
      // #956: without an OK action the github-issues Lambda can never close the issue it
      // opened, so alarm issues accumulate forever.
      const alarms = await allAlarms();
      const missingActions = alarms
        .filter((a) => !a.AlarmActions?.length || !a.OKActions?.length)
        .map((a) => a.AlarmName);

      expect(missingActions).toEqual([]);
    });
  });

  describe('ALB alarms (AC5, AC6, AC7)', () => {
    test('should_measureAvailabilityAsARatio_when_albAlarmsDeployed', async () => {
      const alarm = await byName(`${alarmPrefix}-alb-availability`);
      expect(alarm).toBeDefined();
      expect(alarm!.Threshold).toBe(99.9);
      expect(alarm!.ComparisonOperator).toBe('LessThanThreshold');
      // A math expression, not a raw request count — the alarm this replaces compared
      // Sum(RequestCount) against 99.9 as though a count were a percentage.
      expect(alarm!.Metrics?.some((m) => m.Expression)).toBe(true);
    });

    test('should_alarmOnServerErrors_when_albAlarmsDeployed', async () => {
      const alarm = await byName(`${alarmPrefix}-alb-5xx`);
      expect(alarm).toBeDefined();
      expect(alarm!.ComparisonOperator).toBe('GreaterThanThreshold');
    });

    test('should_alarmOnP95Latency_when_albAlarmsDeployed', async () => {
      const alarm = await byName(`${alarmPrefix}-alb-latency-p95`);
      expect(alarm).toBeDefined();
      expect(alarm!.ExtendedStatistic).toBe('p95');
      // SECONDS: AWS/ApplicationELB TargetResponseTime is in seconds, unlike the
      // AWS/ApiGateway Latency metric the old alarm used, which is milliseconds.
      expect(alarm!.Threshold).toBeLessThan(10);
    });

    test('should_alarmOnUnhealthyTargets_when_albAlarmsDeployed', async () => {
      const alarm = await byName(`${alarmPrefix}-alb-unhealthy-targets`);
      expect(alarm).toBeDefined();
      expect(alarm!.MetricName).toBe('UnHealthyHostCount');
    });
  });

  describe('Resource utilization (AC8)', () => {
    test('should_monitorCpuPerService_when_ecsAlarmsDeployed', async () => {
      const alarms = await allAlarms();
      const cpuAlarms = alarms.filter((a) => a.MetricName === 'CPUUtilization');
      expect(cpuAlarms.length).toBeGreaterThan(0);

      // Every ECS CPU alarm must carry BOTH dimensions, or the metric resolves to nothing.
      const ecsCpu = cpuAlarms.filter((a) => a.Namespace === 'AWS/ECS');
      expect(ecsCpu.length).toBeGreaterThan(0);
      for (const alarm of ecsCpu) {
        const dims = (alarm.Dimensions ?? []).map((d) => d.Name);
        expect(dims).toContain('ClusterName');
        expect(dims).toContain('ServiceName');
      }
    });

    test('should_monitorMemoryPerService_when_ecsAlarmsDeployed', async () => {
      const alarms = await allAlarms();
      const memoryAlarms = alarms.filter(
        (a) => a.Namespace === 'AWS/ECS' && a.MetricName === 'MemoryUtilization'
      );
      expect(memoryAlarms.length).toBeGreaterThan(0);
    });

    test('should_monitorDatabaseStorage_when_rdsAlarmsDeployed', async () => {
      // Replaces the old disk check, which looked for AWS/EBS VolumeUtilization —
      // a metric name that does not exist, against volumes that do not exist.
      const alarm = await byName(`${alarmPrefix}-database-storage-low`);
      expect(alarm).toBeDefined();
      expect(alarm!.Namespace).toBe('AWS/RDS');
      expect(alarm!.MetricName).toBe('FreeStorageSpace');
      expect(alarm!.ComparisonOperator).toBe('LessThanThreshold');
    });

    test('should_monitorDatabaseConnections_when_rdsAlarmsDeployed', async () => {
      const alarm = await byName(`${alarmPrefix}-database-connections`);
      expect(alarm).toBeDefined();
      const dims = (alarm!.Dimensions ?? []).map((d) => d.Name);
      expect(dims).toContain('DBInstanceIdentifier');
      expect(dims).not.toContain('DBClusterIdentifier');
    });
  });

  describe('Alert Notification Configuration', () => {
    test('should_configureSNSNotifications_when_alarmsCreated', async () => {
      const alarms = await allAlarms();
      expect(alarms.length).toBeGreaterThan(0);

      const alarm = alarms[0];
      expect(alarm.AlarmActions).toBeDefined();
      expect(alarm.AlarmActions!.length).toBeGreaterThan(0);
      expect(alarm.AlarmActions![0]).toContain('arn:aws:sns:');
    });
  });
});
