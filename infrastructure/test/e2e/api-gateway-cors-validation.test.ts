/**
 * API Gateway CORS Configuration Validation Tests
 *
 * Validates that AWS API Gateway CORS configuration matches requirements.
 * Prevents CORS-related incidents by validating infrastructure configuration.
 *
 * Run with: TEST_E2E=true TEST_ENVIRONMENT=staging npm test -- test/e2e/api-gateway-cors-validation.test.ts
 */

import { APIGatewayClient, GetRestApisCommand, GetResourcesCommand, GetMethodCommand } from '@aws-sdk/client-api-gateway';

// Only run these tests when TEST_E2E is explicitly set
const describeIf = process.env.TEST_E2E === 'true' ? describe : describe.skip;

describeIf('API Gateway CORS Configuration', () => {
  const environment = process.env.TEST_ENVIRONMENT || 'staging';
  const region = process.env.AWS_REGION || 'eu-central-1';

  let apiGatewayClient: APIGatewayClient;
  let apiId: string;

  // Required CORS headers that frontend uses
  const requiredHeaders = [
    'Authorization',
    'Content-Type',
    'X-Correlation-ID',
    'Accept-Language',
    'Accept',
  ];

  beforeAll(async () => {
    apiGatewayClient = new APIGatewayClient({ region });

    // Find the API Gateway for this environment
    const apis = await apiGatewayClient.send(new GetRestApisCommand({}));
    const apiName = `BATbern Platform API - ${environment}`;
    const api = apis.items?.find(item => item.name === apiName);

    if (!api || !api.id) {
      throw new Error(`API Gateway not found: ${apiName}`);
    }

    apiId = api.id;
  });

  afterAll(async () => {
    apiGatewayClient.destroy();
  });

  test('should have CORS-enabled API Gateway', async () => {
    expect(apiId).toBeDefined();
    expect(apiId).not.toBe('');
  });

  test('should allow required custom headers in CORS configuration', async () => {
    // Get API Gateway resources
    const resources = await apiGatewayClient.send(
      new GetResourcesCommand({ restApiId: apiId })
    );

    // Find the OPTIONS method (preflight)
    let optionsMethodFound = false;
    let corsHeadersValid = false;

    for (const resource of resources.items || []) {
      if (!resource.id || !resource.resourceMethods?.OPTIONS) {
        continue;
      }

      optionsMethodFound = true;

      // Get OPTIONS method configuration
      const method = await apiGatewayClient.send(
        new GetMethodCommand({
          restApiId: apiId,
          resourceId: resource.id,
          httpMethod: 'OPTIONS',
        })
      );

      // Check method response headers for CORS
      const responseParameters = method.methodIntegration?.integrationResponses?.[0]?.responseParameters;

      if (responseParameters) {
        const allowHeadersKey = Object.keys(responseParameters).find(key =>
          key.toLowerCase().includes('access-control-allow-headers')
        );

        if (allowHeadersKey) {
          const allowedHeaders = responseParameters[allowHeadersKey];

          // Verify all required headers are allowed
          const allHeadersAllowed = requiredHeaders.every(header =>
            allowedHeaders?.toLowerCase().includes(header.toLowerCase())
          );

          if (allHeadersAllowed) {
            corsHeadersValid = true;
            break;
          }
        }
      }
    }

    expect(optionsMethodFound).toBe(true);
    expect(corsHeadersValid).toBe(true);
  }, 30000);

  test('should allow correct origins based on environment', async () => {
    const expectedOrigin = environment === 'production'
      ? 'https://www.batbern.ch'
      : environment === 'staging'
      ? 'https://staging.batbern.ch'
      : 'http://localhost:3000';

    // Get API Gateway resources
    const resources = await apiGatewayClient.send(
      new GetResourcesCommand({ restApiId: apiId })
    );

    let originConfigFound = false;

    for (const resource of resources.items || []) {
      if (!resource.id || !resource.resourceMethods?.OPTIONS) {
        continue;
      }

      const method = await apiGatewayClient.send(
        new GetMethodCommand({
          restApiId: apiId,
          resourceId: resource.id,
          httpMethod: 'OPTIONS',
        })
      );

      const responseParameters = method.methodIntegration?.integrationResponses?.[0]?.responseParameters;

      if (responseParameters) {
        const allowOriginKey = Object.keys(responseParameters).find(key =>
          key.toLowerCase().includes('access-control-allow-origin')
        );

        if (allowOriginKey) {
          const allowedOrigin = responseParameters[allowOriginKey];
          if (allowedOrigin?.includes(expectedOrigin) || allowedOrigin === "'*'") {
            originConfigFound = true;
            break;
          }
        }
      }
    }

    expect(originConfigFound).toBe(true);
  }, 30000);

  test('should allow credentials in CORS configuration', async () => {
    const resources = await apiGatewayClient.send(
      new GetResourcesCommand({ restApiId: apiId })
    );

    let credentialsAllowed = false;

    for (const resource of resources.items || []) {
      if (!resource.id || !resource.resourceMethods?.OPTIONS) {
        continue;
      }

      const method = await apiGatewayClient.send(
        new GetMethodCommand({
          restApiId: apiId,
          resourceId: resource.id,
          httpMethod: 'OPTIONS',
        })
      );

      const responseParameters = method.methodIntegration?.integrationResponses?.[0]?.responseParameters;

      if (responseParameters) {
        const allowCredentialsKey = Object.keys(responseParameters).find(key =>
          key.toLowerCase().includes('access-control-allow-credentials')
        );

        if (allowCredentialsKey) {
          const allowCredentials = responseParameters[allowCredentialsKey];
          if (allowCredentials?.toLowerCase().includes('true')) {
            credentialsAllowed = true;
            break;
          }
        }
      }
    }

    expect(credentialsAllowed).toBe(true);
  }, 30000);

  test('should allow required HTTP methods', async () => {
    const requiredMethods = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'];

    const resources = await apiGatewayClient.send(
      new GetResourcesCommand({ restApiId: apiId })
    );

    let methodsConfigured = false;

    for (const resource of resources.items || []) {
      if (!resource.id || !resource.resourceMethods?.OPTIONS) {
        continue;
      }

      const method = await apiGatewayClient.send(
        new GetMethodCommand({
          restApiId: apiId,
          resourceId: resource.id,
          httpMethod: 'OPTIONS',
        })
      );

      const responseParameters = method.methodIntegration?.integrationResponses?.[0]?.responseParameters;

      if (responseParameters) {
        const allowMethodsKey = Object.keys(responseParameters).find(key =>
          key.toLowerCase().includes('access-control-allow-methods')
        );

        if (allowMethodsKey) {
          const allowedMethods = responseParameters[allowMethodsKey];
          const allMethodsAllowed = requiredMethods.every(m =>
            allowedMethods?.includes(m)
          );

          if (allMethodsAllowed) {
            methodsConfigured = true;
            break;
          }
        }
      }
    }

    expect(methodsConfigured).toBe(true);
  }, 30000);
});
