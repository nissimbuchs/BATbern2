/**
 * Email Forwarder Lambda Unit Tests (Story 10.26 — T6-T9)
 *
 * Tests cover:
 *   T6: Header parsing, email address extraction
 *   T7: Address resolution (role-based, event distribution, support contacts)
 *   T8: Sender authorization with caching
 *   T9: Email rewriting
 */

// ========================
// T6: Header Parsing Tests
// ========================

import {
  parseHeaders,
  extractToAddress,
  extractSenderEmail,
  extractSenderName,
  truncateEmail,
} from '../../lambda/email-forwarder/utils';

describe('T6 — S3 event parsing and header extraction', () => {
  const sampleEmail = [
    'From: John Doe <john@example.com>',
    'To: ok@batbern.ch',
    'Subject: Test forwarding',
    'Message-ID: <abc123@example.com>',
    'Content-Type: text/plain',
    '',
    'Hello, this is a test email.',
  ].join('\r\n');

  test('should_parseHeaders_when_validMimeEmail', () => {
    const headers = parseHeaders(sampleEmail);
    expect(headers['from']).toBe('John Doe <john@example.com>');
    expect(headers['to']).toBe('ok@batbern.ch');
    expect(headers['subject']).toBe('Test forwarding');
    expect(headers['message-id']).toBe('<abc123@example.com>');
  });

  test('should_handleFoldedHeaders_when_continuationLines', () => {
    const folded = [
      'From: Very Long Name',
      ' <john@example.com>',
      'To: ok@batbern.ch',
      '',
      'body',
    ].join('\r\n');
    const headers = parseHeaders(folded);
    expect(headers['from']).toBe('Very Long Name <john@example.com>');
  });

  test('should_extractToAddress_when_angleBracketFormat', () => {
    expect(extractToAddress('BATbern <ok@batbern.ch>')).toBe('ok@batbern.ch');
  });

  test('should_extractToAddress_when_plainFormat', () => {
    expect(extractToAddress('ok@batbern.ch')).toBe('ok@batbern.ch');
  });

  test('should_returnUndefined_when_toMissing', () => {
    expect(extractToAddress(undefined)).toBeUndefined();
  });

  test('should_extractSenderEmail_when_angleBracketFormat', () => {
    expect(extractSenderEmail('John Doe <john@example.com>')).toBe('john@example.com');
  });

  test('should_extractSenderEmail_when_plainFormat', () => {
    expect(extractSenderEmail('john@example.com')).toBe('john@example.com');
  });

  test('should_extractSenderName_when_displayNamePresent', () => {
    expect(extractSenderName('John Doe <john@example.com>')).toBe('John Doe');
  });

  test('should_extractSenderName_when_quotedDisplayName', () => {
    expect(extractSenderName('"John Doe" <john@example.com>')).toBe('John Doe');
  });

  test('should_truncateEmail_when_normalLength', () => {
    expect(truncateEmail('john.doe@example.com')).toBe('john.***');
  });

  test('should_truncateEmail_when_shortEmail', () => {
    expect(truncateEmail('ab@x')).toBe('ab@x***');
  });
});

// ========================
// T7: Address Resolution Tests
// ========================

