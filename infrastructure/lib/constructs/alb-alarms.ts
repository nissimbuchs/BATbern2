import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatch_actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';

export interface AlbAlarmsProps {
  /** Environment name (development, staging, production). */
  readonly environment: string;

  /**
   * ALB "full name" — the value CloudWatch expects in the LoadBalancer dimension, e.g.
   * `app/BATber-Servi-XlmJ3ScSHcvA/1234567890abcdef`. NOT the ARN and NOT the DNS name.
   * From CDK: `service.loadBalancer.loadBalancerFullName`.
   */
  readonly loadBalancerFullName: string;

  /**
   * Target group full name, e.g. `targetgroup/BATbe-Targe-ABCDEF/abcdef1234567890`.
   * Required because UnHealthyHostCount is only meaningful per target group.
   * From CDK: `service.targetGroup.targetGroupFullName`.
   */
  readonly targetGroupFullName: string;

  /** SNS topic for alarm + recovery notifications. */
  readonly alarmTopic: sns.ITopic;

  readonly isProduction?: boolean;

  readonly thresholds?: {
    /** Target 5xx responses per 5 minutes. */
    readonly serverErrorCount?: number;
    /** Target 4xx responses per 5 minutes. */
    readonly clientErrorCount?: number;
    /** P95 TargetResponseTime, in SECONDS. */
    readonly latencyP95Seconds?: number;
    /** Availability floor as a percentage, e.g. 99.9. */
    readonly availabilityPercent?: number;
  };
}

/**
 * Alarms for the platform's Application Load Balancer.
 *
 * Issue #970: this replaces four alarms in `AlarmConstruct` that had never evaluated a
 * single datapoint in their entire lifetime (`StateReason: "Unchecked: Initial alarm
 * creation"`). They queried `AWS/ApiGateway` with a dimension of
 * `ApiName=batbern-{env}` — but no REST API Gateway exists in this account. Traffic is
 * served by an ALB in front of ECS Fargate, so the namespace, the dimension name and the
 * dimension value were all wrong, and nothing surfaced that: CloudFormation accepts an
 * alarm whose dimensions match no resource, and it simply sits in INSUFFICIENT_DATA.
 *
 * Defined here, next to the load balancer, rather than in MonitoringStack — the ALB is
 * created by ApiGatewayServiceStack, which runs long after MonitoringStack, so its
 * identifiers are not available there. This mirrors how EcsServiceAlarms already works:
 * MonitoringStack exports the topic, and each stack owning a resource defines its own
 * alarms against it.
 *
 * Every alarm here treats missing data as NOT_BREACHING. The alarm this replaces used
 * BREACHING, which is why it reported ALARM continuously from 2025-10-04 — roughly ten
 * months of a permanently red signal, which is worse than no signal.
 */
export class AlbAlarms extends Construct {
  public readonly alarms: cloudwatch.Alarm[] = [];

