import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface MonitoringStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * Monitoring Stack - Provides CloudWatch dashboards, alarms, and log aggregation
 *
 * Implements monitoring and observability for the BATbern platform
 */
export class MonitoringStack extends cdk.Stack {
  public readonly dashboard: cloudwatch.Dashboard;
  public readonly alarmTopic?: sns.Topic;
  public readonly applicationLogGroup: logs.LogGroup;
  public readonly infrastructureLogGroup: logs.LogGroup;

  constructor(scope: Construct, id: string, props: MonitoringStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';

    // Create SNS topic for alarm notifications (production only)
    if (isProd) {
      this.alarmTopic = new sns.Topic(this, 'AlarmTopic', {
        topicName: `batbern-${props.config.envName}-alarms`,
        displayName: `BATbern ${props.config.envName} Alarms`,
      });

      // TODO: Add email subscriptions via environment variables
      // this.alarmTopic.addSubscription(
      //   new subscriptions.EmailSubscription(process.env.ALARM_EMAIL!)
      // );
    }

    // Application log group
    this.applicationLogGroup = new logs.LogGroup(this, 'ApplicationLogGroup', {
      logGroupName: `/batbern/${props.config.envName}/application`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // Infrastructure log group
    this.infrastructureLogGroup = new logs.LogGroup(this, 'InfrastructureLogGroup', {
      logGroupName: `/batbern/${props.config.envName}/infrastructure`,
      retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // CloudWatch Dashboard
    this.dashboard = new cloudwatch.Dashboard(this, 'Dashboard', {
      dashboardName: `BATbern-${props.config.envName}`,
    });

    // Add dashboard widgets
    this.dashboard.addWidgets(
      new cloudwatch.TextWidget({
        markdown: `# BATbern Platform - ${props.config.envName} Environment\n\nReal-time monitoring dashboard for infrastructure and application metrics.`,
        width: 24,
        height: 2,
      })
    );

    // Create CloudWatch Alarms

    // High CPU Alarm (placeholder - will be connected to actual resources)
    const highCpuAlarm = new cloudwatch.Alarm(this, 'HighCpuAlarm', {
      alarmName: `batbern-${props.config.envName}-high-cpu`,
      alarmDescription: 'Alert when CPU utilization is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ECS',
        metricName: 'CPUUtilization',
        dimensionsMap: {
          ServiceName: `batbern-${props.config.envName}`,
        },
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 80,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // High Error Rate Alarm (placeholder)
    const highErrorAlarm = new cloudwatch.Alarm(this, 'HighErrorAlarm', {
      alarmName: `batbern-${props.config.envName}-high-errors`,
      alarmDescription: 'Alert when error rate is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApiGateway',
        metricName: '5XXError',
        dimensionsMap: {
          ApiName: `batbern-${props.config.envName}`,
        },
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 10,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // High Latency Alarm (placeholder)
    const highLatencyAlarm = new cloudwatch.Alarm(this, 'HighLatencyAlarm', {
      alarmName: `batbern-${props.config.envName}-high-latency`,
      alarmDescription: 'Alert when API latency is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApiGateway',
        metricName: 'Latency',
        dimensionsMap: {
          ApiName: `batbern-${props.config.envName}`,
        },
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 1000, // 1 second
      evaluationPeriods: 3,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // Add alarm actions for production
    if (this.alarmTopic) {
      highCpuAlarm.addAlarmAction(new cdk.aws_cloudwatch_actions.SnsAction(this.alarmTopic));
      highErrorAlarm.addAlarmAction(new cdk.aws_cloudwatch_actions.SnsAction(this.alarmTopic));
      highLatencyAlarm.addAlarmAction(new cdk.aws_cloudwatch_actions.SnsAction(this.alarmTopic));
    }

    // Apply tags
    cdk.Tags.of(this).add('Environment', props.config.envName);
    cdk.Tags.of(this).add('Component', 'Monitoring');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'DashboardUrl', {
      value: `https://console.aws.amazon.com/cloudwatch/home?region=${props.config.region}#dashboards:name=${this.dashboard.dashboardName}`,
      description: 'CloudWatch Dashboard URL',
      exportName: `${props.config.envName}-DashboardUrl`,
    });

    new cdk.CfnOutput(this, 'ApplicationLogGroupName', {
      value: this.applicationLogGroup.logGroupName,
      description: 'Application log group name',
      exportName: `${props.config.envName}-ApplicationLogGroup`,
    });

    if (this.alarmTopic) {
      new cdk.CfnOutput(this, 'AlarmTopicArn', {
        value: this.alarmTopic.topicArn,
        description: 'SNS topic for alarms',
        exportName: `${props.config.envName}-AlarmTopicArn`,
      });
    }
  }
}
