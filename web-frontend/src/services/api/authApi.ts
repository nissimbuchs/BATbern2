/**
 * Authentication API Service
 * Story 1.17: Type-safe API client wrappers for auth endpoints
 *
 * Endpoints:
 * - POST /api/v1/auth/logout
 */

import apiClient from './apiClient';

export interface LogoutRequest {
  refreshToken: string;
}

export interface LogoutResponse {
  success: boolean;
}

/**
 * Logout user and invalidate refresh token
 * @param refreshToken - Refresh token to revoke
 * @returns Logout confirmation
 */
export const logout = async (refreshToken: string): Promise<LogoutResponse> => {
  const response = await apiClient.post<LogoutResponse>('/api/v1/auth/logout', { refreshToken });
  return response.data;
};
