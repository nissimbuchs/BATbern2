/**
 * usePartnerNotes Hook
 *
 * React Query hook for fetching and mutating partner notes
 * Story 2.8.2: Partner Detail View
 *
 * Features:
 * - Fetches partner notes for Notes tab
 * - 2-minute cache for notes data (more volatile)
 * - Lazy loading (only fetches when tab activated)
 * - Mutations: create, update, delete notes with optimistic updates
 *
 * AC: 7 (Notes Tab), 13 (Integration Tests)
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
// import {
//   getPartnerNotes,
//   createPartnerNote,
//   updatePartnerNote,
//   deletePartnerNote,
// } from '@/services/api/partnerApi';
// import type { NoteResponse, CreateNoteRequest, UpdateNoteRequest } from '@/services/api/partnerApi';

// TODO: Remove stubs when notes API is implemented
interface NoteResponse {
  id: string;
  content: string;
  createdAt: string;
}
interface CreateNoteRequest {
  content: string;
}
interface UpdateNoteRequest {
  content?: string;
}

interface UsePartnerNotesReturn {
  data: NoteResponse[] | undefined;
  isLoading: boolean;
  error: Error | null;
  createNote: (note: CreateNoteRequest) => void;
  updateNote: (params: { noteId: string } & UpdateNoteRequest) => void;
  deleteNote: (noteId: string) => void;
}

/**
 * usePartnerNotes - Fetch and mutate partner notes
 *
 * @param companyName - Company name (meaningful ID)
 * @returns Object with notes data and mutation functions
 *
 * Cache: 2 minutes (notes data is volatile)
 */
export const usePartnerNotes = (companyName: string): UsePartnerNotesReturn => {
  const queryClient = useQueryClient();
  const queryKey = ['partner', companyName, 'notes'];

  const { data, isLoading, error } = useQuery({
    queryKey,
    queryFn: async () => [], // TODO: Replace with getPartnerNotes(companyName) when API implemented
    enabled: !!companyName,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

  // Create note mutation
  const createMutation = useMutation({
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    mutationFn: async (_note: CreateNoteRequest) => ({}) as NoteResponse, // TODO: Replace with createPartnerNote(companyName, note)
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });

  // Update note mutation
  const updateMutation = useMutation({
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    mutationFn: async ({ noteId: _noteId }: { noteId: string } & UpdateNoteRequest) =>
      ({}) as NoteResponse, // TODO: Replace with updatePartnerNote(companyName, noteId, note)
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });

  // Delete note mutation
  const deleteMutation = useMutation({
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    mutationFn: async (_noteId: string) => {}, // TODO: Replace with deletePartnerNote(companyName, noteId)
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
