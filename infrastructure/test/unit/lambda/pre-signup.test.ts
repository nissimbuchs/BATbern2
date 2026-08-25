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
  ListUsersCommand: jest
    .fn()
    .mockImplementation((input: any) => ({ __type: 'ListUsers', ...input })),
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

    // D1 patch: the lookup matches email case-INSENSITIVELY so a case-variant IdP email
    // still links to a non-normalised native row instead of orphaning a new sub.
    const querySql = mockDbQuery.mock.calls[0][0] as string;
    expect(querySql).toMatch(/lower\(\s*email\s*\)\s*=\s*lower\(\s*\$1\s*\)/i);
  });

  // D2 patch: anonymous registrant (row exists, cognito_user_id NULL) — do NOT link
  // (no Cognito destination; sub unknown pre-confirmation), defer adoption to JIT.
  it('should_notLinkAndDeferToJit_when_emailMatchesAnonymousRowWithNoCognitoId', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({
      rows: [{ cognito_user_id: null, username: 'anon.registrant' }],
      rowCount: 1,
    });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'anon@example.com' },
      'Google_42'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);
    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    expect(mockDbRelease).toHaveBeenCalled();

    // Distinct metric so the adopt-pending case is observable separately from a true new user.
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('FederatedAnonymousPendingJit');
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

  // ----------------------------------------------------------------
  // Verified-additional-email fallback (Epic 12 follow-up)
  // Runs ONLY on the zero-rows primary-match path, BEFORE the brand-new-user outcome.
  // ----------------------------------------------------------------

  it('should_linkViaAdditionalEmail_when_primaryMissesAndAdditionalEmailVerifiedAndIdpVerified', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    // 1st query (primary user_profiles match) → empty; 2nd query (additional-email fallback) → hit.
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 }).mockResolvedValueOnce({
      rows: [{ cognito_user_id: 'owner-sub-uuid', username: 'jane.owner' }],
      rowCount: 1,
    });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com', email_verified: 'true' },
      'Google_700abc'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(1);
    const input = linkCalls[0][0];
    expect(input.UserPoolId).toBe('eu-central-1_TEST');
    expect(input.DestinationUser).toEqual({
      ProviderName: 'Cognito',
      ProviderAttributeValue: 'owner-sub-uuid',
    });
    expect(input.SourceUser).toEqual({
      ProviderName: 'Google',
      ProviderAttributeName: 'Cognito_Subject',
      ProviderAttributeValue: '700abc',
    });

    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    expect(mockDbRelease).toHaveBeenCalled();

    // The fallback query joins user_additional_emails and gates on verified_at + LOWER(email).
    const fallbackSql = mockDbQuery.mock.calls[1][0] as string;
    expect(fallbackSql).toMatch(/user_additional_emails/i);
    expect(fallbackSql).toMatch(/verified_at\s+is\s+not\s+null/i);
    expect(fallbackSql).toMatch(/lower\(\s*ae\.email\s*\)\s*=\s*lower\(\s*\$1\s*\)/i);

    // Distinct metric so the additional-email link is observable separately.
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('FederatedUserLinkedViaAdditionalEmail');
  });

  it('should_notQueryAdditionalEmail_when_emailVerifiedAttributeIsFalse', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com', email_verified: 'false' },
      'Google_700abc'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    // Only the primary query ran — the IdP-unverified gate blocks the fallback entirely.
    expect(mockDbQuery).toHaveBeenCalledTimes(1);
    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);
    // Falls through to the existing brand-new-user outcome.
    expect(result.response.autoConfirmUser).toBe(true);
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('FederatedNewUser');
  });

  it('should_notQueryAdditionalEmail_when_emailVerifiedAttributeAbsent', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com' },
      'Google_700abc'
    );
    await handler(event, {} as any, jest.fn() as any);

    expect(mockDbQuery).toHaveBeenCalledTimes(1);
    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);
  });

  it('should_treatEmailVerifiedCaseInsensitively_when_attributeIsUpperTrue', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 }).mockResolvedValueOnce({
      rows: [{ cognito_user_id: 'owner-sub-uuid', username: 'jane.owner' }],
      rowCount: 1,
    });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com', email_verified: 'TRUE' },
      'Google_700abc'
    );
    await handler(event, {} as any, jest.fn() as any);

    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(1);
  });

  it('should_notLinkAndFallThrough_when_additionalEmailOwnerHasNoCognitoId', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 }).mockResolvedValueOnce({
      rows: [{ cognito_user_id: null, username: 'jane.owner' }],
      rowCount: 1,
    });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com', email_verified: 'true' },
      'Google_700abc'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    // No destination Cognito user → no link; falls through, still auto-confirmed.
    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);
    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    expect(mockDbRelease).toHaveBeenCalled();

    // Owner found via a verified additional email but with no Cognito sub yet → the
    // deferred-adoption outcome, observable via FederatedAnonymousPendingJit (canonical
    // JIT reconciles once the owner has a sub).
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('FederatedAnonymousPendingJit');
  });

  it('should_treatAsNewUser_when_primaryMissesAndFallbackReturnsZeroRowsBecauseAdditionalEmailUnverified', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    // 1st query (primary user_profiles match) → empty.
    // 2nd query (additional-email fallback) → ZERO rows: the additional email exists but is
    // UNVERIFIED (verified_at IS NULL), so the SQL `verified_at IS NOT NULL` gate excludes it.
    // This documents that an unverified additional email must NOT link a federated identity.
    mockDbQuery
      .mockResolvedValueOnce({ rows: [], rowCount: 0 })
      .mockResolvedValueOnce({ rows: [], rowCount: 0 });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com', email_verified: 'true' },
      'Google_700abc'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    // Fallback ran (IdP verified + primary missed) but matched nothing → no link.
    expect(mockDbQuery).toHaveBeenCalledTimes(2);
    const linkCalls = mockCognitoSend.mock.calls.filter(
      (c: any[]) => c[0]?.__type === 'AdminLinkProviderForUser'
    );
    expect(linkCalls).toHaveLength(0);

    // Genuinely a brand-new federated user → FederatedNewUser outcome, auto-confirmed.
    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    expect(mockDbRelease).toHaveBeenCalled();
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('FederatedNewUser');
  });

  it('should_notQueryAdditionalEmail_when_primaryMatchAlreadyLinked', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery.mockResolvedValueOnce({
      rows: [{ cognito_user_id: 'native-sub-uuid', username: 'john.doe' }],
      rowCount: 1,
    });

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'john@example.com', email_verified: 'true' },
      'Google_117xyz'
    );
    await handler(event, {} as any, jest.fn() as any);

    // Primary path wins — the fallback is never queried.
    expect(mockDbQuery).toHaveBeenCalledTimes(1);
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('FederatedUserLinked');
    expect(metricNames).not.toContain('FederatedUserLinkedViaAdditionalEmail');
  });

  it('should_returnEventAndNotThrow_when_additionalEmailFallbackQueryThrows', async () => {
    mockGetDbClient.mockResolvedValue(makeDbClient());
    mockDbQuery
      .mockResolvedValueOnce({ rows: [], rowCount: 0 })
      .mockRejectedValueOnce(new Error('fallback query exploded'));

    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'gmail-private@gmail.com', email_verified: 'true' },
      'Google_700abc'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    // Never throws; auto-confirm preserved; failure surfaced as PreSignUpFailure metric.
    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    expect(mockDbRelease).toHaveBeenCalled();
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('PreSignUpFailure');
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
    // Contract: missing-email must NOT auto-confirm (it falls through unchanged). Pinning this
    // catches a regression that moved the autoConfirmUser assignment above the email guard.
    expect(result.response.autoConfirmUser).toBe(false);
    expect(result.response.autoVerifyEmail).toBe(false);
  });

  // Resilience: federated path must never throw, even on DB/SDK failure (a throw 503s login)
  it('should_returnEventAndNotThrow_when_dbLookupFails', async () => {
    mockGetDbClient.mockRejectedValue(new Error('db down'));
    const event = makeEvent(
      'PreSignUp_ExternalProvider',
      { email: 'john@example.com' },
      'Google_117xyz'
    );
    const result = await handler(event, {} as any, jest.fn() as any);

    // Fail-open: the user is auto-confirmed (set before the try) so the sign-in still completes...
    expect(result.response.autoConfirmUser).toBe(true);
    expect(result.response.autoVerifyEmail).toBe(true);
    // ...and the failure is surfaced as a PreSignUpFailure metric (the D3 CloudWatch alarm fires on it).
    const metricNames = mockCloudWatchSend.mock.calls.map(
      (c: any[]) => c[0]?.MetricData?.[0]?.MetricName
    );
    expect(metricNames).toContain('PreSignUpFailure');
  });
});
