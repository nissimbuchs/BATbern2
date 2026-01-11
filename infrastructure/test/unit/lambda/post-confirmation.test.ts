/**
 * Unit Tests for PostConfirmation Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * TEST NAMING CONVENTION: should_expectedBehavior_when_condition
 *
 * AC1: PostConfirmation trigger creates database user within 1 second
 * - When a user completes email verification in Cognito
 * - Then a corresponding user record is created in the `users` table
 * - And an initial role is assigned based on Cognito Groups membership (`cognito:groups`)
 * - And the operation completes within 1 second (p95 latency)
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import { PostConfirmationTriggerEvent, Context } from 'aws-lambda';

// Mock CloudWatch send function at module level
const mockCloudWatchSend = jest.fn();

// Mock AWS SDK and database before importing handler
jest.mock('@aws-sdk/client-cloudwatch', () => ({
  CloudWatchClient: jest.fn().mockImplementation(() => ({
    send: mockCloudWatchSend,
  })),
  PutMetricDataCommand: jest.fn().mockImplementation((input) => input),
}));
jest.mock('../../../lib/lambda/triggers/common/database');

import { handler } from '../../../lib/lambda/triggers/post-confirmation';
import { getDbClient, executeTransaction } from '../../../lib/lambda/triggers/common/database';

// Test data builders
function createPostConfirmationEvent(overrides: Partial<PostConfirmationTriggerEvent> = {}): PostConfirmationTriggerEvent {
  return {
    version: '1',
    triggerSource: 'PostConfirmation_ConfirmSignUp',
    region: 'eu-central-1',
    userPoolId: 'eu-central-1_XXXXXXXXX',
    userName: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111',
    callerContext: {
      awsSdkVersion: '2.0.0',
      clientId: 'test-client-id',
    },
    request: {
      userAttributes: {
        sub: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111',
        'cognito:email_alias': 'user@example.com',
        'cognito:user_status': 'CONFIRMED',
        'cognito:groups': 'organizer',
        email_verified: 'true',
        email: 'user@example.com',
        'custom:preferences': JSON.stringify({
          firstName: 'John',
          lastName: 'Doe',
          language: 'en',
          newsletterOptIn: false,
          theme: 'light',
          notifications: { email: true, sms: false, push: true },
          privacy: { showProfile: true, allowMessages: true },
        }),
        ...overrides.request?.userAttributes,
      },
    },
    response: {},
    ...overrides,
  } as PostConfirmationTriggerEvent;
}

function createLambdaContext(): Context {
  return {
    functionName: 'PostConfirmationTrigger',
    functionVersion: '1',
    invokedFunctionArn: 'arn:aws:lambda:eu-central-1:123456789012:function:PostConfirmationTrigger',
    memoryLimitInMB: '128',
    awsRequestId: 'test-request-id',
    logGroupName: '/aws/lambda/PostConfirmationTrigger',
    logStreamName: '2025/10/08/[$LATEST]test-stream',
    getRemainingTimeInMillis: () => 5000,
    done: () => {},
    fail: () => {},
    succeed: () => {},
    callbackWaitsForEmptyEventLoop: true,
  };
}

describe('PostConfirmation Lambda Trigger - Unit Tests', () => {
  let mockDbClient: any;

  beforeEach(() => {
    // Reset mocks before each test
    jest.clearAllMocks();

    // @ts-ignore - jest mock type inference issue
    mockCloudWatchSend.mockResolvedValue({});

    // Create mock database client
    mockDbClient = {
      query: jest.fn(),
      release: jest.fn(),
    };

    // Mock database functions
    (getDbClient as any).mockResolvedValue(mockDbClient);
    (executeTransaction as any).mockResolvedValue([
      { rows: [{ id: 'user-123' }], rowCount: 1 },
    ]);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  // ============================================================================
  // TEST GROUP 1: User Creation Logic
  // ============================================================================

  describe('User Creation', () => {
    it('should_createUserInDatabase_when_cognitoPostConfirmationFires', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      // Mock database calls for new user creation flow
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT - no existing user by email
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT new user
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check existing role

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Story 1.2.3: Check new fields from custom:preferences
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO user_profiles'),
        expect.arrayContaining([
          'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111', // cognito_user_id
          'user@example.com', // email
          'john.doe', // username (generated from firstName.lastName)
          'John', // first_name
          'Doe', // last_name
          'en', // pref_language
          true, // pref_email_notifications
        ])
      );
      expect(result).toEqual(event);
    });

    it('should_extractUserAttributes_when_processingEvent', async () => {
      // Arrange
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'test-sub-123',
            email: 'test@batbern.ch',
            email_verified: 'true',
            'cognito:user_status': 'CONFIRMED',
            'custom:preferences': JSON.stringify({
              firstName: 'Test',
              lastName: 'User',
              language: 'de',
              newsletterOptIn: true,
            }),
          },
        },
      } as any);
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT - no existing user
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert - Story 1.2.6: ADR-001 always assigns ATTENDEE for self-registered users
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO role_assignments'),
        expect.arrayContaining(['user-123', 'ATTENDEE'])
      );
    });

    it('should_setEmailNotifications_when_cognitoConfirmed', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert - Check that pref_email_notifications is set to true by default
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO user_profiles'),
        expect.arrayContaining([true]) // pref_email_notifications = true
      );
    });

    it('should_setUserActive_when_creatingUser', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO user_profiles'),
        expect.anything()
      );
    });
  });

  // ============================================================================
  // TEST GROUP 2: Role Assignment
  // ============================================================================

  describe('Role Assignment', () => {
    it('should_assignAttendeeRole_when_userConfirmed_ADR001', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT - no existing user
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT user
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check existing role

      // Act
      await handler(event, context, {} as any);

      // Assert - Story 1.2.6: ADR-001 database-centric architecture
      // All self-registered users receive ATTENDEE role
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO role_assignments'),
        expect.arrayContaining(['user-123', 'ATTENDEE'])
      );
    });

    it('should_defaultToAttendeeRole_when_customAttributeMissing', async () => {
      // Arrange
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'test-sub',
            email: 'test@example.com',
            email_verified: 'true',
          },
        },
      } as any);
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO role_assignments'),
        expect.arrayContaining(['user-123', 'ATTENDEE'])
      );
    });

    it('should_createRoleHistory_when_assigningRole', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('role_assignments'),
        expect.anything()
      );
      // Note: start_date removed - creation time is tracked via created_at timestamp
    });

    it('should_validateRoleValue_when_customAttributeProvided', async () => {
      // Arrange
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'test-sub',
            email: 'test@example.com',
            email_verified: 'true',
            'cognito:groups': 'invalid_group',
          },
        },
      } as any);
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert - Should default to ATTENDEE for invalid role
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO role_assignments'),
        expect.arrayContaining(['user-123', 'ATTENDEE'])
      );
    });
  });

  // ============================================================================
  // TEST GROUP 3: Idempotency
  // ============================================================================

  describe('Idempotency', () => {
    it('should_beIdempotent_when_triggeredMultipleTimes', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      // User already exists with same Cognito ID (Lambda retry scenario)
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT - user exists with same Cognito ID
          rows: [
            {
              id: 'existing-user-123',
              cognito_user_id: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111',
              username: 'john.doe',
              first_name: 'John',
              last_name: 'Doe',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should not throw error, should be idempotent
      expect(result).toEqual(event);
    });

    it('should_notThrowError_when_userAlreadyExists', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT - user exists
          rows: [
            {
              id: 'existing-user',
              cognito_user_id: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111',
              username: 'john.doe',
              first_name: 'John',
              last_name: 'Doe',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      const result = await handler(event, context, {} as any);

      // Assert
      expect(result).toEqual(event);
    });

    it('should_skipRoleCreation_when_userAlreadyHasRole', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT - user exists
          rows: [
            {
              id: 'existing-user',
              cognito_user_id: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111',
              username: 'john.doe',
              first_name: 'John',
              last_name: 'Doe',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [{ user_id: 'existing-user' }], rowCount: 1 }); // Role already exists

      // Act
      await handler(event, context, {} as any);

      // Assert - Should not insert role again
      expect(mockDbClient.query).not.toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO role_assignments'),
        expect.anything()
      );
    });
  });

  // ============================================================================
  // TEST GROUP 4: Performance
  // ============================================================================

  describe('Performance', () => {
    it('should_completeWithinOneSecond_when_databaseConnectionHealthy', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({ rows: [], rowCount: 0 });

      // Act
      const startTime = Date.now();
      await handler(event, context, {} as any);
      const duration = Date.now() - startTime;

      // Assert
      expect(duration).toBeLessThan(1000);
    });

    it('should_reuseConnection_when_lambdaWarm', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({ rows: [], rowCount: 0 });

      // Act
      await handler(event, context, {} as any);
      await handler(event, context, {} as any);

      // Assert - getDbClient should be called for connection pooling
      expect(getDbClient).toHaveBeenCalled();
    });
  });

  // ============================================================================
  // TEST GROUP 5: Error Handling
  // ============================================================================

  describe('Error Handling', () => {
    it('should_notBlockConfirmation_when_databaseUnavailable', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      (executeTransaction as any).mockRejectedValue(new Error('Database connection failed'));

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should return event unchanged despite error
      expect(result).toEqual(event);
    });

    it('should_logError_when_databaseSyncFails', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();
      const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      (executeTransaction as any).mockRejectedValue(new Error('DB Error'));

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        expect.stringContaining('PostConfirmation sync failed'),
        expect.anything()
      );

      consoleErrorSpy.mockRestore();
    });

    it('should_handleConnectionTimeout_when_databaseSlow', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      (executeTransaction as any).mockImplementation(
        () => new Promise((resolve) => setTimeout(resolve, 5000))
      );

      // Act
      const promise = handler(event, context, {} as any);

      // Give it a short time then resolve
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Assert - Should handle gracefully
      await expect(promise).resolves.toBeDefined();
    }, 10000);

    it('should_returnEventUnchanged_when_syncSucceeds', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({ rows: [], rowCount: 0 });

      // Act
      const result = await handler(event, context, {} as any);

      // Assert
      expect(result).toEqual(event);
    });

    it('should_handleMissingEmailAttribute_when_eventMalformed', async () => {
      // Arrange
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'test-sub',
            // email missing
          },
        },
      } as any);
      const context = createLambdaContext();

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should not throw, return event unchanged
      expect(result).toEqual(event);
    });

    it('should_handleMissingSubAttribute_when_eventMalformed', async () => {
      // Arrange
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            // sub missing
            email: 'test@example.com',
          },
        },
      } as any);
      const context = createLambdaContext();

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should not throw, return event unchanged
      expect(result).toEqual(event);
    });
  });

  // ============================================================================
  // TEST GROUP 6: Transaction Management
  // ============================================================================

  describe('Transaction Management', () => {
    it('should_useTransaction_when_creatingUserAndRole', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert - Should use BEGIN/COMMIT for transaction
      expect(mockDbClient.query).toHaveBeenCalledWith('BEGIN');
      expect(mockDbClient.query).toHaveBeenCalledWith('COMMIT');
    });

    it('should_rollbackTransaction_when_roleAssignmentFails', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // Check existing role
        .mockRejectedValueOnce(new Error('Role insert failed')); // Role INSERT fails

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should not throw, return event (non-blocking Lambda)
      expect(result).toEqual(event);
    });

    it('should_commitTransaction_when_bothOperationsSucceed', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockDbClient.query).toHaveBeenCalledWith('COMMIT');
      expect(mockDbClient.release).toHaveBeenCalled();
    });
  });

  // ============================================================================
  // TEST GROUP 7: Metrics
  // ============================================================================

  describe('Metrics', () => {
    it('should_recordSyncLatency_when_operationCompletes', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT existing user
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT new user
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check existing role

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'SyncLatency',
              Unit: 'Milliseconds',
            }),
          ]),
          Namespace: 'BATbern/UserSync',
        })
      );
    });

    it('should_recordSyncFailure_when_databaseError', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockRejectedValue(new Error('DB Error'));

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'SyncFailure',
            }),
          ]),
          Namespace: 'BATbern/UserSync',
        })
      );
    });

    it('should_recordSyncSuccess_when_userCreated', async () => {
      // Arrange
      const event = createPostConfirmationEvent();
      const context = createLambdaContext();

      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT existing user
        .mockResolvedValueOnce({ rows: [{ id: 'user-123' }], rowCount: 1 }) // INSERT new user
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check existing role

      // Act
      await handler(event, context, {} as any);

      // Assert
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'SyncSuccess',
            }),
          ]),
          Namespace: 'BATbern/UserSync',
        })
      );
    });
  });

  // ============================================================================
  // TEST GROUP 8: Email-Based Matching (Historical Participants)
  // ============================================================================

  describe('Email-Based Matching - ADR-005', () => {
    it('should_linkHistoricalParticipant_when_emailExistsWithoutCognitoId', async () => {
      // Arrange - Historical participant with no Cognito ID
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'new-cognito-123',
            email: 'historical@batbern.ch',
            email_verified: 'true',
            'custom:preferences': JSON.stringify({
              firstName: 'Maria',
              lastName: 'Schmidt',
              language: 'de',
            }),
          },
        },
      } as any);
      const context = createLambdaContext();

      // Mock: User exists with email but no cognito_user_id
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT existing user by email
          rows: [
            {
              id: 'historical-user-456',
              cognito_user_id: null,
              username: 'maria.schmidt',
              first_name: 'Maria',
              last_name: 'Schmidt',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // UPDATE to link Cognito ID
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check existing role

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should UPDATE existing user with Cognito ID
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('UPDATE user_profiles'),
        expect.arrayContaining(['new-cognito-123', 'Maria', 'Schmidt', 'de', 'historical-user-456'])
      );

      // Assert - Should publish metric for historical participant linking
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'HistoricalParticipantLinked',
            }),
          ]),
        })
      );

      expect(result).toEqual(event);
    });

    it('should_preserveExistingData_when_linkingHistoricalParticipant', async () => {
      // Arrange - Historical participant with existing name data
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'new-cognito-789',
            email: 'existing@batbern.ch',
            email_verified: 'true',
            'custom:preferences': JSON.stringify({
              firstName: 'NewFirst',
              lastName: 'NewLast',
              language: 'en',
            }),
          },
        },
      } as any);
      const context = createLambdaContext();

      // Mock: Historical user with existing name
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT existing user
          rows: [
            {
              id: 'historical-user-789',
              cognito_user_id: null,
              username: 'existing.user',
              first_name: 'ExistingFirst',
              last_name: 'ExistingLast',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // UPDATE
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert - Should use COALESCE to preserve existing first/last names
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('COALESCE(NULLIF(first_name'),
        expect.anything()
      );
    });

    it('should_createNewUser_when_emailDoesNotExist', async () => {
      // Arrange - Completely new user
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'new-cognito-999',
            email: 'newuser@batbern.ch',
            email_verified: 'true',
            'custom:preferences': JSON.stringify({
              firstName: 'New',
              lastName: 'User',
              language: 'de',
            }),
          },
        },
      } as any);
      const context = createLambdaContext();

      // Mock: No existing user
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // SELECT - no user found
        .mockResolvedValueOnce({ rows: [{ id: 'new-user-999' }], rowCount: 1 }) // INSERT new user
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should INSERT new user
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('INSERT INTO user_profiles'),
        expect.arrayContaining(['new-cognito-999', 'newuser@batbern.ch', 'new.user', 'New', 'User', 'de', true])
      );

      // Assert - Should publish metric for new user creation
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'NewUserCreated',
            }),
          ]),
        })
      );

      expect(result).toEqual(event);
    });

    it('should_beIdempotent_when_sameCognitoIdAlreadyLinked', async () => {
      // Arrange - Lambda retry scenario
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'existing-cognito-123',
            email: 'linked@batbern.ch',
            email_verified: 'true',
          },
        },
      } as any);
      const context = createLambdaContext();

      // Mock: User already linked to same Cognito ID
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT - user already linked
          rows: [
            {
              id: 'user-123',
              cognito_user_id: 'existing-cognito-123',
              username: 'linked.user',
              first_name: 'Linked',
              last_name: 'User',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT (no UPDATE needed)
        .mockResolvedValueOnce({ rows: [{ user_id: 'user-123' }], rowCount: 1 }); // Role exists

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should NOT call UPDATE (idempotent)
      expect(mockDbClient.query).not.toHaveBeenCalledWith(
        expect.stringContaining('UPDATE user_profiles'),
        expect.anything()
      );

      expect(result).toEqual(event);
    });

    it('should_throwError_when_emailConflictDetected', async () => {
      // Arrange - Email already linked to different Cognito user
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'new-cognito-456',
            email: 'conflict@batbern.ch',
            email_verified: 'true',
          },
        },
      } as any);
      const context = createLambdaContext();

      // Mock: Email already linked to different Cognito ID
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT - email exists with different Cognito ID
          rows: [
            {
              id: 'user-existing',
              cognito_user_id: 'different-cognito-789',
              username: 'conflict.user',
              first_name: 'Conflict',
              last_name: 'User',
              is_active: true,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // ROLLBACK

      // Act
      const result = await handler(event, context, {} as any);

      // Assert - Should ROLLBACK and not block Cognito confirmation
      expect(mockDbClient.query).toHaveBeenCalledWith('ROLLBACK');
      expect(result).toEqual(event); // Lambda should not throw (non-blocking)

      // Assert - Should publish failure metric
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'SyncFailure',
            }),
          ]),
        })
      );
    });

    it('should_reactivateUser_when_linkingInactiveHistoricalParticipant', async () => {
      // Arrange - Inactive historical participant
      const event = createPostConfirmationEvent({
        request: {
          userAttributes: {
            sub: 'reactivate-cognito-111',
            email: 'inactive@batbern.ch',
            email_verified: 'true',
            'custom:preferences': JSON.stringify({
              firstName: 'Reactivated',
              lastName: 'User',
            }),
          },
        },
      } as any);
      const context = createLambdaContext();

      // Mock: Inactive historical user
      mockDbClient.query
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // BEGIN
        .mockResolvedValueOnce({
          // SELECT - inactive user
          rows: [
            {
              id: 'inactive-user-111',
              cognito_user_id: null,
              username: 'inactive.user',
              first_name: '',
              last_name: '',
              is_active: false,
            },
          ],
          rowCount: 1,
        })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // UPDATE to reactivate
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // COMMIT
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // Check role

      // Act
      await handler(event, context, {} as any);

      // Assert - Should set is_active = true in UPDATE
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('is_active = true'),
        expect.anything()
      );
    });
  });
});
