/**
 * Unit tests for the PreSignUp Lambda trigger (Story 12.6 — SSO Phase 2).
 *
 * This is the MANDATORY handler test per CLAUDE.md "Lambda Handler Tests": a Lambda
 * that fails module-load (Runtime.ImportModuleError) 503s ALL auth, and a
 * Template.fromStack test cannot catch that — only importing+running the handler can.
 *
 * Two load-bearing invariants under test:
 *  - NATIVE path (PreSignUp_SignUp / PreSignUp_AdminCreateUser) preserves the legacy
 *    inline company-UUID validation verbatim AND performs NO DB call.
 *  - FEDERATED path (PreSignUp_ExternalProvider) links the Google identity into the
 *    existing native user via AdminLinkProviderForUser (sub preserved), never throws,
 *    and auto-confirms/auto-verifies both linked and brand-new federated users.
 */

import { describe, it, expect, jest, beforeEach } from '@jest/globals';
import type { PreSignUpTriggerEvent } from 'aws-lambda';

// ------------------------------------------------------------------
// Mocks
// ------------------------------------------------------------------

const mockCloudWatchSend = jest.fn<(...args: any[]) => Promise<any>>();
const mockCognitoSend = jest.fn<(...args: any[]) => Promise<any>>();
const mockDbQuery = jest.fn<(...args: any[]) => Promise<any>>();
const mockDbRelease = jest.fn<(...args: any[]) => void>();
const mockGetDbClient = jest.fn<(...args: any[]) => Promise<any>>();

jest.mock('@aws-sdk/client-cloudwatch', () => ({
  CloudWatchClient: jest.fn().mockImplementation(() => ({ send: mockCloudWatchSend })),
  PutMetricDataCommand: jest.fn().mockImplementation((input) => input),
}));

jest.mock('@aws-sdk/client-cognito-identity-provider', () => ({
  CognitoIdentityProviderClient: jest.fn().mockImplementation(() => ({ send: mockCognitoSend })),
  AdminLinkProviderForUserCommand: jest.fn().mockImplementation((input: any) => ({
    __type: 'AdminLinkProviderForUser',
    ...input,
  })),
  ListUsersCommand: jest.fn().mockImplementation((input: any) => ({ __type: 'ListUsers', ...input })),
}));

jest.mock('../../../lib/lambda/triggers/common/database', () => ({
  getDbClient: mockGetDbClient,
}));

import { handler } from '../../../lib/lambda/triggers/pre-signup';

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeEvent(
  triggerSource: PreSignUpTriggerEvent['triggerSource'],
  attributes: Record<string, string> = {},
  userName = 'native-user'
): PreSignUpTriggerEvent {
  return {
    version: '1',
    triggerSource,
    region: 'eu-central-1',
    userPoolId: 'eu-central-1_TEST',
    userName,
    callerContext: { awsSdkVersion: '3.0.0', clientId: 'client' },
    request: {
      userAttributes: { email: 'user@example.com', ...attributes },
      validationData: {},
      clientMetadata: {},
    },
    response: {
      autoConfirmUser: false,
      autoVerifyEmail: false,
      autoVerifyPhone: false,
    },
  } as PreSignUpTriggerEvent;
}

function makeDbClient() {
  return { query: mockDbQuery, release: mockDbRelease };
}

const VALID_UUID = '123e4567-e89b-12d3-a456-426614174000';

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('pre-signup Lambda handler', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCloudWatchSend.mockResolvedValue({});
    mockCognitoSend.mockResolvedValue({});
  });

  // AC5.1
  it('module loads without crashing', () => {
    expect(typeof handler).toBe('function');
  });

  // AC5.2 — native validation unchanged, no DB call
  it('should_returnEvent_when_nativeSignupHasValidCompanyId', async () => {
    const event = makeEvent('PreSignUp_SignUp', { 'custom:companyId': VALID_UUID });
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toEqual(event);
    expect(mockGetDbClient).not.toHaveBeenCalled();
  });

  it('should_throwInvalidCompanyId_when_nativeSignupHasMalformedCompanyId', async () => {
    const event = makeEvent('PreSignUp_SignUp', { 'custom:companyId': 'not-a-uuid' });
    await expect(handler(event, {} as any, jest.fn() as any)).rejects.toThrow(
      'Invalid company ID format. Must be a valid UUID.'
    );
    expect(mockGetDbClient).not.toHaveBeenCalled();
  });

  it('should_returnEvent_when_nativeSignupHasNoCompanyId', async () => {
    const event = makeEvent('PreSignUp_SignUp');
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toEqual(event);
    expect(mockGetDbClient).not.toHaveBeenCalled();
  });

  it('should_treatAdminCreateUserAsNativePath', async () => {
    const event = makeEvent('PreSignUp_AdminCreateUser', { 'custom:companyId': 'bad' });
    await expect(handler(event, {} as any, jest.fn() as any)).rejects.toThrow(
      'Invalid company ID format. Must be a valid UUID.'
    );
    expect(mockGetDbClient).not.toHaveBeenCalled();
  });

  // AC5.3 — federated-link path
  it('should_linkGoogleIdentityToNativeUser_when_emailMatchesExistingUser', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({
      rows: [{ cognito_user_id: 'native-sub-uuid', username: 'john.doe' }],
      rowCount: 1,
    });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'john@example.com' },
      'Google_117xyz'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    // AdminLinkProviderForUser sent exactly once with the right merge shape
    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(1);
    const input = linkCalls[0][0];
    expect(input.UserPoolId).toBe('eu-central-1_TEST');
    expect(input.DestinationUser).toEqual({
      ProviderName: 'Cognito',
      ProviderAttributeValue: 'native-sub-uuid',
    });
    expect(input.SourceUser).toEqual({
      ProviderName: 'Google',
      ProviderAttributeName: 'Cognito_Subject',
      ProviderAttributeValue: '117xyz',
    });

    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    expect(mockDbRelease).toHaveBeenCalled();
  });

  // AC5.4 — federated-new-user path
  it('should_notLink_when_noExistingNativeUserFound', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'brand-new@example.com' },
      'Google_999'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);
    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
  });

  // AC5.5 — missing-email guard
  it('should_notLinkAndNotThrow_when_federatedEventHasNoEmail', async () => {
    const event = makeEvent('PreSignUp_ExternalProvider', { email: '' }, 'Google_555');
    const result = await handler(event, {} as any, jest.fn() as any);

    expect(mockGetDbClient).not.toHaveBeenCalled();
    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);
    expect(result).toBeDefined();
  });

  // Resilience: federated path must never throw, even on DB/SDK failure (a throw 503s login)
  it('should_returnEventAndNotThrow_when_dbLookupFails', async () => {
    mockGetDbClient.mockRejectedValue(new Error('db down'));
    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'john@example.com' },
      'Google_117xyz'
    );
    await expect(handler(event, {} as any, jest.fn() as any)).resolves.toBeDefined();
  });
});
