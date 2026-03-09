/**
 * watchPairingApi Tests
 *
 * Coverage for Watch Pairing API (Story W2.1):
 * - generatePairingCode: POST
 * - getPairingStatus: GET
 * - unpairWatch: DELETE
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import watchPairingApi from './watchPairingApi';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

const mockPost = vi.mocked(apiClient.post);
const mockGet = vi.mocked(apiClient.get);
const mockDelete = vi.mocked(apiClient.delete);

describe('watchPairingApi', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('generatePairingCode', () => {
    it('should POST to user watch-pairing endpoint', async () => {
      const response = { pairingCode: 'ABC-123', expiresAt: '2026-03-07T22:00:00Z' };
      mockPost.mockResolvedValue({ data: response });

      const result = await watchPairingApi.generatePairingCode('alice');

      expect(mockPost).toHaveBeenCalledWith('/users/alice/watch-pairing');
      expect(result).toEqual(response);
    });

    it('should propagate errors', async () => {
      mockPost.mockRejectedValue(new Error('Conflict'));

      await expect(watchPairingApi.generatePairingCode('alice')).rejects.toThrow('Conflict');
    });
  });

  describe('getPairingStatus', () => {
    it('should GET watch-pairing status for user', async () => {
      const response = { paired: true, watchId: 'watch-xyz', pairedAt: '2026-03-07T20:00:00Z' };
      mockGet.mockResolvedValue({ data: response });

      const result = await watchPairingApi.getPairingStatus('alice');

      expect(mockGet).toHaveBeenCalledWith('/users/alice/watch-pairing');
      expect(result).toEqual(response);
    });

    it('should return unpaired status', async () => {
      mockGet.mockResolvedValue({ data: { paired: false } });

      const result = await watchPairingApi.getPairingStatus('bob');

      expect(result).toEqual({ paired: false });
    });
  });

  describe('unpairWatch', () => {
    it('should DELETE the watch pairing', async () => {
      mockDelete.mockResolvedValue({});

      await watchPairingApi.unpairWatch('alice', 'watch-xyz');

      expect(mockDelete).toHaveBeenCalledWith('/users/alice/watch-pairing/watch-xyz');
    });

    it('should resolve to undefined on success', async () => {
      mockDelete.mockResolvedValue({ data: null });

      const result = await watchPairingApi.unpairWatch('alice', 'watch-xyz');

      expect(result).toBeUndefined();
    });

    it('should propagate 404 errors', async () => {
      mockDelete.mockRejectedValue(new Error('Not Found'));

      await expect(watchPairingApi.unpairWatch('alice', 'unknown-watch')).rejects.toThrow(
        'Not Found'
      );
    });
  });
});
