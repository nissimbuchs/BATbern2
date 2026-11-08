/**
 * useAiAssist Hooks Tests
 *
 * Coverage for:
 * - useAiGenerateDescription: POST /events/{eventCode}/ai/description
 * - useAiGenerateThemeImage: POST /events/{eventCode}/ai/theme-image
 * - useAiApplyThemeImage: POST apply + cache invalidation
 * - useAiAnalyzeAbstract: POST /speakers/{id}/ai/analyze-abstract
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import MockAdapter from 'axios-mock-adapter';

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

// HTTP is stubbed at the boundary: the real service and apiClient run.
let http: MockAdapter;
beforeEach(() => {
  http = new MockAdapter(apiClient);
});
afterEach(() => {
  http.restore();
});
const posted = () => http.history.post.map((r) => r.url);
const fetched = () => http.history.get.map((r) => r.url);

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
    http
      .onPost('/events/BAT142/ai/description')
      .reply(200, { description: 'Generated description' });

    const { result } = renderHook(() => useAiGenerateDescription('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(posted()).toEqual(['/events/BAT142/ai/description']);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.description).toBe('Generated description');
  });

  it('should throw mapped error on 503', async () => {
    http.onPost('/events/BAT142/ai/description').reply(503);

    const { result } = renderHook(() => useAiGenerateDescription('BAT142'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync().catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('aiAssist.disabled');
  });

  it('should throw generic error on other failures', async () => {
    http.onPost('/events/BAT142/ai/description').networkError();

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
  // 2026-11-07: generation takes ~40 s, the HTTP API ends requests at 30 s. POST starts a job
  // (202), the hook polls it until DONE or FAILED.
  let qc: QueryClient;
  const FAST = { pollMs: 1, maxWaitMs: 1000 };
  const started = { jobId: 'job-1', status: 'PENDING' };
  const jobUrl = '/events/BAT142/ai/theme-image/jobs/job-1';

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should start a job and return the image once the job is DONE', async () => {
    http.onPost('/events/BAT142/ai/theme-image').reply(202, started);
    http
      .onGet(jobUrl)
      .replyOnce(200, { jobId: 'job-1', status: 'PENDING' })
      .onGet(jobUrl)
      .replyOnce(200, {
        jobId: 'job-1',
        status: 'DONE',
        imageUrl: 'https://cdn.x.com/img.png',
        s3Key: 'k',
      });

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142', FAST), {
      wrapper: wrapper(qc),
    });

    let image: unknown;
    await act(async () => {
      image = await result.current.mutateAsync({});
    });

    expect(posted()).toEqual(['/events/BAT142/ai/theme-image']);
    expect(fetched()).toEqual([jobUrl, jobUrl]);
    expect(image).toEqual({ imageUrl: 'https://cdn.x.com/img.png', s3Key: 'k' });
  });

  it('should include seed query param when provided', async () => {
    http.onPost('/events/BAT142/ai/theme-image?seed=tech-clouds').reply(202, started);
    http.onGet(jobUrl).reply(200, {
      jobId: 'job-1',
      status: 'DONE',
      imageUrl: 'https://cdn.x.com/img.png',
      s3Key: 'k',
    });

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142', FAST), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({ seed: 'tech-clouds' });
    });

    expect(posted()).toEqual(['/events/BAT142/ai/theme-image?seed=tech-clouds']);
  });

  it('should fail when the job ends FAILED', async () => {
    http.onPost('/events/BAT142/ai/theme-image').reply(202, started);
    http.onGet(jobUrl).reply(200, { jobId: 'job-1', status: 'FAILED' });

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142', FAST), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({}).catch(() => undefined);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('aiAssist.error');
  });

  it('should give up when the job stays PENDING too long', async () => {
    http.onPost('/events/BAT142/ai/theme-image').reply(202, started);
    http.onGet(jobUrl).reply(200, { jobId: 'job-1', status: 'PENDING' });

    const { result } = renderHook(
      () => useAiGenerateThemeImage('BAT142', { pollMs: 1, maxWaitMs: 20 }),
      { wrapper: wrapper(qc) }
    );

    await act(async () => {
      await result.current.mutateAsync({}).catch(() => undefined);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should set isError when the start fails', async () => {
    http.onPost('/events/BAT142/ai/theme-image').reply(503);

    const { result } = renderHook(() => useAiGenerateThemeImage('BAT142', FAST), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync({}).catch(() => undefined);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(fetched()).toEqual([]);
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
    http.onPost('/events/BAT142/ai/theme-image/apply').reply(200);

    const { result } = renderHook(() => useAiApplyThemeImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ imageUrl: 'https://cdn.x.com/img.jpg' });
    });

    expect(posted()).toEqual(['/events/BAT142/ai/theme-image/apply']);
    expect(JSON.parse(http.history.post[0].data)).toEqual({
      imageUrl: 'https://cdn.x.com/img.jpg',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event cache on success', async () => {
    http.onPost('/events/BAT142/ai/theme-image/apply').reply(200);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useAiApplyThemeImage('BAT142'), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ imageUrl: 'https://cdn.x.com/img.jpg' });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    http.onPost('/events/BAT142/ai/theme-image/apply').reply(500);

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
    http.onPost('/speakers/sp-pool-1/ai/analyze-abstract').reply(200, response);

    const { result } = renderHook(() => useAiAnalyzeAbstract('sp-pool-1'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(posted()).toEqual(['/speakers/sp-pool-1/ai/analyze-abstract']);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.noPromotionScore).toBe(90);
  });

  it('should set isError on failure', async () => {
    http.onPost('/speakers/sp-pool-1/ai/analyze-abstract').reply(503);

    const { result } = renderHook(() => useAiAnalyzeAbstract('sp-pool-1'), {
      wrapper: wrapper(qc),
    });

    await act(async () => {
      await result.current.mutateAsync().catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
