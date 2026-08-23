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
    /** P95 TargetResponseTime, in SECONDS. */
    readonly latencyP95Seconds?: number;
    /**
     * Minimum requests in a 5-minute window before the latency alarm evaluates at all.
     * Below this the p95 is computed over too few samples to mean anything.
     */
    readonly latencyMinRequests?: number;
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
      // SECONDS. AWS/ApplicationELB TargetResponseTime is in seconds, unlike
      // AWS/ApiGateway Latency which is milliseconds — the alarm this replaces carried a
      // threshold of 500 written for milliseconds, i.e. a 500-second SLA had it ever run.
      // coding-standards.md targets P95 < 200ms; 500ms is the alerting floor above it.
      latencyP95Seconds: props.thresholds?.latencyP95Seconds ?? 0.5,
      latencyMinRequests: props.thresholds?.latencyMinRequests ?? 20,
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

    // ── 4xx: deliberately NOT alarmed on at the ALB (#986) ─────────────────────────
    // There used to be a `batbern-{env}-alb-4xx` alarm on HTTPCode_Target_4XX_Count > 50
    // per 5 minutes. It fired six times in the three days to 2026-08-23 and self-resolved
    // every time, and the cause was never once a fault.
    //
    // Measured, 2026-08-23 14:35-14:50 UTC (the window the last page cited: 118 4xx at
    // 14:40, 73 at 14:45), from 808 api-gateway request log lines:
    //
    //   /actuator/health   360 requests    1 distinct path
    //   /api/v1/*           64 requests    6 distinct paths   <- all the real traffic
    //   neither            384 requests  186 distinct paths
    //
    // The 384 are a webshell sweep against api.batbern.ch — /gecko-new.php, /aa.php,
    // /wp-content/plugins/hellopress/wp_filemanager.php, /qyffk.php, 186 distinct
    // nonexistent .php paths in fifteen minutes. Every one is a 404, and every 404 is one
    // HTTPCode_Target_4XX_Count. api.batbern.ch resolves straight to this ALB with no
    // CloudFront and no WAF, so there is nothing between a scanner and a 404.
    //
    // Raw target-4xx is therefore not an actionable signal at ANY threshold: the number it
    // reports is a property of the internet, not of BATbern. Raising the threshold would
    // only set the bar at "how large a scan before we care", which is not a question worth
    // answering. Note the deleted alarm's own comment already anticipated this ("404s from
    // a scanner is not an incident") — it just could not act on it with this metric.
    //
    // The signal that alarm was reaching for — a deploy that starts rejecting real
    // requests — is worth keeping, but it has to be measured on paths the gateway actually
    // serves rather than on everything that reaches the listener. That replacement is
    // #986 option 2.

    // ── Latency ────────────────────────────────────────────────────────────────────
    // Fires only on SUSTAINED latency, because the thing that actually breaches here is a
    // deploy, and a deploy recovers on its own.
    //
    // Measured 2026-08-19 across two ECS task replacements (windows are 5 minutes):
    //
    //   requests   p95        requests   p95
    //         25   3.7081s          35   4.7058s
    //        116   1.9862s         220   1.1722s
    //        256   0.8424s         533   0.4328s
    //        386   0.5252s
    //         45   0.0057s
    //
    // Volume ramps while latency decays — a JVM warming up under real load, not a metric
    // artifact. Hundreds of genuine requests take seconds for roughly 10-15 minutes after
    // a task is replaced, then settle to a 2-25 MILLIsecond baseline.
    //
    // A first attempt gated this on request volume (`IF(requests >= 20, ...)`) on the
    // theory that a p95 over one request is meaningless. That theory was wrong for THIS
    // signal: every breaching window above carries 25-533 requests and clears the floor,
    // so the gate would not have suppressed a single one of the five notifications sent on
    // 2026-08-19. The volume gate is retained because it is independently correct — a p95
    // over 2-6 requests really is noise — but it is not what makes this alarm quiet.
    //
    // What makes it quiet is requiring the breach to PERSIST: 5 of 6 periods, i.e. 25
    // minutes. The observed warmups breached for 4 windows and 2 windows respectively, so
    // neither trips this, while latency that is genuinely stuck still does. That is a
    // deliberate trade — detection of a real regression is delayed by ~25 minutes in
    // exchange for not paging on every deploy.
    //
    // The warmup itself is real user-visible latency and is NOT fixed by this alarm
    // change; it is quantified and tracked separately.
    const latency = new cloudwatch.Alarm(this, 'LatencyP95', {
      alarmName: `batbern-${env}-alb-latency-p95`,
      alarmDescription:
        `P95 target response time above ${thresholds.latencyP95Seconds}s, sustained for 25 ` +
        `minutes, over at least ${thresholds.latencyMinRequests} requests per window ` +
        '(coding-standards.md targets P95 < 200ms; deploy warmup lasts 10-15 min and is excluded)',
      metric: new cloudwatch.MathExpression({
        expression: `IF(requests >= ${thresholds.latencyMinRequests}, latency, 0)`,
        usingMetrics: {
          requests: albMetric('RequestCount', 'Sum'),
          latency: albMetric('TargetResponseTime', 'p95'),
        },
        period,
        label: 'P95 latency (traffic-gated)',
      }),
      threshold: thresholds.latencyP95Seconds,
      // 5 of 6 periods = 25 minutes of sustained breach. See the note above: a deploy
      // warmup breaches for 2-4 periods and must not page.
      evaluationPeriods: 6,
      datapointsToAlarm: 5,
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

    this.alarms.push(serverErrors, latency, availability, unhealthyTargets);

    for (const alarm of this.alarms) {
      alarm.addAlarmAction(action);
      // #956: without an OK action the github-issues Lambda can never close the issue it
      // opened, so every alarm issue stays open forever.
      alarm.addOkAction(action);
    }
  }
}
