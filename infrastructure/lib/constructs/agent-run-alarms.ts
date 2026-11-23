import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatch_actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';

/**
 * Alarms on agent runs (agentic interaction, epic agent runtime, story 10, R10).
 *
 * agent-service writes one marker line per run end an alarm watches (`RunAlarms` in
 * services/agent-service/src/main/java/ch/batbern/agent/run/RunAlarms.java). No service exports
 * Micrometer to CloudWatch, so the alarms are log based, as `EcsServiceAlarms` alarms 4 and 5: a
 * literal-term metric filter per marker on agent-service's own log group, the service in the
 * metric name (a literal pattern cannot carry dimensions). agent-run-alarms.test.ts reads the
 * markers from the Java source, so a rename there fails the CDK build instead of silencing an alarm.
 *
 * - `Agent-Run-Failures`: a run ended FAILED (a fault in agent-service), any in 5 minutes.
 * - `Agent-Refused-Mints`: CUMS refused the run grant or a mint, or could not be asked (missing
 *   caller secret, signing key not installed, revoked principal), any in 5 minutes.
 * - `Agent-Daily-Budget-Breaches`: a run was stopped by the daily token budget, which stops every
 *   further run until midnight Europe/Zurich; any within 24 hours (1-hour periods), so the OK
 *   action closes the issue a day after the last breach.
 *
 * Per-run caps (steps, run tokens, replies) are counted in `RunCapsReached-Agent` without an alarm:
 * the cap is doing its job, and the run is handed to the organizers. A run handed over because the
 * model was unreachable is not marked at all (no alarm; decision recorded in the story 10 plan).
 */

/** Namespace of the log-based agent-run metrics. */
export const AGENT_RUNS_NAMESPACE = 'BATbern/AgentRuns';

/** RunAlarms markers (agent-service), each the first token of its line. */
export const AGENT_RUN_FAILED_MARKER = 'AGENT_RUN_FAILED';
export const AGENT_RUN_CAP_REACHED_MARKER = 'AGENT_RUN_CAP_REACHED';
export const AGENT_DAILY_BUDGET_BREACHED_MARKER = 'AGENT_DAILY_BUDGET_BREACHED';
export const AGENT_CREDENTIALS_REFUSED_MARKER = 'AGENT_CREDENTIALS_REFUSED';

/** Metric names, one per marker. */
export const AGENT_RUN_METRICS = {
  runFailures: 'RunFailures-Agent',
  runCapsReached: 'RunCapsReached-Agent',
  dailyBudgetBreaches: 'DailyBudgetBreaches-Agent',
  refusedMints: 'RefusedMints-Agent',
} as const;

export interface AgentRunAlarmsProps {
  /** Environment name, as in every alarm name (`batbern-{env}-...`) */
  readonly environment: string;
  /** agent-service's own log group, the one its containers write to */
  readonly serviceLogGroup: logs.ILogGroup;
  /** The shared alarm topic (alarm -> GitHub issue, OK -> close, #956) */
  readonly alarmTopic: sns.ITopic;
}

export class AgentRunAlarms extends Construct {
  public readonly alarms: cloudwatch.Alarm[];

  constructor(scope: Construct, id: string, props: AgentRunAlarmsProps) {
    super(scope, id);

    const action = new cloudwatch_actions.SnsAction(props.alarmTopic);

    const filter = (filterId: string, marker: string, metricName: string): logs.MetricFilter =>
      new logs.MetricFilter(this, filterId, {
        logGroup: props.serviceLogGroup,
        metricNamespace: AGENT_RUNS_NAMESPACE,
        metricName,
        filterPattern: logs.FilterPattern.literal(`"${marker}"`),
        metricValue: '1',
        defaultValue: 0,
      });

    const failures = filter(
      'RunFailureMetricFilter',
      AGENT_RUN_FAILED_MARKER,
      AGENT_RUN_METRICS.runFailures
    );
    filter(
      'RunCapReachedMetricFilter',
      AGENT_RUN_CAP_REACHED_MARKER,
      AGENT_RUN_METRICS.runCapsReached
    );
    const budget = filter(
      'DailyBudgetMetricFilter',
      AGENT_DAILY_BUDGET_BREACHED_MARKER,
      AGENT_RUN_METRICS.dailyBudgetBreaches
    );
    const refused = filter(
      'RefusedMintMetricFilter',
      AGENT_CREDENTIALS_REFUSED_MARKER,
      AGENT_RUN_METRICS.refusedMints
    );

    const runFailureAlarm = new cloudwatch.Alarm(this, 'RunFailures', {
      alarmName: `batbern-${props.environment}-Agent-Run-Failures`,
      alarmDescription: `Agent: a run ended FAILED, a fault in agent-service itself (log marker ${AGENT_RUN_FAILED_MARKER}); the run is in agent_runs with end_reason INTERNAL_ERROR`,
      metric: new cloudwatch.Metric({
        namespace: AGENT_RUNS_NAMESPACE,
        metricName: AGENT_RUN_METRICS.runFailures,
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 0,
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    runFailureAlarm.node.addDependency(failures);

    const refusedMintAlarm = new cloudwatch.Alarm(this, 'RefusedMints', {
      alarmName: `batbern-${props.environment}-Agent-Refused-Mints`,
      alarmDescription: `Agent: CUMS refused a run grant or an agent-token mint, or could not be asked (log marker ${AGENT_CREDENTIALS_REFUSED_MARKER}); the line names the CUMS code, the run went to the organizers`,
      metric: new cloudwatch.Metric({
        namespace: AGENT_RUNS_NAMESPACE,
        metricName: AGENT_RUN_METRICS.refusedMints,
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 0,
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    refusedMintAlarm.node.addDependency(refused);

    const budgetAlarm = new cloudwatch.Alarm(this, 'DailyBudgetBreaches', {
      alarmName: `batbern-${props.environment}-Agent-Daily-Budget-Breaches`,
      alarmDescription: `Agent: the daily token budget (agent.limits.daily-tokens) stopped a run (log marker ${AGENT_DAILY_BUDGET_BREACHED_MARKER}); every further run is handed to the organizers until midnight Europe/Zurich`,
      metric: new cloudwatch.Metric({
        namespace: AGENT_RUNS_NAMESPACE,
        metricName: AGENT_RUN_METRICS.dailyBudgetBreaches,
        statistic: 'Sum',
        period: cdk.Duration.hours(1),
      }),
      threshold: 0,
      // The budget is per day: one breach in the last 24 hours keeps the alarm on, so its OK action
      // closes the issue a day after the last breach, not 5 minutes after it
      evaluationPeriods: 24,
      datapointsToAlarm: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    budgetAlarm.node.addDependency(budget);

    this.alarms = [runFailureAlarm, refusedMintAlarm, budgetAlarm];
    for (const alarm of this.alarms) {
      alarm.addAlarmAction(action);
      alarm.addOkAction(action); // #956: close the issue on recovery
    }
  }
}
