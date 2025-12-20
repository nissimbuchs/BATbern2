# Zustand Store Pattern

**Category**: Frontend - Client State Management
**Used in Stories**: 2.5.3 (Event Management Frontend), 2.8.1 (Partner Directory)
**Last Updated**: 2025-01-20
**Source**: Extracted from stories 2.5.3 and 2.8.1

## Overview

Zustand is a lightweight state management library for React that provides a simple, hook-based API for managing client-side state. Use it for UI state, filters, and local preferences - NOT for server data (use React Query for that).

**Use this pattern when**:
- Managing UI state (modals, view modes, selected items)
- Storing filter and sort preferences
- Persisting user preferences to localStorage
- Sharing state across components without prop drilling
- Implementing pagination state

**Don't use this pattern for**:
- Server data (events, users, etc.) - use React Query instead
- Authentication state - use React Query + auth context
- Form state - use React Hook Form instead

## Prerequisites

```bash
npm install zustand
```

## State Separation Strategy

| State Type | Storage | Library | Example |
|------------|---------|---------|---------|
| **Server State** | Backend API | React Query | Event lists, user profiles, partner data |
| **Client State** (UI) | Zustand Store | Zustand | Filters, sort order, modal state, view mode |
| **Client State** (Persistent) | Zustand + localStorage | Zustand persist | User preferences, last used filters |
| **Form State** | Component state | React Hook Form | Create/edit forms |

## Implementation Steps

### Step 1: Define Store Interface

```typescript
// src/stores/{entity}Store.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

// Define filter types
interface EntityFilters {
  status: 'all' | 'active' | 'inactive';
  tier: string | 'all';
  searchQuery: string;
}

// Define store interface
interface EntityStore {
  // State
  filters: EntityFilters;
  viewMode: 'grid' | 'list';
  sortBy: 'name' | 'date' | 'engagement';
  sortOrder: 'asc' | 'desc';
  page: number;
  selectedEntityId?: string;
  isCreateModalOpen: boolean;
  isEditModalOpen: boolean;

  // Actions
  setFilters: (filters: Partial<EntityFilters>) => void;
  setViewMode: (mode: 'grid' | 'list') => void;
  setSearchQuery: (query: string) => void;
  setSortBy: (sortBy: EntityStore['sortBy']) => void;
  setSortOrder: (order: 'asc' | 'desc') => void;
  setPage: (page: number) => void;
  setSelectedEntityId: (id: string | undefined) => void;
  openCreateModal: () => void;
  closeCreateModal: () => void;
  openEditModal: (id: string) => void;
  closeEditModal: () => void;
  resetFilters: () => void;
}
```

### Step 2: Create Zustand Store with Persistence

```typescript
// src/stores/{entity}Store.ts (continued)

export const useEntityStore = create<EntityStore>()(
  persist(
    (set) => ({
      // ========================================
      // Initial State
      // ========================================
      filters: {
        status: 'all',
        tier: 'all',
        searchQuery: '',
      },
      viewMode: 'grid',
      sortBy: 'name',
      sortOrder: 'asc',
      page: 0,
      selectedEntityId: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,

      // ========================================
      // Filter Actions
      // ========================================
      setFilters: (newFilters) =>
        set((state) => ({
          filters: { ...state.filters, ...newFilters },
          page: 0, // IMPORTANT: Reset to first page when filters change
        })),

      setSearchQuery: (query) =>
        set({
          filters: (state) => ({ ...state.filters, searchQuery: query }),
          page: 0, // Reset to first page on search
        }),

      resetFilters: () =>
        set({
          filters: { status: 'all', tier: 'all', searchQuery: '' },
          page: 0,
        }),

      // ========================================
      // View & Sort Actions
      // ========================================
      setViewMode: (mode) => set({ viewMode: mode }),

      setSortBy: (sortBy) =>
        set({
          sortBy,
          page: 0, // Reset to first page when sort changes
        }),

      setSortOrder: (order) => set({ sortOrder: order }),

      // ========================================
      // Pagination Actions
      // ========================================
      setPage: (page) => set({ page }),

      // ========================================
      // Selection Actions
      // ========================================
      setSelectedEntityId: (id) => set({ selectedEntityId: id }),

      // ========================================
      // Modal Actions
      // ========================================
      openCreateModal: () => set({ isCreateModalOpen: true }),

      closeCreateModal: () => set({ isCreateModalOpen: false }),

      openEditModal: (id) =>
        set({
          selectedEntityId: id,
          isEditModalOpen: true,
        }),

      closeEditModal: () =>
        set({
          isEditModalOpen: false,
          selectedEntityId: undefined,
        }),
    }),
    {
      // ========================================
      // Persistence Configuration
      // ========================================
      name: 'entity-store', // localStorage key

      // IMPORTANT: Only persist user preferences, NOT transient UI state
      partialPersist: (state) => ({
        viewMode: state.viewMode,    // Persist view mode
        sortBy: state.sortBy,         // Persist sort preference
        sortOrder: state.sortOrder,   // Persist sort order
        // DON'T persist: page, modals, selectedEntityId (transient state)
      }),
    }
  )
);
```

