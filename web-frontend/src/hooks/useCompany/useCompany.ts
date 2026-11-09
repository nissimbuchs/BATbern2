/**
 * useCompany Hook
 *
 * React Query hook for fetching single company detail (the logo is always included)
 * AC 8 (Company Detail View), AC 10 (Performance), AC 15 (Service Integration)
 */

import { useQuery } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';

/**
 * Fetch single company by name
 *
 * Features:
 * - 10 minute stale time (AC 10 - Performance)
 * - The logo is always part of the response (GET /companies/{name} has no expansion parameter)
 * - Disabled query when name is empty/undefined
 * - One cache entry per company name
 *
 * Story 1.16.2: Uses company name as identifier instead of UUID
 *
 * @param name - Company name (unique identifier)
 * @returns React Query result with company detail data
 */
export const useCompany = (name: string) => {
  return useQuery({
    // GET /companies/{name} always includes the logo and takes no query parameter (17.0c1).
    queryKey: ['company', name],
    queryFn: () => companyApiClient.getCompany(name),
    staleTime: 10 * 60 * 1000, // 10 minutes (AC 10 - Performance requirement)
    gcTime: 15 * 60 * 1000, // 15 minutes garbage collection
    enabled: !!name && name.trim().length > 0, // Only fetch when valid name provided
    refetchOnWindowFocus: true, // AC 14 - State Management requirement
  });
};
