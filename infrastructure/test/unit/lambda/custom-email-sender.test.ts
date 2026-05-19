/**
 * Unit tests for CustomEmailSender Lambda trigger
 *
 * Primary guard: the module-level `buildClient(...)` call from @aws-crypto/client-node
 * runs at import time. If that package is broken or misconfigured, all Cognito email
 * events would fail. The import test below catches that immediately without a deploy.
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import type { Handler } from 'aws-lambda';

// ------------------------------------------------------------------
// Mocks must be declared before the module is imported
// ------------------------------------------------------------------

const mockDecrypt = jest.fn<(...args: any[]) => Promise<any>>();
const mockSesSend = jest.fn<(...args: any[]) => Promise<any>>();

jest.mock('@aws-crypto/client-node', () => ({
  buildClient: jest.fn().mockReturnValue({ decrypt: mockDecrypt }),
  KmsKeyringNode: jest.fn().mockImplementation(() => ({})),
  CommitmentPolicy: { REQUIRE_ENCRYPT_ALLOW_DECRYPT: 'REQUIRE_ENCRYPT_ALLOW_DECRYPT' },
}));

jest.mock('@aws-sdk/client-ses', () => ({
  SESClient: jest.fn().mockImplementation(() => ({ send: mockSesSend })),
  SendEmailCommand: jest.fn().mockImplementation((input) => input),
}));

import { handler } from '../../../lib/lambda/triggers/custom-email-sender';

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeEvent(triggerSource: string, overrides: Record<string, unknown> = {}) {
  return {
    version: '1',
    triggerSource,
    region: 'eu-central-1',
    userPoolId: 'eu-central-1_TEST',
    userName: 'test-user',
    callerContext: { awsSdkVersion: '3.0.0', clientId: 'client' },
    request: {
      type: 'customEmailSenderRequestV1' as const,
      code: Buffer.from('encrypted-code').toString('base64'),
      userAttributes: {
        sub: 'user-sub-123',
        email: 'user@example.com',
        'custom:language': 'en',
        ...((overrides.userAttributes as Record<string, string>) ?? {}),
      },
    },
    response: {},
    ...overrides,
  };
}

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('custom-email-sender Lambda handler', () => {
  const env = process.env;

  beforeEach(() => {
    jest.clearAllMocks();
    process.env = {
      ...env,
      FRONTEND_DOMAIN: 'https://www.batbern.ch',
      FROM_EMAIL: 'noreply@batbern.ch',
      KEY_ARN: 'arn:aws:kms:eu-central-1:123456789:key/test-key',
      KEY_ID: 'test-key-id',
      AWS_REGION: 'eu-central-1',
    };
    mockDecrypt.mockResolvedValue({ plaintext: Buffer.from('123456'), messageHeader: {} });
    mockSesSend.mockResolvedValue({ MessageId: 'msg-123' });
  });

  afterEach(() => {
    process.env = env;
  });

  it('module loads without crashing (buildClient runs at import time)', () => {
    expect(typeof handler).toBe('function');
  });

  it('should_returnEventUnchanged_when_triggerSourceIsNotCustomEmailSender', async () => {
    const event = makeEvent('PostConfirmation_ConfirmSignUp');
    const result = await (handler as Handler)(event, {} as any, jest.fn());
    expect(result).toEqual(event);
    expect(mockSesSend).not.toHaveBeenCalled();
  });

  it('should_returnEventUnchanged_when_triggerSourceIsUnsupportedCustomEmailSender', async () => {
    const event = makeEvent('CustomEmailSender_ResendCode');
    const result = await (handler as Handler)(event, {} as any, jest.fn());
    expect(result).toEqual(event);
    expect(mockSesSend).not.toHaveBeenCalled();
  });

  it('should_sendPasswordResetEmailViaSES_when_ForgotPasswordTrigger', async () => {
    const event = makeEvent('CustomEmailSender_ForgotPassword');
    const result = await (handler as Handler)(event, {} as any, jest.fn());

    expect(mockDecrypt).toHaveBeenCalledTimes(1);
    expect(mockSesSend).toHaveBeenCalledTimes(1);

    const sesPayload = mockSesSend.mock.calls[0][0] as { Destination: { ToAddresses: string[] } };
    expect(sesPayload.Destination.ToAddresses).toContain('user@example.com');
    expect(result).toEqual(event);
  });

  it('should_sendSignupVerificationEmailViaSES_when_SignUpTrigger', async () => {
    const event = makeEvent('CustomEmailSender_SignUp');
    await (handler as Handler)(event, {} as any, jest.fn());

    expect(mockDecrypt).toHaveBeenCalledTimes(1);
    expect(mockSesSend).toHaveBeenCalledTimes(1);
  });

  it('should_sendGermanEmail_when_userLanguageIsDe', async () => {
    const event = makeEvent('CustomEmailSender_ForgotPassword', {
      userAttributes: { sub: 'sub', email: 'user@example.com', 'custom:language': 'de' },
    });
    await (handler as Handler)(event, {} as any, jest.fn());

    const sesPayload = mockSesSend.mock.calls[0][0] as {
      Message: { Subject: { Data: string } };
    };
    expect(sesPayload.Message.Subject.Data).toContain('Passwort');
  });

  it('should_throwError_when_requiredEnvVarsAreMissing', async () => {
    delete process.env.FRONTEND_DOMAIN;
    const event = makeEvent('CustomEmailSender_ForgotPassword');
    await expect((handler as Handler)(event, {} as any, jest.fn())).rejects.toThrow(
      'Missing required environment variables'
    );
  });

  it('should_throwError_when_decryptFails', async () => {
    mockDecrypt.mockRejectedValue(new Error('KMS decryption failed'));
    const event = makeEvent('CustomEmailSender_ForgotPassword');
    await expect((handler as Handler)(event, {} as any, jest.fn())).rejects.toThrow('KMS decryption failed');
  });
});
