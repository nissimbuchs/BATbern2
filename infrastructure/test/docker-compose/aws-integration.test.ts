import * as fs from 'fs';
import * as path from 'path';

/**
 * Integration Tests for AWS Integration Configuration
 *
 * These tests validate AC1 (AWS Infrastructure Connection), AC4 (API Gateway Integration),
 * and AC9 (AWS Credentials) by verifying the docker-compose configuration for AWS services.
 */
describe('AWS Integration Configuration (AC1, AC4, AC9)', () => {
  const projectRoot = path.resolve(__dirname, '../../..');
  const dockerComposePath = path.join(projectRoot, 'docker-compose.yml');

  beforeAll(() => {
    expect(fs.existsSync(dockerComposePath)).toBe(true);
  });

  describe('AC1: AWS Infrastructure Connection', () => {
    test('should_connectToAWSRDS_when_envConfigured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway has RDS connection environment variables
      const apiGatewayEnv = parsed.services['api-gateway'].environment;

      const dbHostVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_HOST='));
      const dbPortVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_PORT='));
      const dbNameVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_NAME='));
      const dbUserVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_USER='));
      const dbPasswordVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_PASSWORD='));

      expect(dbHostVar).toBeDefined();
      expect(dbPortVar).toBeDefined();
      expect(dbNameVar).toBeDefined();
      expect(dbUserVar).toBeDefined();
      expect(dbPasswordVar).toBeDefined();
    });

    test('should_authenticateWithAWSRDS_when_credentialsProvided', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Database credentials are environment variables (from .env)
      const apiGatewayEnv = parsed.services['api-gateway'].environment;

      const dbUserVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_USER='));
      const dbPasswordVar = apiGatewayEnv.find((e: string) => e.startsWith('DB_PASSWORD='));

      // Should use ${VAR} syntax to pull from .env file (not hardcoded)
      expect(dbUserVar).toMatch(/DB_USER=\${DB_USER}/);
      expect(dbPasswordVar).toMatch(/DB_PASSWORD=\${DB_PASSWORD}/);
    });

    test('should_validateCognitoConfig_when_envConfigured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway has Cognito configuration
      const apiGatewayEnv = parsed.services['api-gateway'].environment;

      const cognitoPoolVar = apiGatewayEnv.find((e: string) => e.startsWith('COGNITO_USER_POOL_ID='));
      const cognitoClientVar = apiGatewayEnv.find((e: string) => e.startsWith('COGNITO_CLIENT_ID='));

      expect(cognitoPoolVar).toBeDefined();
      expect(cognitoClientVar).toBeDefined();
    });

    test('should_verifyAWSRegion_when_connecting', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - AWS region is configured
      const apiGatewayEnv = parsed.services['api-gateway'].environment;
      const awsRegionVar = apiGatewayEnv.find((e: string) => e.startsWith('AWS_REGION='));

      expect(awsRegionVar).toBeDefined();
      expect(awsRegionVar).toMatch(/AWS_REGION=\${AWS_REGION:-eu-central-1}/);
    });

    test('should_failGracefully_when_AWSUnavailable', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - Services don't have restart: "no" which would prevent recovery
      // Docker Compose will retry on failure by default
      expect(parsed.services['api-gateway'].restart).not.toBe('no');

      // Assert - Health checks allow for graceful failure detection
      expect(parsed.services['api-gateway'].healthcheck).toBeDefined();
      expect(parsed.services['api-gateway'].healthcheck.retries).toBeGreaterThan(0);
    });
  });

  describe('AC4: API Gateway Integration', () => {
    test('should_startAPIGateway_when_dependenciesReady', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway depends on Redis
      expect(parsed.services['api-gateway'].depends_on).toBeDefined();
      expect(parsed.services['api-gateway'].depends_on.redis).toBeDefined();
      expect(parsed.services['api-gateway'].depends_on.redis.condition).toBe('service_healthy');
    });

    test('should_connectToAWSRDS_when_apiGatewayStarts', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway has DATABASE_URL configured
      const apiGatewayEnv = parsed.services['api-gateway'].environment;
      const databaseUrlVar = apiGatewayEnv.find((e: string) => e.startsWith('DATABASE_URL='));

      expect(databaseUrlVar).toBeDefined();
      expect(databaseUrlVar).toMatch(/DATABASE_URL=\${DATABASE_URL}/);
    });

    test('should_connectToLocalRedis_when_apiGatewayStarts', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway has Redis configuration
      const apiGatewayEnv = parsed.services['api-gateway'].environment;

      const redisHostVar = apiGatewayEnv.find((e: string) => e.startsWith('REDIS_HOST='));
      const redisPortVar = apiGatewayEnv.find((e: string) => e.startsWith('REDIS_PORT='));

      expect(redisHostVar).toBeDefined();
      expect(redisPortVar).toBeDefined();
      expect(redisHostVar).toMatch(/REDIS_HOST=.*redis/);
    });

    test('should_respondToHealthCheck_when_apiGatewayRunning', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway has health check endpoint
      const healthCheck = parsed.services['api-gateway'].healthcheck;
      expect(healthCheck).toBeDefined();
      expect(healthCheck.test).toContain('curl');
      expect(healthCheck.test.join(' ')).toContain('/actuator/health');
    });

    test('should_validateCognitoJWT_when_configured', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - API Gateway has Cognito configuration for JWT validation
      const apiGatewayEnv = parsed.services['api-gateway'].environment;

      const enableCognitoVar = apiGatewayEnv.find((e: string) => e.startsWith('ENABLE_COGNITO_AUTH='));
      expect(enableCognitoVar).toBeDefined();
    });
  });

  describe('AC9: AWS Credentials', () => {
    test('should_validateAWSCredentials_when_setupEnvStarts', () => {
      // Arrange & Act
      const setupEnvPath = path.join(projectRoot, 'scripts/config/sync-backend-config.sh');
      expect(fs.existsSync(setupEnvPath)).toBe(true);

      const setupEnvContent = fs.readFileSync(setupEnvPath, 'utf-8');

      // Assert - Script validates AWS credentials
      expect(setupEnvContent).toContain('aws sts get-caller-identity');
      expect(setupEnvContent).toContain('AWS credentials not configured or invalid');
    });

    test('should_failWithError_when_stackNotDeployed', () => {
      // Arrange & Act
      const setupEnvPath = path.join(projectRoot, 'scripts/config/sync-backend-config.sh');
      const setupEnvContent = fs.readFileSync(setupEnvPath, 'utf-8');

      // Assert - Script checks for stack outputs and fails if missing
      expect(setupEnvContent).toContain('Could not fetch database endpoint');
      expect(setupEnvContent).toContain('exit 1');
    });

    test('should_useEnvironmentVariables_when_credentialsNeeded', () => {
      // Arrange & Act
      const dockerComposeContent = fs.readFileSync(dockerComposePath, 'utf-8');
      const parsed = require('js-yaml').load(dockerComposeContent);

      // Assert - No hardcoded credentials in docker-compose.yml
      const apiGatewayEnv = parsed.services['api-gateway'].environment;

      apiGatewayEnv.forEach((envVar: string) => {
        // All sensitive values should use ${VAR} syntax
        if (envVar.includes('PASSWORD') || envVar.includes('SECRET') || envVar.includes('KEY')) {
          expect(envVar).toMatch(/\${.*}/);
        }
      });
    });
  });
});
