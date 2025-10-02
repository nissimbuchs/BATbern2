import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import { Construct } from 'constructs';

export interface MonitoringWidgetsProps {
  environment: string;
  dashboardName?: string;
}

/**
 * Reusable construct for creating monitoring dashboard widgets
 *
 * This construct encapsulates the logic for creating various types of monitoring widgets
 * including service health, business metrics, cost monitoring, security, and X-Ray tracing.
 */
export class MonitoringWidgetsConstruct extends Construct {
  public readonly widgets: cloudwatch.IWidget[];

  constructor(scope: Construct, id: string, props: MonitoringWidgetsProps) {
    super(scope, id);

    this.widgets = [];

    // Add all widget categories
    this.widgets.push(...this.createServiceHealthWidgets(props));
    this.widgets.push(...this.createBusinessMetricsWidgets(props));
    this.widgets.push(...this.createCostMonitoringWidgets(props));
    this.widgets.push(...this.createSecurityWidgets(props));
    this.widgets.push(...this.createXRayTracingWidgets(props));
  }

  /**
   * Create service health monitoring widgets
   */
  private createServiceHealthWidgets(props: MonitoringWidgetsProps): cloudwatch.IWidget[] {
    const widgets: cloudwatch.IWidget[] = [];

    // CPU Utilization Widget
    const cpuMetric = new cloudwatch.Metric({
      namespace: 'AWS/ECS',
      metricName: 'CPUUtilization',
      dimensionsMap: {
        ServiceName: `batbern-${props.environment}`,
      },
      statistic: 'Average',
      period: cdk.Duration.minutes(5),
    });

    // Error Rate Widget
    const errorMetric = new cloudwatch.Metric({
      namespace: 'AWS/ApiGateway',
      metricName: '5XXError',
      dimensionsMap: {
        ApiName: `batbern-${props.environment}`,
      },
      statistic: 'Sum',
      period: cdk.Duration.minutes(5),
    });

    // Latency Widget
    const latencyMetric = new cloudwatch.Metric({
      namespace: 'AWS/ApiGateway',
      metricName: 'Latency',
      dimensionsMap: {
        ApiName: `batbern-${props.environment}`,
      },
      statistic: 'p95',
      period: cdk.Duration.minutes(5),
    });

    widgets.push(
      new cloudwatch.GraphWidget({
        title: 'Service Health - CPU Utilization',
        left: [cpuMetric],
        width: 12,
        height: 6,
      }),
      new cloudwatch.GraphWidget({
        title: 'Service Health - Error Rate',
        left: [errorMetric],
        width: 12,
        height: 6,
      }),
      new cloudwatch.GraphWidget({
        title: 'Service Health - Latency (P95)',
        left: [latencyMetric],
        width: 24,
        height: 6,
      })
    );

    return widgets;
  }

  /**
   * Create business metrics widgets
   */
  private createBusinessMetricsWidgets(props: MonitoringWidgetsProps): cloudwatch.IWidget[] {
    const widgets: cloudwatch.IWidget[] = [];

    // Event Creation Metrics
    const eventCreationMetric = new cloudwatch.Metric({
      namespace: 'BATbern',
      metricName: 'EventsCreated',
      statistic: 'Sum',
      period: cdk.Duration.hours(1),
    });

    // User Activity Metrics
    const userActivityMetric = new cloudwatch.Metric({
      namespace: 'BATbern',
      metricName: 'UserActivity',
      statistic: 'Sum',
      period: cdk.Duration.hours(1),
    });

    widgets.push(
      new cloudwatch.GraphWidget({
        title: 'Business Metrics - Event Creation',
        left: [eventCreationMetric],
        width: 12,
        height: 6,
      }),
      new cloudwatch.GraphWidget({
        title: 'Business Metrics - User Activity',
        left: [userActivityMetric],
        width: 12,
        height: 6,
      })
    );

    return widgets;
  }

  /**
   * Create cost monitoring widgets
   */
  private createCostMonitoringWidgets(props: MonitoringWidgetsProps): cloudwatch.IWidget[] {
    const widgets: cloudwatch.IWidget[] = [];

    // AWS Cost Metrics
    const estimatedChargesMetric = new cloudwatch.Metric({
      namespace: 'AWS/Billing',
      metricName: 'EstimatedCharges',
      dimensionsMap: {
        Currency: 'USD',
      },
      statistic: 'Maximum',
      period: cdk.Duration.hours(6),
    });

    widgets.push(
      new cloudwatch.GraphWidget({
        title: 'Cost Monitoring - Budget Tracking',
        left: [estimatedChargesMetric],
        width: 24,
        height: 6,
      })
    );

    return widgets;
  }

  /**
   * Create security monitoring widgets
   */
  private createSecurityWidgets(props: MonitoringWidgetsProps): cloudwatch.IWidget[] {
    const widgets: cloudwatch.IWidget[] = [];

    // Unauthorized Access Attempts
    const unauthorizedAccessMetric = new cloudwatch.Metric({
      namespace: 'BATbern',
      metricName: 'UnauthorizedAccess',
      statistic: 'Sum',
      period: cdk.Duration.minutes(15),
    });

    // Security Events
    const securityEventsMetric = new cloudwatch.Metric({
      namespace: 'BATbern',
      metricName: 'SecurityEvents',
      statistic: 'Sum',
      period: cdk.Duration.minutes(15),
    });

    widgets.push(
      new cloudwatch.GraphWidget({
        title: 'Security - Unauthorized Access Attempts',
        left: [unauthorizedAccessMetric],
        width: 12,
        height: 6,
      }),
      new cloudwatch.GraphWidget({
        title: 'Security - Security Events',
        left: [securityEventsMetric],
        width: 12,
        height: 6,
      })
    );

    return widgets;
  }

  /**
   * Create X-Ray tracing widgets
   */
  private createXRayTracingWidgets(props: MonitoringWidgetsProps): cloudwatch.IWidget[] {
    const widgets: cloudwatch.IWidget[] = [];

    // X-Ray Trace Count
    const traceCountMetric = new cloudwatch.Metric({
      namespace: 'AWS/X-Ray',
      metricName: 'TraceCount',
      statistic: 'Sum',
      period: cdk.Duration.minutes(5),
    });

    // X-Ray Response Time
    const responseTimeMetric = new cloudwatch.Metric({
      namespace: 'AWS/X-Ray',
      metricName: 'ResponseTime',
      statistic: 'Average',
      period: cdk.Duration.minutes(5),
    });

    widgets.push(
      new cloudwatch.GraphWidget({
        title: 'X-Ray Trace Analysis - Trace Count',
        left: [traceCountMetric],
        width: 12,
        height: 6,
      }),
      new cloudwatch.GraphWidget({
        title: 'X-Ray Trace Analysis - Response Time',
        left: [responseTimeMetric],
        width: 12,
        height: 6,
      })
    );

    return widgets;
  }
}
