import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { MonitoringWidgetsConstruct } from '../constructs/monitoring-widgets-construct';
import { AlarmConstruct } from '../constructs/alarm-construct';

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
    // Reduced to 30 days for cost optimization (low traffic application)
    this.applicationLogGroup = new logs.LogGroup(this, 'ApplicationLogGroup', {
      logGroupName: `/batbern/${props.config.envName}/application`,
      retention: logs.RetentionDays.ONE_MONTH, // 30 days for all environments
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // Infrastructure log group
    // Reduced to 30 days for cost optimization (low traffic application)
    this.infrastructureLogGroup = new logs.LogGroup(this, 'InfrastructureLogGroup', {
      logGroupName: `/batbern/${props.config.envName}/infrastructure`,
      retention: logs.RetentionDays.ONE_MONTH, // 30 days for all environments
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // CloudWatch Dashboard
    this.dashboard = new cloudwatch.Dashboard(this, 'Dashboard', {
      dashboardName: `BATbern-${props.config.envName}`,
    });

    // Add dashboard header widget
    this.dashboard.addWidgets(
      new cloudwatch.TextWidget({
        markdown: `# BATbern Platform - ${props.config.envName} Environment\n\nReal-time monitoring dashboard for infrastructure and application metrics.`,
        width: 24,
        height: 2,
      })
    );

    // Use reusable construct for monitoring widgets
    const monitoringWidgets = new MonitoringWidgetsConstruct(this, 'MonitoringWidgets', {
      environment: props.config.envName,
      dashboardName: this.dashboard.dashboardName,
    });

    // Add all widgets from the construct to the dashboard
    this.dashboard.addWidgets(...monitoringWidgets.widgets);

    // Create CloudWatch Alarms using reusable construct
    const alarms = new AlarmConstruct(this, 'Alarms', {
      environment: props.config.envName,
      alarmTopic: this.alarmTopic,
    });

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
