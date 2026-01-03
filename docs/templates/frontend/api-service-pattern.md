# API Service Pattern

**Category**: Frontend - API Integration
**Used in Stories**: 2.5.3 (Event Management Frontend)
**Last Updated**: 2025-12-20
**Source**: Extracted from Story 2.5.3

## Overview

Centralized API client service using Axios with request/response interceptors for authentication, error handling, and standardized API communication.

**Use this pattern when**:
- Creating API client services for domain entities
- Implementing authentication header injection
- Handling global API errors (401, 403, 404, 500)
- Building type-safe API methods with TypeScript
- Supporting resource expansion (`?include=` query parameter)
- Implementing filter, sort, and pagination parameters

## Prerequisites

```bash
npm install axios
npm install --save-dev @types/axios
```

## Implementation Steps

### Step 1: Create Base Axios Instance

```typescript
// src/services/api/{entity}ApiClient.ts
import axios, { AxiosError } from 'axios';
import type { Entity, EntityDetail, CreateEntityRequest, UpdateEntityRequest, EntityFilters } from '../types/entity.types';

// Create axios instance with base configuration
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,  // e.g., http://localhost:8080
  timeout: 10000,  // 10 second timeout
  headers: {
    'Content-Type': 'application/json',
  },
});
```

### Step 2: Add Request Interceptor for Authentication

```typescript
// Request interceptor - inject auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('authToken');  // or sessionStorage
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);
```

### Step 3: Add Response Interceptor for Error Handling

```typescript
// Response interceptor - handle global errors
apiClient.interceptors.response.use(
  (response) => response,  // Pass through successful responses
  (error: AxiosError) => {
    // Handle 401 Unauthorized - redirect to login
    if (error.response?.status === 401) {
      window.location.href = '/login';
    }

    // Handle 403 Forbidden - show permission error
    if (error.response?.status === 403) {
      console.error('Permission denied');
    }

    // Handle 404 Not Found
    if (error.response?.status === 404) {
      console.error('Resource not found');
    }

    // Handle 500 Internal Server Error
    if (error.response?.status === 500) {
      console.error('Server error');
    }

    return Promise.reject(error);
  }
);
```

### Step 4: Create API Methods

```typescript
export const entityApiClient = {
  /**
   * List entities with filters, sorting, and pagination
   */
  getEntities: async (
    filters: EntityFilters,
    page: number,
    limit: number
  ): Promise<{ data: Entity[]; pagination: any }> => {
    const params = new URLSearchParams();
    params.append('page', String(page));
    params.append('limit', String(limit));

    // Add filters conditionally
    if (filters.status) {
      params.append('status', filters.status);
    }
    if (filters.searchQuery) {
      params.append('search', filters.searchQuery);
    }

    const response = await apiClient.get(`/api/v1/entities?${params.toString()}`);
    return response.data;
  },

  /**
   * Get single entity by ID with optional resource expansion
   * @param id - Entity identifier
   * @param include - Optional array of related resources to include (e.g., ['workflow', 'users'])
   */
  getEntity: async (id: string, include?: string[]): Promise<EntityDetail> => {
    const params = include?.length ? `?include=${include.join(',')}` : '';
    const response = await apiClient.get(`/api/v1/entities/${id}${params}`);
    return response.data;
  },

  /**
   * Create new entity
   */
  createEntity: async (data: CreateEntityRequest): Promise<Entity> => {
    const response = await apiClient.post('/api/v1/entities', data);
    return response.data;
  },

  /**
   * Update entity (full update with PUT)
   */
  updateEntity: async (id: string, data: UpdateEntityRequest): Promise<Entity> => {
    const response = await apiClient.put(`/api/v1/entities/${id}`, data);
    return response.data;
  },

  /**
   * Partial update entity (PATCH for partial updates)
   */
  patchEntity: async (id: string, data: Partial<UpdateEntityRequest>): Promise<Entity> => {
    const response = await apiClient.patch(`/api/v1/entities/${id}`, data);
    return response.data;
  },

  /**
   * Delete entity
   */
  deleteEntity: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/v1/entities/${id}`);
  },

  /**
   * Get related resource (example: entity workflow)
   */
  getEntityWorkflow: async (id: string): Promise<any> => {
    const response = await apiClient.get(`/api/v1/entities/${id}/workflow`);
    return response.data;
  },
};
```

## Advanced Patterns

### Resource Expansion Pattern

Use `?include=` query parameter to fetch related resources in single API call (reduces API calls by 90%):

```typescript
// Without includes - 5 API calls
const entity = await getEntity('123');
const workflow = await getEntityWorkflow('123');
const users = await getEntityUsers('123');
const sessions = await getEntitySessions('123');

// With includes - 1 API call
const entityDetail = await getEntity('123', ['workflow', 'users', 'sessions', 'venue']);
```

### Filter Builder Pattern

```typescript
interface EntityFilters {
  status?: 'active' | 'inactive' | 'archived';
  year?: number;
  searchQuery?: string;
  tier?: string;
}

const buildQueryParams = (filters: EntityFilters): URLSearchParams => {
  const params = new URLSearchParams();

  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });

  return params;
};

