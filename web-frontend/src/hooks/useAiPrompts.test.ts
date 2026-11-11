/**
 * useAiPrompts hooks on the MSW harness (Story 4.10b, Epic 17 test fidelity): the real
 * aiPromptService and apiClient run against a prompt store the handlers keep, so each mutation is
 * checked by its effect: read, mutate, read again.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import type { AiPromptResponse } from '@/services/aiPromptService';
import { useAiPrompts, useUpdateAiPrompt, useResetAiPrompt } from './useAiPrompts';

const DEFAULT_TEXT = 'Write a German event description';

let store: AiPromptResponse[];
let qc: QueryClient;

const wrapper = ({ children }: { children: React.ReactNode }) =>
  React.createElement(QueryClientProvider, { client: qc }, children);

beforeEach(() => {
  qc = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  store = [
    {
      promptKey: 'event_description',
      displayName: 'Event Description',
      promptText: 'Schreibe über Brünnimann’s Vortrag',
      defaultText: DEFAULT_TEXT,
      updatedAt: '2026-11-10T08:00:00Z',
    },
  ];
  api.on('listAiPrompts', () => store);
  api.on('updateAiPrompt', ({ params, body }) => {
    store = store.map((p) =>
      p.promptKey === params.promptKey ? { ...p, promptText: body?.promptText ?? p.promptText } : p
    );
    return store[0];
  });
  api.on('deleteAiPromptOverride', ({ params }) => {
    store = store.map((p) =>
      p.promptKey === params.promptKey ? { ...p, promptText: p.defaultText } : p
    );
    return store[0];
  });
});

const renderWith = <T>(useMutationHook: () => T) =>
  renderHook(() => ({ prompts: useAiPrompts(), mutation: useMutationHook() }), { wrapper });

describe('useAiPrompts', () => {
  it('should_loadThePrompts', async () => {
    const { result } = renderHook(() => useAiPrompts(), { wrapper });

    await waitFor(() => expect(result.current.data?.[0].promptKey).toBe('event_description'));
  });

  it('should_setIsError_when_listFails', async () => {
    api.on('listAiPrompts', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));

    const { result } = renderHook(() => useAiPrompts(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useUpdateAiPrompt', () => {
  it('should_showTheNewText_when_updated', async () => {
    const { result } = renderWith(useUpdateAiPrompt);
    await waitFor(() => expect(result.current.prompts.data).toHaveLength(1));

    await act(() =>
      result.current.mutation.mutateAsync({
        promptKey: 'event_description',
        promptText: 'Neu: O’Neill',
      })
    );

    await waitFor(() => expect(result.current.prompts.data?.[0].promptText).toBe('Neu: O’Neill'));
  });
});

describe('useResetAiPrompt', () => {
  it('should_showTheDefaultText_when_overrideDeleted', async () => {
    const { result } = renderWith(useResetAiPrompt);
    await waitFor(() => expect(result.current.prompts.data).toHaveLength(1));

    await act(() => result.current.mutation.mutateAsync('event_description'));

    await waitFor(() => expect(result.current.prompts.data?.[0].promptText).toBe(DEFAULT_TEXT));
    expect(api.calls('deleteAiPromptOverride')[0].params.promptKey).toBe('event_description');
  });

  it('should_setIsError_when_deleteFails', async () => {
    api.on('deleteAiPromptOverride', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));
    const { result } = renderWith(useResetAiPrompt);

    await act(async () => {
      await result.current.mutation.mutateAsync('event_description').catch(() => undefined);
    });

    await waitFor(() => expect(result.current.mutation.isError).toBe(true));
  });
});
