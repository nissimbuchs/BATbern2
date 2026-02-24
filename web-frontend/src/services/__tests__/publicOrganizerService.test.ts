/**
 * Public Organizer Service Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getPublicOrganizers, publicOrganizerService } from '../publicOrganizerService';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

describe('publicOrganizerService', () => {
  const mockGet = vi.mocked(apiClient.get);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getPublicOrganizers', () => {
    const mockOrganizers = [
      { username: 'alice', firstName: 'Alice', lastName: 'Smith', role: 'ORGANIZER' },
      { username: 'bob', firstName: 'Bob', lastName: 'Jones', role: 'ORGANIZER' },
    ];

    it('should call the public organizers endpoint with Skip-Auth header', async () => {
      mockGet.mockResolvedValueOnce({ data: mockOrganizers });

      const result = await getPublicOrganizers();

      expect(mockGet).toHaveBeenCalledWith('/public/organizers', {
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result).toEqual(mockOrganizers);
    });

    it('should be accessible via publicOrganizerService object', async () => {
      mockGet.mockResolvedValueOnce({ data: mockOrganizers });

      const result = await publicOrganizerService.getPublicOrganizers();

      expect(result).toEqual(mockOrganizers);
    });

    it('should pass through errors from the API', async () => {
      mockGet.mockRejectedValueOnce(new Error('Unauthorized'));

      await expect(getPublicOrganizers()).rejects.toThrow('Unauthorized');
    });
  });
});
