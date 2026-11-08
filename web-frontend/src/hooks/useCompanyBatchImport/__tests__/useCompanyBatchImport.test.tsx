/**
 * useCompanyBatchImport Hook Tests
 *
 * The server-side logo upload moved into companyApi.uploadLogoFromUrl. HTTP is stubbed at the
 * boundary with axios-mock-adapter on apiClient; the real services run.
 */

import type { ReactNode } from 'react';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';
import { useCompanyBatchImport } from '../useCompanyBatchImport';
import type { ImportCandidate } from '@/types/companyImport.types';

const candidate = (overrides: Partial<ImportCandidate> = {}): ImportCandidate =>
  ({
    source: {},
    apiPayload: { name: 'acme', displayName: 'ACME AG' },
    logoUrl: 'https://img.example.com/acme.png',
    importStatus: 'pending',
    ...overrides,
  }) as unknown as ImportCandidate;

describe('useCompanyBatchImport', () => {
  let http: MockAdapter;
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    http = new MockAdapter(apiClient);
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  afterEach(() => {
    http.restore();
  });

  it('should_uploadLogoFromUrlThenCreate_when_newCompanyHasLogo', async () => {
    http.onPost('/logos/upload-from-url').reply(200, { uploadId: 'up-1' });
    http.onPost('/companies').reply(201, { name: 'acme' });
    const { result } = renderHook(() => useCompanyBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCompanies>> | undefined;
    await act(async () => {
      outcome = await result.current.importCompanies([candidate()]);
    });

    expect(outcome).toMatchObject({ total: 1, success: 1, failed: 0 });
    expect(http.history.post.map((r) => r.url)).toEqual(['/logos/upload-from-url', '/companies']);
    expect(JSON.parse(http.history.post[0].data)).toEqual({
      url: 'https://img.example.com/acme.png',
      filename: 'acme-logo',
    });
    expect(JSON.parse(http.history.post[1].data)).toMatchObject({
      name: 'acme',
      logoUploadId: 'up-1',
    });
  });

  it('should_createWithoutLogo_when_logoUploadFails', async () => {
    http.onPost('/logos/upload-from-url').reply(502);
    http.onPost('/companies').reply(201, { name: 'acme' });
    const { result } = renderHook(() => useCompanyBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCompanies>> | undefined;
    await act(async () => {
      outcome = await result.current.importCompanies([candidate()]);
    });

    expect(outcome).toMatchObject({ success: 1, failed: 0 });
    expect(JSON.parse(http.history.post[1].data)).not.toHaveProperty('logoUploadId');
  });

  it('should_skipLogoUpload_when_noLogoUrl', async () => {
    http.onPost('/companies').reply(201, { name: 'acme' });
    const { result } = renderHook(() => useCompanyBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCompanies([candidate({ logoUrl: null })]);
    });

    expect(http.history.post.map((r) => r.url)).toEqual(['/companies']);
  });
});
