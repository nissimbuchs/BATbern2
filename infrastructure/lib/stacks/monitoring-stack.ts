import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
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

    const isProd = props.config.isProduction ?? props.config.envName === 'production';

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

    // Create CloudWatch Alarms using reusable construct.
    // Not assigned: the construct is instantiated for its side effect (it adds the alarms to
    // this stack) and nothing here reads it back.
    new AlarmConstruct(this, 'Alarms', {
      environment: props.config.envName,
      alarmTopic: this.alarmTopic,
    });

    // Story 1.2.5: User Sync Alarms and Dashboard (ADR-001: Unidirectional sync monitoring)
    //
    // #1005: gated on `this.alarmTopic` as well as the environment. These seven identity alarms
    // used to publish to a private topic of their own, subscribed only by an unconfirmed email
    // to `admin@batbern.ch` — a mailbox nobody reads. SNS deletes unconfirmed subscriptions
    // after 3 days, so the topic ended up with ZERO subscribers and every one of these alarms
    // fired into nothing, while CloudFormation still held the subscription and therefore never
    // recreated it. They now publish to the shared alarm topic, whose primary subscriber is the
    // github-issues Lambda (no confirmation needed, ever) and which also carries the
    // nissim@buchs.be email subscription. That puts identity failures into the same
    // alarm -> issue -> @claude triage loop as the rest of the estate.
    if (props.config.envName !== 'development' && this.alarmTopic) {
      new UserSyncAlarms(this, 'UserSyncAlarms', {
        alarmTopic: this.alarmTopic,
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
    // evaluationPeriods=3 + datapointsToAlarm=2: requires 2 of 3 consecutive 5-min periods
    // above threshold. Prevents a single transactional email from triggering the alarm when
    // the rolling reputation score is temporarily elevated (e.g. after a large newsletter blast
    // with stale addresses). treatMissingData=NOT_BREACHING: periods with no sends count as OK.
    // Issue #984: this alarm used to watch `Reputation.BounceRate` at 3% and therefore fired
    // forever. That metric is a volume-weighted ROLLING reputation figure, and at BATbern's
    // send rate (~3/day, measured) a historical burst dominates it for months. Measured
    // 2026-08-25: a flat 0.0413 in every hourly datapoint, against the 0.03 threshold, with
    // ZERO bounces in the preceding seven weeks. Permanently over the line, permanently
    // non-actionable. The two emails on 2026-08-20 (ALARM 10:39, OK 10:45) were not a change
    // in conditions — Reputation.* publishes sparsely at this volume, so windows with two
    // datapoints breached and windows with gaps read OK, on repeat.
    //
    // What actually poisoned the denominator, from CloudWatch `Bounce` (Mar-Aug 2026) rather
    // than from the suppression list:
    //
    //   2026-03-05   462 bounces
    //   2026-05-04   465
    //   2026-05-19   772
    //   2026-06-01..06-25  a sustained 6-9/day plateau
    //   2026-07-01   181  (peaks of 43/h at 08:00 and 133/h at 22:00)
    //   2026-07-02    49  (45/h at 07:00)
    //   since then     0
    //
    // So FOUR bursts plus a month-long leak, not the single May event #984 inferred from the
    // 416-entry suppression list (which holds unique addresses, not events). The 2026-07-01
    // burst is the newsletter E2E incident.
    //
    // The fix is to watch the `Bounce` COUNT — new bounces in a window, which is the only
    // form of this signal anyone can act on. `Reputation.BounceRate` is kept, correctly, on
    // bounce-rate-critical below: 5% is the number AWS enforces on, and crossing it pauses
    // sending, which would silently kill speaker invitations.
    //
    // Verified the metric is real before wiring an alarm to it (a dead alarm is exactly the
    // #970 failure class): `AWS/SES` `Bounce` dimensionless does not appear in
    // `list-metrics`, but only because that call hides metrics idle for two weeks, and there
    // have been no bounces since 2026-07-02. `get-metric-statistics` over Mar-Aug returns the
    // datapoints above.
    //
    // Threshold sized against that history, not picked: > 5 in one hour clears natural
    // attrition (1-3/day) and catches every real burst within the hour it starts.
    //
    // NOT covered, deliberately: the slow leak. The June plateau of 6-9 bounces/day spread
    // over 24h would not reach 5 in any single hour. It is a different detector and a
    // different question (why is anything mailing real addresses nightly), and Reputation.
    // BounceRate -> bounce-rate-critical is the long-run backstop for it. Noted on #984.
    const bounceRateWarning = new cloudwatch.Alarm(this, 'BounceRateWarning', {
      alarmName: `batbern-${props.config.envName}-bounce-rate-warning`,
      alarmDescription:
        'More than 5 SES bounces in one hour — a list or send path is bouncing NOW ' +
        '(distinct from bounce-rate-critical, which watches the rolling reputation rate)',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Bounce',
        statistic: 'Sum',
        period: cdk.Duration.hours(1),
      }),
      threshold: 5,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    const bounceRateCritical = new cloudwatch.Alarm(this, 'BounceRateCritical', {
      alarmName: `batbern-${props.config.envName}-bounce-rate-critical`,
      alarmDescription:
        'SES bounce rate exceeds 5% critical threshold — sending may be paused by AWS',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/SES',
        metricName: 'Reputation.BounceRate',
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 0.05,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
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
      // Issue #969: SES publishes Reputation.ComplaintRate only while there is sending
      // activity. With the CDK default (MISSING) the alarm drops to INSUFFICIENT_DATA in
      // every quiet window and flips back to OK on the next send — firing the OK action
      // each time (2-3 notification emails/day at a measured complaint rate of 0.0).
      // NOT_BREACHING makes a no-send period count as OK, so no transition occurs.
      // Both bounce-rate alarms above already do this; this one was the lone omission.
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    if (this.alarmTopic) {
      // Issue #956: OK actions are as important as ALARM actions here. The
      // github-issues Lambda already implements close-on-recovery
      // (lambda/github-issues-integration/index.ts → closeIssue), but it can only run if
      // the alarm actually publishes on the OK transition. Without addOkAction the
      // recovery notification is never sent, so every alarm issue stays open forever —
      // #484 sat open for ~2 months and #648 for ~3.5 weeks after their alarms recovered.
      // The Lambda reopens the SAME issue on a re-ALARM, so a flapping alarm churns one
      // issue rather than accumulating new ones.
      const snsAction = new cloudwatchActions.SnsAction(this.alarmTopic);
      for (const alarm of [bounceRateWarning, bounceRateCritical, complaintRateCritical]) {
        alarm.addAlarmAction(snsAction);
        alarm.addOkAction(snsAction);
      }
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
        // Issue #969: SQS publishes ApproximateNumberOfMessagesVisible only while the
        // queue is active. Without this the alarm oscillates INSUFFICIENT_DATA <-> OK
        // and fires the OK action (an email) on every recovery. An empty DLQ is healthy.
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
        comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      });

      if (this.alarmTopic) {
        const dlqSnsAction = new cloudwatchActions.SnsAction(this.alarmTopic);
        dlqAlarm.addAlarmAction(dlqSnsAction);
        dlqAlarm.addOkAction(dlqSnsAction); // see #956 note above
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
