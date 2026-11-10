import { useQuery } from '@tanstack/react-query';
import { getFeatureFlags } from '@/services/adminSettingsService';

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
      const flags = await getFeatureFlags();
      return { aiContentEnabled: flags.aiContentEnabled };
    },
    staleTime: 5 * 60 * 1000, // 5 minutes — feature flags don't change often
    gcTime: 10 * 60 * 1000,
  });

  return data ?? DEFAULT_FLAGS;
}