  constructor(scope: Construct, id: string, props: AlbAlarmsProps) {
    super(scope, id);

    const isProduction = props.isProduction ?? props.environment === 'production';
    const env = props.environment;
    const lbDimension = { LoadBalancer: props.loadBalancerFullName };

    const thresholds = {
      serverErrorCount: props.thresholds?.serverErrorCount ?? (isProduction ? 5 : 10),
      clientErrorCount: props.thresholds?.clientErrorCount ?? 50,
      // SECONDS. AWS/ApplicationELB TargetResponseTime is in seconds, unlike
      // AWS/ApiGateway Latency which is milliseconds — the alarm this replaces carried a
      // threshold of 500 written for milliseconds, i.e. a 500-second SLA had it ever run.
      // coding-standards.md targets P95 < 200ms; 500ms is the alerting floor above it.
      latencyP95Seconds: props.thresholds?.latencyP95Seconds ?? 0.5,
      availabilityPercent: props.thresholds?.availabilityPercent ?? 99.9,
    };

    const action = new cloudwatch_actions.SnsAction(props.alarmTopic);
    const period = cdk.Duration.minutes(5);

    const albMetric = (metricName: string, statistic: string) =>
      new cloudwatch.Metric({
        namespace: 'AWS/ApplicationELB',
        metricName,
        dimensionsMap: lbDimension,
        statistic,
        period,
      });

    // ── 5xx: the backend is failing ────────────────────────────────────────────────
    // Counts BOTH target 5xx (the service returned an error) and ELB 5xx (the load
    // balancer could not get a response at all — no healthy target, timeout). A deploy
    // that brings tasks down shows up only in the latter, so watching one is not enough.
    const serverErrors = new cloudwatch.Alarm(this, 'ServerErrors', {
      alarmName: `batbern-${env}-alb-5xx`,
      alarmDescription:
        `More than ${thresholds.serverErrorCount} 5xx responses in 5 minutes ` +
        '(target 5xx + ELB 5xx combined)',
      metric: new cloudwatch.MathExpression({
        expression: 'target5xx + elb5xx',
        usingMetrics: {
          target5xx: albMetric('HTTPCode_Target_5XX_Count', 'Sum'),
          elb5xx: albMetric('HTTPCode_ELB_5XX_Count', 'Sum'),
        },
        period,
        label: '5xx responses',
      }),
      threshold: thresholds.serverErrorCount,
      evaluationPeriods: 2,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // ── 4xx: clients are being rejected ────────────────────────────────────────────
    // Deliberately looser and slower than 5xx. A burst of 401s during a token refresh
    // storm, or 404s from a scanner, is not an incident; a sustained elevation is.
    const clientErrors = new cloudwatch.Alarm(this, 'ClientErrors', {
      alarmName: `batbern-${env}-alb-4xx`,
      alarmDescription: `More than ${thresholds.clientErrorCount} target 4xx responses in 5 minutes`,
      metric: albMetric('HTTPCode_Target_4XX_Count', 'Sum'),
      threshold: thresholds.clientErrorCount,
      evaluationPeriods: 3,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // ── Latency ────────────────────────────────────────────────────────────────────
    const latency = new cloudwatch.Alarm(this, 'LatencyP95', {
      alarmName: `batbern-${env}-alb-latency-p95`,
      alarmDescription:
        `P95 target response time exceeds ${thresholds.latencyP95Seconds}s ` +
        '(coding-standards.md targets P95 < 200ms)',
      metric: albMetric('TargetResponseTime', 'p95'),
      threshold: thresholds.latencyP95Seconds,
      evaluationPeriods: 3,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // ── Availability, as an actual ratio ───────────────────────────────────────────
    // The alarm this replaces compared Sum(RequestCount) against 99.9 and described it as
    // "availability below 99.9% SLA". A request count is not a percentage: with BATbern's
    // traffic almost every 5-minute window has fewer than 100 requests, so it would have
    // fired constantly had the metric ever resolved.
    //
    // The IF() guard matters. With no traffic, requests = 0 and the division would yield
    // no datapoint; returning 100 instead states the truth — a system serving no requests
    // is not failing them — and keeps quiet periods out of the alarm.
    const availability = new cloudwatch.Alarm(this, 'Availability', {
      alarmName: `batbern-${env}-alb-availability`,
      alarmDescription: `Successful-request ratio below ${thresholds.availabilityPercent}%`,
      metric: new cloudwatch.MathExpression({
        expression: 'IF(requests > 0, (1 - (target5xx + elb5xx) / requests) * 100, 100)',
        usingMetrics: {
          requests: albMetric('RequestCount', 'Sum'),
          target5xx: albMetric('HTTPCode_Target_5XX_Count', 'Sum'),
          elb5xx: albMetric('HTTPCode_ELB_5XX_Count', 'Sum'),
        },
        period,
        label: 'Availability %',
      }),
      threshold: thresholds.availabilityPercent,
      evaluationPeriods: 2,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // ── Unhealthy targets ──────────────────────────────────────────────────────────
    // The most direct availability signal there is, and one nothing previously watched:
    // a task failing health checks is removed from rotation before it produces any 5xx.
    // Needs the TargetGroup dimension — UnHealthyHostCount is per target group.
    const unhealthyTargets = new cloudwatch.Alarm(this, 'UnhealthyTargets', {
      alarmName: `batbern-${env}-alb-unhealthy-targets`,
      alarmDescription: 'One or more ECS tasks are failing ALB health checks',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApplicationELB',
        metricName: 'UnHealthyHostCount',
        dimensionsMap: {
          LoadBalancer: props.loadBalancerFullName,
          TargetGroup: props.targetGroupFullName,
        },
        statistic: 'Maximum',
        period: cdk.Duration.minutes(1),
      }),
      threshold: 0,
      evaluationPeriods: 3,
      datapointsToAlarm: 3,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    this.alarms.push(serverErrors, clientErrors, latency, availability, unhealthyTargets);

    for (const alarm of this.alarms) {
      alarm.addAlarmAction(action);
      // #956: without an OK action the github-issues Lambda can never close the issue it
      // opened, so every alarm issue stays open forever.
      alarm.addOkAction(action);
    }
  }
}
