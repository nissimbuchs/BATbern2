import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatch_actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

/**
 * CloudWatch Alarms for ECS Service Monitoring
 *
 * Platform Stability Improvements (Phase 3)
 * Monitors memory pressure, OOM kills, task failures, and EventBridge publishing
 *
 * Alarms:
 * - High memory utilization (80% threshold)
 * - OOM kill detection (exit code 137)
 * - Task failure rate (abnormal restarts)
 * - EventBridge publishing failures
 */
export interface EcsServiceAlarmsProps {
  /**
   * Environment name (development, staging, production)
   */
  readonly environment: string;

  /**
   * ECS cluster name
   */
  readonly clusterName: string;

  /**
   * ECS service name
   */
  readonly serviceName: string;

  /**
   * Display name for the service (used in alarm descriptions)
   * Must be a literal string, not a CDK token
   */
  readonly serviceDisplayName: string;

  /**
   * Whether Container Insights is enabled on the ECS cluster
   * OOM kill detection requires Container Insights log group
   * @default false
   */
  readonly containerInsightsEnabled?: boolean;

  /**
   * SNS topic for alarm notifications
   */
  readonly alarmTopic: sns.ITopic;

  /**
   * Optional: Custom alarm thresholds
   */
  readonly thresholds?: {
    readonly memoryUtilization?: number;
    readonly oomKillCount?: number;
    readonly taskFailureCount?: number;
    readonly eventBridgePublishingFailures?: number;
  };
}

export class EcsServiceAlarms extends Construct {
  constructor(scope: Construct, id: string, props: EcsServiceAlarmsProps) {
    super(scope, id);

    // Default thresholds - stricter for production
    const isProduction = props.environment === 'production';
    const thresholds = {
      memoryUtilization: props.thresholds?.memoryUtilization ?? 80,
      oomKillCount: props.thresholds?.oomKillCount ?? (isProduction ? 1 : 3),
      taskFailureCount:
        props.thresholds?.taskFailureCount ?? (isProduction ? 2 : 5),
      eventBridgePublishingFailures:
        props.thresholds?.eventBridgePublishingFailures ??
        (isProduction ? 5 : 10),
    };

    // Create CloudWatch alarm action
    const alarmAction = new cloudwatch_actions.SnsAction(props.alarmTopic);

    // Use serviceDisplayName for alarm names and descriptions (guaranteed to be a literal string)
    const serviceDisplayName = props.serviceDisplayName;

    // Alarm 1: High Memory Utilization
    const memoryUtilizationAlarm = new cloudwatch.Alarm(
      this,
      'HighMemoryUtilization',
      {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-High-Memory`,
        alarmDescription: `${serviceDisplayName} memory utilization exceeds ${thresholds.memoryUtilization}% (average over 5 minutes)`,
        metric: new cloudwatch.Metric({
          namespace: 'AWS/ECS',
          metricName: 'MemoryUtilization',
          dimensionsMap: {
            ServiceName: props.serviceName,
            ClusterName: props.clusterName,
          },
          statistic: 'Average',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.memoryUtilization,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    memoryUtilizationAlarm.addAlarmAction(alarmAction);

    // Alarm 2: OOM Kill Detection (exit code 137)
    // IMPORTANT: Only create if Container Insights is enabled
    // Container Insights creates the log group /aws/ecs/containerinsights/${clusterName}/performance
    // This log group does NOT exist if Container Insights is disabled (e.g., staging environment)
    let oomKillAlarm: cloudwatch.Alarm | undefined;

    if (props.containerInsightsEnabled) {
      const logGroupName = `/aws/ecs/containerinsights/${props.clusterName}/performance`;

      const oomMetricFilter = new logs.MetricFilter(this, 'OOMKillMetricFilter', {
        logGroup: logs.LogGroup.fromLogGroupName(
          this,
          'ContainerInsightsLogGroup',
          logGroupName
        ),
        metricNamespace: 'BATbern/ECS',
        metricName: 'OOMKills',
        filterPattern: logs.FilterPattern.allTerms(
          props.serviceName,
          'exit',
          'code',
          '137'
        ),
        metricValue: '1',
        defaultValue: 0,
        dimensions: {
          ServiceName: props.serviceName,
          ClusterName: props.clusterName,
        },
      });

      oomKillAlarm = new cloudwatch.Alarm(this, 'OOMKillDetection', {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-OOM-Kills`,
        alarmDescription: `${serviceDisplayName} experienced OOM kill (exit code 137) - memory limit reached`,
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/ECS',
          metricName: 'OOMKills',
          dimensionsMap: {
            ServiceName: props.serviceName,
            ClusterName: props.clusterName,
          },
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.oomKillCount,
        evaluationPeriods: 1,
        comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });
      oomKillAlarm.addAlarmAction(alarmAction);
      oomKillAlarm.node.addDependency(oomMetricFilter);
    }

    // Alarm 3: Task Failure Rate (abnormal task stops)
    const taskFailureAlarm = new cloudwatch.Alarm(
      this,
      'HighTaskFailureRate',
      {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-Task-Failures`,
        alarmDescription: `${serviceDisplayName} experiencing abnormal task restarts (>${thresholds.taskFailureCount} failures per 15 minutes)`,
        metric: new cloudwatch.Metric({
          namespace: 'AWS/ECS',
          metricName: 'TaskCount',
          dimensionsMap: {
            ServiceName: props.serviceName,
            ClusterName: props.clusterName,
          },
          statistic: 'SampleCount',
          period: cdk.Duration.minutes(15),
        }),
        threshold: thresholds.taskFailureCount,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    taskFailureAlarm.addAlarmAction(alarmAction);

    // Alarm 4: EventBridge Publishing Failures
    const eventBridgeFailuresAlarm = new cloudwatch.Alarm(
      this,
      'EventBridgePublishingFailures',
      {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-EventBridge-Failures`,
        alarmDescription: `${serviceDisplayName} experiencing EventBridge publishing failures (>${thresholds.eventBridgePublishingFailures} per 5 minutes)`,
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/EventBridge',
          metricName: 'PublishingFailures',
          dimensionsMap: {
            ServiceName: serviceDisplayName,
          },
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.eventBridgePublishingFailures,
        evaluationPeriods: 1,
        comparisonOperator:
          cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      }
    );
    eventBridgeFailuresAlarm.addAlarmAction(alarmAction);

    // Output alarm names for reference
    const alarmNames = [
      memoryUtilizationAlarm.alarmName,
      ...(oomKillAlarm ? [oomKillAlarm.alarmName] : []),
      taskFailureAlarm.alarmName,
      eventBridgeFailuresAlarm.alarmName,
    ];

    new cdk.CfnOutput(this, 'AlarmNames', {
      value: alarmNames.join(','),
      description: `CloudWatch alarm names for ${serviceDisplayName} monitoring`,
    });
  }
}
