# React Query Caching Pattern

**Category**: Frontend - Data Fetching & Caching
**Used in Stories**: 2.5.3 (Event Management Frontend), 2.8.1 (Partner Directory)
**Last Updated**: 2025-01-20
**Source**: Extracted from stories 2.5.3 and 2.8.1

## Overview

React Query provides intelligent caching, background updates, and query invalidation for server state management. This pattern shows how to implement proper caching strategies based on data volatility.

**Use this pattern when**:
- Fetching data from REST APIs
- Implementing lists with filters, sorting, and pagination
- Managing server state (data that lives on the server)
- Optimizing performance through caching
- Implementing optimistic updates for better UX

## Prerequisites

```bash
npm install @tanstack/react-query
npm install axios  # or your HTTP client
```

## Caching Strategy by Data Volatility

| Data Type | Volatility | staleTime | cacheTime | Example |
|-----------|-----------|-----------|-----------|---------|
| Static reference data | Very Low | 15 min | 30 min | Event details with includes |
| Standard entity data | Low | 10 min | 20 min | Event workflow state |
| List data with filters | Medium | 5 min | 10 min | Event lists |
| User activity data | High | 3 min | 5 min | Critical tasks |
| Real-time dashboards | Very High | 2 min | 5 min | Team activity, partner lists |

**Caching Guidelines**:
- **staleTime**: How long data is considered "fresh" before refetch
- **cacheTime**: How long unused data stays in cache memory
- **Rule**: cacheTime should be ≥ staleTime (typically 2x)

## Implementation Steps

### Step 1: Configure QueryClient Provider

```typescript
// src/main.tsx or src/App.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';

// Create QueryClient with sensible defaults
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,                    // Retry failed requests twice
      staleTime: 5 * 60 * 1000,   // Default: 5 minutes
      cacheTime: 10 * 60 * 1000,  // Default: 10 minutes
      refetchOnWindowFocus: true,  // Refetch when user returns to tab
      refetchOnReconnect: true,    // Refetch when internet reconnects
    },
  },
});

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <YourApp />
      {/* DevTools - only visible in development */}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
```

### Step 2: Create API Client Service

```typescript
// src/services/api/{entity}Api.ts
import axios from 'axios';
import { config } from '@/config';

const api = axios.create({
  baseURL: config.apiUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add auth token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor - handle errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized - redirect to login
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// API methods
export interface EntityFilters {
  status?: 'all' | 'active' | 'inactive';
  tier?: string;
  search?: string;
}

export interface EntityPagination {
  page: number;
  limit: number;
}

export interface EntitySort {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}

export const listEntities = async (
  filters: EntityFilters,
  sort: EntitySort,
  pagination: EntityPagination
) => {
  const { data } = await api.get('/api/v1/entities', {
    params: { ...filters, ...sort, ...pagination },
  });
  return data;
};

export const getEntity = async (id: string, include?: string[]) => {
  const { data } = await api.get(`/api/v1/entities/${id}`, {
    params: { include: include?.join(',') },
  });
  return data;
};

export const createEntity = async (payload: CreateEntityRequest) => {
  const { data } = await api.post('/api/v1/entities', payload);
  return data;
};

export const updateEntity = async (id: string, payload: UpdateEntityRequest) => {
  const { data } = await api.put(`/api/v1/entities/${id}`, payload);
  return data;
};

export const deleteEntity = async (id: string) => {
  const { data } = await api.delete(`/api/v1/entities/${id}`);
  return data;
};
```

### Step 3: Create React Query Hooks

