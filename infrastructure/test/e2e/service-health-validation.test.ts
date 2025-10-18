/**
 * Service Health Validation Tests
 *
 * Validates that all deployed services are healthy and accessible.
 * Runs after deployment to ensure services are properly configured.
 *
 * Run with: TEST_E2E=true TEST_ENVIRONMENT=staging npm test -- test/e2e/service-health-validation.test.ts
 */

import { ECSClient, DescribeServicesCommand, ListServicesCommand } from '@aws-sdk/client-ecs';
import { CloudFormationClient, DescribeStacksCommand } from '@aws-sdk/client-cloudformation';

const describeIf = process.env.TEST_E2E === 'true' ? describe : describe.skip;

describeIf('Service Health Validation', () => {
  const environment = process.env.TEST_ENVIRONMENT || 'staging';
  const region = process.env.AWS_REGION || 'eu-central-1';
  const clusterName = `batbern-${environment}`;

  let ecsClient: ECSClient;
  let cfnClient: CloudFormationClient;

  beforeAll(() => {
    ecsClient = new ECSClient({ region });
    cfnClient = new CloudFormationClient({ region });
  });

  afterAll(() => {
    ecsClient.destroy();
    cfnClient.destroy();
  });

  const expectedServices = [
    'event-management',
    'speaker-coordination',
    'partner-coordination',
    'attendee-experience',
    'company-user-management',
    'api-gateway',
  ];

  test('should have ECS cluster deployed', async () => {
    const stackName = `BATbern-${environment}-Cluster`;

    const response = await cfnClient.send(
      new DescribeStacksCommand({ StackName: stackName })
    );

    expect(response.Stacks).toBeDefined();
    expect(response.Stacks!.length).toBeGreaterThan(0);

    const stack = response.Stacks![0];
    expect(stack.StackStatus).toMatch(/CREATE_COMPLETE|UPDATE_COMPLETE/);
  });

  test('should have all microservices deployed', async () => {
    const services = await ecsClient.send(
      new ListServicesCommand({ cluster: clusterName })
    );

    expect(services.serviceArns).toBeDefined();
    expect(services.serviceArns!.length).toBeGreaterThan(0);

    // Verify each expected service is deployed
    for (const expectedService of expectedServices) {
      const serviceExists = services.serviceArns!.some(arn =>
        arn.toLowerCase().includes(expectedService.toLowerCase())
      );
      expect(serviceExists).toBe(true);
    }
  }, 30000);

  test('should have all services running with desired count', async () => {
    // Get all services
    const listResponse = await ecsClient.send(
      new ListServicesCommand({ cluster: clusterName })
    );

    if (!listResponse.serviceArns || listResponse.serviceArns.length === 0) {
      throw new Error('No services found in cluster');
    }

    // Describe all services
    const describeResponse = await ecsClient.send(
      new DescribeServicesCommand({
        cluster: clusterName,
        services: listResponse.serviceArns,
      })
    );

    expect(describeResponse.services).toBeDefined();

    // Verify each service is ACTIVE and running
    for (const service of describeResponse.services!) {
      expect(service.status).toBe('ACTIVE');
      expect(service.runningCount).toBeGreaterThan(0);
      expect(service.runningCount).toBe(service.desiredCount);

      // Check deployment status
      const primaryDeployment = service.deployments?.find(d => d.status === 'PRIMARY');
      expect(primaryDeployment).toBeDefined();
      expect(primaryDeployment!.rolloutState).toMatch(/COMPLETED|IN_PROGRESS/);
    }
  }, 60000);

  test('should have database stack deployed', async () => {
    const stackName = `BATbern-${environment}-Database`;

    const response = await cfnClient.send(
      new DescribeStacksCommand({ StackName: stackName })
    );

    const stack = response.Stacks![0];
    expect(stack.StackStatus).toMatch(/CREATE_COMPLETE|UPDATE_COMPLETE/);
  });

  test('should have API Gateway stack deployed', async () => {
    const stackName = `BATbern-${environment}-ApiGateway`;

    const response = await cfnClient.send(
      new DescribeStacksCommand({ StackName: stackName })
    );

    const stack = response.Stacks![0];
    expect(stack.StackStatus).toMatch(/CREATE_COMPLETE|UPDATE_COMPLETE/);
  });

  test('should have no failed service deployments', async () => {
    const listResponse = await ecsClient.send(
      new ListServicesCommand({ cluster: clusterName })
    );

    const describeResponse = await ecsClient.send(
      new DescribeServicesCommand({
        cluster: clusterName,
        services: listResponse.serviceArns!,
      })
    );

    for (const service of describeResponse.services!) {
      // Check for failed deployments
      const failedDeployments = service.deployments?.filter(d => d.rolloutState === 'FAILED');
      expect(failedDeployments || []).toHaveLength(0);

      // Check service events for errors (recent 5 events)
      const recentEvents = service.events?.slice(0, 5) || [];
      const errorEvents = recentEvents.filter(e =>
        e.message?.toLowerCase().includes('error') ||
        e.message?.toLowerCase().includes('failed')
      );

      // Log warnings but don't fail test (might be old errors)
      if (errorEvents.length > 0) {
        console.warn(`Service ${service.serviceName} has error events:`, errorEvents);
      }
    }
  }, 60000);
});
