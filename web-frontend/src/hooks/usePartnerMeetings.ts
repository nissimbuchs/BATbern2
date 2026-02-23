/**
 * usePartnerMeetings Hook
 *
 * Story 8.3: Partner Meeting Coordination — Task 1b
 *
 * React Query hook for fetching partner meetings.
 * Used by PartnerMeetingsTab in the organizer detail view
 * and the partner portal company view (Story 8.0 integration).
 *
 * Cache: 5 minutes (read-only for partners; organizer invalidates on mutation)
 * AC: 5 (Meeting List)
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { getMeetings, type PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

/**
 * usePartnerMeetings — Fetch all partner meetings (ORGANIZER role required).
 *
 * Note: Partner meetings are global (one per BATbern event, all partners invited).
 * The optional companyName param is accepted for call-site compatibility but is not
 * used for filtering — all meetings are returned regardless.
 *
 * @returns UseQueryResult with array of PartnerMeetingDTO sorted by date descending
 */
export const usePartnerMeetings = (
  companyName?: string
): UseQueryResult<PartnerMeetingDTO[], Error> => {
  return useQuery({
    // Include companyName in key only when provided (stable key for PartnerMeetingsTab usage)
    queryKey: companyName ? ['partnerMeetings', companyName] : ['partnerMeetings'],
    queryFn: getMeetings,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
