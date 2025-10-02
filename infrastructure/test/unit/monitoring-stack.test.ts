import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { MonitoringStack } from '../../lib/stacks/monitoring-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

describe('MonitoringStack', () => {
  describe('CloudWatch Dashboard Creation', () => {
    test('should_createCloudWatchDashboard_when_monitoringStackDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify CloudWatch dashboard exists
      template.resourceCountIs('AWS::CloudWatch::Dashboard', 1);
    });
  });

  describe('CloudWatch Alarms', () => {
    test('should_createAlarms_when_monitoringStackDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify alarms are created (high CPU, errors, latency)
      template.resourceCountIs('AWS::CloudWatch::Alarm', 3);
    });

    test('should_configureAlarmActions_when_productionEnvironment', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify SNS topic for alarm notifications
      template.resourceCountIs('AWS::SNS::Topic', 1);
    });
  });

  describe('Log Groups', () => {
    test('should_createLogGroups_when_monitoringStackDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify log groups for application and infrastructure
      template.resourceCountIs('AWS::Logs::LogGroup', 2);
    });

    test('should_configureLogRetention_when_logGroupsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new MonitoringStack(app, 'TestMonitoringStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify log retention is configured (30 days for dev)
      template.hasResourceProperties('AWS::Logs::LogGroup', {
        RetentionInDays: 30,
      });
    });
  });
});
