import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatch_actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sns_subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';

/**
 * CloudWatch Alarms for User Sync Monitoring
 *
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC5: Monitoring alerts fire on creation failures
 *
 * Alarms:
 * - User creation failures exceeding threshold
 * - Lambda latency exceeding threshold
 * - Drift detection above threshold
 * - Sync failures
 *
 * ADR-001: Monitors unidirectional sync (Cognito → Database)
 */
export interface UserSyncAlarmsProps {
  /**
   * Email address for alarm notifications
   */
  readonly alarmEmail: string;

  /**
   * Environment name (dev, staging, prod)
   */
  readonly environment: string;

  /**
   * Optional: Custom alarm thresholds
   */
  readonly thresholds?: {
    readonly userCreationFailures?: number;
    readonly lambdaLatencyMs?: number;
    readonly driftCount?: number;
  };
}

export class UserSyncAlarms extends Construct {
  public readonly alarmTopic: sns.Topic;

  constructor(scope: Construct, id: string, props: UserSyncAlarmsProps) {
    super(scope, id);

    // Default thresholds
    const thresholds = {
      userCreationFailures: props.thresholds?.userCreationFailures ?? 5,
      lambdaLatencyMs: props.thresholds?.lambdaLatencyMs ?? 2000,
      driftCount: props.thresholds?.driftCount ?? 10,
    };

    // Create SNS topic for alarm notifications
    this.alarmTopic = new sns.Topic(this, 'UserSyncAlarmTopic', {
      displayName: `BATbern User Sync Alarms - ${props.environment}`,
      topicName: `batbern-user-sync-alarms-${props.environment}`,
    });

    // Subscribe email to SNS topic
    this.alarmTopic.addSubscription(
      new sns_subscriptions.EmailSubscription(props.alarmEmail)
    );

    // Create CloudWatch alarm action
    const alarmAction = new cloudwatch_actions.SnsAction(this.alarmTopic);

    // Alarm 1: PostConfirmation Lambda Latency
    const postConfirmationLatencyAlarm = new cloudwatch.Alarm(
      this,
      'PostConfirmationLatencyAlarm',
      {
        alarmName: `${props.environment}-PostConfirmation-High-Latency`,
        alarmDescription:
          'PostConfirmation Lambda latency exceeds 2 seconds (average over 5 minutes)',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'SyncLatency',
          dimensionsMap: {
            SyncType: 'PostConfirmation',
          },
          statistic: 'Average',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.lambdaLatencyMs,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    postConfirmationLatencyAlarm.addAlarmAction(alarmAction);

    // Alarm 2: PreTokenGeneration Lambda Latency
    const preTokenGenerationLatencyAlarm = new cloudwatch.Alarm(
      this,
      'PreTokenGenerationLatencyAlarm',
      {
        alarmName: `${props.environment}-PreTokenGeneration-High-Latency`,
        alarmDescription:
          'PreTokenGeneration Lambda latency exceeds 500ms (average over 5 minutes)',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'SyncLatency',
          dimensionsMap: {
            SyncType: 'PreTokenGeneration',
          },
          statistic: 'Average',
          period: cdk.Duration.minutes(5),
        }),
        threshold: 500, // 500ms for PreTokenGeneration (stricter than PostConfirmation)
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    preTokenGenerationLatencyAlarm.addAlarmAction(alarmAction);

    // Alarm 3: User Creation Failures
    const userCreationFailuresAlarm = new cloudwatch.Alarm(
      this,
      'UserCreationFailuresAlarm',
      {
        alarmName: `${props.environment}-User-Creation-High-Failures`,
        alarmDescription:
          'User creation failures exceed 5 per 5-minute window',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'SyncFailures',
          dimensionsMap: {
            SyncType: 'PostConfirmation',
          },
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.userCreationFailures,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    userCreationFailuresAlarm.addAlarmAction(alarmAction);

    // Alarm 4: JIT Provisioning Failures
    const jitProvisioningFailuresAlarm = new cloudwatch.Alarm(
      this,
      'JITProvisioningFailuresAlarm',
      {
        alarmName: `${props.environment}-JIT-Provisioning-High-Failures`,
        alarmDescription:
          'JIT provisioning failures exceed 5 per 5-minute window',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'SyncFailures',
          dimensionsMap: {
            SyncType: 'JITProvisioning',
          },
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.userCreationFailures,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    jitProvisioningFailuresAlarm.addAlarmAction(alarmAction);

    // Alarm 5: Drift Detection (Reconciliation)
    const driftDetectionAlarm = new cloudwatch.Alarm(
      this,
      'DriftDetectionAlarm',
      {
        alarmName: `${props.environment}-User-Sync-High-Drift`,
        alarmDescription:
          'User sync drift detected (Cognito vs Database mismatch)',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'DriftDetected',
          statistic: 'Sum',
          period: cdk.Duration.hours(1),
        }),
        threshold: thresholds.driftCount,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    driftDetectionAlarm.addAlarmAction(alarmAction);

    // Alarm 6: Reconciliation Job Failures
    const reconciliationFailuresAlarm = new cloudwatch.Alarm(
      this,
      'ReconciliationFailuresAlarm',
      {
        alarmName: `${props.environment}-Reconciliation-Orphaned-Users`,
        alarmDescription:
          'High number of orphaned users detected (Cognito users deleted)',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'ReconciliationOrphanedUsers',
          statistic: 'Sum',
          period: cdk.Duration.days(1),
        }),
        threshold: 5,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    reconciliationFailuresAlarm.addAlarmAction(alarmAction);

    // Output alarm topic ARN
    new cdk.CfnOutput(this, 'AlarmTopicArn', {
      value: this.alarmTopic.topicArn,
      description: 'SNS topic ARN for user sync alarms',
      exportName: `${props.environment}-UserSyncAlarmTopicArn`,
    });

    // Output alarm names
    new cdk.CfnOutput(this, 'AlarmNames', {
      value: [
        postConfirmationLatencyAlarm.alarmName,
        preTokenGenerationLatencyAlarm.alarmName,
        userCreationFailuresAlarm.alarmName,
        jitProvisioningFailuresAlarm.alarmName,
        driftDetectionAlarm.alarmName,
        reconciliationFailuresAlarm.alarmName,
      ].join(','),
      description: 'CloudWatch alarm names for user sync monitoring',
    });
  }
}
