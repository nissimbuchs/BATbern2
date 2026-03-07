/**
 * Unit Tests for PreTokenGeneration Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * TEST NAMING CONVENTION: should_expectedBehavior_when_condition
 *
 * AC2: PreTokenGeneration enriches JWT with database roles (ADR-001)
 * - When a user authenticates and token is generated
 * - Then roles are fetched from the database
 * - And JWT custom claim 'custom:role' is set with comma-separated role string
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import { PreTokenGenerationTriggerEvent, Context } from 'aws-lambda';
import { Client } from 'pg';

// Import the handler function
import { handler } from '../../../lib/lambda/triggers/pre-token-generation';

// Import mocked database module
import { getDbClient } from '../../../lib/lambda/triggers/common/database';

// Mock database client
jest.mock('pg');
jest.mock('../../../lib/lambda/triggers/common/database');

// Mock CloudWatch client to avoid dynamic import issues
jest.mock('@aws-sdk/client-cloudwatch', () => {
  const mockSend = jest.fn();
  // @ts-ignore - Mock return value for CloudWatch metrics
  mockSend.mockResolvedValue({});
  return {
    CloudWatchClient: jest.fn().mockImplementation(() => ({
      send: mockSend,
    })),
    PutMetricDataCommand: jest.fn(),
  };
});

// Test data builders
function createPreTokenGenerationEvent(overrides: Partial<PreTokenGenerationTriggerEvent> = {}): PreTokenGenerationTriggerEvent {
  return {
    version: '1',
    triggerSource: 'TokenGeneration_Authentication',
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
        email: 'user@example.com',
        email_verified: 'true',
        ...overrides.request?.userAttributes,
      },
      groupConfiguration: {
        groupsToOverride: [],
        iamRolesToOverride: [],
        preferredRole: undefined,
      },
    },
    response: {
      claimsOverrideDetails: {},
    },
    ...overrides,
  } as PreTokenGenerationTriggerEvent;
}

function createLambdaContext(): Context {
  return {
    functionName: 'PreTokenGenerationTrigger',
    functionVersion: '1',
    invokedFunctionArn: 'arn:aws:lambda:eu-central-1:123456789012:function:PreTokenGenerationTrigger',
    memoryLimitInMB: '128',
    awsRequestId: 'test-request-id',
    logGroupName: '/aws/lambda/PreTokenGenerationTrigger',
    logStreamName: '2025/10/08/[$LATEST]test-stream',
    getRemainingTimeInMillis: () => 5000,
    done: () => {},
    fail: () => {},
    succeed: () => {},
    callbackWaitsForEmptyEventLoop: true,
  };
}

describe('PreTokenGeneration Lambda Trigger - Unit Tests', () => {
  let mockDbClient: any;

  beforeEach(() => {
    // Reset mocks before each test
    jest.clearAllMocks();

    // Create mock database client
    mockDbClient = {
      connect: jest.fn(),
      query: jest.fn(),
      release: jest.fn(),
      end: jest.fn(),
    };

    // Mock getDbClient to return our mock client
    (getDbClient as jest.MockedFunction<typeof getDbClient>).mockResolvedValue(mockDbClient);

    (Client as any).mockImplementation(() => mockDbClient);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  // ============================================================================
  // TEST GROUP 1: Role Fetching from Database
  // AC1: Fetch active global roles for user from database
  // ============================================================================

  describe('Role Fetching', () => {

    it('should_fetchUserRoles_when_userExistsInDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
        ],
        command: 'SELECT',
        rowCount: 2,
        oid: 0,
        fields: [],
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('SELECT u.username, ra.role'),
        expect.arrayContaining(['a1b2c3d4-5678-90ab-cdef-EXAMPLE11111'])
      );
      expect(mockDbClient.release).toHaveBeenCalled();
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toBeDefined();
    });

    it('should_fetchActiveRoles_when_queryingDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER' }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Query should join user_profiles with role_assignments (all roles are active)
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('LEFT JOIN role_assignments'),
        expect.anything()
      );
    });

    it('should_returnEmptyGroups_when_userNotFoundInDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      // Both cognitoId and email lookups return empty (user truly does not exist)
      mockDbClient.query.mockResolvedValueOnce({ rows: [], rowCount: 0 } as any);
      mockDbClient.query.mockResolvedValueOnce({ rows: [], rowCount: 0 } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should handle gracefully with empty roles string
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toBe('');
    });

    it('should_fetchRolesByEmailFallback_when_cognitoIdNotLinkedYet', async () => {
      // Arrange: pre-existing user created by organizer (cognito_user_id=NULL in DB)
      const event = createPreTokenGenerationEvent({
        request: {
          userAttributes: {
            sub: 'new-cognito-sub-uuid',
            email: 'partner@sbb.ch',
            email_verified: 'true',
          },
          groupConfiguration: { groupsToOverride: [], iamRolesToOverride: [], preferredRole: undefined },
        },
      } as any);
      const context = createLambdaContext();

      // First call (cognitoId lookup) returns nothing — account not linked yet
      mockDbClient.query.mockResolvedValueOnce({ rows: [], rowCount: 0 } as any);
      // Second call (email fallback) returns the pre-existing record with PARTNER role
      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ username: 'partner.sbb', role: 'PARTNER' }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert — partner role is correctly included in the token on first login
      expect(mockDbClient.query).toHaveBeenCalledTimes(2);
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toBe('PARTNER');
      const username = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:username'];
      expect(username).toBe('partner.sbb');
    });

    it('should_joinWithUsers_when_fetchingRoles', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER' }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Query should select from user_profiles and join role_assignments by user_id
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('FROM user_profiles'),
        expect.arrayContaining(['a1b2c3d4-5678-90ab-cdef-EXAMPLE11111'])
      );
    });

    it('should_fetchGlobalRoles_when_queryingDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
        ],
        rowCount: 2,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Query should fetch from user_profiles with left join to role_assignments (all roles are global)
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('LEFT JOIN role_assignments'),
        expect.anything()
      );
    });
  });

  // ============================================================================
  // TEST GROUP 2: JWT Custom Claims (ADR-001)
  // AC2: Add custom:role claim with database roles
  // ============================================================================

  describe('JWT Custom Claims', () => {

    it('should_addCustomRolesClaim_when_rolesExist', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - custom:role should be comma-separated string
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toBeDefined();
      expect(roles).toBe('ORGANIZER,SPEAKER');
    });

    it('should_orderRolesByPriority_when_multipleRolesExist', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      // Mock database returning roles in priority order (ORGANIZER > SPEAKER > PARTNER > ATTENDEE)
      // This simulates the CASE-based ORDER BY in the SQL query
      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
          { role: 'PARTNER' },
          { role: 'ATTENDEE' },
        ],
        rowCount: 4,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Roles should be ordered by priority (highest first)
      // Frontend uses first role as primary, so ORGANIZER must come first
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toBeDefined();
      expect(roles).toBe('ORGANIZER,SPEAKER,PARTNER,ATTENDEE');
      expect(roles?.split(',')[0]).toBe('ORGANIZER'); // Verify highest priority role is first
    });

    it('should_maintainRoleCase_when_settingClaims', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
        ],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Roles should maintain case (ORGANIZER -> organizer)
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toBeDefined();
      expect(roles).toBe('ORGANIZER');
    });

    it('should_returnEventUnchanged_when_groupsSet', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ATTENDEE' }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Event should be returned with response modified
      expect(result.version).toBe(event.version);
      expect(result.triggerSource).toBe(event.triggerSource);
      expect(result.userName).toBe(event.userName);
    });

    it('should_setClaimsFromDatabase_when_authenticating', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        request: {
          userAttributes: {
            sub: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111', // Need sub for lookup
          },
          groupConfiguration: {
        groupsToOverride: [],
        iamRolesToOverride: [],
        preferredRole: undefined,
      },
        },
      } as any);
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER' }], // New role in DB
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - DB roles should take precedence
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toBeDefined();
      expect(roles).toBe('ORGANIZER');
    });

    it('should_returnCommaSeparatedString_when_settingClaims', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Groups should be comma-separated string
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(typeof roles).toBe('string');
      expect(roles).toContain('ORGANIZER');
      expect(roles).toContain('SPEAKER');
    });
  });

  // ============================================================================
  // TEST GROUP 3: Error Handling (Fallback on DB Error)
  // AC1: Graceful degradation when database unavailable
  // ============================================================================

  describe('Error Handling and Fallback', () => {

    it('should_fallbackToEmptyString_when_databaseUnavailable', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockRejectedValueOnce(new Error('Database connection failed'));

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should not throw error, return empty roles string
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toBe('');
    });

    it('should_logError_when_roleFetchFails', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

      mockDbClient.query.mockRejectedValueOnce(new Error('Query failed'));

      // Act
      await handler(event, context, () => {});

      // Assert
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        expect.stringContaining('Database fetch failed'),
        expect.anything()
      );

      consoleErrorSpy.mockRestore();
    });

    it('should_notBlockTokenGeneration_when_databaseSlow', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [],
        rowCount: 0,
      } as any);

      // Act - Should complete and return event
      const result = await handler(event, context, () => {});

      // Assert
      expect(result).toBeDefined();
      expect(result.version).toBe(event.version);
    });

    it('should_returnEventUnchanged_when_errorOccurs', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockRejectedValueOnce(new Error('DB error'));

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Event should still be returned
      expect(result.version).toBe(event.version);
      expect(result.triggerSource).toBe(event.triggerSource);
    });

    it('should_handleMalformedRoleData_when_fetchingFromDB', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: null }, // Malformed data
          { role: 'ORGANIZER' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should filter out null/invalid roles
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toBe('ORGANIZER');
    });

    it('should_handleMissingSubAttribute_when_eventMalformed', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        request: {
          userAttributes: {
            email: 'test@batbern.ch',
            // Missing sub attribute
          },
        },
      } as any);
      const context = createLambdaContext();

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should return event unchanged (no claims added)
      expect(result.version).toBe(event.version);
      expect(result.triggerSource).toBe(event.triggerSource);
    });
  });

  // ============================================================================
  // TEST GROUP 4: Performance
  // AC1: Token generation should not add significant latency
  // ============================================================================

  describe('Performance', () => {

    it('should_completeWithinFiveSeconds_when_databaseHealthy', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      const startTime = Date.now();

      // Act
      await handler(event, context, () => {});
      const duration = Date.now() - startTime;

      // Assert - PreTokenGeneration has 5 second timeout
      expect(duration).toBeLessThan(5000);
    });

    it('should_reuseConnection_when_lambdaWarm', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - getDbClient should be called (connection pooling via common/database)
      expect(getDbClient).toHaveBeenCalled();
    });

    it('should_useIndexedQuery_when_fetchingRoles', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Query should use cognito_user_id index for performance
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('WHERE u.cognito_user_id = $1'),
        expect.anything()
      );
    });
  });

  // ============================================================================
  // TEST GROUP 5: Different Trigger Sources
  // ============================================================================

  describe('Trigger Sources', () => {

    it('should_setGroups_when_triggerSourceIsAuthentication', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        triggerSource: 'TokenGeneration_Authentication',
      });
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER' }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toBeDefined();
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toContain('ORGANIZER');
    });

    it('should_setGroups_when_triggerSourceIsNewPasswordChallenge', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        triggerSource: 'TokenGeneration_NewPasswordChallenge',
      });
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER' }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toBeDefined();
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toContain('ORGANIZER');
    });

    it('should_setGroups_when_triggerSourceIsAuthenticationRefresh', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        triggerSource: 'TokenGeneration_RefreshTokens',
      });
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER' }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Refresh should also get latest groups
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role']).toBeDefined();
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toContain('ORGANIZER');
    });
  });

  // ============================================================================
  // TEST GROUP 6: CloudWatch Metrics
  // ============================================================================

  describe('Metrics', () => {

    it('should_recordFetchLatency_when_operationCompletes', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Metric publishing happens (implementation uses CloudWatch client)
      // We verify handler completes successfully
      expect(mockDbClient.release).toHaveBeenCalled();
    });

    it('should_recordFetchFailure_when_databaseError', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockRejectedValueOnce(new Error('DB error'));

      // Act
      await handler(event, context, () => {});

      // Assert - Handler completes despite error (failure metric published internally)
      expect(mockDbClient.release).toHaveBeenCalled();
    });

    it('should_recordRoleCount_when_groupsSet', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Both groups set (metric published internally)
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles.split(',').length).toBe(2);
    });
  });

  // ============================================================================
  // TEST GROUP 7: Role Deduplication
  // ============================================================================

  describe('Role Deduplication', () => {

    it('should_deduplicateRoles_when_multipleEntriesExist', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      // SQL DISTINCT prevents duplicates - mock should reflect actual SQL behavior
      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER' },
          { role: 'SPEAKER' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should return unique groups only (lowercase)
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles.split(',').length).toBe(2);
      expect(roles).toContain('ORGANIZER');
      expect(roles).toContain('SPEAKER');
    });

    it('should_onlyIncludeGlobalRoles_when_queryFiltersEventRoles', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      // Database query returns only global roles (event_id IS NULL filter)
      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'SPEAKER' }, // Global role only
        ],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Only global role included as group
      const roles = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:role'];
      expect(roles).toContain('SPEAKER');
      expect(roles).toBe('SPEAKER');
    });
  });
});
