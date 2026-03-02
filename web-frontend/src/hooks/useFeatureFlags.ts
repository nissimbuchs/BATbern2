import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/api/apiClient';

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
      const response = await apiClient.get<FeatureFlags>('/public/settings/features');
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes — feature flags don't change often
    gcTime: 10 * 60 * 1000,
  });

  return data ?? DEFAULT_FLAGS;
}