### Step 3: Use Store in Components

```typescript
// src/pages/EntityListPage.tsx
import { useEntityStore } from '@/stores/entityStore';
import { useEntities } from '@/hooks/useEntities';

export function EntityListPage() {
  // Get state and actions from store
  const {
    filters,
    viewMode,
    sortBy,
    sortOrder,
    page,
    setFilters,
    setViewMode,
    setPage,
    openCreateModal,
  } = useEntityStore();

  // Fetch entities using React Query (server state)
  const { data, isLoading } = useEntities(
    filters,
    { sortBy, sortOrder },
    { page, limit: 20 }
  );

  return (
    <div>
      <FilterBar
        filters={filters}
        onFiltersChange={setFilters}
      />

      <ViewModeToggle
        value={viewMode}
        onChange={setViewMode}
      />

      <EntityList
        entities={data?.items}
        viewMode={viewMode}
        isLoading={isLoading}
      />

      <Pagination
        page={page}
        total={data?.total}
        onPageChange={setPage}
      />

      <CreateButton onClick={openCreateModal} />
    </div>
  );
}
```

### Step 4: Use Store in Modals

```typescript
// src/components/EntityCreateModal.tsx
import { useEntityStore } from '@/stores/entityStore';
import { useCreateEntity } from '@/hooks/useEntities';

export function EntityCreateModal() {
  const { isCreateModalOpen, closeCreateModal } = useEntityStore();
  const createEntity = useCreateEntity();

  const handleSubmit = async (data: CreateEntityRequest) => {
    await createEntity.mutateAsync(data);
    closeCreateModal();
  };

  return (
    <Modal open={isCreateModalOpen} onClose={closeCreateModal}>
      <EntityForm onSubmit={handleSubmit} onCancel={closeCreateModal} />
    </Modal>
  );
}
```

## Testing

### Testing Zustand Stores

```typescript
// src/stores/__tests__/{entity}Store.test.ts
import { renderHook, act } from '@testing-library/react';
import { useEntityStore } from '../entityStore';

describe('useEntityStore', () => {
  beforeEach(() => {
    // Reset store before each test
    const { result } = renderHook(() => useEntityStore());
    act(() => {
      result.current.resetFilters();
      result.current.setViewMode('grid');
      result.current.setPage(0);
    });
  });

  it('should update filters and reset page to 0', () => {
    const { result } = renderHook(() => useEntityStore());

    act(() => {
      result.current.setPage(5); // Set page to 5
      result.current.setFilters({ status: 'active' }); // Change filter
    });

    expect(result.current.filters.status).toBe('active');
    expect(result.current.page).toBe(0); // Page reset to 0
  });

  it('should open and close create modal', () => {
    const { result } = renderHook(() => useEntityStore());

    expect(result.current.isCreateModalOpen).toBe(false);

    act(() => {
      result.current.openCreateModal();
    });

    expect(result.current.isCreateModalOpen).toBe(true);

    act(() => {
      result.current.closeCreateModal();
    });

    expect(result.current.isCreateModalOpen).toBe(false);
  });

  it('should open edit modal with selected entity ID', () => {
    const { result } = renderHook(() => useEntityStore());

    act(() => {
      result.current.openEditModal('entity-123');
    });

    expect(result.current.isEditModalOpen).toBe(true);
    expect(result.current.selectedEntityId).toBe('entity-123');
  });

  it('should close edit modal and clear selected entity ID', () => {
    const { result } = renderHook(() => useEntityStore());

    act(() => {
      result.current.openEditModal('entity-123');
      result.current.closeEditModal();
    });

    expect(result.current.isEditModalOpen).toBe(false);
    expect(result.current.selectedEntityId).toBeUndefined();
  });

  it('should reset filters and page', () => {
    const { result } = renderHook(() => useEntityStore());

    act(() => {
      result.current.setFilters({ status: 'active', tier: 'PLATINUM' });
      result.current.setPage(5);
      result.current.resetFilters();
    });

    expect(result.current.filters).toEqual({
      status: 'all',
      tier: 'all',
      searchQuery: '',
    });
    expect(result.current.page).toBe(0);
  });
});
```

## Common Pitfalls

### Pitfall 1: Storing Server Data in Zustand
**Problem**: Data gets stale, no background updates, duplicate state
**Solution**: Use React Query for server data, Zustand only for UI state