describe('T7 — Address resolution', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    jest.resetModules();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  function mockFetch(responses: Record<string, { status: number; body: unknown }>): void {
    global.fetch = jest.fn(async (url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      for (const [pattern, resp] of Object.entries(responses)) {
        if (urlStr.includes(pattern)) {
          return {
            ok: resp.status >= 200 && resp.status < 300,
            status: resp.status,
            json: async () => resp.body,
          } as Response;
        }
      }
      return { ok: false, status: 404, json: async () => ({}) } as Response;
    });
  }

  test('should_resolveOrganizerEmails_when_okAddress', async () => {
    mockFetch({
      'role=ORGANIZER': {
        status: 200,
        body: { data: [{ email: 'org1@test.ch' }, { email: 'org2@test.ch' }], pagination: { totalPages: 1, page: 0 } },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('ok@batbern.ch');
    expect(result).toEqual(['org1@test.ch', 'org2@test.ch']);
  });

  test('should_resolveOrganizerEmails_when_infoAddress', async () => {
    mockFetch({
      'role=ORGANIZER': {
        status: 200,
        body: { data: [{ email: 'org@test.ch' }], pagination: { totalPages: 1, page: 0 } },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('info@batbern.ch');
    expect(result).toEqual(['org@test.ch']);
  });

  test('should_resolveOrganizerEmails_when_eventsAddress', async () => {
    mockFetch({
      'role=ORGANIZER': {
        status: 200,
        body: { data: [{ email: 'org@test.ch' }], pagination: { totalPages: 1, page: 0 } },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('events@batbern.ch');
    expect(result).toEqual(['org@test.ch']);
  });

  test('should_resolvePartnerEmails_when_partnerAddress', async () => {
    mockFetch({
      'role=PARTNER': {
        status: 200,
        body: { data: [{ email: 'partner@test.ch' }], pagination: { totalPages: 1, page: 0 } },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('partner@batbern.ch');
    expect(result).toEqual(['partner@test.ch']);
  });

  test('should_resolveSupportContacts_when_supportAddressAndConfigured', async () => {
    mockFetch({
      'admin/settings': {
        status: 200,
        body: { key: 'email-forwarding.support-contacts', value: 'sup1@test.ch, sup2@test.ch' },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('support@batbern.ch');
    expect(result).toEqual(['sup1@test.ch', 'sup2@test.ch']);
  });

  test('should_fallbackToOrganizers_when_supportContactsEmpty', async () => {
    mockFetch({
      'admin/settings': {
        status: 200,
        body: { key: 'email-forwarding.support-contacts', value: '' },
      },
      'role=ORGANIZER': {
        status: 200,
        body: { data: [{ email: 'org@test.ch' }], pagination: { totalPages: 1, page: 0 } },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('support@batbern.ch');
    expect(result).toEqual(['org@test.ch']);
  });

  test('should_resolveEventRegistrants_when_batbern58Address', async () => {
    mockFetch({
      'events/BATbern58/registrations': {
        status: 200,
        body: {
          data: [{ attendeeEmail: 'att1@test.ch' }, { attendeeEmail: 'att2@test.ch' }],
          pagination: { totalPages: 1, page: 0 },
        },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('batbern58@batbern.ch');
    expect(result).toEqual(['att1@test.ch', 'att2@test.ch']);
  });

  test('should_paginateRegistrants_when_multiplePages', async () => {
    mockFetch({
      'page=0': {
        status: 200,
        body: {
          data: [{ attendeeEmail: 'att1@test.ch' }],
          pagination: { totalPages: 2, page: 0 },
        },
      },
      'page=1': {
        status: 200,
        body: {
          data: [{ attendeeEmail: 'att2@test.ch' }],
          pagination: { totalPages: 2, page: 1 },
        },
      },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('batbern58@batbern.ch');
    expect(result).toEqual(['att1@test.ch', 'att2@test.ch']);
  });

  test('should_returnEmpty_when_eventNotFound', async () => {
    mockFetch({
      'events/BATbern999/registrations': { status: 404, body: {} },
    });
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('batbern999@batbern.ch');
    expect(result).toEqual([]);
  });

  test('should_returnEmpty_when_unknownAddress', async () => {
    const { resolveRecipients } = await import('../../lambda/email-forwarder/address-resolver');
    const result = await resolveRecipients('random@batbern.ch');
    expect(result).toEqual([]);
  });
});

// ========================
// T8: Sender Authorization Tests
// ========================

describe('T8 — Sender authorization', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    jest.resetModules();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  function mockOrganizerFetch(emails: string[]): void {
    global.fetch = jest.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ data: emails.map((e) => ({ email: e })) }),
    })) as jest.Mock;
  }

  test('should_allowOrganizer_when_sendingToOk', async () => {
    mockOrganizerFetch(['org@test.ch']);
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('ok@batbern.ch', 'org@test.ch');
    expect(result).toBe(true);
  });

  test('should_rejectNonOrganizer_when_sendingToOk', async () => {
    mockOrganizerFetch(['org@test.ch']);
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('ok@batbern.ch', 'random@test.ch');
    expect(result).toBe(false);
  });

  test('should_rejectNonOrganizer_when_sendingToPartner', async () => {
    mockOrganizerFetch(['org@test.ch']);
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('partner@batbern.ch', 'random@test.ch');
    expect(result).toBe(false);
  });

  test('should_rejectNonOrganizer_when_sendingToBatbern58', async () => {
    mockOrganizerFetch(['org@test.ch']);
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('batbern58@batbern.ch', 'random@test.ch');
    expect(result).toBe(false);
  });

  test('should_allowAnyone_when_sendingToInfo', async () => {
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('info@batbern.ch', 'anyone@test.ch');
    expect(result).toBe(true);
  });

  test('should_allowAnyone_when_sendingToEvents', async () => {
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('events@batbern.ch', 'anyone@test.ch');
    expect(result).toBe(true);
  });

  test('should_allowAnyone_when_sendingToSupport', async () => {
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    const result = await isAuthorizedSender('support@batbern.ch', 'anyone@test.ch');
    expect(result).toBe(true);
  });

  test('should_cacheOrganizerList_when_calledTwiceWithinTtl', async () => {
    mockOrganizerFetch(['org@test.ch']);
    const { isAuthorizedSender, resetCache } = await import('../../lambda/email-forwarder/sender-auth');
    resetCache();
    await isAuthorizedSender('ok@batbern.ch', 'org@test.ch');
    await isAuthorizedSender('ok@batbern.ch', 'org@test.ch');
    // Should only call fetch once (cached)
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });
});

// ========================
// T9: Email Rewriting Tests
// ========================

import { rewriteEmail } from '../../lambda/email-forwarder/email-rewriter';

describe('T9 — Email rewriting and forwarding', () => {
  const sampleEmail = [
    'From: John Doe <john@example.com>',
    'To: ok@batbern.ch',
    'Subject: Test forwarding',
    'Content-Type: text/plain',
    '',
    'Hello, this is a test.',
  ].join('\r\n');

  const rewriteOptions = {
    originalFrom: 'John Doe <john@example.com>',
    senderName: 'John Doe',
    senderEmail: 'john@example.com',
    sesSender: 'noreply@batbern.ch',
  };

  test('should_rewriteFrom_when_forwarding', () => {
    const result = rewriteEmail(sampleEmail, rewriteOptions);
    expect(result).toContain('From: "John Doe via BATbern" <noreply@batbern.ch>');
    expect(result).not.toContain('From: John Doe <john@example.com>');
  });

  test('should_addReplyTo_when_forwarding', () => {
    const result = rewriteEmail(sampleEmail, rewriteOptions);
    expect(result).toContain('Reply-To: john@example.com');
  });

  test('should_preserveSubject_when_forwarding', () => {
    const result = rewriteEmail(sampleEmail, rewriteOptions);
    expect(result).toContain('Subject: Test forwarding');
  });

  test('should_preserveBody_when_forwarding', () => {
    const result = rewriteEmail(sampleEmail, rewriteOptions);
    expect(result).toContain('Hello, this is a test.');
  });

  test('should_replaceExistingReplyTo_when_alreadyPresent', () => {
    const emailWithReplyTo = sampleEmail.replace(
      'Content-Type: text/plain',
      'Reply-To: original@example.com\r\nContent-Type: text/plain',
    );
    const result = rewriteEmail(emailWithReplyTo, rewriteOptions);
    expect(result).toContain('Reply-To: john@example.com');
    expect(result).not.toContain('Reply-To: original@example.com');
  });

  test('should_stripReturnPath_when_forwarding', () => {
    const emailWithReturnPath = 'Return-Path: <bounce@example.com>\r\n' + sampleEmail;
    const result = rewriteEmail(emailWithReturnPath, rewriteOptions);
    expect(result).not.toContain('Return-Path:');
  });

  test('should_stripDkimSignature_when_forwarding', () => {
    const emailWithDkim = 'DKIM-Signature: v=1; a=rsa-sha256; d=example.com\r\n' + sampleEmail;
    const result = rewriteEmail(emailWithDkim, rewriteOptions);
    expect(result).not.toContain('DKIM-Signature:');
  });
});