```typescript
// src/hooks/useEntities.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import type { AxiosError } from 'axios';
import {
  listEntities,
  getEntity,
  createEntity,
  updateEntity,
  deleteEntity,
  EntityFilters,
  EntitySort,
  EntityPagination,
} from '@/services/api/entityApi';

/**
 * Fetch paginated list of entities with filters and sorting
 * Caching: 5 minutes (standard list data volatility)
 */
export const useEntities = (
  filters: EntityFilters,
  sort: EntitySort,
  pagination: EntityPagination
) => {
  return useQuery({
    queryKey: ['entities', filters, sort, pagination],
    queryFn: () => listEntities(filters, sort, pagination),
    staleTime: 5 * 60 * 1000,     // 5 minutes
    cacheTime: 10 * 60 * 1000,    // 10 minutes
    keepPreviousData: true,        // Keep previous page while fetching next
    refetchOnWindowFocus: true,
    retry: 2,
  });
};

/**
 * Fetch single entity with optional includes
 * Caching: 15 minutes (detailed data cached longer)
 */
export const useEntity = (id: string, include?: string[]) => {
  return useQuery({
    queryKey: ['entity', id, include],
    queryFn: () => getEntity(id, include),
    staleTime: 15 * 60 * 1000,    // 15 minutes
    cacheTime: 30 * 60 * 1000,    // 30 minutes
    enabled: !!id,                 // Only run if id is provided
  });
};

/**
 * Fetch volatile dashboard data
 * Caching: 2 minutes (very volatile data)
 */
export const useEntityActivity = (userId: string, limit: number = 20) => {
  return useQuery({
    queryKey: ['entityActivity', userId, limit],
    queryFn: () => getEntityActivity(userId, limit),
    staleTime: 2 * 60 * 1000,     // 2 minutes
    cacheTime: 5 * 60 * 1000,     // 5 minutes
    enabled: !!userId,
    refetchInterval: 2 * 60 * 1000, // Auto-refetch every 2 minutes
  });
};

/**
 * Create entity mutation
 * Invalidates entity list cache on success
 */
export const useCreateEntity = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateEntityRequest) => createEntity(data),
    onSuccess: () => {
      // Invalidate all entity list queries
      queryClient.invalidateQueries(['entities']);
      toast.success('Entity created successfully');
    },
    onError: (error: AxiosError) => {
      const message = error.response?.data?.message || 'Failed to create entity';
      toast.error(message);
    },
  });
};

/**
 * Update entity mutation with optimistic updates
 * Immediately updates UI, rolls back on error
 */
export const useUpdateEntity = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateEntityRequest }) =>
      updateEntity(id, data),

    // Optimistic update - update UI before server responds
    onMutate: async ({ id, data }) => {
      // Cancel outgoing queries to avoid race conditions
      await queryClient.cancelQueries(['entity', id]);

      // Snapshot previous value for rollback
      const previousEntity = queryClient.getQueryData(['entity', id]);

      // Optimistically update cache
      queryClient.setQueryData(['entity', id], (old: Entity) => ({
        ...old,
        ...data,
      }));

      // Return context with previous value
      return { previousEntity };
    },

    // On success, invalidate to refetch fresh data
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries(['entities']);
      queryClient.invalidateQueries(['entity', id]);
      toast.success('Entity updated successfully');
    },

    // On error, rollback to previous value
    onError: (error: AxiosError, { id }, context) => {
      queryClient.setQueryData(['entity', id], context.previousEntity);

      if (error.response?.status === 409) {
        toast.error('Concurrent edit detected. Please reload and try again.');
      } else {
        toast.error('Failed to update entity');
      }
    },
  });
};

/**
 * Delete entity mutation
 * Invalidates entity list cache on success
 */
export const useDeleteEntity = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => deleteEntity(id),
    onSuccess: () => {
      queryClient.invalidateQueries(['entities']);
      toast.success('Entity deleted successfully');
    },
    onError: (error: AxiosError) => {
      if (error.response?.status === 400) {
        toast.error('Cannot delete entity with dependencies');
      } else {
        toast.error('Failed to delete entity');
      }
    },
  });
};
```

### Step 4: Use Hooks in Components

```typescript
// src/pages/EntityListPage.tsx
import { useState } from 'react';
import { useEntities, useDeleteEntity } from '@/hooks/useEntities';
import { useEntityStore } from '@/stores/entityStore';

export function EntityListPage() {
  const { filters, sort, page } = useEntityStore();
  const [limit] = useState(20);

  // Fetch entities with automatic caching
  const { data, isLoading, error } = useEntities(
    filters,
    sort,
    { page, limit }
  );

  // Delete mutation
  const deleteEntity = useDeleteEntity();

  const handleDelete = (id: string) => {
    if (confirm('Are you sure?')) {
      deleteEntity.mutate(id);
    }
  };

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;

  return (
    <div>
      <EntityList entities={data.items} onDelete={handleDelete} />
      <Pagination total={data.total} page={page} limit={limit} />
    </div>
  );
}
```

## Testing

### Testing React Query Hooks

