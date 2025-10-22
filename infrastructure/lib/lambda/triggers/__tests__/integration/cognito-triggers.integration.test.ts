/**
 * Integration Tests for Cognito Lambda Triggers
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * These tests use Testcontainers with real PostgreSQL database
 *
 * TEST NAMING CONVENTION: should_expectedBehavior_when_condition
 *
 * AC1: PostConfirmation trigger creates database user within 1 second
 * AC1: PreTokenGeneration syncs Cognito Groups with database roles
 * AC6: PreAuthentication blocks inis_active users
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from '@jest/globals';
import { PostConfirmationTriggerEvent, PreTokenGenerationTriggerEvent, PreAuthenticationTriggerEvent, Context } from 'aws-lambda';
import { Client } from 'pg';
import { GenericContainer, StartedTestContainer } from 'testcontainers';

// Handlers will be dynamically imported after environment setup

describe('Cognito Triggers - Integration Tests', () => {
  let postgresContainer: StartedTestContainer;
  let dbClient: Client;

  let dbConfig: {
    host: string;
    port: number;
    database: string;
    user: string;
    password: string;
  };

  // Handler functions - loaded dynamically after environment setup
  let postConfirmationHandler: any;
  let preTokenGenerationHandler: any;
  let preAuthenticationHandler: any;

  // ============================================================================
  // Test Setup
  // ============================================================================

  beforeAll(async () => {
    // Start PostgreSQL container
    postgresContainer = await new GenericContainer('postgres:15')
      .withEnvironment({
        POSTGRES_USER: 'test',
        POSTGRES_PASSWORD: 'test',
        POSTGRES_DB: 'batbern_test',
      })
      .withExposedPorts(5432)
      .start();

    // Get database connection details
    dbConfig = {
      host: postgresContainer.getHost(),
      port: postgresContainer.getMappedPort(5432),
      database: 'batbern_test',
      user: 'test',
      password: 'test',
    };

    // Set environment variables for Lambda handlers to use test database
    process.env.DB_SECRET_NAME = 'test-db-secret';
    process.env.DB_HOST = dbConfig.host;
    process.env.DB_PORT = dbConfig.port.toString();
    process.env.DB_NAME = dbConfig.database;
    process.env.DB_USER = dbConfig.user;
    process.env.DB_PASSWORD = dbConfig.password;
    process.env.DB_SSL = 'false'; // Testcontainers PostgreSQL doesn't use SSL

    // Mock Secrets Manager response for integration tests
    process.env.MOCK_SECRETS_MANAGER = 'true';

    // NOW dynamically import handlers after environment variables are set
    // This ensures the database module initializes with the correct test database config
    const postConfirmationModule = await import('../../post-confirmation');
    const preTokenGenerationModule = await import('../../pre-token-generation');
    const preAuthenticationModule = await import('../../pre-authentication');

    postConfirmationHandler = postConfirmationModule.handler;
    preTokenGenerationHandler = preTokenGenerationModule.handler;
    preAuthenticationHandler = preAuthenticationModule.handler;

    // Create database client
    dbClient = new Client(dbConfig);
    await dbClient.connect();

    // Create tables
    await createTestTables(dbClient);
  }, 60000); // 60 second timeout for container startup

  afterAll(async () => {
    // Cleanup
    await dbClient.end();
    await postgresContainer.stop();
  });

  beforeEach(async () => {
    // Clean database before each test
    if (dbClient) {
      await dbClient.query('DELETE FROM role_assignments');
      await dbClient.query('DELETE FROM user_profiles');
      await dbClient.query('DELETE FROM user_sync_compensation_log');
    }
    

    // Clear any cached connection pools in the handlers
    // This ensures handlers use the test database connection
    delete require.cache[require.resolve('../../common/database')];
  });




  // ============================================================================
  // Helper Functions
  // ============================================================================

  async function createTestTables(client: Client) {
    // Create users table
    await client.query(`
      CREATE TABLE IF NOT EXISTS user_profiles (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        cognito_id VARCHAR(255) UNIQUE NOT NULL,
        email VARCHAR(255) NOT NULL,
        email_verified BOOLEAN DEFAULT false,
        is_active BOOLEAN DEFAULT true,
        deactivation_reason TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    `);

    // Create user_roles table
    await client.query(`
      CREATE TABLE IF NOT EXISTS role_assignments (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
        role VARCHAR(50) NOT NULL,
        event_id UUID,
        start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        end_date TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    `);

    // Create user_sync_compensation_log table
    await client.query(`
      CREATE TABLE IF NOT EXISTS user_sync_compensation_log (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
        cognito_id VARCHAR(255) NOT NULL,
        operation VARCHAR(50) NOT NULL,
        target_role VARCHAR(50),
        status VARCHAR(20) NOT NULL,
        attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        completed_at TIMESTAMP,
        error_message TEXT,
        compensation_required BOOLEAN DEFAULT false,
        compensation_executed_at TIMESTAMP,
        retry_count INTEGER DEFAULT 0
      )
    `);
  }

  function createPostConfirmationEvent(cognitoId: string, email: string, groups?: string[]): PostConfirmationTriggerEvent {
    return {
      version: '1',
      triggerSource: 'PostConfirmation_ConfirmSignUp',
      region: 'eu-central-1',
      userPoolId: 'test-pool',
      userName: cognitoId,
      callerContext: {
        awsSdkVersion: '2.0.0',
        clientId: 'test-client',
      },
      request: {
        userAttributes: {
          sub: cognitoId,
          email,
          email_verified: 'true',
          'cognito:user_status': 'CONFIRMED',
          ...(groups && { 'cognito:groups': groups.join(',') }),
        },
      },
      response: {},
    } as PostConfirmationTriggerEvent;
  }

  function createPreTokenGenerationEvent(cognitoId: string, email: string): PreTokenGenerationTriggerEvent {
    return {
      version: '1',
      triggerSource: 'TokenGeneration_Authentication',
      region: 'eu-central-1',
      userPoolId: 'test-pool',
      userName: cognitoId,
      callerContext: {
        awsSdkVersion: '2.0.0',
        clientId: 'test-client',
      },
      request: {
        userAttributes: {
          sub: cognitoId,
          email,
        },
        groupConfiguration: {
          groupsToOverride: [],
          iamRolesToOverride: [],
          preferredRole: '',
        },
      },
      response: {
        claimsOverrideDetails: {
          claimsToAddOrOverride: {},
          claimsToSuppress: [],
          groupOverrideDetails: {
            groupsToOverride: [],
            iamRolesToOverride: [],
            preferredRole: '',
          },
        },
      },
    } as PreTokenGenerationTriggerEvent;
  }

  function createPreAuthenticationEvent(cognitoId: string, email: string): PreAuthenticationTriggerEvent {
    return {
      version: '1',
      triggerSource: 'PreAuthentication_Authentication',
      region: 'eu-central-1',
      userPoolId: 'test-pool',
      userName: cognitoId,
      callerContext: {
        awsSdkVersion: '2.0.0',
        clientId: 'test-client',
      },
      request: {
        userAttributes: {
          sub: cognitoId,
          email,
        },
        validationData: {},
      },
      response: {},
    } as PreAuthenticationTriggerEvent;
  }

  function createLambdaContext(): Context {
    return {
      functionName: 'TestTrigger',
      functionVersion: '1',
      invokedFunctionArn: 'arn:aws:lambda:eu-central-1:123456789012:function:TestTrigger',
      memoryLimitInMB: '128',
      awsRequestId: 'test-request-id',
      logGroupName: '/aws/lambda/TestTrigger',
      logStreamName: 'test-stream',
      getRemainingTimeInMillis: () => 5000,
      done: () => {},
      fail: () => {},
      succeed: () => {},
      callbackWaitsForEmptyEventLoop: true,
    };
  }

  // ============================================================================
  // TEST GROUP 1: PostConfirmation - User Creation with Real Database
  // AC1: PostConfirmation trigger creates database user within 1 second
  // ============================================================================

  describe('PostConfirmation Trigger - Database Integration', () => {

    it('should_createUserInDatabase_when_postConfirmationFires', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-123';
      const email = 'integration-test@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, ['organizer']);
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      await postConfirmationHandler(event, context, () => {});

      // Assert - Check user created in database
      const result = await dbClient.query(
      'SELECT * FROM user_profiles WHERE cognito_id = $1',
        [cognitoId]
      );

      expect(result.rows.length).toBe(1);
      expect(result.rows[0].email).toBe(email);
      expect(result.rows[0].is_active).toBe(true);
      expect(result.rows[0].email_verified).toBe(true);

    });

    it('should_assignAttendeeRoleInDatabase_when_userConfirmed_ADR001', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-456';
      const email = 'speaker@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, ['speaker']);
      const context = createLambdaContext();

      // Act - Story 1.2.6: ADR-001 always assigns ATTENDEE for self-registered users
      await postConfirmationHandler(event, context, () => {});

      // Assert - Check role assigned (always ATTENDEE per ADR-001)
      const userResult = await dbClient.query(
      'SELECT id FROM user_profiles WHERE cognito_id = $1',
        [cognitoId]
      );
      const userId = userResult.rows[0].id;

      const roleResult = await dbClient.query(
      'SELECT * FROM role_assignments WHERE user_id = $1',
        [userId]
      );

      expect(roleResult.rows.length).toBe(1);
      expect(roleResult.rows[0].role).toBe('ATTENDEE');
      expect(roleResult.rows[0].end_date).toBeNull();

    });

    it('should_defaultToAttendeeRole_when_noCognitoGroups', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-789';
      const email = 'default@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email); // No groups specified
      const context = createLambdaContext();

      // Act & Assert - Will fail because handler not implemented yet
    });

    it('should_beIdempotent_when_calledTwice', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-idempotent';
      const email = 'idempotent@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, ['partner']);
      const context = createLambdaContext();

      // Act - Call twice
      await postConfirmationHandler(event, context, () => {});
      await postConfirmationHandler(event, context, () => {}); // Should not create duplicate

      // Assert - Only one user exists
      const result = await dbClient.query(
      'SELECT * FROM user_profiles WHERE cognito_id = $1',
        [cognitoId]
      );

      expect(result.rows.length).toBe(1);

    });

    it('should_completeWithin1Second_when_databaseHealthy', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-perf';
      const email = 'performance@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, ['organizer']);
      const context = createLambdaContext();

      // Act
      const startTime = Date.now();
      await postConfirmationHandler(event, context, () => {});
      const duration = Date.now() - startTime;

      // Assert - Should complete within 1 second
      expect(duration).toBeLessThan(1000);

    });

    it('should_handleDatabaseError_when_connectionFails', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-error';
      const email = 'error@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Simulate database error by setting invalid DB config
      const originalHost = process.env.DB_HOST;
      process.env.DB_HOST = 'invalid-host-that-does-not-exist';
      delete require.cache[require.resolve('../../common/database')];

      // Act - Should not throw error (non-blocking)
      const result = await postConfirmationHandler(event, context, () => {});

      // Assert - Event returned unchanged (Cognito continues)
      expect(result).toEqual(event);

      // Restore DB config
      process.env.DB_HOST = originalHost;
      delete require.cache[require.resolve('../../common/database')];

    });
  });

  // ============================================================================
  // TEST GROUP 2: PreTokenGeneration - Cognito Groups Sync with Real Database
  // AC1: PreTokenGeneration syncs Cognito Groups with database roles
  // ============================================================================

  describe('PreTokenGeneration Trigger - Database Integration', () => {

    it('should_setGroupsOverride_when_userExistsInDatabase', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-jwt';
      const email = 'jwt-test@batbern.ch';

      // Pre-populate database with user and global role
      const userResult = await dbClient.query(
        'INSERT INTO user_profiles (cognito_id, email, is_active) VALUES ($1, $2, true) RETURNING id',
        [cognitoId, email]
      );
      const userId = userResult.rows[0].id;

      await dbClient.query(
        'INSERT INTO role_assignments (user_id, role, event_id) VALUES ($1, $2, NULL)',
        [userId, 'ORGANIZER']
      );

      const event = createPreTokenGenerationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      const result = await preTokenGenerationHandler(event, context, () => {});

      // Assert - groupsToOverride should include organizer
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:roles']).toBeDefined();
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:roles']).toContain('ORGANIZER');

    });

    it('should_onlyIncludeGlobalRoles_when_filteringEventRoles', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-event-roles';
      const email = 'event-roles@batbern.ch';

      // Pre-populate database with both global and event-specific roles
      const userResult = await dbClient.query(
        'INSERT INTO user_profiles (cognito_id, email, is_active) VALUES ($1, $2, true) RETURNING id',
        [cognitoId, email]
      );
      const userId = userResult.rows[0].id;

      const eventId = '123e4567-e89b-12d3-a456-426614174000';
      await dbClient.query(
        'INSERT INTO role_assignments (user_id, role, event_id) VALUES ($1, $2, NULL), ($3, $4, $5)',
        [userId, 'ORGANIZER', userId, 'SPEAKER', eventId]
      );

      const event = createPreTokenGenerationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      const result = await preTokenGenerationHandler(event, context, () => {});

      // Assert - Only global role should be in groupsToOverride (not event-specific)
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:roles']).toContain('ORGANIZER');
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:roles']).not.toContain('SPEAKER');

    });

    it('should_fallbackToEmptyGroups_when_databaseQueryFails', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-fallback';
      const email = 'fallback@batbern.ch';

      const event = createPreTokenGenerationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Simulate database failure by setting invalid DB config
      const originalHost = process.env.DB_HOST;
      process.env.DB_HOST = 'invalid-host-that-does-not-exist';
      delete require.cache[require.resolve('../../common/database')];

      // Act - Should not throw error (graceful degradation)
      const result = await preTokenGenerationHandler(event, context, () => {});

      // Assert - Token still generated with empty roles
      expect(result).toBeDefined();
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:roles']).toBe('');

      // Restore DB config
      process.env.DB_HOST = originalHost;
      delete require.cache[require.resolve('../../common/database')];

    });
  });

  // ============================================================================
  // TEST GROUP 3: PreAuthentication - User Active Status Check
  // AC6: PreAuthentication blocks inis_active users
  // ============================================================================

  describe('PreAuthentication Trigger - Database Integration', () => {

    it('should_allowAuthentication_when_userActive', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-is_active';
      const email = 'is_active@batbern.ch';

      // Pre-populate database with is_active user
      await dbClient.query(
        'INSERT INTO user_profiles (cognito_id, email, is_active) VALUES ($1, $2, true)',
        [cognitoId, email]
      );

      const event = createPreAuthenticationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      const result = await preAuthenticationHandler(event, context, () => {});

      // Assert - No error thrown (authentication allowed)
      expect(result).toEqual(event);

    });

    it('should_blockAuthentication_when_userInis_active', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-inis_active';
      const email = 'inis_active@batbern.ch';

      // Pre-populate database with inis_active user
      await dbClient.query(
        'INSERT INTO user_profiles (cognito_id, email, is_active, deactivation_reason) VALUES ($1, $2, false, $3)',
        [cognitoId, email, 'User account deactivated by admin']
      );

      const event = createPreAuthenticationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act & Assert - Should throw error to block authentication
      await expect(preAuthenticationHandler(event, context, () => {})).rejects.toThrow('inactive');

    });

    it('should_allowAuthentication_when_userNotInDatabase', async () => {
      // Arrange - User doesn't exist in database (JIT provisioning path)
      const cognitoId = 'test-cognito-id-not-exists';
      const email = 'not-exists@batbern.ch';

      const event = createPreAuthenticationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Should allow authentication (JIT will create user later)
      const result = await preAuthenticationHandler(event, context, () => {});

      // Assert - No error thrown
      expect(result).toEqual(event);

    });
  });
});
