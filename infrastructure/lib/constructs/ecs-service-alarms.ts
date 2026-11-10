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
 * Monitors memory pressure, OOM kills and task failures
 *
 * Alarms:
 * - High memory utilization (80% threshold)
 * - OOM kill detection (exit code 137)
 * - Task failure rate (abnormal restarts)
 * - Domain event listener failures swallowed by the publisher (log based, with serviceLogGroup)
 * - Scheduled-job failures recorded by EMS ScheduledJobFailures (log based, opt-in)
 */
/** Namespace of the log-based domain-event metrics. */
export const DOMAIN_EVENTS_NAMESPACE = 'BATbern/DomainEvents';

/** Namespace of the log-based scheduled-job metrics. */
export const SCHEDULED_JOBS_NAMESPACE = 'BATbern/ScheduledJobs';

/** ScheduledJobFailures.LOG_MARKER (EMS): first token of its failure line. */
export const SCHEDULED_JOB_FAILURE_MARKER = 'SCHEDULED_JOB_FAILED';

/** SpringDomainEventPublisher.LOG_MARKER (shared-kernel): first token of its listener-failure line. */
export const DOMAIN_EVENT_LISTENER_FAILURE_MARKER = 'DOMAIN_EVENT_LISTENER_FAILED';

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
   * The service's own application log group (`/aws/ecs/BATbern-{env}/{service}`). When given,
   * the `{Service}-Event-Listener-Failures` alarm is created: a metric filter matches the fixed
   * marker `SpringDomainEventPublisher` (shared-kernel) writes when it swallows a failing domain
   * event listener. Pass it only for services that run that publisher (EMS, CUMS, PCS).
   */
  readonly serviceLogGroup?: logs.ILogGroup;

  /**
   * With serviceLogGroup: also create the `{Service}-Scheduled-Job-Failures` alarm on the marker
   * EMS's `ScheduledJobFailures` writes when a scheduled job skips an item it could not process
   * (Story 4.9b). Only for services that have that recorder (EMS).
   * @default false
   */
  readonly scheduledJobFailures?: boolean;

  /**
   * Override production detection (defaults to environment === 'production')
   */
  readonly isProduction?: boolean;

  /**
   * Optional: Custom alarm thresholds
   */
  readonly thresholds?: {
    readonly memoryUtilization?: number;
    /** CPU utilization percentage. Issue #970. */
    readonly cpuUtilization?: number;
    readonly oomKillCount?: number;
    readonly taskFailureCount?: number;
  };
}

