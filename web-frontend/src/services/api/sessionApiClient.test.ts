/**
 * Session API Client Tests (Story BAT-17)
 *
 * Tests for session management HTTP client:
 * - updateSession, associateMaterials, getMaterialDownloadUrl
 * - deleteSession, assignSpeaker, removeSpeaker
 * - Error handling including 401/403 passthrough
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AxiosError } from 'axios';
import { sessionApiClient } from './sessionApiClient';
import type { SessionUpdateRequest, AssignSpeakerRequest } from './sessionApiClient';
import apiClient from './apiClient';

vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

// Helper to create a realistic AxiosError-like object
function makeAxiosError(status: number, message = 'Error'): AxiosError {
  const err = new AxiosError(message);
  Object.defineProperty(err, 'isAxiosError', { value: true, writable: false });
  err.response = {
    status,
    data: { message, error: message },
    headers: { 'x-correlation-id': 'corr-99' },
    statusText: String(status),
    config: {} as AxiosError['config'],
  };
  return err;
}

describe('sessionApiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── updateSession ─────────────────────────────────────────────────────────────

  describe('updateSession', () => {
    it('should PATCH session and return updated session', async () => {
      const mockSession = { slug: 'session-1', title: 'Updated Title', durationMinutes: 45 };
      mockApiClient.patch.mockResolvedValue({ data: mockSession });

      const updates: SessionUpdateRequest = { title: 'Updated Title', durationMinutes: 45 };
      const result = await sessionApiClient.updateSession('BATbern142', 'session-1', updates);

      expect(mockApiClient.patch).toHaveBeenCalledWith(
        '/events/BATbern142/sessions/session-1',
        updates
      );
      expect(result.title).toBe('Updated Title');
    });

    it('should re-throw 401 errors without transformation', async () => {
      const authError = makeAxiosError(401);
      mockApiClient.patch.mockRejectedValue(authError);

      await expect(
        sessionApiClient.updateSession('BATbern142', 'session-1', {})
      ).rejects.toBeInstanceOf(AxiosError);
    });

    it('should re-throw 403 errors without transformation', async () => {
      const authError = makeAxiosError(403);
      mockApiClient.patch.mockRejectedValue(authError);

      await expect(
        sessionApiClient.updateSession('BATbern142', 'session-1', {})
      ).rejects.toBeInstanceOf(AxiosError);
    });

    it('should transform non-auth errors', async () => {
      mockApiClient.patch.mockRejectedValue(makeAxiosError(500));

      await expect(sessionApiClient.updateSession('BATbern142', 'session-1', {})).rejects.toThrow(
        'Error (Correlation ID: corr-99)'
      );
    });

    it('should handle 404 with Event in message', async () => {
      const err = makeAxiosError(404, 'Event not found');
      mockApiClient.patch.mockRejectedValue(err);

      await expect(sessionApiClient.updateSession('NOTFOUND', 'session-1', {})).rejects.toThrow(
        'Event not found'
      );
    });

    it('should handle 404 with Session in message', async () => {
      const err = makeAxiosError(404, 'Session not found');
      mockApiClient.patch.mockRejectedValue(err);

      await expect(sessionApiClient.updateSession('BATbern142', 'no-session', {})).rejects.toThrow(
        'Session not found'
      );
    });
  });

  // ── associateMaterials ────────────────────────────────────────────────────────

  describe('associateMaterials', () => {
    it('should POST materials and return created list', async () => {
      const mockMaterials = [{ id: 'mat-1', fileName: 'slides.pdf', materialType: 'PRESENTATION' }];
      mockApiClient.post.mockResolvedValue({ data: mockMaterials });

      const result = await sessionApiClient.associateMaterials('BATbern142', 'session-1', {
        materials: [
          {
            uploadId: 'up-1',
            materialType: 'PRESENTATION',
            fileName: 'slides.pdf',
            fileExtension: 'pdf',
            fileSize: 500_000,
            mimeType: 'application/pdf',
          },
        ],
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/events/BATbern142/sessions/session-1/materials',
        expect.objectContaining({
          materials: expect.arrayContaining([expect.objectContaining({ uploadId: 'up-1' })]),
        })
      );
      expect(result[0].fileName).toBe('slides.pdf');
    });

    it('should re-throw 401 errors', async () => {
      mockApiClient.post.mockRejectedValue(makeAxiosError(401));

      await expect(
        sessionApiClient.associateMaterials('BATbern142', 'session-1', { materials: [] })
      ).rejects.toBeInstanceOf(AxiosError);
    });
  });

  // ── getMaterialDownloadUrl ────────────────────────────────────────────────────

  describe('getMaterialDownloadUrl', () => {
    it('should return download URL object', async () => {
      mockApiClient.get.mockResolvedValue({ data: { downloadUrl: 'https://s3.example.com/file' } });

      const result = await sessionApiClient.getMaterialDownloadUrl(
        'BATbern142',
        'session-1',
        'mat-uuid'
      );

      expect(mockApiClient.get).toHaveBeenCalledWith(
        '/events/BATbern142/sessions/session-1/materials/mat-uuid/download'
      );
      expect(result.downloadUrl).toBe('https://s3.example.com/file');
    });

    it('should transform errors', async () => {
      mockApiClient.get.mockRejectedValue(makeAxiosError(404, 'Session not found'));

      await expect(
        sessionApiClient.getMaterialDownloadUrl('BATbern142', 'no-session', 'mat')
      ).rejects.toThrow('Session not found');
    });
  });

  // ── deleteSession ─────────────────────────────────────────────────────────────

  describe('deleteSession', () => {
    it('should DELETE session', async () => {
      mockApiClient.delete.mockResolvedValue({ data: undefined });

      await sessionApiClient.deleteSession('BATbern142', 'session-1');

      expect(mockApiClient.delete).toHaveBeenCalledWith('/events/BATbern142/sessions/session-1');
    });

    it('should re-throw 403 errors', async () => {
      mockApiClient.delete.mockRejectedValue(makeAxiosError(403));

      await expect(
        sessionApiClient.deleteSession('BATbern142', 'session-1')
      ).rejects.toBeInstanceOf(AxiosError);
    });

    it('should transform 500 errors', async () => {
      const err = makeAxiosError(500, 'Internal error');
      mockApiClient.delete.mockRejectedValue(err);

      await expect(sessionApiClient.deleteSession('BATbern142', 'session-1')).rejects.toThrow();
    });
  });

  // ── assignSpeaker ─────────────────────────────────────────────────────────────

  describe('assignSpeaker', () => {
    it('should POST speaker assignment and return SessionSpeaker', async () => {
      const mockSpeaker = { username: 'alice', speakerRole: 'PRIMARY_SPEAKER' };
      mockApiClient.post.mockResolvedValue({ data: mockSpeaker });

      const request: AssignSpeakerRequest = {
        username: 'alice',
        speakerRole: 'PRIMARY_SPEAKER',
      };
      const result = await sessionApiClient.assignSpeaker('BATbern142', 'session-1', request);

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/events/BATbern142/sessions/session-1/speakers',
        request
      );
      expect(result.username).toBe('alice');
    });

    it('should re-throw 401 errors', async () => {
      mockApiClient.post.mockRejectedValue(makeAxiosError(401));

      await expect(
        sessionApiClient.assignSpeaker('BATbern142', 'session-1', {
          username: 'alice',
          speakerRole: 'PRIMARY_SPEAKER',
        })
      ).rejects.toBeInstanceOf(AxiosError);
    });

    it('should transform non-auth errors', async () => {
      mockApiClient.post.mockRejectedValue(makeAxiosError(404, 'Session not found'));

      await expect(
        sessionApiClient.assignSpeaker('BATbern142', 'no-session', {
          username: 'alice',
          speakerRole: 'PRIMARY_SPEAKER',
        })
      ).rejects.toThrow('Session not found');
    });
  });

  // ── removeSpeaker ─────────────────────────────────────────────────────────────

  describe('removeSpeaker', () => {
    it('should DELETE speaker from session', async () => {
      mockApiClient.delete.mockResolvedValue({ data: undefined });

      await sessionApiClient.removeSpeaker('BATbern142', 'session-1', 'alice');

      expect(mockApiClient.delete).toHaveBeenCalledWith(
        '/events/BATbern142/sessions/session-1/speakers/alice'
      );
    });

    it('should re-throw 401 on auth error', async () => {
      mockApiClient.delete.mockRejectedValue(makeAxiosError(401));

      await expect(
        sessionApiClient.removeSpeaker('BATbern142', 'session-1', 'alice')
      ).rejects.toBeInstanceOf(AxiosError);
    });

    it('should transform plain Error', async () => {
      const err = new Error('plain error');
      mockApiClient.delete.mockRejectedValue(err);

      await expect(
        sessionApiClient.removeSpeaker('BATbern142', 'session-1', 'alice')
      ).rejects.toThrow('plain error');
    });
  });
});
