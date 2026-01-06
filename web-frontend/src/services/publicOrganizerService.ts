/**
 * Public Organizer Service
 * API calls for fetching organizer information (no authentication required)
 */

import apiClient from './api/apiClient';
import type { User } from '@/types/user.types';

/**
 * Get all organizers (public information only)
 * No authentication required
 *
 * @returns Promise<User[]> List of organizers with public information
 */
export const getPublicOrganizers = async (): Promise<User[]> => {
  const response = await apiClient.get<User[]>('/public/organizers', {
    headers: {
      // Public endpoint - skip auth header (prevents 401 if expired token exists)
      'Skip-Auth': 'true',
    },
  });
  return response.data;
};

export const publicOrganizerService = {
  getPublicOrganizers,
};
