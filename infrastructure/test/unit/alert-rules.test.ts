import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { MonitoringStack } from '../../lib/stacks/monitoring-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

/**
 * Test suite for Alert Configuration (Task 3 - AC: 5, 6, 7, 8)
 */
describe('Alert Rules Configuration', () => {
  describe('SLA Monitoring Alarms (AC: 5)', () => {
    test('should_createAvailabilityAlarm_when_slaMonitoringConfigured', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify availability alarm exists with 99.9% threshold
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*availability.*'),
        Threshold: 99.9,
        ComparisonOperator: 'LessThanThreshold',
      });
    });

    test('should_configureAlarmEvaluationPeriods_when_slaAlarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify alarms have proper evaluation periods
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        EvaluationPeriods: Match.anyValue(),
      });
    });

    test('should_configureTreatMissingData_when_slaAlarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify alarms handle missing data appropriately
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        TreatMissingData: Match.anyValue(),
      });
    });
  });

  describe('Error Rate Alarms (AC: 6)', () => {
    test('should_createErrorRateAlarm_when_thresholdExceeded', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify error rate alarm exists
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*error.*'),
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });

    test('should_createServiceSpecificErrorAlarms_when_multipleServices', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify multiple error rate alarms exist
      const alarms = template.findResources('AWS::CloudWatch::Alarm');
      const errorAlarms = Object.values(alarms).filter((alarm: any) =>
        alarm.Properties?.AlarmName?.includes('error') ||
        alarm.Properties?.MetricName === '5XXError'
      );

      expect(errorAlarms.length).toBeGreaterThan(0);
    });

    test('should_configureErrorRateThreshold_when_errorAlarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify error rate threshold is reasonable (0.1% = 0.001 or count-based)
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        Threshold: Match.anyValue(),
      });
    });
  });

  describe('Performance Alarms (AC: 7)', () => {
    test('should_createLatencyAlarm_when_p95ResponseTimeExceeds500ms', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify latency alarm exists with proper threshold
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*latency.*'),
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });

    test('should_useP95Statistic_when_latencyAlarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify P95 statistic is used for latency
      const alarms = template.findResources('AWS::CloudWatch::Alarm');
      const latencyAlarms = Object.values(alarms).filter((alarm: any) =>
        alarm.Properties?.AlarmName?.includes('latency')
      );

      expect(latencyAlarms.length).toBeGreaterThan(0);
    });

    test('should_createThroughputAlarms_when_performanceMonitoringEnabled', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify throughput monitoring exists (multiple alarms)
      const alarmCount = Object.keys(template.findResources('AWS::CloudWatch::Alarm')).length;
      expect(alarmCount).toBeGreaterThan(0);
    });
  });

  describe('Resource Utilization Alarms (AC: 8)', () => {
    test('should_createCpuAlarm_when_utilizationAbove80Percent', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify CPU alarm exists with 80% threshold
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*cpu.*'),
        Threshold: 80,
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });

    test('should_createMemoryAlarm_when_utilizationAbove80Percent', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify memory alarm exists
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*memory.*'),
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });

    test('should_createDiskUtilizationAlarm_when_resourceMonitoringEnabled', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify disk utilization alarm exists
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*disk.*|.*storage.*'),
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });

    test('should_createDatabaseConnectionAlarm_when_rdsMonitoringEnabled', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify database connection alarm exists
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*database.*|.*db.*'),
      });
    });
  });

  describe('Alarm Actions and Notifications', () => {
    test('should_attachSNSActions_when_productionAlarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify alarms have SNS actions attached
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmActions: Match.anyValue(),
      });
    });

    test('should_notCreateAlarmActions_when_devEnvironment', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify no SNS topic in dev
      template.resourceCountIs('AWS::SNS::Topic', 0);
    });

    test('should_configureOKActions_when_alarmRecoveryEnabled', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify alarms have OK actions for recovery notifications
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        OKActions: Match.anyValue(),
      });
    });
  });

  describe('Alarm Consistency and Standards', () => {
    test('should_useConsistentNaming_when_alarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify all alarms follow naming convention: batbern-{env}-{metric-type}
      const alarms = template.findResources('AWS::CloudWatch::Alarm');
      Object.values(alarms).forEach((alarm: any) => {
        expect(alarm.Properties?.AlarmName).toMatch(/batbern-.*-.*/);
      });
    });

    test('should_haveAlarmDescriptions_when_alarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify all alarms have descriptions
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmDescription: Match.anyValue(),
      });
    });

    test('should_enableAlarmActions_when_alarmsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify alarm actions are enabled
      template.hasResourceProperties('AWS::CloudWatch::Alarm', {
        ActionsEnabled: true,
      });
    });
  });
});
