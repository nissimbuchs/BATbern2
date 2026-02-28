/**
 * useEmailTemplates Hook Tests (Story 10.2 — Code Review fix)
 *
 * Tests all hooks: useEmailTemplates, useLayoutTemplates,
 * useCreateEmailTemplate, useUpdateEmailTemplate, useDeleteEmailTemplate
 */

import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import React from 'react';
import {
  useEmailTemplates,
  useLayoutTemplates,
  useCreateEmailTemplate,
  useUpdateEmailTemplate,
  useDeleteEmailTemplate,
} from './useEmailTemplates';

vi.mock('@/services/emailTemplateService', () => ({
  emailTemplateService: {
    listTemplates: vi.fn(),
    getTemplate: vi.fn(),
    createTemplate: vi.fn(),
    updateTemplate: vi.fn(),
    deleteTemplate: vi.fn(),
  },
}));

const mockTemplate = {
  templateKey: 'speaker-invitation',
  locale: 'de',
  category: 'SPEAKER',
  subject: 'Einladung',
  htmlBody: '<p>Hello</p>',
  variables: null,
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

const mockLayoutTemplate = {
  ...mockTemplate,
  templateKey: 'batbern-default',
  isLayout: true,
  subject: null,
  category: 'LAYOUT',
  layoutKey: null,
};

const makeWrapper = () => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client }, children);
};

describe('useEmailTemplates', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns templates from service', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.listTemplates).mockResolvedValue([mockTemplate]);

    const { result } = renderHook(() => useEmailTemplates(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].templateKey).toBe('speaker-invitation');
  });

  it('passes params to listTemplates', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.listTemplates).mockResolvedValue([]);

    renderHook(() => useEmailTemplates({ category: 'SPEAKER' }), { wrapper: makeWrapper() });

    await waitFor(() =>
      expect(emailTemplateService.listTemplates).toHaveBeenCalledWith({ category: 'SPEAKER' })
    );
  });
});

describe('useLayoutTemplates', () => {
  it('calls listTemplates with isLayout=true', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.listTemplates).mockResolvedValue([mockLayoutTemplate]);

    const { result } = renderHook(() => useLayoutTemplates(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(emailTemplateService.listTemplates).toHaveBeenCalledWith({ isLayout: true });
  });
});

describe('useCreateEmailTemplate', () => {
  it('calls createTemplate and invalidates queries on success', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.createTemplate).mockResolvedValue({
      ...mockTemplate,
      isSystemTemplate: false,
    });

    const { result } = renderHook(() => useCreateEmailTemplate(), { wrapper: makeWrapper() });

    await result.current.mutateAsync({
      templateKey: 'my-custom',
      locale: 'de',
      category: 'SPEAKER',
      subject: 'Betreff',
      htmlBody: '<p>body</p>',
      isLayout: false,
      layoutKey: 'batbern-default',
    });

    expect(emailTemplateService.createTemplate).toHaveBeenCalledOnce();
  });
});

describe('useUpdateEmailTemplate', () => {
  it('calls updateTemplate with key, locale and req', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.updateTemplate).mockResolvedValue({
      ...mockTemplate,
      subject: 'New Subject',
    });

    const { result } = renderHook(() => useUpdateEmailTemplate(), { wrapper: makeWrapper() });

    await result.current.mutateAsync({
      templateKey: 'speaker-invitation',
      locale: 'de',
      req: { subject: 'New Subject' },
    });

    expect(emailTemplateService.updateTemplate).toHaveBeenCalledWith('speaker-invitation', 'de', {
      subject: 'New Subject',
    });
  });
});

describe('useDeleteEmailTemplate', () => {
  it('calls deleteTemplate with key and locale', async () => {
    const { emailTemplateService } = await import('@/services/emailTemplateService');
    vi.mocked(emailTemplateService.deleteTemplate).mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteEmailTemplate(), { wrapper: makeWrapper() });

    await result.current.mutateAsync({ templateKey: 'my-custom', locale: 'de' });

    expect(emailTemplateService.deleteTemplate).toHaveBeenCalledWith('my-custom', 'de');
  });
});
