/**
 * usePartnerNotes Hook
 *
 * React Query hook for fetching and mutating partner notes.
 * Story 8.4: wired to real backend (replaces stub from Story 2.8.2).
 *
 * Features:
 * - Fetches partner notes for Notes tab (ORGANIZER-only)
 * - 2-minute stale time (notes data is volatile)
 * - Lazy loading (only fetches when companyName is defined)
 * - Mutations: create, update, delete notes with cache invalidation
 *
 * AC: 6 (frontend wired), 7 (ORGANIZER-only)
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPartnerNotes,
  createPartnerNote,
  updatePartnerNote,
  deletePartnerNote,
} from '@/services/api/partnerNotesApi';
import type {
  PartnerNoteDTO,
  CreateNoteRequest,
  UpdateNoteRequest,
} from '@/services/api/partnerNotesApi';

interface UsePartnerNotesReturn {
  data: PartnerNoteDTO[] | undefined;
  isLoading: boolean;
  error: Error | null;
  createNote: (note: CreateNoteRequest) => void;
  updateNote: (params: { noteId: string } & UpdateNoteRequest) => void;
  deleteNote: (noteId: string) => void;
}

/**
 * usePartnerNotes - Fetch and mutate partner notes
 *
 * @param companyName - Company name (meaningful ID per ADR-003)
 * @returns Object with notes data and mutation functions
 *
 * Cache: 2 minutes stale time
 */
export const usePartnerNotes = (companyName: string): UsePartnerNotesReturn => {
  const queryClient = useQueryClient();
  const queryKey = ['partner', companyName, 'notes'];

  const { data, isLoading, error } = useQuery({
    queryKey,
    queryFn: () => getPartnerNotes(companyName),
    enabled: !!companyName,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

  const createMutation = useMutation({
    mutationFn: (note: CreateNoteRequest) => createPartnerNote(companyName, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });

  const updateMutation = useMutation({
    mutationFn: async ({ noteId, ...req }: { noteId: string } & UpdateNoteRequest) =>
      updatePartnerNote(companyName, noteId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (noteId: string) => deletePartnerNote(companyName, noteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });

  return {
    data,
    isLoading,
    error: error as Error | null,
    createNote: createMutation.mutate,
    updateNote: updateMutation.mutate,
    deleteNote: deleteMutation.mutate,
  };
};
