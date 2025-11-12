/**
 * usePublicPartners Hook
 * Fetches active partners for public homepage showcase
 * No authentication required
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';
import type { PartnerListResponse } from '@/services/api/partnerApi';

/**
 * Fetch active partners for public display
 * Includes company enrichment for logos and websites
 */
const fetchPublicPartners = async (): Promise<PartnerListResponse> => {
  const response = await apiClient.get<PartnerListResponse>('/partners', {
    params: {
      page: 0,
      size: 100, // Get all partners for showcase
      include: 'company', // Need logos and websites
      filter: 'isActive:true', // Only active partners
    },
    headers: {
      // Skip auth for public endpoint - prevents 401 with expired tokens
      'Skip-Auth': 'true',
    },
  });

  return response.data;
};

/**
 * Hook for fetching public partners (no auth required)
 * Cached for 5 minutes - partners don't change frequently
 */
export const usePublicPartners = (): UseQueryResult<PartnerListResponse, Error> => {
  return useQuery({
    queryKey: ['public-partners'],
    queryFn: fetchPublicPartners,
    staleTime: 5 * 60 * 1000, // 5 minutes
    refetchOnWindowFocus: false, // Don't refetch on focus for public page
  });
};
