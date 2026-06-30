import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-app-settings-api.types';

// Wire type — generated from docs/api/event-app-settings-api.openapi.yml (single source of truth).
type FeatureFlagsResponse = components['schemas']['FeatureFlagsResponse'];

// Normalized, all-fields-present shape the app consumes (derived from the wire type, so it
// cannot drift from the contract).
interface FeatureFlags {
  aiContentEnabled: boolean;
}

const DEFAULT_FLAGS: FeatureFlags = {
  aiContentEnabled: false,
};

export function useFeatureFlags(): FeatureFlags {
  const { data } = useQuery<FeatureFlags>({
    queryKey: ['feature-flags'],
    queryFn: async () => {
      const response = await apiClient.get<FeatureFlagsResponse>('/public/settings/features');
      return { aiContentEnabled: response.data.aiContentEnabled ?? false };
    },
    staleTime: 5 * 60 * 1000, // 5 minutes — feature flags don't change often
    gcTime: 10 * 60 * 1000,
  });

  return data ?? DEFAULT_FLAGS;
}
