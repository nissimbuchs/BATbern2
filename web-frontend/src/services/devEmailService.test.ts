/**
 * devEmailService Tests
 *
 * Coverage for:
 * - fetchAll: aggregates from EMS + PCS, merged newest-first
 * - clearAll: DELETE on both services
 * - replyToEmail: POST /dev/emails/{id}/reply
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { devEmailService } from './devEmailService';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const EMS_EMAIL = {
  id: 'ems-1',
  to: 'alice@batbern.ch',
  subject: 'Speaker Invitation',
  htmlBody: '<p>Hello</p>',
  fromEmail: 'noreply@batbern.ch',
  fromName: 'BATbern Team',
  capturedAt: '2025-12-01T10:00:00Z',
  attachments: [],
};

const PCS_EMAIL = {
  id: 'pcs-1',
  to: 'partner@company.com',
  subject: 'Einladung: BATbern Partner-Meeting',
  htmlBody: '<p>Liebe Partner</p>',
  fromEmail: 'noreply@batbern.ch',
  fromName: 'BATbern Team',
  capturedAt: '2025-12-01T12:00:00Z', // newer
  attachments: [{ filename: 'partner-meeting.ics', mimeType: 'text/calendar', sizeBytes: 512 }],
};

describe('devEmailService', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('fetchAll', () => {
    it('merges emails from EMS and PCS, sorted newest first', async () => {
      mockFetch
        .mockResolvedValueOnce({ ok: true, json: async () => [EMS_EMAIL] }) // EMS
        .mockResolvedValueOnce({ ok: true, json: async () => [PCS_EMAIL] }); // PCS

      const result = await devEmailService.fetchAll();

      expect(mockFetch).toHaveBeenCalledTimes(2);
      expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('8002/dev/emails'));
      expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('8004/dev/emails'));
      // PCS email is newer → should come first
      expect(result[0].id).toBe('pcs-1');
      expect(result[1].id).toBe('ems-1');
    });

    it('returns only EMS emails when PCS is unavailable', async () => {
      mockFetch
        .mockResolvedValueOnce({ ok: true, json: async () => [EMS_EMAIL] }) // EMS ok
        .mockRejectedValueOnce(new Error('Connection refused')); // PCS down

      const result = await devEmailService.fetchAll();

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('ems-1');
    });

    it('returns only PCS emails when EMS returns non-ok', async () => {
      mockFetch
        .mockResolvedValueOnce({ ok: false, status: 503 }) // EMS unhealthy
        .mockResolvedValueOnce({ ok: true, json: async () => [PCS_EMAIL] }); // PCS ok

      const result = await devEmailService.fetchAll();

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('pcs-1');
    });

    it('returns empty array when both services are unavailable', async () => {
      mockFetch.mockRejectedValue(new Error('Connection refused'));

      const result = await devEmailService.fetchAll();

      expect(result).toEqual([]);
    });

    it('returns empty array when both services return empty', async () => {
      mockFetch
        .mockResolvedValueOnce({ ok: true, json: async () => [] })
        .mockResolvedValueOnce({ ok: true, json: async () => [] });

      const result = await devEmailService.fetchAll();

      expect(result).toEqual([]);
    });
  });

  describe('clearAll', () => {
    it('sends DELETE to both EMS and PCS', async () => {
      mockFetch.mockResolvedValue({ ok: true, status: 204 });

      await devEmailService.clearAll();

      expect(mockFetch).toHaveBeenCalledTimes(2);
      expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('8002/dev/emails'), {
        method: 'DELETE',
      });
      expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('8004/dev/emails'), {
        method: 'DELETE',
      });
    });

    it('resolves even when PCS is unavailable', async () => {
      mockFetch
        .mockResolvedValueOnce({ ok: true, status: 204 }) // EMS ok
        .mockRejectedValueOnce(new Error('Connection refused')); // PCS down

      await expect(devEmailService.clearAll()).resolves.toBeUndefined();
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