export class EcsServiceAlarms extends Construct {
  constructor(scope: Construct, id: string, props: EcsServiceAlarmsProps) {
    super(scope, id);

    // Default thresholds - stricter for production
    const isProduction = props.isProduction ?? props.environment === 'production';
    const thresholds = {
      memoryUtilization: props.thresholds?.memoryUtilization ?? 80,
      cpuUtilization: props.thresholds?.cpuUtilization ?? 80,
      oomKillCount: props.thresholds?.oomKillCount ?? (isProduction ? 1 : 3),
      taskFailureCount: props.thresholds?.taskFailureCount ?? (isProduction ? 2 : 5),
    };

    // Create CloudWatch alarm action
    const alarmAction = new cloudwatch_actions.SnsAction(props.alarmTopic);

    // Use serviceDisplayName for alarm names and descriptions (guaranteed to be a literal string)
    const serviceDisplayName = props.serviceDisplayName;

    // Alarm 1: High Memory Utilization
    const memoryUtilizationAlarm = new cloudwatch.Alarm(this, 'HighMemoryUtilization', {
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
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    memoryUtilizationAlarm.addAlarmAction(alarmAction);
    memoryUtilizationAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 1b: High CPU Utilization
    //
    // Issue #970: CPU was nominally covered by `batbern-{env}-high-cpu` in AlarmConstruct,
    // but that alarm set dimensionsMap { ServiceName: `batbern-{env}` } — which is the
    // CLUSTER name, not a service name — and omitted ClusterName entirely. AWS/ECS needs
    // BOTH dimensions to resolve a datapoint, so it reported "Insufficient Data:
    // 2 datapoints were unknown" for its entire life and never once evaluated.
    //
    // Defining it here instead gives per-service CPU with the dimensions the memory alarm
    // beside it has always used correctly, for all six services rather than one bogus
    // aggregate.
    const cpuUtilizationAlarm = new cloudwatch.Alarm(this, 'HighCpuUtilization', {
      alarmName: `batbern-${props.environment}-${serviceDisplayName}-High-CPU`,
      alarmDescription: `${serviceDisplayName} CPU utilization exceeds ${thresholds.cpuUtilization}% (average over 5 minutes)`,
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ECS',
        metricName: 'CPUUtilization',
        dimensionsMap: {
          ServiceName: props.serviceName,
          ClusterName: props.clusterName,
        },
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: thresholds.cpuUtilization,
      // Two consecutive periods: Fargate tasks spike on JVM start and during a rolling
      // deploy, and a single 5-minute average above 80% is not an incident.
      evaluationPeriods: 2,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    cpuUtilizationAlarm.addAlarmAction(alarmAction);
    cpuUtilizationAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 2: OOM Kill Detection (exit code 137)
    // IMPORTANT: Only create if Container Insights is enabled
    // Container Insights creates the log group /aws/ecs/containerinsights/${clusterName}/performance
    // This log group does NOT exist if Container Insights is disabled (e.g., staging environment)
    let oomKillAlarm: cloudwatch.Alarm | undefined;

    if (props.containerInsightsEnabled) {
      const logGroupName = `/aws/ecs/containerinsights/${props.clusterName}/performance`;

      // The metric is named per service rather than dimensioned per service, and that is
      // forced by the API, not a style choice. Measured against the live CloudWatch Logs API
      // on 2026-08-23 with a throwaway log group:
      //
      //   dimensions + defaultValue -> InvalidParameterException
      //                                "dimensions and default value are mutually exclusive"
      //   dimensions alone          -> InvalidParameterException
      //                                "The specified filter pattern does not support dimensions"
      //
      // A literal-term FilterPattern cannot carry dimensions at all; that needs a structured
      // pattern with named fields, and this one greps free text for an exit code. So the only
      // way to keep six services distinguishable is to put the service in the metric name.
      //
      // This shape has NEVER deployed. Container Insights is disabled on the cluster
      // (cluster-stack.ts) and no stack passes containerInsightsEnabled, so the whole branch
      // is dead today. It was declared invalid and would have failed the first deploy after
      // anyone re-enabled Insights — found because the identical mistake in AlbAlarms rolled
      // back BATbern-staging-ApiGatewayService. Fixed here rather than left for that person.
      const oomMetricName = `OOMKills-${serviceDisplayName}`;

      const oomMetricFilter = new logs.MetricFilter(this, 'OOMKillMetricFilter', {
        logGroup: logs.LogGroup.fromLogGroupName(this, 'ContainerInsightsLogGroup', logGroupName),
        metricNamespace: 'BATbern/ECS',
        metricName: oomMetricName,
        filterPattern: logs.FilterPattern.allTerms(props.serviceName, 'exit', 'code', '137'),
        metricValue: '1',
        defaultValue: 0,
      });

      oomKillAlarm = new cloudwatch.Alarm(this, 'OOMKillDetection', {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-OOM-Kills`,
        alarmDescription: `${serviceDisplayName} experienced OOM kill (exit code 137) - memory limit reached`,
        metric: new cloudwatch.Metric({
          namespace: 'BATbern/ECS',
          metricName: oomMetricName,
          // No dimensionsMap — it must match the filter above, which cannot have one.
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: thresholds.oomKillCount,
        evaluationPeriods: 1,
        comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });
      oomKillAlarm.addAlarmAction(alarmAction);
      oomKillAlarm.addOkAction(alarmAction); // #956: close the issue on recovery
      oomKillAlarm.node.addDependency(oomMetricFilter);
    }

    // Alarm 3: Task Failure Rate (abnormal task stops)
    const taskFailureAlarm = new cloudwatch.Alarm(this, 'HighTaskFailureRate', {
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
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    taskFailureAlarm.addAlarmAction(alarmAction);
    taskFailureAlarm.addOkAction(alarmAction); // #956: close the issue on recovery

    // Alarm 4: swallowed domain-event listener failures (owner go 2026-11-09).
    //
    // SpringDomainEventPublisher catches an exception from a synchronous listener, logs it at
    // ERROR and lets the caller continue (whether it should fail the caller is story 17.12).
    // It also counts it in Micrometer, but no service exports metrics to CloudWatch, so the
    // alarm reads the log instead: a literal-term filter on the marker, which matches both the
    // LogstashEncoder JSON (CUMS) and the plain-text pattern layout (EMS, PCS). The marker is
    // SpringDomainEventPublisher.LOG_MARKER; domain-event-listener-alarm.test.ts reads it from
    // the Java source, so renaming it there fails the CDK build instead of silencing this alarm.
    // Like the OOM filter above, the metric is named per service because a literal-term
    // pattern cannot carry dimensions.
    let listenerFailureAlarm: cloudwatch.Alarm | undefined;

    if (props.serviceLogGroup) {
      const listenerFailureMetricName = `ListenerFailures-${serviceDisplayName}`;

      const listenerFailureFilter = new logs.MetricFilter(
        this,
        'EventListenerFailureMetricFilter',
        {
          logGroup: props.serviceLogGroup,
          metricNamespace: DOMAIN_EVENTS_NAMESPACE,
          metricName: listenerFailureMetricName,
          filterPattern: logs.FilterPattern.literal(`"${DOMAIN_EVENT_LISTENER_FAILURE_MARKER}"`),
          metricValue: '1',
          defaultValue: 0,
        }
      );

      listenerFailureAlarm = new cloudwatch.Alarm(this, 'EventListenerFailures', {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-Event-Listener-Failures`,
        alarmDescription: `${serviceDisplayName}: a domain event listener threw and the publisher swallowed it (log marker ${DOMAIN_EVENT_LISTENER_FAILURE_MARKER}); the side effect it should have had did not happen`,
        metric: new cloudwatch.Metric({
          namespace: DOMAIN_EVENTS_NAMESPACE,
          metricName: listenerFailureMetricName,
          // No dimensionsMap: it must match the filter above, which cannot have one.
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: 0,
        evaluationPeriods: 1,
        comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });
      listenerFailureAlarm.addAlarmAction(alarmAction);
      listenerFailureAlarm.addOkAction(alarmAction); // #956: close the issue on recovery
      listenerFailureAlarm.node.addDependency(listenerFailureFilter);
    }

    // Alarm 5: items a scheduled job could not process (Story 4.9b, assertion rule "no silent
    // catch in schedulers without a metric"). Same log-based pattern as alarm 4: EMS's
    // ScheduledJobFailures logs SCHEDULED_JOB_FAILED and counts it in Micrometer, which reaches
    // nobody; scheduled-job-failure-alarm.test.ts reads the marker from the Java source.
    let scheduledJobFailureAlarm: cloudwatch.Alarm | undefined;

    if (props.serviceLogGroup && props.scheduledJobFailures) {
      const scheduledJobMetricName = `ScheduledJobFailures-${serviceDisplayName}`;

      const scheduledJobFilter = new logs.MetricFilter(this, 'ScheduledJobFailureMetricFilter', {
        logGroup: props.serviceLogGroup,
        metricNamespace: SCHEDULED_JOBS_NAMESPACE,
        metricName: scheduledJobMetricName,
        filterPattern: logs.FilterPattern.literal(`"${SCHEDULED_JOB_FAILURE_MARKER}"`),
        metricValue: '1',
        defaultValue: 0,
      });

      scheduledJobFailureAlarm = new cloudwatch.Alarm(this, 'ScheduledJobFailures', {
        alarmName: `batbern-${props.environment}-${serviceDisplayName}-Scheduled-Job-Failures`,
        alarmDescription: `${serviceDisplayName}: a scheduled job could not process an item and skipped it (log marker ${SCHEDULED_JOB_FAILURE_MARKER}, e.g. an auto-publish that failed for one event)`,
        metric: new cloudwatch.Metric({
          namespace: SCHEDULED_JOBS_NAMESPACE,
          metricName: scheduledJobMetricName,
          statistic: 'Sum',
          period: cdk.Duration.minutes(5),
        }),
        threshold: 0,
        evaluationPeriods: 1,
        comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });
      scheduledJobFailureAlarm.addAlarmAction(alarmAction);
      scheduledJobFailureAlarm.addOkAction(alarmAction);
      scheduledJobFailureAlarm.node.addDependency(scheduledJobFilter);
    }

    // Output alarm names for reference
    const alarmNames = [
      memoryUtilizationAlarm.alarmName,
      ...(oomKillAlarm ? [oomKillAlarm.alarmName] : []),
      taskFailureAlarm.alarmName,
      ...(listenerFailureAlarm ? [listenerFailureAlarm.alarmName] : []),
      ...(scheduledJobFailureAlarm ? [scheduledJobFailureAlarm.alarmName] : []),
    ];

    new cdk.CfnOutput(this, 'AlarmNames', {
      value: alarmNames.join(','),
      description: `CloudWatch alarm names for ${serviceDisplayName} monitoring`,
    });
  }
}
