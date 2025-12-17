# Frontend API Service Pattern

**TEMPLATE FOR ALL FRONTEND API SERVICES**

This pattern MUST be followed for all frontend API service implementations to prevent double URL concatenation bugs.

## Critical Rule: NO `/api/v1` Prefix in Frontend Paths

**Why**: Runtime config already sets `apiBaseUrl: 'http://localhost:8080/api/v1'`

```typescript
// ❌ WRONG - Causes /api/v1/api/v1/users error
await apiClient.get('/api/v1/users');

// ✅ CORRECT - Resolves to /api/v1/users
const USER_API_PATH = '/users';
await apiClient.get(USER_API_PATH);
```

## Standard API Service Template

```typescript
/**
 * [Domain] API Service
 *
 * HTTP client for [Domain] Service APIs
 * Based on Story X.X.X backend API contracts
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/[domain]-api.types';

// Type aliases for cleaner code
type Resource = components['schemas']['ResourceResponse'];
type ResourceListResponse = components['schemas']['PaginatedResourceResponse'];
type CreateResourceRequest = components['schemas']['CreateResourceRequest'];

// API base path for [domain] endpoints
// Note: apiClient baseURL is set from runtime config to 'http://localhost:8080/api/v1'
// so we only need '/[resources]' (the /api/v1 prefix is already in the baseURL)
const RESOURCE_API_PATH = '/[resources]';

/**
 * Get paginated list of resources
 */
export const listResources = async (
  pagination: { page: number; limit: number },
  filters?: ResourceFilters
): Promise<ResourceListResponse> => {
  const params: Record<string, string | number> = {
    page: pagination.page,
    limit: pagination.limit,
  };

  // Add filters if provided
  if (filters) {
    params.filter = JSON.stringify(filters);
  }

  const response = await apiClient.get<ResourceListResponse>(RESOURCE_API_PATH, { params });
  // ✅ Resolves to: http://localhost:8080/api/v1/[resources]
  return response.data;
};

/**
 * Search resources with autocomplete
 */
export const searchResources = async (query: string): Promise<Resource[]> => {
  const response = await apiClient.get<Resource[]>(`${RESOURCE_API_PATH}/search`, {
    params: { query, limit: 10 },
  });
  // ✅ Resolves to: http://localhost:8080/api/v1/[resources]/search
  return response.data;
};

/**
 * Get single resource by ID
 */
export const getResourceById = async (id: string): Promise<Resource> => {
  const response = await apiClient.get<Resource>(`${RESOURCE_API_PATH}/${id}`);
  // ✅ Resolves to: http://localhost:8080/api/v1/[resources]/{id}
  return response.data;
};

/**
 * Create new resource
 */
export const createResource = async (data: CreateResourceRequest): Promise<Resource> => {
  const response = await apiClient.post<Resource>(RESOURCE_API_PATH, data);
  // ✅ Resolves to: http://localhost:8080/api/v1/[resources]
  return response.data;
};

/**
 * Update existing resource
 */
export const updateResource = async (id: string, data: UpdateResourceRequest): Promise<Resource> => {
  const response = await apiClient.put<Resource>(`${RESOURCE_API_PATH}/${id}`, data);
  // ✅ Resolves to: http://localhost:8080/api/v1/[resources]/{id}
  return response.data;
};

/**
 * Delete resource
 */
export const deleteResource = async (id: string): Promise<void> => {
  await apiClient.delete(`${RESOURCE_API_PATH}/${id}`);
  // ✅ Resolves to: http://localhost:8080/api/v1/[resources]/{id}
};
```

## Test Template (MockAdapter)

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { listResources, createResource } from './[domain]Api';

describe('[Domain]Api', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mock.restore();
  });

  it('should_fetchResources_when_listCalled', async () => {
    const mockResponse = { data: [...], pagination: {...} };

    // ✅ CORRECT - Mock path matches implementation (WITHOUT /api/v1)
    mock.onGet('/[resources]').reply(200, mockResponse);

    const result = await listResources({ page: 1, limit: 20 });

    expect(result).toEqual(mockResponse);
  });

  it('should_createResource_when_validDataProvided', async () => {
    const mockResponse = { id: '123', name: 'Test' };

    // ✅ CORRECT - Mock path matches implementation
    mock.onPost('/[resources]').reply(201, mockResponse);

    const result = await createResource({ name: 'Test' });

    expect(result).toEqual(mockResponse);
  });
});
```

## Standard Path Constants

Use these across all API services for consistency:

```typescript
const USER_API_PATH = '/users';
const COMPANY_API_PATH = '/companies';
const EVENT_API_PATH = '/events';
const SPEAKER_API_PATH = '/speakers';
const PARTNER_API_PATH = '/partners';
const ATTENDEE_API_PATH = '/attendees';
const SESSION_API_PATH = '/sessions';
const REGISTRATION_API_PATH = '/registrations';
```

## Common Mistakes to Avoid

### ❌ Mistake 1: Including `/api/v1` prefix
```typescript
// WRONG - Doubles the prefix
const response = await apiClient.get('/api/v1/users');
// Result: http://localhost:8080/api/v1/api/v1/users ❌
```

### ❌ Mistake 2: Not using path constants
```typescript
// WRONG - Hard to maintain, error-prone
const response = await apiClient.get('/users');
const response2 = await apiClient.get('/user');  // Typo!
```

### ❌ Mistake 3: Mismatched test mocks
```typescript
// WRONG - Mock includes /api/v1, implementation doesn't
mock.onGet('/api/v1/users').reply(200, data);
const response = await apiClient.get('/users');  // Mock won't match!
```

### ✅ Correct Pattern
```typescript
// Define constant
const USER_API_PATH = '/users';

// Use in implementation
const response = await apiClient.get(USER_API_PATH);

// Use in tests
mock.onGet('/users').reply(200, mockData);
```

## Reference Implementation

See `web-frontend/src/services/api/companyApi.ts` for a complete working example.

## Checklist for New API Services

- [ ] Defined `const [RESOURCE]_API_PATH = '/[resources]'` (no `/api/v1` prefix)
- [ ] All `apiClient` calls use the constant (e.g., `apiClient.get(RESOURCE_API_PATH)`)
- [ ] Test mocks match implementation paths (no `/api/v1`)
- [ ] Added inline comments showing resolved URLs (e.g., `// ✅ Resolves to: http://localhost:8080/api/v1/users`)
- [ ] Followed OpenAPI type generation pattern
- [ ] Exported all functions from `src/services/api/index.ts`

## Related Documentation

- **Architecture**: `docs/architecture/05-frontend-architecture.md#http-client-configuration`
- **Runtime Config**: `web-frontend/src/config/runtime-config.ts`
- **API Client**: `web-frontend/src/services/api/apiClient.ts`
- **Example**: `web-frontend/src/services/api/companyApi.ts`
