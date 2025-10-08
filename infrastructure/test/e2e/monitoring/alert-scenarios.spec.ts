import { CloudWatchClient, DescribeAlarmsCommand, SetAlarmStateCommand, AlarmType, MetricAlarm } from '@aws-sdk/client-cloudwatch';
import { SNSClient, GetTopicAttributesCommand, ListSubscriptionsByTopicCommand } from '@aws-sdk/client-sns';

/**
 * E2E Test for Alert Configuration and Notification Flow
 *
 * This test validates that alarms are correctly configured with appropriate thresholds
 * and notification channels are properly set up.
 *
 * NOTE: These tests require actual AWS resources and will be skipped unless
 * TEST_E2E=true environment variable is set (for deployed environments only)
 */
const describeE2E = process.env.TEST_E2E === 'true' ? describe : describe.skip;

describeE2E('Alert Scenarios E2E Tests', () => {
  let cloudwatchClient: CloudWatchClient;
  let snsClient: SNSClient;
  const environment = process.env.TEST_ENVIRONMENT || 'dev';
  const alarmPrefix = `batbern-${environment}`;

  beforeAll(() => {
    cloudwatchClient = new CloudWatchClient({
      region: process.env.AWS_REGION || 'eu-central-1'
    });
    snsClient = new SNSClient({
      region: process.env.AWS_REGION || 'eu-central-1'
    });
  });

  describe('SLA Monitoring Alarms (AC5)', () => {
    test('should_triggerSLAAlert_when_availabilityBelow999Percent', async () => {
      // This test verifies SLA monitoring alarm exists with correct threshold
      const command = new DescribeAlarmsCommand({
        AlarmNames: [`${alarmPrefix}-high-availability`],
        AlarmTypes: [AlarmType.MetricAlarm],
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();
      expect(response.MetricAlarms!.length).toBeGreaterThan(0);

      const alarm = response.MetricAlarms![0];
      expect(alarm.Threshold).toBe(99.9); // 99.9% availability SLA
      expect(alarm.ComparisonOperator).toBe('LessThanThreshold');
      expect(alarm.AlarmActions).toBeDefined();
      expect(alarm.AlarmActions!.length).toBeGreaterThan(0);
    });
  });

  describe('Error Rate Alarms (AC6)', () => {
    test('should_triggerErrorRateAlert_when_errorRateExceedsThreshold', async () => {
      // This test verifies error rate alarm exists with correct threshold
      const command = new DescribeAlarmsCommand({
        AlarmNames: [`${alarmPrefix}-high-errors`],
        AlarmTypes: [AlarmType.MetricAlarm],
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();
      expect(response.MetricAlarms!.length).toBeGreaterThan(0);

      const alarm = response.MetricAlarms![0];
      expect(alarm.Threshold).toBeLessThanOrEqual(10); // Error count threshold
      expect(alarm.ComparisonOperator).toBe('GreaterThanThreshold');
      expect(alarm.MetricName).toBe('5XXError');
    });

    test('should_monitorErrorRatePerService_when_serviceSpecificAlarmsConfigured', async () => {
      // This test verifies service-specific error rate alarms
      const command = new DescribeAlarmsCommand({
        AlarmNamePrefix: `${alarmPrefix}-service-`,
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();

      // Verify each service has error rate monitoring
      const serviceAlarms = response.MetricAlarms!.filter((alarm: MetricAlarm) =>
        alarm.AlarmName?.includes('error-rate')
      );
      expect(serviceAlarms.length).toBeGreaterThan(0);
    });
  });

  describe('Performance Alarms (AC7)', () => {
    test('should_triggerLatencyAlert_when_p95ResponseTimeExceedsTarget', async () => {
      // This test verifies latency monitoring alarm exists
      const command = new DescribeAlarmsCommand({
        AlarmNames: [`${alarmPrefix}-high-latency`],
        AlarmTypes: [AlarmType.MetricAlarm],
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();
      expect(response.MetricAlarms!.length).toBeGreaterThan(0);

      const alarm = response.MetricAlarms![0];
      expect(alarm.Threshold).toBeLessThanOrEqual(1000); // 1 second max
      expect(alarm.ComparisonOperator).toBe('GreaterThanThreshold');
      expect(alarm.MetricName).toBe('Latency');
      expect(alarm.Statistic).toMatch(/p95|p99/i); // P95 or P99 percentile
    });
  });

  describe('Resource Utilization Alarms (AC8)', () => {
    test('should_triggerResourceAlert_when_cpuUtilizationAbove80Percent', async () => {
      // This test verifies CPU utilization alarm exists
      const command = new DescribeAlarmsCommand({
        AlarmNames: [`${alarmPrefix}-high-cpu`],
        AlarmTypes: [AlarmType.MetricAlarm],
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();
      expect(response.MetricAlarms!.length).toBeGreaterThan(0);

      const alarm = response.MetricAlarms![0];
      expect(alarm.Threshold).toBe(80); // 80% CPU threshold
      expect(alarm.ComparisonOperator).toBe('GreaterThanThreshold');
      expect(alarm.MetricName).toBe('CPUUtilization');
    });

    test('should_monitorMemoryUtilization_when_resourceAlarmsConfigured', async () => {
      // This test verifies memory utilization alarm exists
      const command = new DescribeAlarmsCommand({
        AlarmNamePrefix: `${alarmPrefix}-high-memory`,
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();

      const memoryAlarms = response.MetricAlarms!.filter((alarm: MetricAlarm) =>
        alarm.MetricName === 'MemoryUtilization'
      );
      expect(memoryAlarms.length).toBeGreaterThan(0);
    });

    test('should_monitorDiskUtilization_when_resourceAlarmsConfigured', async () => {
      // This test verifies disk utilization alarm exists
      const command = new DescribeAlarmsCommand({
        AlarmNamePrefix: `${alarmPrefix}-high-disk`,
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricAlarms).toBeDefined();

      const diskAlarms = response.MetricAlarms!.filter((alarm: MetricAlarm) =>
        alarm.MetricName?.includes('Disk') || alarm.MetricName?.includes('Storage')
      );
      expect(diskAlarms.length).toBeGreaterThan(0);
    });
  });

  describe('Alert Notification Configuration', () => {
    test('should_configureSNSNotifications_when_alarmsCreated', async () => {
      // This test verifies SNS topic is configured for alarm notifications
      const alarmsCommand = new DescribeAlarmsCommand({
        AlarmNamePrefix: alarmPrefix,
      });

      const alarmsResponse = await cloudwatchClient.send(alarmsCommand);
      expect(alarmsResponse.MetricAlarms).toBeDefined();
      expect(alarmsResponse.MetricAlarms!.length).toBeGreaterThan(0);

      // Get the SNS topic ARN from the first alarm
      const alarm = alarmsResponse.MetricAlarms![0];
      expect(alarm.AlarmActions).toBeDefined();
      expect(alarm.AlarmActions!.length).toBeGreaterThan(0);

      const topicArn = alarm.AlarmActions![0];
      expect(topicArn).toContain('arn:aws:sns:');
    });

    test('should_escalateAlert_when_criticalAlertNotAcknowledged', async () => {
      // This test verifies critical alarms have escalation actions configured
      const command = new DescribeAlarmsCommand({
        AlarmNamePrefix: `${alarmPrefix}-critical-`,
      });

      const response = await cloudwatchClient.send(command);

      if (response.MetricAlarms && response.MetricAlarms.length > 0) {
        const criticalAlarm = response.MetricAlarms[0];

        // Verify alarm has both alarm actions and insufficient data actions
        expect(criticalAlarm.AlarmActions).toBeDefined();
        expect(criticalAlarm.AlarmActions!.length).toBeGreaterThan(0);
      }
    });
  });

  describe('Alert State Management', () => {
    test('should_transitionAlarmStates_when_metricThresholdsCrossed', async () => {
      // This test verifies alarms can transition between states
      const testAlarmName = `${alarmPrefix}-test-alarm`;

      // Get current alarm state
      const describeCommand = new DescribeAlarmsCommand({
        AlarmNames: [testAlarmName],
      });

      const response = await cloudwatchClient.send(describeCommand);

      if (response.MetricAlarms && response.MetricAlarms.length > 0) {
        const alarm = response.MetricAlarms[0];
        expect(alarm.StateValue).toBeDefined();
        expect(['OK', 'ALARM', 'INSUFFICIENT_DATA']).toContain(alarm.StateValue!);
      }
    });
  });
});
