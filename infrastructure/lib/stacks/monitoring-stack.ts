import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { MonitoringWidgetsConstruct } from '../constructs/monitoring-widgets-construct';
import { AlarmConstruct } from '../constructs/alarm-construct';
import { GitHubIssuesConstruct } from '../constructs/github-issues-construct';
import { UserSyncAlarms } from '../constructs/user-sync-alarms';
import { UserSyncDashboard } from '../constructs/user-sync-dashboard';

export interface MonitoringStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  enableGitHubIssues?: boolean;
  githubOwner?: string;
  githubRepo?: string;
  /** Story 10.29: DLQ name for bounce processing — enables DLQ visibility alarm. */
  bounceProcessingDlqName?: string;
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

    const isProd = props.config.isProduction ?? (props.config.envName === 'production');

    // Create SNS topic for alarm notifications (production and staging)
    if (isProd || props.config.envName === 'staging') {
      this.alarmTopic = new sns.Topic(this, 'AlarmTopic', {
        topicName: `batbern-${props.config.envName}-alarms`,
        displayName: `BATbern ${props.config.envName} Alarms`,
      });

      // GitHub Issues integration - automatically create issues from alarms
      if (props.enableGitHubIssues !== false) {
        new GitHubIssuesConstruct(this, 'GitHubIssues', {
          alarmTopic: this.alarmTopic,
          environment: props.config.envName,
          githubOwner: props.githubOwner,
          githubRepo: props.githubRepo,
        });
      }

      // Optional: Add email subscriptions via environment variables
      // this.alarmTopic.addSubscription(
      //   new subscriptions.EmailSubscription(process.env.ALARM_EMAIL!)
      // );
    }

    // Application log group
    // Cost optimization: 7 days for non-prod, 30 days for production
    this.applicationLogGroup = new logs.LogGroup(this, 'ApplicationLogGroup', {
      logGroupName: `/aws/logs/BATbern-${props.config.envName}/application`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Infrastructure log group
    // Cost optimization: 7 days for non-prod, 30 days for production
    this.infrastructureLogGroup = new logs.LogGroup(this, 'InfrastructureLogGroup', {
      logGroupName: `/aws/logs/BATbern-${props.config.envName}/infrastructure`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
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

    // Story 1.2.5: User Sync Alarms and Dashboard (ADR-001: Unidirectional sync monitoring)
    if (props.config.envName !== 'development') {
      const alarmEmail = process.env.ALARM_EMAIL || `admin@batbern.ch`;

      const userSyncAlarms = new UserSyncAlarms(this, 'UserSyncAlarms', {
        alarmEmail,
        environment: props.config.envName,
        thresholds: {
          userCreationFailures: props.config.envName === 'production' ? 5 : 10,
          lambdaLatencyMs: props.config.envName === 'production' ? 2000 : 3000,
          driftCount: props.config.envName === 'production' ? 10 : 20,
        },
      });

      const userSyncDashboard = new UserSyncDashboard(this, 'UserSyncDashboard', {
        environment: props.config.envName,
      });

      // Output user sync dashboard URL
      new cdk.CfnOutput(this, 'UserSyncDashboardUrl', {
        value: `https://console.aws.amazon.com/cloudwatch/home?region=${props.config.region}#dashboards:name=${userSyncDashboard.dashboard.dashboardName}`,
        description: 'CloudWatch Dashboard URL for User Sync monitoring (ADR-001)',
        exportName: `${props.config.envName}-UserSyncDashboardUrl`,
      });
    }

    // Story 10.29 AC9: SES Bounce/Complaint Rate Alarms
    const bounceRateWarning = new cloudwatch.Alarm(this, 'BounceRateWarning', {
      alarmName: `batbern-${props.config.envName}-bounce-rate-warning`,
      alarmDescription: 'SES bounce rate exceeds 3% warning threshold',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Reputation.BounceRate',
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 0.03,
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    const bounceRateCritical = new cloudwatch.Alarm(this, 'BounceRateCritical', {
      alarmName: `batbern-${props.config.envName}-bounce-rate-critical`,
      alarmDescription: 'SES bounce rate exceeds 5% critical threshold — sending may be paused by AWS',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Reputation.BounceRate',
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 0.05,
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    const complaintRateCritical = new cloudwatch.Alarm(this, 'ComplaintRateCritical', {
      alarmName: `batbern-${props.config.envName}-complaint-rate-critical`,
      alarmDescription: 'SES complaint rate exceeds 0.05% — sending may be paused by AWS',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Reputation.ComplaintRate',
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 0.0005,
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    if (this.alarmTopic) {
      const snsAction = new cloudwatchActions.SnsAction(this.alarmTopic);
      bounceRateWarning.addAlarmAction(snsAction);
      bounceRateCritical.addAlarmAction(snsAction);
      complaintRateCritical.addAlarmAction(snsAction);
    }

    // Story 10.29 AC9: Bounce processing DLQ alarm (when DLQ name provided)
    if (props.bounceProcessingDlqName) {
      const dlqAlarm = new cloudwatch.Alarm(this, 'BounceProcessingDLQAlarm', {
        alarmName: `batbern-${props.config.envName}-bounce-processing-dlq`,
        alarmDescription: 'Bounce processing failures detected — messages in DLQ',
        metric: new cloudwatch.Metric({
          namespace: 'AWS/SQS',
          metricName: 'ApproximateNumberOfMessagesVisible',
          dimensionsMap: { QueueName: props.bounceProcessingDlqName },
          statistic: 'Maximum',
          period: cdk.Duration.minutes(1),
        }),
        threshold: 0,
        evaluationPeriods: 1,
        comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      });

      if (this.alarmTopic) {
        dlqAlarm.addAlarmAction(new cloudwatchActions.SnsAction(this.alarmTopic));
      }
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
