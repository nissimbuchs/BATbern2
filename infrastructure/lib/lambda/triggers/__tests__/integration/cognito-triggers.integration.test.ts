/**
 * Integration Tests for Cognito Lambda Triggers
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * These tests use Testcontainers with real PostgreSQL database
 *
 * TEST NAMING CONVENTION: should_expectedBehavior_when_condition
 *
 * AC1: PostConfirmation trigger creates database user within 1 second
 * AC1: PreTokenGeneration enriches JWT with roles from database
 * AC6: PreAuthentication blocks inactive users
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from '@jest/globals';
import { PostConfirmationTriggerEvent, PreTokenGenerationTriggerEvent, PreAuthenticationTriggerEvent, Context } from 'aws-lambda';
import { Client } from 'pg';
import { GenericContainer, StartedTestContainer } from 'testcontainers';

// Import handlers (to be implemented)
// import { handler as postConfirmationHandler } from '../../post-confirmation';
// import { handler as preTokenGenerationHandler } from '../../pre-token-generation';
// import { handler as preAuthenticationHandler } from '../../pre-authentication';

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
    await dbClient.query('DELETE FROM user_roles');
    await dbClient.query('DELETE FROM users');
    await dbClient.query('DELETE FROM user_sync_compensation_log');
  });

  // ============================================================================
  // Helper Functions
  // ============================================================================

  async function createTestTables(client: Client) {
    // Create users table
    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        cognito_id VARCHAR(255) UNIQUE NOT NULL,
        email VARCHAR(255) NOT NULL,
        email_verified BOOLEAN DEFAULT false,
        active BOOLEAN DEFAULT true,
        deactivation_reason TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    `);

    // Create user_roles table
    await client.query(`
      CREATE TABLE IF NOT EXISTS user_roles (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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
        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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

  function createPostConfirmationEvent(cognitoId: string, email: string, role?: string): PostConfirmationTriggerEvent {
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
          ...(role && { 'custom:batbern_role': role }),
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
      const event = createPostConfirmationEvent(cognitoId, email, 'ORGANIZER');
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      // await postConfirmationHandler(event, context);

      // Assert - Check user created in database
      // const result = await dbClient.query(
      //   'SELECT * FROM users WHERE cognito_id = $1',
      //   [cognitoId]
      // );

      // expect(result.rows.length).toBe(1);
      // expect(result.rows[0].email).toBe(email);
      // expect(result.rows[0].active).toBe(true);
      // expect(result.rows[0].email_verified).toBe(true);

      throw new Error('PostConfirmation handler not implemented - integration test fails with real database');
    });

    it('should_assignRoleInDatabase_when_customAttributeProvided', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-456';
      const email = 'speaker@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, 'SPEAKER');
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      // await postConfirmationHandler(event, context);

      // Assert - Check role assigned
      // const userResult = await dbClient.query(
      //   'SELECT id FROM users WHERE cognito_id = $1',
      //   [cognitoId]
      // );
      // const userId = userResult.rows[0].id;

      // const roleResult = await dbClient.query(
      //   'SELECT * FROM user_roles WHERE user_id = $1',
      //   [userId]
      // );

      // expect(roleResult.rows.length).toBe(1);
      // expect(roleResult.rows[0].role).toBe('SPEAKER');
      // expect(roleResult.rows[0].end_date).toBeNull();

      throw new Error('PostConfirmation handler not implemented - role assignment integration test fails');
    });

    it('should_defaultToAttendeeRole_when_noCustomAttribute', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-789';
      const email = 'default@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email); // No role specified
      const context = createLambdaContext();

      // Act & Assert - Will fail because handler not implemented yet
      throw new Error('PostConfirmation handler not implemented - default role integration test fails');
    });

    it('should_beIdempotent_when_calledTwice', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-idempotent';
      const email = 'idempotent@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, 'PARTNER');
      const context = createLambdaContext();

      // Act - Call twice
      // await postConfirmationHandler(event, context);
      // await postConfirmationHandler(event, context); // Should not create duplicate

      // Assert - Only one user exists
      // const result = await dbClient.query(
      //   'SELECT * FROM users WHERE cognito_id = $1',
      //   [cognitoId]
      // );

      // expect(result.rows.length).toBe(1);

      throw new Error('PostConfirmation handler not implemented - idempotency integration test fails');
    });

    it('should_completeWithin1Second_when_databaseHealthy', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-perf';
      const email = 'performance@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email, 'ORGANIZER');
      const context = createLambdaContext();

      // Act
      const startTime = Date.now();
      // await postConfirmationHandler(event, context);
      const duration = Date.now() - startTime;

      // Assert - Should complete within 1 second
      // expect(duration).toBeLessThan(1000);

      throw new Error('PostConfirmation handler not implemented - performance test fails');
    });

    it('should_handleDatabaseError_when_connectionFails', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-error';
      const email = 'error@batbern.ch';
      const event = createPostConfirmationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Simulate database error by closing connection temporarily
      await dbClient.end();

      // Act - Should not throw error (non-blocking)
      // const result = await postConfirmationHandler(event, context);

      // Assert - Event returned unchanged (Cognito continues)
      // expect(result).toEqual(event);

      // Reconnect for cleanup
      dbClient = new Client(dbConfig);
      await dbClient.connect();

      throw new Error('PostConfirmation handler not implemented - error handling integration test fails');
    });
  });

  // ============================================================================
  // TEST GROUP 2: PreTokenGeneration - JWT Enrichment with Real Database
  // AC1: PreTokenGeneration enriches JWT with roles from database
  // ============================================================================

  describe('PreTokenGeneration Trigger - Database Integration', () => {

    it('should_enrichJWTWithRoles_when_userExistsInDatabase', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-jwt';
      const email = 'jwt-test@batbern.ch';

      // Pre-populate database with user and role
      const userResult = await dbClient.query(
        'INSERT INTO users (cognito_id, email, active) VALUES ($1, $2, true) RETURNING id',
        [cognitoId, email]
      );
      const userId = userResult.rows[0].id;

      await dbClient.query(
        'INSERT INTO user_roles (user_id, role) VALUES ($1, $2)',
        [userId, 'ORGANIZER']
      );

      const event = createPreTokenGenerationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      // const result = await preTokenGenerationHandler(event, context);

      // Assert - JWT claims should include roles
      // expect(result.response.claimsOverrideDetails.claimsToAddOrOverride).toBeDefined();
      // expect(result.response.claimsOverrideDetails.claimsToAddOrOverride['custom:batbern_roles']).toContain('ORGANIZER');

      throw new Error('PreTokenGeneration handler not implemented - JWT enrichment integration test fails');
    });

    it('should_includeEventRoles_when_userHasEventSpecificRoles', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-event-roles';
      const email = 'event-roles@batbern.ch';

      // Pre-populate database with event-specific role
      const userResult = await dbClient.query(
        'INSERT INTO users (cognito_id, email, active) VALUES ($1, $2, true) RETURNING id',
        [cognitoId, email]
      );
      const userId = userResult.rows[0].id;

      const eventId = '123e4567-e89b-12d3-a456-426614174000';
      await dbClient.query(
        'INSERT INTO user_roles (user_id, role, event_id) VALUES ($1, $2, $3)',
        [userId, 'SPEAKER', eventId]
      );

      const event = createPreTokenGenerationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act & Assert - Will fail because handler not implemented yet
      throw new Error('PreTokenGeneration handler not implemented - event-specific roles integration test fails');
    });

    it('should_fallbackToEmptyRoles_when_databaseQueryFails', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-fallback';
      const email = 'fallback@batbern.ch';

      const event = createPreTokenGenerationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Close database to simulate failure
      await dbClient.end();

      // Act - Should not throw error (graceful degradation)
      // const result = await preTokenGenerationHandler(event, context);

      // Assert - Token still generated
      // expect(result).toBeDefined();

      // Reconnect
      dbClient = new Client(dbConfig);
      await dbClient.connect();

      throw new Error('PreTokenGeneration handler not implemented - fallback integration test fails');
    });
  });

  // ============================================================================
  // TEST GROUP 3: PreAuthentication - User Active Status Check
  // AC6: PreAuthentication blocks inactive users
  // ============================================================================

  describe('PreAuthentication Trigger - Database Integration', () => {

    it('should_allowAuthentication_when_userActive', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-active';
      const email = 'active@batbern.ch';

      // Pre-populate database with active user
      await dbClient.query(
        'INSERT INTO users (cognito_id, email, active) VALUES ($1, $2, true)',
        [cognitoId, email]
      );

      const event = createPreAuthenticationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Will fail because handler not implemented yet
      // const result = await preAuthenticationHandler(event, context);

      // Assert - No error thrown (authentication allowed)
      // expect(result).toEqual(event);

      throw new Error('PreAuthentication handler not implemented - active user integration test fails');
    });

    it('should_blockAuthentication_when_userInactive', async () => {
      // Arrange
      const cognitoId = 'test-cognito-id-inactive';
      const email = 'inactive@batbern.ch';

      // Pre-populate database with inactive user
      await dbClient.query(
        'INSERT INTO users (cognito_id, email, active, deactivation_reason) VALUES ($1, $2, false, $3)',
        [cognitoId, email, 'User account deactivated by admin']
      );

      const event = createPreAuthenticationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act & Assert - Should throw error to block authentication
      // await expect(preAuthenticationHandler(event, context)).rejects.toThrow('User account is inactive');

      throw new Error('PreAuthentication handler not implemented - inactive user blocking integration test fails');
    });

    it('should_allowAuthentication_when_userNotInDatabase', async () => {
      // Arrange - User doesn't exist in database (JIT provisioning path)
      const cognitoId = 'test-cognito-id-not-exists';
      const email = 'not-exists@batbern.ch';

      const event = createPreAuthenticationEvent(cognitoId, email);
      const context = createLambdaContext();

      // Act - Should allow authentication (JIT will create user later)
      // const result = await preAuthenticationHandler(event, context);

      // Assert - No error thrown
      // expect(result).toEqual(event);

      throw new Error('PreAuthentication handler not implemented - JIT provisioning path integration test fails');
    });
  });
});
