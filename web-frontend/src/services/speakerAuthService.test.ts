/**
 * speakerAuthService Tests
 *
 * Coverage for:
 * - validateMagicLink: POST /api/v1/auth/speaker-magic-login
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { speakerAuthService } from './speakerAuthService';

vi.mock('@/services/api/apiClient', () => ({
  default: { post: vi.fn() },
}));

import apiClient from './api/apiClient';

const mockPost = vi.mocked(apiClient.post);

const MOCK_RESPONSE = {
  speakerPoolId: 'sp-1',
  speakerName: 'Alice Smith',
  eventCode: 'BAT142',
  eventTitle: 'BATbern #142',
  sessionToken: 'tok-abc-123',
};

describe('speakerAuthService', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('validateMagicLink', () => {
    it('should POST JWT token to magic login endpoint', async () => {
      mockPost.mockResolvedValue({ data: MOCK_RESPONSE });

      const result = await speakerAuthService.validateMagicLink('eyJhbGc...');

      expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/speaker-magic-login', {
        jwtToken: 'eyJhbGc...',
      });
      expect(result).toEqual(MOCK_RESPONSE);
    });

    it('should return speaker context with session token', async () => {
      mockPost.mockResolvedValue({ data: MOCK_RESPONSE });

      const result = await speakerAuthService.validateMagicLink('valid-jwt');

      expect(result.sessionToken).toBe('tok-abc-123');
      expect(result.eventCode).toBe('BAT142');
      expect(result.speakerName).toBe('Alice Smith');
    });

    it('should propagate errors for invalid tokens', async () => {
      mockPost.mockRejectedValue(new Error('Unauthorized'));

      await expect(speakerAuthService.validateMagicLink('bad-token')).rejects.toThrow(
        'Unauthorized'
      );
    });

    it('should propagate 410 Gone for expired tokens', async () => {
      mockPost.mockRejectedValue(new Error('Gone'));

      await expect(speakerAuthService.validateMagicLink('expired-jwt')).rejects.toThrow('Gone');
    });
  });
});
