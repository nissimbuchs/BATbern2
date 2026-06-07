import apiClient from './api/apiClient';

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

export const adminSettingsService = {
  getAdminSetting,
  updateAdminSetting,
};
