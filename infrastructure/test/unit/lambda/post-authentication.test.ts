/**
 * Unit tests for PostAuthentication Lambda trigger (ADR-005)
 *
 * Critical invariant: this trigger MUST NOT throw. Any unhandled error blocks
 * the user's login — the handler catches all errors and returns the event.
 * Tests verify both the happy-path linking and the fail-open error behaviour.
 */

import { describe, it, expect, jest, beforeEach } from '@jest/globals';
import type { PostAuthenticationTriggerEvent } from 'aws-lambda';

// ------------------------------------------------------------------
// Mocks
// ------------------------------------------------------------------

const mockCloudWatchSend = jest.fn<(...args: any[]) => Promise<any>>();
const mockDbQuery = jest.fn<(...args: any[]) => Promise<any>>();
const mockDbRelease = jest.fn<(...args: any[]) => void>();
const mockGetDbClient = jest.fn<(...args: any[]) => Promise<any>>();

jest.mock('@aws-sdk/client-cloudwatch', () => ({
  CloudWatchClient: jest.fn().mockImplementation(() => ({ send: mockCloudWatchSend })),
  PutMetricDataCommand: jest.fn().mockImplementation((input) => input),
}));

const mockCognitoSend = jest.fn<(...args: any[]) => Promise<any>>();
jest.mock('@aws-sdk/client-cognito-identity-provider', () => ({
  CognitoIdentityProviderClient: jest.fn().mockImplementation(() => ({ send: mockCognitoSend })),
  AdminUpdateUserAttributesCommand: jest
    .fn()
    .mockImplementation((input) => ({ __type: 'AdminUpdateUserAttributes', ...(input as object) })),
}));

jest.mock('../../../lib/lambda/triggers/common/database', () => ({
  getDbClient: mockGetDbClient,
  executeTransaction: jest.fn(),
}));

import { handler } from '../../../lib/lambda/triggers/post-authentication';

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeEvent(attributes: Record<string, string> = {}): PostAuthenticationTriggerEvent {
  return {
    version: '1',
    triggerSource: 'PostAuthentication_Authentication',
    region: 'eu-central-1',
    userPoolId: 'eu-central-1_TEST',
    userName: 'test-user',
    callerContext: { awsSdkVersion: '3.0.0', clientId: 'client' },
    request: {
      userAttributes: {
        sub: 'cognito-sub-123',
        email: 'user@example.com',
        email_verified: 'true',
        ...attributes,
      },
      newDeviceUsed: false,
      clientMetadata: {},
    },
    response: {},
  };
}

