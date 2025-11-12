/**
 * usePartnerMeetings Hook
 *
 * React Query hook for fetching partner meetings
 * Story 2.8.2: Partner Detail View
 *
 * Features:
 * - Fetches partner meetings for Meetings tab
 * - 2-minute cache for meeting data (more volatile)
 * - Lazy loading (only fetches when tab activated)
 *
 * AC: 5 (Meetings Tab), 13 (Integration Tests)
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
// import { getPartnerMeetings } from '@/services/api/partnerApi';
// import type { MeetingResponse } from '@/services/api/partnerApi';

// TODO: Remove stub when meetings API is implemented
interface MeetingResponse {
  id: string;
  title: string;
  date: string;
}

/**
 * usePartnerMeetings - Fetch partner meetings
 *
 * @param companyName - Company name (meaningful ID)
 * @returns UseQueryResult with array of meetings
 *
 * Cache: 2 minutes (meeting data is more volatile)
 */
export const usePartnerMeetings = (
  companyName: string
): UseQueryResult<MeetingResponse[], Error> => {
  return useQuery({
    queryKey: ['partner', companyName, 'meetings'],
    queryFn: async () => [], // TODO: Replace with getPartnerMeetings(companyName) when API implemented
    enabled: !!companyName,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });
};
