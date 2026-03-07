/**
 * publicOrganizerService Tests
 *
 * Coverage for public organizer service (no-auth endpoint):
 * - getPublicOrganizers: GET with Skip-Auth header
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getPublicOrganizers, publicOrganizerService } from './publicOrganizerService';

vi.mock('@/services/api/apiClient', () => ({
  default: { get: vi.fn() },
}));

import apiClient from './api/apiClient';

const mockGet = vi.mocked(apiClient.get);

const MOCK_ORGANIZERS = [
  { id: '1', firstName: 'Alice', lastName: 'Smith', email: 'alice@batbern.ch' },
  { id: '2', firstName: 'Bob', lastName: 'Jones', email: 'bob@batbern.ch' },
];

describe('getPublicOrganizers', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should GET /public/organizers with Skip-Auth header', async () => {
    mockGet.mockResolvedValue({ data: MOCK_ORGANIZERS });

    const result = await getPublicOrganizers();

    expect(mockGet).toHaveBeenCalledWith('/public/organizers', {
      headers: { 'Skip-Auth': 'true' },
    });
    expect(result).toEqual(MOCK_ORGANIZERS);
  });

  it('should return empty array when no organizers', async () => {
    mockGet.mockResolvedValue({ data: [] });

    const result = await getPublicOrganizers();

    expect(result).toEqual([]);
  });

  it('should propagate API errors', async () => {
    mockGet.mockRejectedValue(new Error('Service unavailable'));

    await expect(getPublicOrganizers()).rejects.toThrow('Service unavailable');
  });

  it('should be accessible via publicOrganizerService object', async () => {
    mockGet.mockResolvedValue({ data: MOCK_ORGANIZERS });

    const result = await publicOrganizerService.getPublicOrganizers();

    expect(result).toEqual(MOCK_ORGANIZERS);
  });
});