// Usage
const params = buildQueryParams(filters);
const response = await apiClient.get(`/api/v1/entities?${params.toString()}`);
```

### Error Response Transformation

Transform backend error responses to user-friendly messages (Story 1.9 pattern):

```typescript
import type { ErrorResponse } from '../types/error.types';

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    const errorResponse = error.response?.data;

    // Extract correlation ID for debugging (Story 1.9)
    const correlationId = error.response?.headers['x-correlation-id'];

    // Map error codes to user-friendly messages
    const errorMessages: Record<string, string> = {
      ERR_VALIDATION: 'Invalid input. Please check your data.',
      ERR_NOT_FOUND: 'Resource not found.',
      ERR_UNAUTHORIZED: 'You don\'t have permission to access this resource.',
      ERR_SERVICE: 'An unexpected error occurred. Please try again.',
    };

    const message = errorResponse?.error
      ? errorMessages[errorResponse.error] || errorResponse.message
      : 'Network error. Please check your connection.';

    console.error('API Error:', { message, correlationId, error: errorResponse });

    return Promise.reject(error);
  }
);
```

## Testing

### Mock API Client for Tests

```typescript
// src/services/api/__mocks__/entityApiClient.ts
export const entityApiClient = {
  getEntities: vi.fn(),
  getEntity: vi.fn(),
  createEntity: vi.fn(),
  updateEntity: vi.fn(),
  patchEntity: vi.fn(),
  deleteEntity: vi.fn(),
};

// In test file
import { entityApiClient } from '../entityApiClient';

vi.mock('../entityApiClient');

describe('EntityService', () => {
  it('should fetch entities', async () => {
    const mockData = [{ id: '1', name: 'Test' }];
    (entityApiClient.getEntities as any).mockResolvedValue({ data: mockData, pagination: {} });

    const result = await entityApiClient.getEntities({}, 1, 20);

    expect(result.data).toEqual(mockData);
    expect(entityApiClient.getEntities).toHaveBeenCalledWith({}, 1, 20);
  });
});
```

## Common Pitfalls

### Pitfall 1: Hardcoding Base URL
**Problem**: API client breaks when deploying to different environments
**Solution**: Use environment variables

```typescript
// ❌ WRONG - hardcoded URL
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
});

// ✅ CORRECT - environment variable
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});
```

### Pitfall 2: Not Handling Token Refresh
**Problem**: User gets logged out when token expires during session
**Solution**: Implement token refresh interceptor (advanced pattern, not shown here)

### Pitfall 3: Exposing Sensitive Data in Interceptors
**Problem**: Logging full request/response including tokens
**Solution**: Sanitize logs

```typescript
// ❌ WRONG - logging full config with token
console.log('Request:', config);

// ✅ CORRECT - sanitize sensitive data
console.log('Request:', {
  url: config.url,
  method: config.method,
  // Don't log headers.Authorization
});
```

### Pitfall 4: Not Using TypeScript Types
**Problem**: Runtime errors from incorrect API responses
**Solution**: Define types for all API methods

```typescript
// ✅ CORRECT - typed API methods
getEntity: async (id: string, include?: string[]): Promise<EntityDetail> => {
  const response = await apiClient.get<EntityDetail>(`/api/v1/entities/${id}`);
  return response.data;
}
```

## Story-Specific Adaptations

### Story 2.5.3: Event Management API Client

```typescript
export const eventApiClient = {
  getEvents: async (filters: EventFilters, page: number, limit: number) => {
    const params = new URLSearchParams();
    params.append('page', String(page));
    params.append('limit', String(limit));

    if (filters.status) params.append('status', filters.status);
    if (filters.year) params.append('year', String(filters.year));
    if (filters.eventType) params.append('eventType', filters.eventType);
    if (filters.searchQuery) params.append('search', filters.searchQuery);

    const response = await apiClient.get(`/api/v1/events?${params.toString()}`);
    return response.data;
  },

  // Resource expansion for 90% API call reduction
  getEvent: async (eventCode: string, include?: string[]) => {
    const params = include?.length ? `?include=${include.join(',')}` : '';
    const response = await apiClient.get(`/api/v1/events/${eventCode}${params}`);
    return response.data;
  },

  // PATCH for partial updates (auto-save use case)
  patchEvent: async (eventCode: string, data: Partial<UpdateEventRequest>) => {
    const response = await apiClient.patch(`/api/v1/events/${eventCode}`, data);
    return response.data;
  },

  // Uses meaningful ID (eventCode) instead of UUID (Story 1.16.2)
  deleteEvent: async (eventCode: string) => {
    await apiClient.delete(`/api/v1/events/${eventCode}`);
  },
};
```

## Related Templates

- `react-query-caching-pattern.md` - Use API client methods in React Query hooks
- `zustand-store-pattern.md` - Manage filters and pagination state
- `form-validation-pattern.md` - Use API client for form submissions

## References

- **Axios Documentation**: https://axios-http.com/docs/intro
- **Story 1.9**: Error Handling Essentials (correlation IDs, error format)
- **Story 1.16.2**: Meaningful IDs pattern (use eventCode, username vs UUIDs)
- **Story 2.5.3**: Event Management Frontend (lines 1396-1493)