```typescript
// src/hooks/__tests__/useEntities.test.tsx
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEntities } from '../useEntities';
import * as entityApi from '@/services/api/entityApi';

// Create test QueryClient
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },  // Disable retries in tests
      mutations: { retry: false },
    },
  });

// Wrapper component for tests
const createWrapper = () => {
  const testQueryClient = createTestQueryClient();
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={testQueryClient}>
      {children}
    </QueryClientProvider>
  );
};

describe('useEntities', () => {
  it('should fetch entities successfully', async () => {
    const mockEntities = [{ id: '1', name: 'Entity 1' }];
    vi.spyOn(entityApi, 'listEntities').mockResolvedValue({
      items: mockEntities,
      total: 1,
    });

    const { result } = renderHook(
      () => useEntities({ status: 'all' }, { sortBy: 'name', sortOrder: 'asc' }, { page: 0, limit: 20 }),
      { wrapper: createWrapper() }
    );

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual({ items: mockEntities, total: 1 });
  });

  it('should cache entities with staleTime', async () => {
    const mockEntities = [{ id: '1', name: 'Entity 1' }];
    const listSpy = vi.spyOn(entityApi, 'listEntities').mockResolvedValue({
      items: mockEntities,
      total: 1,
    });

    const { result, rerender } = renderHook(
      () => useEntities({ status: 'all' }, { sortBy: 'name', sortOrder: 'asc' }, { page: 0, limit: 20 }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    // Should use cached data, not call API again
    rerender();
    expect(listSpy).toHaveBeenCalledTimes(1);
  });
});
```

## Common Pitfalls

### Pitfall 1: Incorrect Query Keys
**Problem**: Cache not invalidated properly, stale data shown
**Solution**: Include all parameters that affect the query in queryKey

```typescript
// ❌ WRONG - missing parameters in queryKey
queryKey: ['entities'],
queryFn: () => listEntities(filters, sort, pagination),

// ✅ CORRECT - all parameters in queryKey
queryKey: ['entities', filters, sort, pagination],
queryFn: () => listEntities(filters, sort, pagination),
```

### Pitfall 2: Not Using keepPreviousData for Pagination
**Problem**: UI flashes loading state when changing pages
**Solution**: Use `keepPreviousData: true` for paginated queries

```typescript
// ✅ CORRECT - smooth pagination UX
useQuery({
  queryKey: ['entities', page],
  queryFn: () => listEntities({ page }),
  keepPreviousData: true,  // Keep old data while fetching new page
});
```

### Pitfall 3: Over-Caching Volatile Data
**Problem**: Dashboard shows stale data
**Solution**: Use shorter staleTime (2-3 min) and refetchInterval

```typescript
// ✅ CORRECT - auto-refresh volatile data
useQuery({
  queryKey: ['dashboard'],
  queryFn: getDashboard,
  staleTime: 2 * 60 * 1000,
  refetchInterval: 2 * 60 * 1000,  // Auto-refetch every 2 minutes
});
```

### Pitfall 4: Forgetting to Invalidate After Mutations
**Problem**: List doesn't update after creating/updating entity
**Solution**: Always invalidate related queries in mutation onSuccess

```typescript
// ✅ CORRECT - invalidate related queries
useMutation({
  mutationFn: createEntity,
  onSuccess: () => {
    queryClient.invalidateQueries(['entities']);  // Refetch lists
  },
});
```

### Pitfall 5: Not Handling Enabled Condition
**Problem**: Query runs with undefined/null parameters
**Solution**: Use `enabled` to conditionally run queries

```typescript
// ✅ CORRECT - only run if id is provided
useQuery({
  queryKey: ['entity', id],
  queryFn: () => getEntity(id),
  enabled: !!id,  // Don't run if id is null/undefined
});
```

## Story-Specific Adaptations

### Paginated Lists (Story 2.8.1)
```typescript
export const usePartners = (filters, sort, pagination) => {
  return useQuery({
    queryKey: ['partners', filters, sort, pagination],
    queryFn: () => listPartners(filters, sort, pagination),
    staleTime: 2 * 60 * 1000,
    keepPreviousData: true,  // Essential for pagination
  });
};
```

### Detail View with Includes (Story 2.5.3)
```typescript
export const useEvent = (eventCode: string, include?: string[]) => {
  return useQuery({
    queryKey: ['event', eventCode, include],  // Include affects data shape
    queryFn: () => getEvent(eventCode, include),
    staleTime: 15 * 60 * 1000,  // Longer cache for detailed data
    enabled: !!eventCode,
  });
};
```

### Real-Time Dashboards (Story 2.5.3)
```typescript
export const useTeamActivity = (userId: string, limit = 20) => {
  return useQuery({
    queryKey: ['teamActivity', userId, limit],
    queryFn: () => getTeamActivity(userId, limit),
    staleTime: 2 * 60 * 1000,          // Short cache
    refetchInterval: 2 * 60 * 1000,    // Auto-refresh
    enabled: !!userId,
  });
};
```

## Related Templates

- `zustand-store-pattern.md` - Client state management (filters, UI state)
- `react-component-pattern.md` - Component structure using hooks
- `form-validation-pattern.md` - Form handling with mutations

## References

- **React Query Docs**: https://tanstack.com/query/latest/docs/react/overview
- **Story 2.5.3**: Event Management Frontend (lines 1346-1461)
- **Story 2.8.1**: Partner Directory (lines 677-696)
