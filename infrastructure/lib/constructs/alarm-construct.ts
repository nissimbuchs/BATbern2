import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
import { Construct } from 'constructs';

export interface AlarmConstructProps {
  environment: string;
  alarmTopic?: sns.Topic;
}

/**
 * Reusable construct for creating comprehensive CloudWatch alarms
 *
 * Implements alarms for:
 * - SLA monitoring (AC: 5)
 * - Error rate thresholds (AC: 6)
 * - Performance/latency (AC: 7)
 * - Resource utilization (AC: 8)
 */
export class AlarmConstruct extends Construct {
  public readonly alarms: cloudwatch.Alarm[];

  constructor(scope: Construct, id: string, props: AlarmConstructProps) {
    super(scope, id);

    this.alarms = [];

    // Create all alarm categories
    this.alarms.push(...this.createSLAAlarms(props));
    this.alarms.push(...this.createErrorRateAlarms(props));
    this.alarms.push(...this.createPerformanceAlarms(props));
    this.alarms.push(...this.createResourceUtilizationAlarms(props));
    this.alarms.push(...this.createCostAlarms(props));
  }

  /**
   * Create cost monitoring alarms (AC: 11)
   * Target: Budget threshold alerts
   */
  private createCostAlarms(props: AlarmConstructProps): cloudwatch.Alarm[] {
    const alarms: cloudwatch.Alarm[] = [];

    // Budget Overage Alarm
    const budgetAlarm = new cloudwatch.Alarm(this, 'BudgetAlarm', {
      alarmName: `batbern-${props.environment}-budget-overage`,
      alarmDescription: 'Alert when AWS costs exceed budget threshold',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/Billing',
        metricName: 'EstimatedCharges',
        dimensionsMap: {
          Currency: 'USD',
        },
        statistic: 'Maximum',
        period: cdk.Duration.hours(6),
      }),
      threshold: props.environment === 'production' ? 1000 : 100, // $1000 for prod, $100 for dev/staging
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      budgetAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      budgetAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(budgetAlarm);
    return alarms;
  }

  /**
   * Create SLA monitoring alarms (AC: 5)
   * Target: 99.9% availability
   */
  private createSLAAlarms(props: AlarmConstructProps): cloudwatch.Alarm[] {
    const alarms: cloudwatch.Alarm[] = [];

    // Availability Alarm - 99.9% SLA
    const availabilityAlarm = new cloudwatch.Alarm(this, 'AvailabilityAlarm', {
      alarmName: `batbern-${props.environment}-high-availability`,
      alarmDescription: 'Alert when availability drops below 99.9% SLA',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApiGateway',
        metricName: 'Count',
        dimensionsMap: {
          ApiName: `batbern-${props.environment}`,
        },
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 99.9,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.BREACHING,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      availabilityAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      availabilityAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(availabilityAlarm);
    return alarms;
  }

  /**
   * Create error rate alarms (AC: 6)
   * Target: < 0.1% error rate
   */
  private createErrorRateAlarms(props: AlarmConstructProps): cloudwatch.Alarm[] {
    const alarms: cloudwatch.Alarm[] = [];

    // High Error Rate Alarm
    const highErrorAlarm = new cloudwatch.Alarm(this, 'HighErrorAlarm', {
      alarmName: `batbern-${props.environment}-high-errors`,
      alarmDescription: 'Alert when error rate is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApiGateway',
        metricName: '5XXError',
        dimensionsMap: {
          ApiName: `batbern-${props.environment}`,
        },
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 10,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      highErrorAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      highErrorAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(highErrorAlarm);

    // 4XX Error Rate Alarm (client errors)
    const clientErrorAlarm = new cloudwatch.Alarm(this, 'ClientErrorAlarm', {
      alarmName: `batbern-${props.environment}-high-client-errors`,
      alarmDescription: 'Alert when client error rate is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApiGateway',
        metricName: '4XXError',
        dimensionsMap: {
          ApiName: `batbern-${props.environment}`,
        },
        statistic: 'Sum',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 50,
      evaluationPeriods: 3,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      clientErrorAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      clientErrorAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(clientErrorAlarm);
    return alarms;
  }

  /**
   * Create performance/latency alarms (AC: 7)
   * Target: P95 < 500ms
   */
  private createPerformanceAlarms(props: AlarmConstructProps): cloudwatch.Alarm[] {
    const alarms: cloudwatch.Alarm[] = [];

    // High Latency Alarm (P95)
    const highLatencyAlarm = new cloudwatch.Alarm(this, 'HighLatencyAlarm', {
      alarmName: `batbern-${props.environment}-high-latency`,
      alarmDescription: 'Alert when P95 API latency exceeds 500ms SLA target',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ApiGateway',
        metricName: 'Latency',
        dimensionsMap: {
          ApiName: `batbern-${props.environment}`,
        },
        statistic: 'p95',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 500, // 500ms as per SLA requirements (AC: 7)
      evaluationPeriods: 3,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      highLatencyAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      highLatencyAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(highLatencyAlarm);
    return alarms;
  }

  /**
   * Create resource utilization alarms (AC: 8)
   * Targets: CPU < 80%, Memory < 80%, Disk < 85%
   */
  private createResourceUtilizationAlarms(props: AlarmConstructProps): cloudwatch.Alarm[] {
    const alarms: cloudwatch.Alarm[] = [];

    // High CPU Alarm
    const highCpuAlarm = new cloudwatch.Alarm(this, 'HighCpuAlarm', {
      alarmName: `batbern-${props.environment}-high-cpu`,
      alarmDescription: 'Alert when CPU utilization is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ECS',
        metricName: 'CPUUtilization',
        dimensionsMap: {
          ServiceName: `batbern-${props.environment}`,
        },
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 80,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      highCpuAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      highCpuAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(highCpuAlarm);

    // High Memory Alarm
    const highMemoryAlarm = new cloudwatch.Alarm(this, 'HighMemoryAlarm', {
      alarmName: `batbern-${props.environment}-high-memory`,
      alarmDescription: 'Alert when memory utilization is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/ECS',
        metricName: 'MemoryUtilization',
        dimensionsMap: {
          ServiceName: `batbern-${props.environment}`,
        },
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 80,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      highMemoryAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      highMemoryAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(highMemoryAlarm);

    // High Disk Utilization Alarm
    const highDiskAlarm = new cloudwatch.Alarm(this, 'HighDiskAlarm', {
      alarmName: `batbern-${props.environment}-high-disk`,
      alarmDescription: 'Alert when disk utilization is high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/EBS',
        metricName: 'VolumeUtilization',
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 85,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      highDiskAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      highDiskAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(highDiskAlarm);

    // Database Connection Alarm
    const databaseConnectionAlarm = new cloudwatch.Alarm(this, 'DatabaseConnectionAlarm', {
      alarmName: `batbern-${props.environment}-database-connections`,
      alarmDescription: 'Alert when database connections are high',
      metric: new cloudwatch.Metric({
        namespace: 'AWS/RDS',
        metricName: 'DatabaseConnections',
        dimensionsMap: {
          DBClusterIdentifier: `batbern-${props.environment}`,
        },
        statistic: 'Average',
        period: cdk.Duration.minutes(5),
      }),
      threshold: 80,
      evaluationPeriods: 2,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      actionsEnabled: true,
    });

    if (props.alarmTopic) {
      databaseConnectionAlarm.addAlarmAction(new cloudwatchActions.SnsAction(props.alarmTopic));
      databaseConnectionAlarm.addOkAction(new cloudwatchActions.SnsAction(props.alarmTopic));
    }

    alarms.push(databaseConnectionAlarm);

    return alarms;
  }
}
