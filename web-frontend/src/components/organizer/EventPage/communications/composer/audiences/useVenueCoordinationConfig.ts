import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAdminSetting } from '@/services/adminSettingsService';
import {
  VENUE_COORDINATION_CONFIG_KEY,
  type VenueCoordinationConfig,
} from '@/components/organizer/Admin/VenueCateringContactsTab';

export type VenueRecipientRole = 'VENUE' | 'CATERING';

function parseConfig(raw: string | null | undefined): VenueCoordinationConfig | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as VenueCoordinationConfig;
  } catch {
    return null;
  }
}

/** The venue coordination config and which parts of it are filled in. */
export function useVenueCoordinationConfig() {
  const query = useQuery({
    queryKey: ['admin-settings', VENUE_COORDINATION_CONFIG_KEY],
    queryFn: () => getAdminSetting(VENUE_COORDINATION_CONFIG_KEY),
  });
  const config = useMemo(() => parseConfig(query.data?.value), [query.data]);
  const venueConfigured = Boolean(config?.venue?.name && config?.venue?.email);
  const cateringConfigured = Boolean(config?.catering?.name && config?.catering?.email);
  const coordinatorConfigured = Boolean(config?.coordinatorUsername);
  return {
    isLoading: query.isLoading,
    config,
    venueConfigured,
    cateringConfigured,
    fullyConfigured: venueConfigured && cateringConfigured && coordinatorConfigured,
  };
}