function makeDbClient(rows: Record<string, unknown>[] = []) {
  return {
    query: mockDbQuery,
    release: mockDbRelease,
  };
}

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('post-authentication Lambda handler', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCloudWatchSend.mockResolvedValue({});
    mockCognitoSend.mockResolvedValue({});
  });

  it('module loads without crashing (CloudWatchClient initialized at module level)', () => {
    expect(typeof handler).toBe('function');
  });

  it('should_returnEvent_when_noAnonymousUserExists', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    // Restore-check query: no user_profiles row for the sub -> no drift handling
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });
    // Find anonymous user — returns nothing
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });

    const event = makeEvent();
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toEqual(event);
  });

  it('should_linkAnonymousUser_when_matchingEmailFound', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    // Restore-check query: no row -> no drift handling
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });
    // Find anonymous user
    mockDbQuery.mockResolvedValueOnce({
      rows: [{ id: 'anon-id-1', username: 'anon_user', cognito_user_id: null }],
      rowCount: 1,
    });
    // Second query: update cognito_user_id
    mockDbQuery.mockResolvedValueOnce({
      rows: [{ username: 'anon_user' }],
      rowCount: 1,
    });

    const event = makeEvent();
    const result = await handler(event, {} as any, jest.fn() as any);

    expect(mockDbQuery).toHaveBeenCalledTimes(3);
    const updateCall = mockDbQuery.mock.calls[2] as [string, string[]];
    expect(updateCall[0]).toContain('UPDATE user_profiles');
    expect(updateCall[1]).toContain('cognito-sub-123');
    expect(result).toEqual(event);
  });

  it('should_publishCloudWatchMetric_when_userLinkedSuccessfully', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery
      .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // restore-check: no row
      .mockResolvedValueOnce({
        rows: [{ id: 'id', username: 'u', cognito_user_id: null }],
        rowCount: 1,
      })
      .mockResolvedValueOnce({ rows: [{ username: 'u' }], rowCount: 1 });

    const event = makeEvent();
    await handler(event, {} as any, jest.fn() as any);

    expect(mockCloudWatchSend).toHaveBeenCalledTimes(1);
  });

  it('should_returnEventWithoutThrowing_when_dbConnectionFails', async () => {
    // ADR-005 fail-open: a DB failure must NOT block login
    mockGetDbClient.mockRejectedValue(new Error('Connection refused'));

    const event = makeEvent();
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toEqual(event);
  });

  it('should_returnEventWithoutThrowing_when_updateQueryFails', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery
      .mockResolvedValueOnce({ rows: [], rowCount: 0 }) // restore-check: no row
      .mockResolvedValueOnce({
        rows: [{ id: 'id', username: 'u', cognito_user_id: null }],
        rowCount: 1,
      })
      .mockRejectedValueOnce(new Error('Deadlock'));

    const event = makeEvent();
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toEqual(event);
  });

  it('should_throwError_when_requiredAttributeSubIsMissing', async () => {
    // extractUserAttributes throws if sub is missing — handler catches and returns event
    mockGetDbClient.mockResolvedValue(makeDbClient());
    const event = makeEvent({ sub: '', email: 'user@example.com' });
    const result = await handler(event, {} as any, jest.fn() as any);
    // Still returns event (fail-open)
    expect(result).toEqual(event);
  });

  // PR #745: canonical-email restore after federated attribute-sync drift
  describe('canonical email restore (federated sync drift)', () => {
    it('should_restoreCanonicalEmail_when_eventEmailDriftedFromDbEmail', async () => {
      mockGetDbClient.mockResolvedValue(makeDbClient());
      mockDbQuery
        .mockResolvedValueOnce({ rows: [{ email: 'native@example.ch' }], rowCount: 1 }) // restore-check: drifted
        .mockResolvedValueOnce({ rows: [], rowCount: 0 }); // anonymous lookup: none

      const event = makeEvent({ email: 'gmail-additional@gmail.com' });
      const result = await handler(event, {} as any, jest.fn() as any);

      expect(mockCognitoSend).toHaveBeenCalledTimes(1);
      const cmd = mockCognitoSend.mock.calls[0][0] as any;
      expect(cmd.UserPoolId).toBe('eu-central-1_TEST');
      expect(cmd.Username).toBe('cognito-sub-123');
      expect(cmd.UserAttributes).toEqual([
        { Name: 'email', Value: 'native@example.ch' },
        { Name: 'email_verified', Value: 'true' },
      ]);
      expect(result).toEqual(event);
    });

    it('should_notTouchCognito_when_emailsMatchCaseInsensitively', async () => {
      mockGetDbClient.mockResolvedValue(makeDbClient());
      mockDbQuery
        .mockResolvedValueOnce({ rows: [{ email: 'User@Example.COM' }], rowCount: 1 })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 });

      const event = makeEvent({ email: 'user@example.com' });
      const result = await handler(event, {} as any, jest.fn() as any);

      expect(mockCognitoSend).not.toHaveBeenCalled();
      expect(result).toEqual(event);
    });

    it('should_notTouchCognito_when_noProfileRowForSub', async () => {
      mockGetDbClient.mockResolvedValue(makeDbClient());
      mockDbQuery
        .mockResolvedValueOnce({ rows: [], rowCount: 0 })
        .mockResolvedValueOnce({ rows: [], rowCount: 0 });

      const event = makeEvent();
      await handler(event, {} as any, jest.fn() as any);

      expect(mockCognitoSend).not.toHaveBeenCalled();
    });

    it('should_returnEventWithoutThrowing_when_adminUpdateFails', async () => {
      mockGetDbClient.mockResolvedValue(makeDbClient());
      mockDbQuery.mockResolvedValueOnce({ rows: [{ email: 'native@example.ch' }], rowCount: 1 });
      mockCognitoSend.mockRejectedValue(new Error('AccessDenied'));

      const event = makeEvent({ email: 'drifted@gmail.com' });
      const result = await handler(event, {} as any, jest.fn() as any);

      // Fail-open: login must proceed
      expect(result).toEqual(event);
    });
  });
});
