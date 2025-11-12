/**
 * usePartnerMutations Hooks (Story 2.8.3)
 *
 * React Query mutation hooks for partner create/update operations with optimistic updates
 * AC 8 (Create Partnership), AC 9 (Edit Partnership)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { createPartner, updatePartner } from '@/services/api/partnerApi';
import type {
  PartnerResponse,
  CreatePartnerRequest,
  UpdatePartnerRequest,
} from '@/services/api/partnerApi';

/**
 * Create new partnership mutation
 *
 * Features:
 * - Automatic cache invalidation of partners list and statistics
 * - Navigation to detail view on success
 * - Error handling with correlation IDs
 *
 * @returns Mutation hook for creating partnerships
 */
export const useCreatePartner = () => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (data: CreatePartnerRequest) => createPartner(data),
    onSuccess: (data) => {
      // Invalidate partners list and statistics to refetch with new partnership
      queryClient.invalidateQueries({ queryKey: ['partners'] });
      queryClient.invalidateQueries({ queryKey: ['partner-statistics'] });

      // Navigate to detail view
      navigate(`/organizer/partners/${data.companyName}`);
    },
  });
};

/**
 * Update existing partnership mutation with optimistic updates
 *
 * Features:
 * - Optimistic UI updates (immediate feedback)
 * - Rollback on error
 * - Cache invalidation of both list and detail
 * - Partial update support (only changed fields)
 *
 * @returns Mutation hook for updating partnerships
 */
export const useUpdatePartner = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ companyName, data }: { companyName: string; data: UpdatePartnerRequest }) =>
      updatePartner(companyName, data),

    // Optimistic update (AC 9 - immediate UI feedback)
    onMutate: async ({ companyName, data }) => {
      // Cancel any outgoing refetches to prevent overwriting optimistic update
      await queryClient.cancelQueries({ queryKey: ['partner', companyName] });

      // Snapshot the previous value for rollback
      const previousPartner = queryClient.getQueryData(['partner', companyName, undefined]);

      // Optimistically update to the new value
      if (previousPartner) {
        queryClient.setQueryData(['partner', companyName, undefined], (old: PartnerResponse) => ({
          ...old,
          ...data,
        }));
      }

      // Return context with snapshot for rollback
      return { previousPartner };
    },

    // On success, invalidate caches to refetch fresh data
    onSuccess: (_, { companyName }) => {
      queryClient.invalidateQueries({ queryKey: ['partners'] });
      queryClient.invalidateQueries({ queryKey: ['partner', companyName] });
      queryClient.invalidateQueries({ queryKey: ['partner-statistics'] });
    },

    // Rollback on error (AC 9 - error handling with rollback)
    onError: (_error, { companyName }, context) => {
      if (context?.previousPartner) {
        queryClient.setQueryData(['partner', companyName, undefined], context.previousPartner);
      }
    },
  });
};