```typescript
// ❌ WRONG - storing server data in Zustand
const useEntityStore = create((set) => ({
  entities: [],
  fetchEntities: async () => {
    const data = await api.getEntities();
    set({ entities: data });
  },
}));

// ✅ CORRECT - use React Query for server data, Zustand for UI state
const useEntityStore = create((set) => ({
  filters: {},
  viewMode: 'grid',
}));

const { data: entities } = useEntities(filters); // React Query
```

### Pitfall 2: Forgetting to Reset Page on Filter Change
**Problem**: User changes filter but stays on page 10, sees empty results
**Solution**: Always reset page to 0 when filters change

```typescript
// ✅ CORRECT - reset page when filters change
setFilters: (newFilters) =>
  set((state) => ({
    filters: { ...state.filters, ...newFilters },
    page: 0, // Always reset to first page
  })),
```

### Pitfall 3: Persisting Transient State
**Problem**: User reopens app with modal still open, or stuck on page 10
**Solution**: Only persist user preferences, not transient UI state

```typescript
// ✅ CORRECT - partial persistence
persist(
  (set) => ({ /* state */ }),
  {
    name: 'entity-store',
    partialPersist: (state) => ({
      viewMode: state.viewMode,    // ✅ Persist preference
      sortBy: state.sortBy,         // ✅ Persist preference
      // DON'T persist: page, modals, selectedEntityId
    }),
  }
)
```

### Pitfall 4: Not Using TypeScript Interface
**Problem**: Hard to understand store shape, typos, no autocomplete
**Solution**: Always define TypeScript interface for your store

```typescript
// ✅ CORRECT - full TypeScript interface
interface EntityStore {
  filters: EntityFilters;
  setFilters: (filters: Partial<EntityFilters>) => void;
  // ... all state and actions
}

const useEntityStore = create<EntityStore>()(/* implementation */);
```

### Pitfall 5: Mutating State Directly
**Problem**: State updates don't trigger re-renders
**Solution**: Always use `set()` to update state

```typescript
// ❌ WRONG - direct mutation
setFilters: (newFilters) => {
  state.filters = { ...state.filters, ...newFilters }; // Won't trigger re-render
},

// ✅ CORRECT - use set()
setFilters: (newFilters) =>
  set((state) => ({
    filters: { ...state.filters, ...newFilters },
  })),
```

## Story-Specific Adaptations

### Event Management (Story 2.5.3)
```typescript
export const useEventStore = create<EventStore>()(
  persist(
    (set) => ({
      filters: {},
      selectedEventCode: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,

      setFilters: (filters) => set({ filters }),
      setSelectedEventCode: (eventCode) => set({ selectedEventCode: eventCode }),
      openCreateModal: () => set({ isCreateModalOpen: true }),
      closeCreateModal: () => set({ isCreateModalOpen: false }),
      openEditModal: (eventCode) => set({
        selectedEventCode: eventCode,
        isEditModalOpen: true,
      }),
      closeEditModal: () => set({
        isEditModalOpen: false,
        selectedEventCode: undefined,
      }),
    }),
    {
      name: 'event-store',
      partialPersist: (state) => ({ filters: state.filters }),
    }
  )
);
```

### Partner Directory (Story 2.8.1)
```typescript
export const usePartnerStore = create<PartnerStore>()(
  persist(
    (set) => ({
      filters: { tier: 'all', status: 'all' },
      viewMode: 'grid',
      searchQuery: '',
      sortBy: 'engagement',
      sortOrder: 'desc',
      page: 0,

      setFilters: (newFilters) =>
        set((state) => ({
          filters: { ...state.filters, ...newFilters },
          page: 0,
        })),

      setViewMode: (mode) => set({ viewMode: mode }),
      setSearchQuery: (query) => set({ searchQuery: query, page: 0 }),
      setSortBy: (sortBy) => set({ sortBy, page: 0 }),
      setSortOrder: (order) => set({ sortOrder: order }),
      setPage: (page) => set({ page }),

      resetFilters: () =>
        set({
          filters: { tier: 'all', status: 'all' },
          searchQuery: '',
          page: 0,
        }),
    }),
    {
      name: 'partner-store',
      partialPersist: (state) => ({ viewMode: state.viewMode }),
    }
  )
);
```

## Related Templates

- `react-query-caching-pattern.md` - Server state management (use with Zustand)
- `react-component-pattern.md` - Component structure using Zustand hooks
- `form-validation-pattern.md` - Form state (use React Hook Form instead)

## References

- **Zustand Docs**: https://zustand-demo.pmnd.rs/
- **Zustand Persist**: https://github.com/pmndrs/zustand#persist-middleware
- **Story 2.5.3**: Event Management Frontend (lines 1463-1491)
- **Story 2.8.1**: Partner Directory (lines 699-765)
