/**
 * devEmailService Tests
 *
 * Coverage for:
 * - fetchAll: GET /dev/emails
 * - clearAll: DELETE /dev/emails
 * - replyToEmail: POST /dev/emails/{id}/reply
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { devEmailService } from './devEmailService';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const MOCK_EMAILS = [
  {
    id: 'email-1',
    to: 'alice@batbern.ch',
    subject: 'BATbern Invitation',
    htmlBody: '<p>Hello</p>',
    fromEmail: 'noreply@batbern.ch',
    fromName: 'BATbern Team',
    capturedAt: '2025-12-01T10:00:00Z',
    attachments: [],
  },
];

describe('devEmailService', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('fetchAll', () => {
    it('should GET /dev/emails and return parsed emails', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: async () => MOCK_EMAILS,
      });

      const result = await devEmailService.fetchAll();

      expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/dev/emails'));
      expect(result).toEqual(MOCK_EMAILS);
    });

    it('should return empty array when no emails', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: async () => [],
      });

      const result = await devEmailService.fetchAll();

      expect(result).toEqual([]);
    });

    it('should throw on non-ok response', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 503 });

      await expect(devEmailService.fetchAll()).rejects.toThrow('Failed to fetch emails: 503');
    });
  });

  describe('clearAll', () => {
    it('should DELETE /dev/emails', async () => {
      mockFetch.mockResolvedValue({ ok: true, status: 200 });

      await devEmailService.clearAll();

      expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/dev/emails'), {
        method: 'DELETE',
      });
    });

    it('should resolve successfully on 204 No Content', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 204 });

      await expect(devEmailService.clearAll()).resolves.toBeUndefined();
    });

    it('should throw on non-ok non-204 response', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 500 });

      await expect(devEmailService.clearAll()).rejects.toThrow('Failed to clear inbox: 500');
    });
  });

  describe('replyToEmail', () => {
    it('should POST to /dev/emails/{id}/reply with the reply body', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        text: async () => 'Reply routed: CANCEL',
      });

      const result = await devEmailService.replyToEmail('email-1', 'CANCEL');

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/dev/emails/email-1/reply'),
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ replyBody: 'CANCEL' }),
        })
      );
      expect(result).toBe('Reply routed: CANCEL');
    });

    it('should throw on non-ok response', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 404 });

      await expect(devEmailService.replyToEmail('unknown-id', 'CANCEL')).rejects.toThrow(
        'Reply failed: 404'
      );
    });
  });
});
