import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatch_actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
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
   * The shared alarm topic to publish to.
   *
   * #1005: this construct used to create its OWN topic
   * (`batbern-user-sync-alarms-{env}`) and subscribe an email to it. SNS email subscriptions
   * must be confirmed by clicking a link and are deleted after 3 days if they are not, so the
   * subscription silently expired and the topic ended up with ZERO subscribers — all seven
   * alarms below fired into nothing, while CloudFormation still held the subscription resource
   * and therefore never recreated it.
   *
   * Publishing to the shared `batbern-{env}-alarms` topic removes the confirmation dependency
   * entirely (its primary subscriber is the github-issues Lambda, and Lambda subscriptions need
   * no confirmation) and puts these alarms into the same
   * `SNS -> issue -> @claude triage` loop as every other alarm in the estate.
   */
  readonly alarmTopic: sns.ITopic;

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
  public readonly alarmTopic: sns.ITopic;

  constructor(scope: Construct, id: string, props: UserSyncAlarmsProps) {
    super(scope, id);

    // Default thresholds
    const thresholds = {
      userCreationFailures: props.thresholds?.userCreationFailures ?? 5,
      lambdaLatencyMs: props.thresholds?.lambdaLatencyMs ?? 2000,
      driftCount: props.thresholds?.driftCount ?? 10,
    };

    // #1005: publish to the shared alarm topic rather than a private one of our own. See the
    // note on UserSyncAlarmsProps.alarmTopic for why the private topic was a delivery defect.
    // Removing it deletes `batbern-user-sync-alarms-{env}`, which is intended — nothing else
    // publishes to or subscribes to it.
    this.alarmTopic = props.alarmTopic;

    // Create CloudWatch alarm action
    const alarmAction = new cloudwatch_actions.SnsAction(this.alarmTopic);

    // Alarm 1: PostConfirmation Lambda Latency
    const postConfirmationLatencyAlarm = new cloudwatch.Alarm(
      this,
      'PostConfirmationLatencyAlarm',
      {
        alarmName: `batbern-${props.environment}-PostConfirmation-High-Latency`,
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
    postConfirmationLatencyAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 2: PreTokenGeneration Lambda Latency
    const preTokenGenerationLatencyAlarm = new cloudwatch.Alarm(
      this,
      'PreTokenGenerationLatencyAlarm',
      {
        alarmName: `batbern-${props.environment}-PreTokenGeneration-High-Latency`,
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
    preTokenGenerationLatencyAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 3: User Creation Failures
    const userCreationFailuresAlarm = new cloudwatch.Alarm(
      this,
      'UserCreationFailuresAlarm',
      {
        alarmName: `batbern-${props.environment}-User-Creation-High-Failures`,
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
    userCreationFailuresAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 4: JIT Provisioning Failures
    const jitProvisioningFailuresAlarm = new cloudwatch.Alarm(
      this,
      'JITProvisioningFailuresAlarm',
      {
        alarmName: `batbern-${props.environment}-JIT-Provisioning-High-Failures`,
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
    jitProvisioningFailuresAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 5: Drift Detection (Reconciliation)
    const driftDetectionAlarm = new cloudwatch.Alarm(
      this,
      'DriftDetectionAlarm',
      {
        alarmName: `batbern-${props.environment}-User-Sync-High-Drift`,
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
    driftDetectionAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 6: Reconciliation Job Failures
    const reconciliationFailuresAlarm = new cloudwatch.Alarm(
      this,
      'ReconciliationFailuresAlarm',
      {
        alarmName: `batbern-${props.environment}-Reconciliation-Orphaned-Users`,
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
    reconciliationFailuresAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 7: PreSignUp Account-Linking Failures (Story 12.6 — SSO Phase 2)
    // The PreSignUp federated path NEVER throws (a throw 503s the sign-in), so a failed
    // AdminLinkProviderForUser is fail-open: the federated user is still auto-confirmed but
    // ends up as an UNLINKED standalone identity (orphaned from the native sub) — visible ONLY
    // as this metric. The alarm makes that silent orphan actionable. Emitted (un-dimensioned)
    // by pre-signup.ts publishMetric('PreSignUpFailure'). threshold 0 → any failure pages.
    const preSignUpFailuresAlarm = new cloudwatch.Alarm(
      this,
      'PreSignUpFailuresAlarm',
      {
        alarmName: `batbern-${props.environment}-PreSignUp-Linking-Failures`,
        alarmDescription:
          'PreSignUp federated account-linking failed (fail-open) — a federated user may be orphaned from its native sub',
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/UserSync',
          metricName: 'PreSignUpFailure',
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: 0,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    preSignUpFailuresAlarm.addAlarmAction(alarmAction);
    preSignUpFailuresAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

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
        preSignUpFailuresAlarm.alarmName,
      ].join(','),
      description: 'CloudWatch alarm names for user sync monitoring',
    });
  }
}
