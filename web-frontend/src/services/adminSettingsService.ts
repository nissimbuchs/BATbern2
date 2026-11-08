import apiClient from './api/apiClient';
import type { components } from '@/types/generated/event-app-settings-api.types';

// Wire type — generated from docs/api/event-app-settings-api.openapi.yml.
export type FeatureFlagsResponse = components['schemas']['FeatureFlagsResponse'];

export interface AdminSetting {
  key: string;
  value: string | null;
}

export const getAdminSetting = async (key: string): Promise<AdminSetting> => {
  const response = await apiClient.get<AdminSetting>(`/admin/settings/${key}`);
  return response.data;
};

export const updateAdminSetting = async (key: string, value: string): Promise<AdminSetting> => {
  const response = await apiClient.put<AdminSetting>(`/admin/settings/${key}`, { value });
  return response.data;
};

/** Public runtime feature flags (no role needed). */
export const getFeatureFlags = async (): Promise<FeatureFlagsResponse> => {
  const response = await apiClient.get<FeatureFlagsResponse>('/public/settings/features');
  return response.data;
};

export const adminSettingsService = {
  getAdminSetting,
  updateAdminSetting,
  getFeatureFlags,
};
