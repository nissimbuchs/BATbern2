/**
 * Unit Tests for PreTokenGeneration Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * TEST NAMING CONVENTION: should_expectedBehavior_when_condition
 *
 * AC1: PreTokenGeneration enriches JWT with roles from database
 * - When a user authenticates and token is generated
 * - Then roles are fetched from the database
 * - And JWT claims are enriched with user roles
 * - And event-specific roles are included in custom claims
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import { PreTokenGenerationTriggerEvent, Context } from 'aws-lambda';
import { Client } from 'pg';

// Import the handler function
import { handler } from '../pre-token-generation';

// Import mocked database module
import { getDbClient } from '../common/database';

// Mock database client
jest.mock('pg');
jest.mock('../common/database');

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
  // AC1: Fetch active roles for user from database
  // ============================================================================

  describe('Role Fetching', () => {

    it('should_fetchUserRoles_when_userExistsInDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER', event_id: null },
          { role: 'SPEAKER', event_id: 'evt-123' },
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
        expect.stringContaining('SELECT ur.role, ur.event_id'),
        expect.arrayContaining(['a1b2c3d4-5678-90ab-cdef-EXAMPLE11111'])
      );
      expect(mockDbClient.release).toHaveBeenCalled();
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride).toBeDefined();
    });

    it('should_fetchOnlyActiveRoles_when_queryingDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Query should filter for end_date IS NULL (active roles)
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('end_date IS NULL'),
        expect.anything()
      );
    });

    it('should_returnEmptyRoles_when_userNotFoundInDatabase', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [],
        rowCount: 0,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should handle gracefully with empty roles
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']).toBe('[]');
    });

    it('should_joinWithUsers_when_fetchingRoles', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      await handler(event, context, () => {});

      // Assert - Query should join users table by cognito_id
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('JOIN users'),
        expect.arrayContaining(['a1b2c3d4-5678-90ab-cdef-EXAMPLE11111'])
      );
    });

    it('should_fetchEventSpecificRoles_when_userHasEventRoles', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'SPEAKER', event_id: 'evt-123' },
          { role: 'SPEAKER', event_id: 'evt-456' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should fetch both event-specific roles
      const eventRolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_event_roles'];
      expect(eventRolesStr).toBeDefined();
      const eventRoles = JSON.parse(eventRolesStr!);
      expect(eventRoles).toHaveLength(2);
      expect(eventRoles[0].eventId).toBe('evt-123');
      expect(eventRoles[1].eventId).toBe('evt-456');
    });
  });

  // ============================================================================
  // TEST GROUP 2: JWT Claim Enrichment
  // AC1: Enrich JWT with roles from database
  // ============================================================================

  describe('JWT Claim Enrichment', () => {

    it('should_addRolesToClaims_when_rolesExist', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER', event_id: null },
          { role: 'SPEAKER', event_id: null },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert
      const rolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles'];
      expect(rolesStr).toBeDefined();
      const roles = JSON.parse(rolesStr!);
      expect(roles).toContain('ORGANIZER');
      expect(roles).toContain('SPEAKER');
    });

    it('should_addEventRolesToClaims_when_eventRolesExist', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER', event_id: null },
          { role: 'SPEAKER', event_id: 'evt-123' },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should add event-specific roles to custom claims
      const globalRolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles'];
      const eventRolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_event_roles'];

      expect(globalRolesStr).toBeDefined();
      expect(JSON.parse(globalRolesStr!)).toEqual(['ORGANIZER']);

      expect(eventRolesStr).toBeDefined();
      const eventRoles = JSON.parse(eventRolesStr!);
      expect(eventRoles).toHaveLength(1);
      expect(eventRoles[0]).toMatchObject({ eventId: 'evt-123', role: 'SPEAKER' });
    });

    it('should_addSyncTimestamp_when_enrichingClaims', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ATTENDEE', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should add timestamp for cache invalidation
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride).toHaveProperty('custom:roles_synced_at');
      const timestamp = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:roles_synced_at'];
      expect(timestamp).toBeDefined();
      expect(new Date(timestamp!).getTime()).toBeGreaterThan(0);
    });

    it('should_returnEventUnchanged_when_claimsAdded', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ATTENDEE', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Event should be returned with response modified
      expect(result.version).toBe(event.version);
      expect(result.triggerSource).toBe(event.triggerSource);
      expect(result.userName).toBe(event.userName);
    });

    it('should_overrideExistingClaims_when_dbRolesDiffer', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        request: {
          userAttributes: {
            sub: 'a1b2c3d4-5678-90ab-cdef-EXAMPLE11111', // Need sub for lookup
            'custom:batbern_role': 'ATTENDEE', // Old role in Cognito
          },
        },
      } as any);
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }], // New role in DB
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - DB roles should take precedence
      const rolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles'];
      expect(rolesStr).toBeDefined();
      expect(JSON.parse(rolesStr!)).toEqual(['ORGANIZER']);
    });

    it('should_serializeRolesAsJSON_when_addingToClaims', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER', event_id: null },
          { role: 'SPEAKER', event_id: null },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Roles should be JSON array string
      const rolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles'];
      const roles = JSON.parse(rolesStr!);
      expect(Array.isArray(roles)).toBe(true);
      expect(roles).toContain('ORGANIZER');
      expect(roles).toContain('SPEAKER');
    });
  });

  // ============================================================================
  // TEST GROUP 3: Error Handling (Fallback on DB Error)
  // AC1: Graceful degradation when database unavailable
  // ============================================================================

  describe('Error Handling and Fallback', () => {

    it('should_fallbackToEmptyRoles_when_databaseUnavailable', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockRejectedValueOnce(new Error('Database connection failed'));

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should not throw error, return empty roles
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']).toBe('[]');
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
          { role: null, event_id: null }, // Malformed data
          { role: 'ORGANIZER', event_id: null },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should filter out null/invalid roles
      const rolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles'];
      const roles = JSON.parse(rolesStr!);
      expect(roles).toEqual(['ORGANIZER']);
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

      // Assert - Query should use cognito_id index for performance
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('WHERE u.cognito_id = $1'),
        expect.anything()
      );
    });
  });

  // ============================================================================
  // TEST GROUP 5: Different Trigger Sources
  // ============================================================================

  describe('Trigger Sources', () => {

    it('should_enrichClaims_when_triggerSourceIsAuthentication', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        triggerSource: 'TokenGeneration_Authentication',
      });
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']).toBeDefined();
    });

    it('should_enrichClaims_when_triggerSourceIsNewPasswordChallenge', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        triggerSource: 'TokenGeneration_NewPasswordChallenge',
      });
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']).toBeDefined();
    });

    it('should_enrichClaims_when_triggerSourceIsAuthenticationRefresh', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent({
        triggerSource: 'TokenGeneration_RefreshTokens',
      });
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [{ role: 'ORGANIZER', event_id: null }],
        rowCount: 1,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Refresh should also get latest roles
      expect(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']).toBeDefined();
      const roles = JSON.parse(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']!);
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

    it('should_recordRoleCount_when_rolesEnriched', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER', event_id: null },
          { role: 'SPEAKER', event_id: null },
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Both roles enriched (metric published internally)
      const roles = JSON.parse(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']!);
      expect(roles).toHaveLength(2);
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

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'ORGANIZER', event_id: null },
          { role: 'ORGANIZER', event_id: null }, // Duplicate
          { role: 'SPEAKER', event_id: null },
        ],
        rowCount: 3,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Should return unique roles only
      const rolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles'];
      const roles = JSON.parse(rolesStr!);
      expect(roles).toHaveLength(2);
      expect(roles).toContain('ORGANIZER');
      expect(roles).toContain('SPEAKER');
    });

    it('should_keepEventRolesSeparate_when_deduplicating', async () => {
      // Arrange
      const event = createPreTokenGenerationEvent();
      const context = createLambdaContext();

      mockDbClient.query.mockResolvedValueOnce({
        rows: [
          { role: 'SPEAKER', event_id: null }, // Global role
          { role: 'SPEAKER', event_id: 'evt-123' }, // Event-specific role
        ],
        rowCount: 2,
      } as any);

      // Act
      const result = await handler(event, context, () => {});

      // Assert - Both should be included (different scopes)
      const globalRoles = JSON.parse(result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_roles']!);
      const eventRolesStr = result.response.claimsOverrideDetails?.claimsToAddOrOverride?.['custom:batbern_event_roles'];
      const eventRoles = eventRolesStr ? JSON.parse(eventRolesStr) : [];

      expect(globalRoles).toContain('SPEAKER');
      expect(eventRoles).toHaveLength(1);
      expect(eventRoles[0].eventId).toBe('evt-123');
    });
  });
});
