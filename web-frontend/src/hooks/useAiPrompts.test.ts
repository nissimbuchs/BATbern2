/**
 * useAiPrompts Hook Tests
 *
 * Coverage for:
 * - useAiPrompts: fetch list of AI prompts
 * - useUpdateAiPrompt: mutation + cache invalidation
 * - useResetAiPrompt: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/aiPromptService', () => ({
  listAiPrompts: vi.fn(),
  updateAiPrompt: vi.fn(),
  resetAiPrompt: vi.fn(),
}));

import { listAiPrompts, updateAiPrompt, resetAiPrompt } from '@/services/aiPromptService';
import { useAiPrompts, useUpdateAiPrompt, useResetAiPrompt } from './useAiPrompts';

const mockList = vi.mocked(listAiPrompts);
const mockUpdate = vi.mocked(updateAiPrompt);
const mockReset = vi.mocked(resetAiPrompt);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_PROMPTS = [
  { promptKey: 'event_description', promptText: 'Describe the event', isDefault: false },
  { promptKey: 'theme_image', promptText: 'Generate image', isDefault: true },
];

describe('useAiPrompts', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch and return AI prompts list', async () => {
    mockList.mockResolvedValue(MOCK_PROMPTS as never);

    const { result } = renderHook(() => useAiPrompts(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockList).toHaveBeenCalled();
    expect(result.current.data).toEqual(MOCK_PROMPTS);
  });

  it('should set isError on failure', async () => {
    mockList.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useAiPrompts(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should start in loading state', () => {
    mockList.mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useAiPrompts(), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(true);
  });
});

describe('useUpdateAiPrompt', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateAiPrompt with promptKey and promptText', async () => {
    mockUpdate.mockResolvedValue(MOCK_PROMPTS[0] as never);

    const { result } = renderHook(() => useUpdateAiPrompt(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        promptKey: 'event_description',
        promptText: 'New prompt text',
      });
    });

    expect(mockUpdate).toHaveBeenCalledWith('event_description', 'New prompt text');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate aiPrompts cache on success', async () => {
    mockUpdate.mockResolvedValue(MOCK_PROMPTS[0] as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateAiPrompt(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ promptKey: 'event_description', promptText: 'text' });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['aiPrompts'] });
  });

  it('should set isError on failure', async () => {
    mockUpdate.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useUpdateAiPrompt(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ promptKey: 'missing', promptText: 'x' }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useResetAiPrompt', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call resetAiPrompt with the promptKey', async () => {
    mockReset.mockResolvedValue(MOCK_PROMPTS[1] as never);

    const { result } = renderHook(() => useResetAiPrompt(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('theme_image');
    });

    expect(mockReset).toHaveBeenCalledWith('theme_image');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate aiPrompts cache on success', async () => {
    mockReset.mockResolvedValue(MOCK_PROMPTS[1] as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useResetAiPrompt(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('theme_image');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['aiPrompts'] });
  });

  it('should set isError on failure', async () => {
    mockReset.mockRejectedValue(new Error('Server error'));

    const { result } = renderHook(() => useResetAiPrompt(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('event_description').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
