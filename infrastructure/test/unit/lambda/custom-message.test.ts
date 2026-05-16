/**
 * Unit tests for CustomMessage Lambda trigger
 *
 * This trigger sends password-reset emails via SES and suppresses Cognito's
 * default email. Key invariants:
 * - Non-ForgotPassword triggers pass through unchanged
 * - SES errors are swallowed (fail-open: Cognito sends its default email)
 * - Cognito's default email is suppressed on success (empty subject/message)
 */

import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import type { CustomMessageTriggerEvent } from 'aws-lambda';

const mockSesSend = jest.fn<(...args: any[]) => Promise<any>>();

jest.mock('@aws-sdk/client-ses', () => ({
  SESClient: jest.fn().mockImplementation(() => ({ send: mockSesSend })),
  SendEmailCommand: jest.fn().mockImplementation((input) => input),
}));

import { handler } from '../../../lib/lambda/triggers/custom-message';

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeEvent(
  triggerSource: string,
  overrides: Partial<CustomMessageTriggerEvent['request']> = {}
): CustomMessageTriggerEvent {
  return {
    version: '1',
    triggerSource: triggerSource as CustomMessageTriggerEvent['triggerSource'],
    region: 'eu-central-1',
    userPoolId: 'eu-central-1_TEST',
    userName: 'test-user',
    callerContext: { awsSdkVersion: '3.0.0', clientId: 'client' },
    request: {
      userAttributes: {
        sub: 'user-sub-123',
        email: 'user@example.com',
        'custom:language': 'en',
      },
      codeParameter: '123456',
      usernameParameter: undefined,
      linkParameter: undefined,
      ...overrides,
    },
    response: {},
  } as unknown as CustomMessageTriggerEvent;
}

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('custom-message Lambda handler', () => {
  const env = process.env;

  beforeEach(() => {
    jest.clearAllMocks();
    process.env = { ...env, FRONTEND_DOMAIN: 'https://www.batbern.ch', AWS_REGION: 'eu-central-1' };
    mockSesSend.mockResolvedValue({ MessageId: 'msg-abc' });
  });

  afterEach(() => {
    process.env = env;
  });

  it('module loads without crashing', () => {
    expect(typeof handler).toBe('function');
  });

  it('should_returnEventUnchanged_when_triggerIsNotForgotPassword', async () => {
    const event = makeEvent('CustomMessage_SignUp');
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toEqual(event);
    expect(mockSesSend).not.toHaveBeenCalled();
  });

  it('should_sendEmailViaSES_when_ForgotPasswordTrigger', async () => {
    const event = makeEvent('CustomMessage_ForgotPassword');
    await handler(event, {} as any, jest.fn() as any);

    expect(mockSesSend).toHaveBeenCalledTimes(1);
    const payload = mockSesSend.mock.calls[0][0] as { Destination: { ToAddresses: string[] } };
    expect(payload.Destination.ToAddresses).toContain('user@example.com');
  });

  it('should_suppressCognitoDefaultEmail_when_SESSucceeds', async () => {
    const event = makeEvent('CustomMessage_ForgotPassword');
    const result = await handler(event, {} as any, jest.fn() as any);

    expect((result as CustomMessageTriggerEvent).response.emailSubject).toBe('');
    expect((result as CustomMessageTriggerEvent).response.emailMessage).toBe('');
  });

  it('should_sendGermanEmail_when_userLanguageIsDe', async () => {
    const event = makeEvent('CustomMessage_ForgotPassword', {
      userAttributes: { sub: 'sub', email: 'user@example.com', 'custom:language': 'de' },
      codeParameter: '654321',
    });
    await handler(event, {} as any, jest.fn() as any);

    const payload = mockSesSend.mock.calls[0][0] as {
      Message: { Subject: { Data: string } };
    };
    expect(payload.Message.Subject.Data).toContain('Passwort');
  });

  it('should_returnEventWithoutThrowing_when_SESFails', async () => {
    mockSesSend.mockRejectedValue(new Error('SES throttled'));
    const event = makeEvent('CustomMessage_ForgotPassword');

    // Must not throw — Cognito falls back to its own email
    const result = await handler(event, {} as any, jest.fn() as any);
    expect(result).toBeDefined();
  });

  it('should_buildResetLinkWithCodeEmailAndLanguage', async () => {
    const event = makeEvent('CustomMessage_ForgotPassword', {
      userAttributes: { sub: 'sub', email: 'alice@example.com', 'custom:language': 'en' },
      codeParameter: '999999',
    });
    await handler(event, {} as any, jest.fn() as any);

    const payload = mockSesSend.mock.calls[0][0] as {
      Message: { Body: { Text: { Data: string } } };
    };
    expect(payload.Message.Body.Text.Data).toContain('999999');
    expect(payload.Message.Body.Text.Data).toContain('alice%40example.com');
    expect(payload.Message.Body.Text.Data).toContain('lang=en');
  });
});
