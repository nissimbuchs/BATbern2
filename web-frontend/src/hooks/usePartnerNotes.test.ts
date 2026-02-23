/**
 * usePartnerNotes Hook Tests
 *
 * Story 8.4: Updated for real API (replaces stub assertions from Story 2.8.2).
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePartnerNotes } from './usePartnerNotes';
import React from 'react';

// Mock the API module
vi.mock('@/services/api/partnerNotesApi', () => ({
  getPartnerNotes: vi.fn(),
  createPartnerNote: vi.fn(),
  updatePartnerNote: vi.fn(),
  deletePartnerNote: vi.fn(),
}));

import {
  getPartnerNotes,
  createPartnerNote,
  deletePartnerNote,
} from '@/services/api/partnerNotesApi';

const mockGetPartnerNotes = vi.mocked(getPartnerNotes);
const mockCreatePartnerNote = vi.mocked(createPartnerNote);
const mockDeletePartnerNote = vi.mocked(deletePartnerNote);

const note1 = {
  id: 'note-1',
  title: 'Q1 Planning',
  content: '<p>Content 1</p>',
  authorUsername: 'organizer1',
  createdAt: '2025-01-09T10:00:00Z',
  updatedAt: '2025-01-09T10:00:00Z',
};
const note2 = {
  id: 'note-2',
  title: 'Contract Review',
  content: '<p>Content 2</p>',
  authorUsername: 'organizer1',
  createdAt: '2025-01-08T10:00:00Z',
  updatedAt: '2025-01-08T10:00:00Z',
};

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });

const createWrapper = (queryClient: QueryClient) => {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
};

describe('usePartnerNotes', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = createTestQueryClient();
    vi.clearAllMocks();
  });

  // Test 1: should_fetchNotes_when_usePartnerNotesCalled
  it('should_fetchNotes_when_usePartnerNotesCalled', async () => {
    mockGetPartnerNotes.mockResolvedValue([note1, note2]);

    const { result } = renderHook(() => usePartnerNotes('GoogleZH'), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBeNull();
    expect(result.current.data).toEqual([note1, note2]);
    expect(mockGetPartnerNotes).toHaveBeenCalledWith('GoogleZH');
  });

  // Test 2: should_notFetch_when_companyNameUndefined
  it('should_notFetch_when_companyNameUndefined', () => {
    const { result } = renderHook(() => usePartnerNotes(undefined as unknown as string), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.data).toBeUndefined();
    expect(mockGetPartnerNotes).not.toHaveBeenCalled();
  });

  // Test 3: should_haveCorrectQueryKey_when_hookCalled
  it('should_haveCorrectQueryKey_when_hookCalled', async () => {
    mockGetPartnerNotes.mockResolvedValue([]);
    const companyName = 'GoogleZH';

    renderHook(() => usePartnerNotes(companyName), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => {
      const queries = queryClient.getQueryCache().findAll();
      expect(queries.length).toBeGreaterThan(0);
    });

    const queries = queryClient.getQueryCache().findAll();
    const query = queries.find((q) => {
      const key = q.queryKey;
      return (
        Array.isArray(key) && key[0] === 'partner' && key[1] === companyName && key[2] === 'notes'
      );
    });

    expect(query).toBeDefined();
  });

  // Test 4: should_createNote_when_createNoteCalled
  it('should_createNote_when_createNoteCalled', async () => {
    mockGetPartnerNotes.mockResolvedValue([]);
    mockCreatePartnerNote.mockResolvedValue(note1);

    const { result } = renderHook(() => usePartnerNotes('GoogleZH'), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      result.current.createNote({ title: 'Q1 Planning', content: '<p>Content</p>' });
    });

    await waitFor(() => {
      expect(mockCreatePartnerNote).toHaveBeenCalledWith('GoogleZH', {
        title: 'Q1 Planning',
        content: '<p>Content</p>',
      });
    });
  });

  // Test 5: should_deleteNote_when_deleteNoteCalled
  it('should_deleteNote_when_deleteNoteCalled', async () => {
    mockGetPartnerNotes.mockResolvedValue([note1]);
    mockDeletePartnerNote.mockResolvedValue(undefined);

    const { result } = renderHook(() => usePartnerNotes('GoogleZH'), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      result.current.deleteNote('note-1');
    });

    await waitFor(() => {
      expect(mockDeletePartnerNote).toHaveBeenCalledWith('GoogleZH', 'note-1');
    });
  });
});
