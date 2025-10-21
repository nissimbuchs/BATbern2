import { CloudWatchClient, GetDashboardCommand, GetMetricDataCommand } from '@aws-sdk/client-cloudwatch';

/**
 * E2E Test for CloudWatch Dashboard Creation and Validation
 *
 * This test validates that the monitoring infrastructure is correctly deployed
 * and dashboards are accessible with the expected widgets and metrics.
 *
 * NOTE: These tests require actual AWS resources and will be skipped unless
 * TEST_E2E=true environment variable is set (for deployed environments only)
 */
const describeE2E = process.env.TEST_E2E === 'true' ? describe : describe.skip;

describeE2E('CloudWatch Dashboard E2E Tests', () => {
  let cloudwatchClient: CloudWatchClient;
  const environment = process.env.TEST_ENVIRONMENT || 'dev';
  const dashboardName = `BATbern-${environment}`;

  beforeAll(() => {
    cloudwatchClient = new CloudWatchClient({
      region: process.env.AWS_REGION || 'eu-central-1'
    });
  });

  describe('Dashboard Creation', () => {
    test('should_createCloudWatchDashboard_when_environmentDeployed', async () => {
      // This test verifies that the CloudWatch dashboard exists after deployment
      const command = new GetDashboardCommand({ DashboardName: dashboardName });

      const response = await cloudwatchClient.send(command);

      expect(response.DashboardName).toBe(dashboardName);
      expect(response.DashboardBody).toBeDefined();

      // Parse dashboard body to verify widgets
      const dashboardBody = JSON.parse(response.DashboardBody!);
      expect(dashboardBody.widgets).toBeDefined();
      expect(dashboardBody.widgets.length).toBeGreaterThan(0);
    });

    test('should_haveServiceHealthWidgets_when_dashboardLoaded', async () => {
      // This test verifies service health monitoring widgets exist
      const command = new GetDashboardCommand({ DashboardName: dashboardName });
      const response = await cloudwatchClient.send(command);

      const dashboardBody = JSON.parse(response.DashboardBody!);
      const widgets = dashboardBody.widgets;

      // Verify CPU utilization widget exists
      const cpuWidget = widgets.find((w: any) =>
        w.properties?.title?.includes('CPU')
      );
      expect(cpuWidget).toBeDefined();

      // Verify error rate widget exists
      const errorWidget = widgets.find((w: any) =>
        w.properties?.title?.includes('Error')
      );
      expect(errorWidget).toBeDefined();

      // Verify latency widget exists
      const latencyWidget = widgets.find((w: any) =>
        w.properties?.title?.includes('Latency')
      );
      expect(latencyWidget).toBeDefined();
    });

    test('should_haveBusinessMetricsWidgets_when_dashboardLoaded', async () => {
      // This test verifies business metrics widgets exist
      const command = new GetDashboardCommand({ DashboardName: dashboardName });
      const response = await cloudwatchClient.send(command);

      const dashboardBody = JSON.parse(response.DashboardBody!);
      const widgets = dashboardBody.widgets;

      // Verify event creation metrics widget
      const eventMetricsWidget = widgets.find((w: any) =>
        w.properties?.title?.includes('Event')
      );
      expect(eventMetricsWidget).toBeDefined();
    });

    test('should_haveCostMonitoringWidgets_when_dashboardLoaded', async () => {
      // This test verifies cost monitoring widgets exist
      const command = new GetDashboardCommand({ DashboardName: dashboardName });
      const response = await cloudwatchClient.send(command);

      const dashboardBody = JSON.parse(response.DashboardBody!);
      const widgets = dashboardBody.widgets;

      // Verify cost monitoring widget exists
      const costWidget = widgets.find((w: any) =>
        w.properties?.title?.includes('Cost') || w.properties?.title?.includes('Budget')
      );
      expect(costWidget).toBeDefined();
    });

    test('should_haveSecurityDashboardWidgets_when_dashboardLoaded', async () => {
      // This test verifies security monitoring widgets exist
      const command = new GetDashboardCommand({ DashboardName: dashboardName });
      const response = await cloudwatchClient.send(command);

      const dashboardBody = JSON.parse(response.DashboardBody!);
      const widgets = dashboardBody.widgets;

      // Verify security events widget exists
      const securityWidget = widgets.find((w: any) =>
        w.properties?.title?.includes('Security') || w.properties?.title?.includes('Unauthorized')
      );
      expect(securityWidget).toBeDefined();
    });
  });

  describe('Metric Collection', () => {
    test('should_collectCustomMetrics_when_serviceOperates', async () => {
      // This test verifies custom metrics are being collected
      const endTime = new Date();
      const startTime = new Date(endTime.getTime() - 3600000); // 1 hour ago

      const command = new GetMetricDataCommand({
        MetricDataQueries: [
          {
            Id: 'eventCreationMetric',
            MetricStat: {
              Metric: {
                Namespace: 'BATbern',
                MetricName: 'EventsCreated',
              },
              Period: 300,
              Stat: 'Sum',
            },
          },
        ],
        StartTime: startTime,
        EndTime: endTime,
      });

      const response = await cloudwatchClient.send(command);

      expect(response.MetricDataResults).toBeDefined();
      expect(response.MetricDataResults!.length).toBeGreaterThan(0);
    });
  });
});
