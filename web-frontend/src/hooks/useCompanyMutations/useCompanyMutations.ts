/**
 * useCompanyMutations Hooks
 *
 * React Query mutation hooks for company CRUD operations with optimistic updates
 * AC 3 (Create Company), AC 4 (Edit Company), AC 10 (Performance), AC 14 (State Management)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';
import type {
  Company,
  CreateCompanyRequest,
  UpdateCompanyRequest,
} from '@/types/company.types';

/**
 * Create new company mutation
 *
 * Features:
 * - Automatic cache invalidation of companies list
 * - Support for draft mode (partial validation)
 * - Error handling with correlation IDs
 *
 * @returns Mutation hook for creating companies
 */
export const useCreateCompany = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateCompanyRequest) => companyApiClient.createCompany(data),
    onSuccess: () => {
      // Invalidate companies list to refetch with new company
      queryClient.invalidateQueries({ queryKey: ['companies'] });
    },
  });
};

/**
 * Update existing company mutation with optimistic updates
 *
 * Features:
 * - Optimistic UI updates (immediate feedback)
 * - Rollback on error
 * - Cache invalidation of both list and detail
 * - Partial update support (only changed fields)
 *
 * @returns Mutation hook for updating companies
 */
export const useUpdateCompany = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateCompanyRequest }) =>
      companyApiClient.updateCompany(id, data),

    // Optimistic update (AC 14 - State Management with immediate UI feedback)
    onMutate: async ({ id, data }) => {
      // Cancel any outgoing refetches to prevent overwriting optimistic update
      await queryClient.cancelQueries({ queryKey: ['company', id] });

      // Snapshot the previous value for rollback
      const previousCompany = queryClient.getQueryData(['company', id, undefined]);

      // Optimistically update to the new value
      if (previousCompany) {
        queryClient.setQueryData(['company', id, undefined], (old: Company) => ({
          ...old,
          ...data,
        }));
      }

      // Return context with snapshot for rollback
      return { previousCompany };
    },

    // On success, invalidate caches to refetch fresh data
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['companies'] });
      queryClient.invalidateQueries({ queryKey: ['company', id] });
    },

    // Rollback on error (AC 14 - Error handling with rollback)
    onError: (_error, { id }, context) => {
      if (context?.previousCompany) {
        queryClient.setQueryData(['company', id, undefined], context.previousCompany);
      }
    },
  });
};

/**
 * Delete company mutation
 *
 * Features:
 * - Cache invalidation of companies list
 * - Removal of deleted company from detail cache
 * - Error handling for permission/dependency issues
 *
 * @returns Mutation hook for deleting companies
 */
export const useDeleteCompany = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => companyApiClient.deleteCompany(id),
    onSuccess: (_, id) => {
      // Invalidate companies list
      queryClient.invalidateQueries({ queryKey: ['companies'] });

      // Remove deleted company from cache
      queryClient.removeQueries({ queryKey: ['company', id] });
    },
  });
};
