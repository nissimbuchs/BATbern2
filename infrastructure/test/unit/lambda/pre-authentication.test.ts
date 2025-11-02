import { PreAuthenticationTriggerEvent } from 'aws-lambda';
import * as database from '../../../lib/lambda/triggers/common/database';

// Mock CloudWatch send function at module level
const mockCloudWatchSend = jest.fn();

// Mock AWS SDK v3 before importing handler
jest.mock('@aws-sdk/client-cloudwatch', () => {
  const actualModule = jest.requireActual('@aws-sdk/client-cloudwatch');
  return {
    ...actualModule,
    CloudWatchClient: jest.fn().mockImplementation(() => ({
      send: (...args: any[]) => mockCloudWatchSend(...args),
    })),
    PutMetricDataCommand: jest.fn().mockImplementation((input) => input),
  };
});

// Mock database module
jest.mock('../../../lib/lambda/triggers/common/database');

// Import handler AFTER mocks are set up
import { handler } from '../../../lib/lambda/triggers/pre-authentication';

describe('PreAuthentication Lambda Tests', () => {
  let mockDbClient: any;
  let mockCallback: jest.Mock;

  beforeEach(() => {
    // Reset all mocks
    jest.clearAllMocks();
    mockCloudWatchSend.mockResolvedValue({});

    // Setup database client mock
    mockDbClient = {
      query: jest.fn(),
      release: jest.fn(),
    };
    (database.getDbClient as jest.Mock).mockResolvedValue(mockDbClient);

    // Setup callback mock
    mockCallback = jest.fn();
  });

  const createEvent = (cognitoId: string = 'test-cognito-id'): PreAuthenticationTriggerEvent => ({
    version: '1',
    triggerSource: 'PreAuthentication_Authentication' as any,
    region: 'eu-central-1',
    userPoolId: 'eu-central-1_TEST123',
    userName: cognitoId,
    callerContext: {
      awsSdkVersion: '2.0.0',
      clientId: 'test-client-id',
    },
    request: {
      userAttributes: {
        sub: cognitoId,
        email: 'test@example.com',
        email_verified: 'true',
      },
      validationData: {},
    },
    response: {},
  });

  describe('User Active Status Check', () => {
    test('should_allowAuthentication_when_userNotFoundInDatabase', async () => {
      // Given: User does not exist in database
      mockDbClient.query.mockResolvedValue({ rows: [] });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication allowed
      expect(mockCallback).toHaveBeenCalledWith(null, event);

      // And: Correct query executed
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('SELECT is_active, deactivation_reason'),
        ['test-cognito-id']
      );

      // And: CloudWatch metrics published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          Namespace: 'BATbern/UserSync',
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'UserNotFoundAllowed',
              Value: 1,
            }),
          ]),
        })
      );

      // And: Latency metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
            expect.objectContaining({
              MetricName: 'PreAuthLatency',
              Unit: 'Milliseconds',
            }),
          ]),
        })
      );

      // And: Database connection released
      expect(mockDbClient.release).toHaveBeenCalled();
    });

    test('should_allowAuthentication_when_userActiveInDatabase', async () => {
      // Given: User exists and is active
      mockDbClient.query.mockResolvedValue({
        rows: [
          {
            is_active: true,
            deactivation_reason: null,
          },
        ],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication allowed
      expect(mockCallback).toHaveBeenCalledWith(null, event);

      // And: CloudWatch metrics published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'ActiveUserAllowed',
                Value: 1,
              }),
            ]),
        })
      );

      // And: Database connection released
      expect(mockDbClient.release).toHaveBeenCalled();
    });

    test('should_blockAuthentication_when_userInactiveInDatabase', async () => {
      // Given: User exists but is inactive
      mockDbClient.query.mockResolvedValue({
        rows: [
          {
            is_active: false,
            deactivation_reason: 'User violated terms of service',
          },
        ],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      // Then: Should throw error to block authentication
      await expect(handler(event, {} as any, mockCallback)).rejects.toThrow('inactive');

      // And: Callback should have been called with error message
      expect(mockCallback).toHaveBeenCalledWith(
        'User account is inactive. Reason: User violated terms of service',
        event
      );

      // And: CloudWatch metrics published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'InactiveUserBlocked',
                Value: 1,
              }),
            ]),
        })
      );

      // And: Database connection released
      expect(mockDbClient.release).toHaveBeenCalled();
    });

    test('should_blockAuthentication_when_userInactiveWithoutReason', async () => {
      // Given: User exists but is inactive with no reason
      mockDbClient.query.mockResolvedValue({
        rows: [
          {
            is_active: false,
            deactivation_reason: null,
          },
        ],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      // Then: Should throw error to block authentication
      await expect(handler(event, {} as any, mockCallback)).rejects.toThrow('inactive');

      // And: Callback should have been called with default message
      expect(mockCallback).toHaveBeenCalledWith(
        'User account is inactive. Reason: Account deactivated',
        event
      );

      // And: Database connection released
      expect(mockDbClient.release).toHaveBeenCalled();
    });
  });

  describe('Error Handling and Graceful Degradation', () => {
    test('should_allowAuthentication_when_databaseConnectionFails', async () => {
      // Given: Database connection fails
      (database.getDbClient as jest.Mock).mockRejectedValue(
        new Error('Database connection timeout')
      );

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication allowed (graceful degradation)
      expect(mockCallback).toHaveBeenCalledWith(null, event);

      // And: Failure metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'PreAuthFailure',
                Value: 1,
              }),
            ]),
        })
      );
    });

    test('should_allowAuthentication_when_databaseQueryFails', async () => {
      // Given: Database query fails
      mockDbClient.query.mockRejectedValue(new Error('Query execution failed'));

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication allowed (graceful degradation)
      expect(mockCallback).toHaveBeenCalledWith(null, event);

      // And: Failure metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'PreAuthFailure',
                Value: 1,
              }),
            ]),
        })
      );

      // And: Database connection released
      expect(mockDbClient.release).toHaveBeenCalled();
    });

    test('should_releaseConnection_when_errorOccurs', async () => {
      // Given: Database query fails
      mockDbClient.query.mockRejectedValue(new Error('Database error'));

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Database connection released
      expect(mockDbClient.release).toHaveBeenCalled();
    });
  });

  describe('CloudWatch Metrics', () => {
    test('should_publishMetrics_when_userNotFound', async () => {
      // Given: User not found
      mockDbClient.query.mockResolvedValue({ rows: [] });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: UserNotFoundAllowed metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          
            Namespace: 'BATbern/UserSync',
            MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'UserNotFoundAllowed',
                Value: 1,
                Unit: 'Count',
              }),
            ]),
        })
      );

      // And: Latency metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'PreAuthLatency',
                Unit: 'Milliseconds',
              }),
            ]),
        })
      );
    });

    test('should_publishMetrics_when_userActive', async () => {
      // Given: User active
      mockDbClient.query.mockResolvedValue({
        rows: [{ is_active: true, deactivation_reason: null }],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: ActiveUserAllowed metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'ActiveUserAllowed',
                Value: 1,
              }),
            ]),
        })
      );
    });

    test('should_publishMetrics_when_userInactive', async () => {
      // Given: User inactive
      mockDbClient.query.mockResolvedValue({
        rows: [{ is_active: false, deactivation_reason: 'Test reason' }],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      // Then: Should throw error to block authentication
      await expect(handler(event, {} as any, mockCallback)).rejects.toThrow('inactive');

      // And: InactiveUserBlocked metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'InactiveUserBlocked',
                Value: 1,
              }),
            ]),
        })
      );
    });

    test('should_continueExecution_when_metricPublishingFails', async () => {
      // Given: CloudWatch metric publishing fails
      mockCloudWatchSend.mockRejectedValue(new Error('CloudWatch error'));
      mockDbClient.query.mockResolvedValue({
        rows: [{ is_active: true, deactivation_reason: null }],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication still allowed
      expect(mockCallback).toHaveBeenCalledWith(null, event);
    });
  });

  describe('JIT Provisioning Path', () => {
    test('should_allowAuthenticationForJIT_when_newUserFromCognito', async () => {
      // Given: User exists in Cognito but not in database
      mockDbClient.query.mockResolvedValue({ rows: [] });

      const event = createEvent('new-cognito-user-123');

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication allowed for JIT provisioning
      expect(mockCallback).toHaveBeenCalledWith(null, event);

      // And: Correct cognito_user_id queried
      expect(mockDbClient.query).toHaveBeenCalledWith(
        expect.stringContaining('WHERE cognito_user_id = $1'),
        ['new-cognito-user-123']
      );

      // And: UserNotFoundAllowed metric published
      expect(mockCloudWatchSend).toHaveBeenCalledWith(
        expect.objectContaining({
          MetricData: expect.arrayContaining([
              expect.objectContaining({
                MetricName: 'UserNotFoundAllowed',
              }),
            ]),
        })
      );
    });
  });

  describe('Connection Management', () => {
    test('should_releaseConnection_when_successfulExecution', async () => {
      // Given: Successful execution
      mockDbClient.query.mockResolvedValue({
        rows: [{ is_active: true, deactivation_reason: null }],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Database connection released
      expect(mockDbClient.release).toHaveBeenCalledTimes(1);
    });

    test('should_releaseConnection_when_userInactive', async () => {
      // Given: User inactive
      mockDbClient.query.mockResolvedValue({
        rows: [{ is_active: false, deactivation_reason: 'Test' }],
      });

      const event = createEvent();

      // When: PreAuthentication trigger fires
      // Then: Should throw error to block authentication
      await expect(handler(event, {} as any, mockCallback)).rejects.toThrow('inactive');

      // And: Database connection released even when blocking
      expect(mockDbClient.release).toHaveBeenCalledTimes(1);
    });

    test('should_notReleaseConnection_when_connectionFailsToEstablish', async () => {
      // Given: Database connection fails
      (database.getDbClient as jest.Mock).mockRejectedValue(
        new Error('Connection failed')
      );

      const event = createEvent();

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Release not called (no connection to release)
      expect(mockDbClient.release).not.toHaveBeenCalled();
    });
  });

  describe('Trigger Source Handling', () => {
    test('should_handleAuthentication_triggerSource', async () => {
      // Given: Authentication trigger source
      mockDbClient.query.mockResolvedValue({
        rows: [{ is_active: true, deactivation_reason: null }],
      });

      const event = createEvent();
      event.triggerSource = 'PreAuthentication_Authentication' as any;

      // When: PreAuthentication trigger fires
      await handler(event, {} as any, mockCallback);

      // Then: Authentication allowed
      expect(mockCallback).toHaveBeenCalledWith(null, event);
    });
  });
});
