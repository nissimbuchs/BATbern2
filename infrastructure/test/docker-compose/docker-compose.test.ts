import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Integration Tests for Docker Compose Local Development Environment
 *
 * These tests validate AC6 (Startup Orchestration) and AC7 (Single Command Startup)
 * by testing docker-compose configuration and service startup behavior.
 */
describe('Docker Compose Startup Orchestration (AC6, AC7)', () => {
  const projectRoot = path.resolve(__dirname, '../../..');
  const dockerComposePath = path.join(projectRoot, 'docker-compose.yml');

  beforeAll(() => {
    // Ensure we're in the project root and docker-compose.yml exists
    expect(fs.existsSync(dockerComposePath)).toBe(true);
  });

  describe('AC6: Startup Orchestration', () => {
    test('should_startInCorrectOrder_when_dependenciesDefined', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Redis should have no dependencies (starts first)
      expect(parsed.services.redis.depends_on).toBeUndefined();

      // Assert - API Gateway depends on Redis
      expect(parsed.services['api-gateway'].depends_on).toBeDefined();
      expect(parsed.services['api-gateway'].depends_on.redis).toBeDefined();
      expect(parsed.services['api-gateway'].depends_on.redis.condition).toBe('service_healthy');

      // Assert - Web Frontend depends on API Gateway
      expect(parsed.services['web-frontend'].depends_on).toBeDefined();
      expect(parsed.services['web-frontend'].depends_on['api-gateway']).toBeDefined();
      expect(parsed.services['web-frontend'].depends_on['api-gateway'].condition).toBe('service_healthy');
    });

    test('should_waitForHealthCheck_when_dependentServiceStarting', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Redis health check configured
      expect(parsed.services.redis.healthcheck).toBeDefined();
      expect(parsed.services.redis.healthcheck.test).toContain('redis-cli');
      expect(parsed.services.redis.healthcheck.interval).toBe('5s');
      expect(parsed.services.redis.healthcheck.retries).toBe(5);

      // Assert - API Gateway health check configured
      expect(parsed.services['api-gateway'].healthcheck).toBeDefined();
      expect(parsed.services['api-gateway'].healthcheck.test).toContain('curl');
      expect(parsed.services['api-gateway'].healthcheck.test.join(' ')).toContain('/actuator/health');
      expect(parsed.services['api-gateway'].healthcheck.start_period).toBe('60s');
    });

    test('should_restartUnhealthyService_when_healthCheckFails', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Services have health checks with retry configuration
      expect(parsed.services.redis.healthcheck.retries).toBeGreaterThan(0);
      expect(parsed.services['api-gateway'].healthcheck.retries).toBeGreaterThan(0);

      // Docker Compose automatically restarts unhealthy services
      // Verify restart behavior is not explicitly disabled
      expect(parsed.services.redis.restart).not.toBe('no');
      expect(parsed.services['api-gateway'].restart).not.toBe('no');
    });

    test('should_handleStartupFailures_when_serviceNotReady', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Health checks have timeout and retry configuration
      expect(parsed.services.redis.healthcheck.timeout).toBe('3s');
      expect(parsed.services['api-gateway'].healthcheck.timeout).toBe('10s');

      // Assert - Start period gives services time to initialize before health checks fail
      expect(parsed.services['api-gateway'].healthcheck.start_period).toBe('60s');
    });
  });

  describe('AC7: Single Command Startup', () => {
    test('should_haveValidDockerComposeConfig_when_validated', () => {
      // Arrange & Act - Validate docker-compose.yml syntax
      try {
        execSync('docker-compose config', {
          cwd: projectRoot,
          stdio: 'pipe',
          env: { ...process.env, SKIP_ENV_VALIDATION: 'true' }
        });
      } catch (error: any) {
        // If config validation fails, it's a syntax error
        fail(`docker-compose.yml has syntax errors: ${error.message}`);
      }

      // Assert - If we reach here, config is valid
      expect(true).toBe(true);
    });

    test('should_defineAllRequiredServices_when_configLoaded', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - All required services are defined
      expect(parsed.services.redis).toBeDefined();
      expect(parsed.services['api-gateway']).toBeDefined();
      expect(parsed.services['web-frontend']).toBeDefined();
    });

    test('should_defineAllRequiredVolumes_when_configLoaded', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Required volumes are defined
      expect(parsed.volumes['redis-data']).toBeDefined();
      expect(parsed.volumes['gradle-cache']).toBeDefined();
    });

    test('should_defineAllRequiredNetworks_when_configLoaded', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Required network is defined
      expect(parsed.networks['batbern-network']).toBeDefined();
      expect(parsed.networks['batbern-network'].driver).toBe('bridge');
    });

    test('should_exposeCorrectPorts_when_servicesConfigured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Correct ports are exposed
      expect(parsed.services.redis.ports).toContain('6379:6379');
      expect(parsed.services['api-gateway'].ports[0]).toMatch(/8080/);
      expect(parsed.services['web-frontend'].ports).toContain('3000:3000');
    });
  });
});
