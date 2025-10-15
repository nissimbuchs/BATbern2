/**
 * useCompanySearch Hook
 *
 * React Query hook for company autocomplete search with debouncing
 * AC 2 (Search & Filters), AC 10 (Performance <500ms), AC 15 (Service Integration)
 */

import { useQuery } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';
import type { PaginationParams } from '@/types/company.types';

/**
 * Search companies by query string (autocomplete)
 *
 * Features:
 * - 15 minute stale time (AC 10 - Caffeine-cached backend search)
 * - Minimum 3 characters required (AC 2 - Search requirement)
 * - Disabled query when query too short
 * - Default limit of 10 results for autocomplete
 *
 * @param query - Search query string
 * @param pagination - Optional pagination params (defaults to page 1, limit 10)
 * @returns React Query result with search results
 */
export const useCompanySearch = (
  query: string,
  pagination: PaginationParams = { page: 1, limit: 10 }
) => {
  const trimmedQuery = query?.trim() || '';
  const isQueryValid = trimmedQuery.length >= 3;

  return useQuery({
    queryKey: ['companySearch', trimmedQuery, pagination],
    queryFn: () => companyApiClient.searchCompanies(trimmedQuery, pagination),
    staleTime: 15 * 60 * 1000, // 15 minutes (AC 10 - Backend cached for 15min)
    gcTime: 20 * 60 * 1000, // 20 minutes garbage collection
    enabled: isQueryValid, // Only search with 3+ characters (AC 2 requirement)
    refetchOnWindowFocus: false, // Don't refetch autocomplete on focus
  });
};
