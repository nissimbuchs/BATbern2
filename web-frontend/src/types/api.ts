/**
 * API types for BATbern platform
 * Based on API Gateway standardized response format
 */

export interface StandardResponse<T = unknown> {
  success: boolean;
  data: T;
  error: ErrorInfo | null;
  metadata: Record<string, unknown>;
  requestId: string;
  timestamp: string;
}

export interface ErrorInfo {
  code: string;
  message: string;
  field?: string;
  details?: Record<string, unknown>;
}

export interface PaginationMetadata {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PaginatedResponse<T> extends StandardResponse<T[]> {
  metadata: PaginationMetadata & Record<string, unknown>;
}

export interface ApiError extends Error {
  code: string;
  statusCode: number;
  details?: Record<string, unknown>;
  requestId?: string;
}

// HTTP Client configuration
export interface ApiClientConfig {
  baseURL: string;
  timeout: number;
  retries: number;
  retryDelay: number;
  headers: Record<string, string>;
}

// Request/Response interceptors
export interface RequestInterceptor {
  (config: RequestConfig): RequestConfig | Promise<RequestConfig>;
}

export interface ResponseInterceptor {
  <T>(response: StandardResponse<T>): StandardResponse<T> | Promise<StandardResponse<T>>;
}

export interface ErrorInterceptor {
  (error: ApiError): Promise<ApiError>;
}

export interface RequestConfig {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  data?: unknown;
  params?: Record<string, string | number | boolean>;
  headers?: Record<string, string>;
  timeout?: number;
  retries?: number;
}
