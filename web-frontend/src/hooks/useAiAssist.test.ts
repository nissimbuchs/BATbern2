/**
 * useAiAssist Hooks Tests
 *
 * Coverage for:
 * - useAiGenerateDescription: POST /events/{eventCode}/ai/description
 * - useAiGenerateThemeImage: POST /events/{eventCode}/ai/theme-image
 * - useAiApplyThemeImage: POST apply + cache invalidation
 * - useAiAnalyzeAbstract: POST /speakers/{id}/ai/analyze-abstract
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/apiClient', () => ({
  default: { post: vi.fn() },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

import apiClient from '@/services/api/apiClient';
import {
  useAiGenerateDescription,
  useAiGenerateThemeImage,
  useAiApplyThemeImage,
  useAiAnalyzeAbstract,
} from './useAiAssist';

const mockPost = vi.mocked(apiClient.post);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

// ── useAiGenerateDescription ──────────────────────────────────────────────────

describe('useAiGenerateDescription', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should POST to event AI description endpoint', async () => {
    mockPost.mockResolvedValue({ data: { description: 'Generated description' } });

    const { result } = renderHook(() => useAiGenerateDescription('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(mockPost).toHaveBeenCalledWith('/events/BAT142/ai/description');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.description).toBe('Generated description');
  });

  it('should throw mapped error on 503', async () => {
    mockPost.mockRejectedValue({ response: { status: 503 } });

    const { result } = renderHook(() => useAiGenerateDescription('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync().catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should throw generic error on other failures', async () => {
    mockPost.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useAiGenerateDescription('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync().catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAiGenerateThemeImage ───────────────────────────────────────────────────

describe('useAiGenerateThemeImage', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should POST to theme-image endpoint without seed', async () => {
    mockPost.mockResolvedValue({ data: { imageUrl: 'https://cdn.x.com/img.jpg', s3Key: 'k' } });

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({});
    });

    expect(mockPost).toHaveBeenCalledWith('/events/BAT142/ai/theme-image');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should include seed query param when provided', async () => {
    mockPost.mockResolvedValue({ data: { imageUrl: 'https://cdn.x.com/img.jpg', s3Key: 'k' } });

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ seed: 'tech-clouds' });
    });

    expect(mockPost).toHaveBeenCalledWith('/events/BAT142/ai/theme-image?seed=tech-clouds');
  });

  it('should set isError on failure', async () => {
    mockPost.mockRejectedValue(new Error('AI unavailable'));

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({}).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAiApplyThemeImage ──────────────────────────────────────────────────────

describe('useAiApplyThemeImage', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should POST apply with imageUrl', async () => {
    mockPost.mockResolvedValue({ data: undefined });

    const { result } = renderHook(() => useAiApplyThemeImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ imageUrl: 'https://cdn.x.com/img.jpg' });
    });

    expect(mockPost).toHaveBeenCalledWith('/events/BAT142/ai/theme-image/apply', {
      imageUrl: 'https://cdn.x.com/img.jpg',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    mockPost.mockResolvedValue({ data: undefined });
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useAiApplyThemeImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ imageUrl: 'https://cdn.x.com/img.jpg' });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockPost.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useAiApplyThemeImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ imageUrl: 'https://cdn.x.com/img.jpg' }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAiAnalyzeAbstract ──────────────────────────────────────────────────────

describe('useAiAnalyzeAbstract', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should POST to speaker analyze-abstract endpoint', async () => {
    const response = {
      noPromotionScore: 90,
      noPromotionFeedback: 'Good',
      lessonsLearnedScore: 85,
      lessonsLearnedFeedback: 'Clear lessons',
      wordCount: 150,
      shortenedAbstract: null,
    };
    mockPost.mockResolvedValue({ data: response });

    const { result } = renderHook(() => useAiAnalyzeAbstract('sp-pool-1'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(mockPost).toHaveBeenCalledWith('/speakers/sp-pool-1/ai/analyze-abstract');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.noPromotionScore).toBe(90);
  });

  it('should set isError on failure', async () => {
    mockPost.mockRejectedValue(new Error('Service unavailable'));

    const { result } = renderHook(() => useAiAnalyzeAbstract('sp-pool-1'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync().catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
