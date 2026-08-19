import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import { Construct } from 'constructs';

export interface AlarmConstructProps {
  environment: string;
  alarmTopic?: sns.Topic;
  /**
   * RDS instance identifier for the database alarms.
   *
   * Defaults to `batbern-{environment}-postgres`, which is the identifier
   * `DatabaseStack` sets explicitly (`lib/stacks/database-stack.ts`, `instanceIdentifier`).
   * It is passed as a plain string rather than a CDK reference on purpose: DatabaseStack is
   * created before MonitoringStack in `bin/batbern-infrastructure.ts`, and a cross-stack
   * reference here would add a dependency edge for a value that is deterministic anyway.
   * If DatabaseStack's naming ever changes, change it here too.
   */
  dbInstanceIdentifier?: string;
}

/**
 * Platform alarms owned by MonitoringStack.
 *
 * ## History — issue #970
 *
 * This construct used to declare nine alarms covering SLA, error rate, latency, resource
 * utilization and cost. **Not one of them had ever evaluated a single datapoint.** Live
 * `describe-alarms` output showed eight reporting `"Unchecked: Initial alarm creation"`
 * and the ninth `"Insufficient Data"`, because every one queried a resource that does not
 * exist in this account:
 *
 * | alarm | queried | reality |
 * |---|---|---|
 * | `high-availability` | `AWS/ApiGateway`, `ApiName=batbern-{env}` | traffic is served by an **ALB**; no REST API Gateway exists |
 * | `high-errors` | same | same |
 * | `high-client-errors` | same | same |
 * | `high-latency` | same | same |
 * | `high-cpu` | `AWS/ECS`, `ServiceName` only | `batbern-{env}` is the **cluster**; AWS/ECS needs ClusterName **and** ServiceName |
 * | `high-memory` | same | same |
 * | `high-disk` | `AWS/EBS VolumeUtilization` | not a real EBS metric name, and there are no EBS volumes — storage is RDS |
 * | `database-connections` | `AWS/RDS`, `DBClusterIdentifier` | RDS here is a single **instance**, not an Aurora cluster |
 * | `budget-overage` | `AWS/Billing` in eu-central-1 | `AWS/Billing` is only ever published in **us-east-1** |
 *
 * `high-availability` was worse still: it compared `Sum(RequestCount)` — a request count —
 * against `99.9` as though it were a percentage, and used
 * `treatMissingData: BREACHING`, so it reported ALARM continuously from 2025-10-04. Ten
 * months of a permanently red alarm trains everyone to ignore the channel.
 *
 * None of this was catchable by the tests that existed. `Template.fromStack()` assertions
 * pass on an alarm whose dimensions match nothing, and the E2E specs asserted
 * `alarm.Threshold === 99.9` — true of a dead alarm — while never running in CI, which
 * only executes `test/unit`.
 *
 * ## What lives here now
 *
 * Only alarms whose target identifiers are genuinely available to MonitoringStack. The
 * rest moved next to the resources they watch, which is where `EcsServiceAlarms` and
 * `UserSyncAlarms` already were:
 *
 * - availability / 5xx / 4xx / latency / unhealthy targets → `AlbAlarms`, in
 *   `ApiGatewayServiceStack` (it owns the ALB)
 * - CPU and memory → `EcsServiceAlarms`, per service, in each service stack
 * - cost → deliberately not replaced here; see below
 *
 * **Cost monitoring is intentionally absent.** `AWS/Billing` cannot work from
 * eu-central-1, and the correct home is AWS Budgets in the management account
 * (510187933511), which holds consolidated billing and where the CFO already has billing
 * access. Tracked separately rather than reimplemented wrongly a second time.
 */
export class AlarmConstruct extends Construct {
  public readonly alarms: cloudwatch.Alarm[];

  constructor(scope: Construct, id: string, props: AlarmConstructProps) {
    super(scope, id);

    this.alarms = [...this.createDatabaseAlarms(props)];

    if (props.alarmTopic) {
      const action = new cloudwatchActions.SnsAction(props.alarmTopic);
      for (const alarm of this.alarms) {
        alarm.addAlarmAction(action);
        // #956: the github-issues Lambda closes an alarm's issue on the OK transition; with
        // no OK action the issue stays open forever.
        alarm.addOkAction(action);
      }
    }
  }

  /**
   * RDS alarms. These stay in MonitoringStack because the instance identifier is a
   * deterministic string (see `dbInstanceIdentifier`), so no cross-stack reference is
   * needed to build the dimension.
   */
  private createDatabaseAlarms(props: AlarmConstructProps): cloudwatch.Alarm[] {
    const dbInstanceIdentifier =
      props.dbInstanceIdentifier ?? `batbern-${props.environment}-postgres`;
    const isProduction = props.environment === 'production';

    const rdsMetric = (metricName: string, statistic: string) =>
      new cloudwatch.Metric({
        namespace: 'AWS/RDS',
        metricName,
        // DBInstanceIdentifier, not DBClusterIdentifier — this is a single
        // db.t4g.micro instance (see tech-stack.md), never an Aurora cluster. The old
        // alarm used the cluster dimension and therefore matched nothing.
        dimensionsMap: { DBInstanceIdentifier: dbInstanceIdentifier },
        statistic,
        period: cdk.Duration.minutes(5),
      });

    const connections = new cloudwatch.Alarm(this, 'DatabaseConnectionAlarm', {
      alarmName: `batbern-${props.environment}-database-connections`,
      alarmDescription: 'RDS connection count is high — connection pool may be exhausted',
      metric: rdsMetric('DatabaseConnections', 'Maximum'),
      // db.t4g.micro's default max_connections is ~112 (DBInstanceClassMemory/9531392).
      // 80 leaves room to react before the pool is exhausted and Hikari starts timing out.
      threshold: 80,
      evaluationPeriods: 2,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // Replaces the `high-disk` alarm, which queried `AWS/EBS VolumeUtilization` — not a
    // real EBS metric, against volumes that do not exist. The storage that can actually
    // fill up and take the platform down is the RDS instance's.
    //
    // FreeStorageSpace is in BYTES and the comparison is LESS_THAN: unlike a utilization
    // percentage, less is worse.
    const freeStorage = new cloudwatch.Alarm(this, 'DatabaseStorageAlarm', {
      alarmName: `batbern-${props.environment}-database-storage-low`,
      alarmDescription:
        'RDS free storage is low — the database will stop accepting writes when it fills',
      metric: rdsMetric('FreeStorageSpace', 'Minimum'),
      threshold: (isProduction ? 5 : 2) * 1024 * 1024 * 1024, // 5 GiB prod, 2 GiB otherwise
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
      // A missing datapoint here must NOT read as healthy the way it can for a
      // count-based alarm, but BREACHING would produce the permanently-red failure mode of
      // the old high-availability alarm. MISSING keeps the last known state instead.
      treatMissingData: cloudwatch.TreatMissingData.MISSING,
    });

    const cpu = new cloudwatch.Alarm(this, 'DatabaseCpuAlarm', {
      alarmName: `batbern-${props.environment}-database-cpu`,
      alarmDescription: 'RDS CPU utilization is high',
      metric: rdsMetric('CPUUtilization', 'Average'),
      threshold: 80,
      evaluationPeriods: 3,
      datapointsToAlarm: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    return [connections, freeStorage, cpu];
  }
}
