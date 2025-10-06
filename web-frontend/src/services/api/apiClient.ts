/**
 * API Client Configuration
 * Story 1.2.1: HTTP Client with i18n Support
 */

import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import i18n from '@/i18n/config';

/**
 * Create axios instance with default configuration
 */
const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Request interceptor to add Accept-Language header
 */
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add Accept-Language header based on current i18n language
    config.headers['Accept-Language'] = i18n.language;

    // Add authentication token if available
    const token = sessionStorage.getItem('accessToken') || localStorage.getItem('accessToken');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Response interceptor for error handling
 */
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle common HTTP errors
    if (error.response) {
      const { status } = error.response;

      switch (status) {
        case 401:
          // Unauthorized - redirect to login
          window.location.href = '/login';
          break;
        case 403:
          // Forbidden - insufficient permissions
          console.error('Forbidden: Insufficient permissions');
          break;
        case 500:
          // Server error
          console.error('Server error occurred');
          break;
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
