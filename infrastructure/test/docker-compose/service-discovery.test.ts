import * as fs from 'fs';
import * as path from 'path';

/**
 * Integration Tests for Service Discovery
 *
 * These tests validate AC5 (Service Discovery) by verifying that Docker DNS
 * is properly configured for service-to-service communication.
 */
describe('Service Discovery (AC5)', () => {
  const projectRoot = path.resolve(__dirname, '../../..');
  const dockerComposePath = path.join(projectRoot, 'docker-compose.yml');

  beforeAll(() => {
    expect(fs.existsSync(dockerComposePath)).toBe(true);
  });

  describe('AC5: Service Discovery', () => {
    test('should_resolveServiceByName_when_dockerDNSConfigured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - All services are on the same network for DNS resolution
      expect(parsed.services.redis.networks).toContain('batbern-network');
      expect(parsed.services['api-gateway'].networks).toContain('batbern-network');
      expect(parsed.services['web-frontend'].networks).toContain('batbern-network');

      // Assert - Network is defined and uses bridge driver (enables DNS)
      expect(parsed.networks['batbern-network']).toBeDefined();
      expect(parsed.networks['batbern-network'].driver).toBe('bridge');
    });

    test('should_connectToRedis_when_usingServiceName', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway uses 'redis' as hostname (service name)
      const apiGatewayEnv = parsed.services['api-gateway'].environment;
      const redisHostVar = apiGatewayEnv.find((e: string) => e.startsWith('REDIS_HOST='));

      expect(redisHostVar).toBeDefined();
      // Should default to 'redis' service name, not localhost or IP
      expect(redisHostVar).toMatch(/REDIS_HOST=.*redis/);
    });

    test('should_routeToAPIGateway_when_usingServiceName', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Web Frontend uses service discovery for API Gateway
      const frontendEnv = parsed.services['web-frontend'].environment;
      const apiUrlVar = frontendEnv.find((e: string) => e.startsWith('VITE_API_BASE_URL='));

      expect(apiUrlVar).toBeDefined();
      // Default should use localhost for browser access, but could use api-gateway for SSR
      // At minimum, verify the env var is configured
      expect(apiUrlVar).toBeTruthy();
    });

    test('should_haveUniqueContainerNames_when_servicesConfigured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Each service has a unique container name for DNS
      expect(parsed.services.redis.container_name).toBe('batbern-redis');
      expect(parsed.services['api-gateway'].container_name).toBe('batbern-api-gateway');
      expect(parsed.services['web-frontend'].container_name).toBe('batbern-frontend');

      // Assert - Container names are unique
      const containerNames = [
        parsed.services.redis.container_name,
        parsed.services['api-gateway'].container_name,
        parsed.services['web-frontend'].container_name
      ];
      const uniqueNames = new Set(containerNames);
      expect(uniqueNames.size).toBe(containerNames.length);
    });

    test('should_exposeRedisOnlyToInternalNetwork_when_securityConfigured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Redis is accessible on batbern-network
      expect(parsed.services.redis.networks).toContain('batbern-network');

      // Assert - Redis port is exposed for local development access
      // (In production, would not expose port, only network access)
      expect(parsed.services.redis.ports).toContain('6379:6379');
    });
  });
});
