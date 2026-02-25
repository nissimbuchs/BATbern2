/**
 * Email Templates React Query Hooks (Story 10.2)
 *
 * Custom hooks for email template data fetching and mutations.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  emailTemplateService,
  type CreateEmailTemplateRequest,
  type EmailTemplateResponse,
  type ListEmailTemplatesParams,
  type UpdateEmailTemplateRequest,
} from '@/services/emailTemplateService';

const QUERY_KEY_BASE = 'emailTemplates' as const;

export function useEmailTemplates(params?: ListEmailTemplatesParams) {
  return useQuery({
    queryKey: [QUERY_KEY_BASE, params ?? {}],
    queryFn: () => emailTemplateService.listTemplates(params),
  });
}

export function useLayoutTemplates() {
  return useEmailTemplates({ isLayout: true });
}

export function useCreateEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateEmailTemplateRequest) => emailTemplateService.createTemplate(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY_BASE] });
    },
  });
}

export function useUpdateEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      templateKey,
      locale,
      req,
    }: {
      templateKey: string;
      locale: string;
      req: UpdateEmailTemplateRequest;
    }) => emailTemplateService.updateTemplate(templateKey, locale, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY_BASE] });
    },
  });
}

export function useDeleteEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ templateKey, locale }: { templateKey: string; locale: string }) =>
      emailTemplateService.deleteTemplate(templateKey, locale),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY_BASE] });
    },
  });
}

export type { EmailTemplateResponse };
