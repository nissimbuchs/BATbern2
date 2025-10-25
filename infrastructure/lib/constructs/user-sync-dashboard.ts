import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import { Construct } from 'constructs';

/**
 * CloudWatch Dashboard for User Sync Monitoring
 *
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC5: Monitoring and observability
 *
 * Dashboard widgets:
 * - Lambda latency metrics (PostConfirmation, PreTokenGeneration, PreAuthentication)
 * - User creation metrics (PostConfirmation, JIT Provisioning, Reconciliation)
 * - Failure rates
 * - Drift detection
 * - Reconciliation job metrics
 *
 * ADR-001: Monitors unidirectional sync (Cognito → Database)
 */
export interface UserSyncDashboardProps {
  /**
   * Environment name (dev, staging, prod)
   */
  readonly environment: string;
}

export class UserSyncDashboard extends Construct {
  public readonly dashboard: cloudwatch.Dashboard;

  constructor(scope: Construct, id: string, props: UserSyncDashboardProps) {
    super(scope, id);

    // Create CloudWatch Dashboard
    this.dashboard = new cloudwatch.Dashboard(this, 'UserSyncDashboard', {
      dashboardName: `BATbern-UserSync-${props.environment}`,
    });

    // === ROW 1: Lambda Latency Metrics ===
    this.dashboard.addWidgets(
      // PostConfirmation Latency
      new cloudwatch.GraphWidget({
        title: 'PostConfirmation Lambda Latency',
        width: 8,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PostConfirmation' },
            statistic: 'Average',
            label: 'Average',
            color: cloudwatch.Color.BLUE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PostConfirmation' },
            statistic: 'p95',
            label: 'p95',
            color: cloudwatch.Color.ORANGE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PostConfirmation' },
            statistic: 'Maximum',
            label: 'Max',
            color: cloudwatch.Color.RED,
          }),
        ],
        leftYAxis: {
          label: 'Latency (ms)',
          min: 0,
        },
      }),

      // PreTokenGeneration Latency
      new cloudwatch.GraphWidget({
        title: 'PreTokenGeneration Lambda Latency',
        width: 8,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PreTokenGeneration' },
            statistic: 'Average',
            label: 'Average',
            color: cloudwatch.Color.BLUE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PreTokenGeneration' },
            statistic: 'p95',
            label: 'p95',
            color: cloudwatch.Color.ORANGE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PreTokenGeneration' },
            statistic: 'Maximum',
            label: 'Max',
            color: cloudwatch.Color.RED,
          }),
        ],
        leftYAxis: {
          label: 'Latency (ms)',
          min: 0,
        },
      }),

      // PreAuthentication Latency
      new cloudwatch.GraphWidget({
        title: 'PreAuthentication Lambda Latency',
        width: 8,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PreAuthentication' },
            statistic: 'Average',
            label: 'Average',
            color: cloudwatch.Color.BLUE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncLatency',
            dimensionsMap: { SyncType: 'PreAuthentication' },
            statistic: 'p95',
            label: 'p95',
            color: cloudwatch.Color.ORANGE,
          }),
        ],
        leftYAxis: {
          label: 'Latency (ms)',
          min: 0,
        },
      })
    );

    // === ROW 2: User Creation Metrics ===
    this.dashboard.addWidgets(
      // User Created by Source
      new cloudwatch.GraphWidget({
        title: 'Users Created (by Source)',
        width: 12,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'UserCreated',
            dimensionsMap: { Source: 'POST_CONFIRMATION' },
            statistic: 'Sum',
            label: 'PostConfirmation',
            color: cloudwatch.Color.GREEN,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'UserCreated',
            dimensionsMap: { Source: 'JIT_PROVISIONING' },
            statistic: 'Sum',
            label: 'JIT Provisioning',
            color: cloudwatch.Color.BLUE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'UserCreated',
            dimensionsMap: { Source: 'RECONCILIATION' },
            statistic: 'Sum',
            label: 'Reconciliation',
            color: cloudwatch.Color.PURPLE,
          }),
        ],
        leftYAxis: {
          label: 'Count',
          min: 0,
        },
      }),

      // Sync Failures
      new cloudwatch.GraphWidget({
        title: 'Sync Failures (by Type)',
        width: 12,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncFailures',
            dimensionsMap: { SyncType: 'PostConfirmation' },
            statistic: 'Sum',
            label: 'PostConfirmation',
            color: cloudwatch.Color.RED,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'SyncFailures',
            dimensionsMap: { SyncType: 'JITProvisioning' },
            statistic: 'Sum',
            label: 'JIT Provisioning',
            color: cloudwatch.Color.ORANGE,
          }),
        ],
        leftYAxis: {
          label: 'Failures',
          min: 0,
        },
      })
    );

    // === ROW 3: Drift Detection & Reconciliation ===
    this.dashboard.addWidgets(
      // Drift Detected
      new cloudwatch.GraphWidget({
        title: 'Drift Detected (Cognito ≠ Database)',
        width: 8,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'DriftDetected',
            statistic: 'Sum',
            label: 'Drift Count',
            color: cloudwatch.Color.ORANGE,
          }),
        ],
        leftYAxis: {
          label: 'Count',
          min: 0,
        },
      }),

      // Reconciliation - Orphaned Users
      new cloudwatch.GraphWidget({
        title: 'Reconciliation: Orphaned Users Deactivated',
        width: 8,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'ReconciliationOrphanedUsers',
            statistic: 'Sum',
            label: 'Orphaned Users',
            color: cloudwatch.Color.RED,
          }),
        ],
        leftYAxis: {
          label: 'Count',
          min: 0,
        },
      }),

      // Reconciliation - Missing Users
      new cloudwatch.GraphWidget({
        title: 'Reconciliation: Missing Users Created',
        width: 8,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'ReconciliationMissingUsers',
            statistic: 'Sum',
            label: 'Missing Users',
            color: cloudwatch.Color.BLUE,
          }),
        ],
        leftYAxis: {
          label: 'Count',
          min: 0,
        },
      })
    );

    // === ROW 4: Reconciliation Job Metrics ===
    this.dashboard.addWidgets(
      // Reconciliation Duration
      new cloudwatch.GraphWidget({
        title: 'Reconciliation Job Duration',
        width: 12,
        left: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'ReconciliationDuration',
            statistic: 'Average',
            label: 'Average Duration',
            color: cloudwatch.Color.BLUE,
          }),
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'ReconciliationDuration',
            statistic: 'Maximum',
            label: 'Max Duration',
            color: cloudwatch.Color.RED,
          }),
        ],
        leftYAxis: {
          label: 'Duration (ms)',
          min: 0,
        },
      }),

      // Single Value Widget - Latest Reconciliation
      new cloudwatch.SingleValueWidget({
        title: 'Latest Reconciliation Job Duration',
        width: 12,
        metrics: [
          new cloudwatch.Metric({
            namespace: 'BATbern/UserSync',
            metricName: 'ReconciliationDuration',
            statistic: 'Average',
          }),
        ],
      })
    );

    // === ROW 5: ADR-001 Architecture Notes ===
    this.dashboard.addWidgets(
      new cloudwatch.TextWidget({
        markdown: `
### User Sync Architecture (ADR-001)

**Unidirectional Sync: Cognito → Database**

- ✅ **PostConfirmation**: Creates DB user when Cognito confirms registration
- ✅ **PreTokenGeneration**: Reads roles from DB, adds to JWT custom claims
- ✅ **PreAuthentication**: Validates user is active before authentication
- ✅ **JIT Provisioning**: Creates DB user on first API request if missing
- ✅ **Reconciliation**: Daily job detects drift (orphaned/missing users)
- ❌ **NO Reverse Sync**: Database → Cognito (roles managed exclusively in DB)

**Key Metrics:**
- Lambda latency should be <1000ms (PostConfirmation), <500ms (PreToken)
- Failures should be <5 per 5-minute window
- Drift should be minimal after initial reconciliation
        `,
        width: 24,
        height: 8,
      })
    );
  }
}
