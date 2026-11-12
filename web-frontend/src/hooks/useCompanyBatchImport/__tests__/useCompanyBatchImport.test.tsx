/**
 * useCompanyBatchImport Hook Tests
 *
 * The logo is imported on the server (`POST /uploads/company-logo/import`, Story 17.11). The
 * backend answers through the MSW harness, which checks every request against the specs; the
 * real services run.
 */

import type { ReactNode } from 'react';
import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
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
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('should_importLogoThenCreate_when_newCompanyHasLogo', async () => {
    api.on(
      'importFileUpload',
      () => ({
        uploadId: 'up-1',
        purpose: 'company-logo' as const,
        status: 'CONFIRMED' as const,
        fileUrl: null,
      }),
      { status: 201 }
    );
    const { result } = renderHook(() => useCompanyBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCompanies>> | undefined;
    await act(async () => {
      outcome = await result.current.importCompanies([candidate()]);
    });

    expect(outcome).toMatchObject({ total: 1, success: 1, failed: 0 });
    expect(api.allCalls().map((c) => c.key)).toEqual(['importFileUpload', 'createCompany']);
    expect(api.calls('importFileUpload')[0].params).toEqual({ purpose: 'company-logo' });
    expect(api.calls('importFileUpload')[0].body).toEqual({
      url: 'https://img.example.com/acme.png',
      fileName: 'acme-logo',
    });
    expect(api.calls('createCompany')[0].body).toMatchObject({
      name: 'acme',
      logoUploadId: 'up-1',
    });
  });

  it('should_createWithoutLogo_when_logoImportFails', async () => {
    api.on('importFileUpload', () =>
      HttpResponse.json({ status: 502, message: 'upstream down' }, { status: 502 })
    );
    const { result } = renderHook(() => useCompanyBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importCompanies>> | undefined;
    await act(async () => {
      outcome = await result.current.importCompanies([candidate()]);
    });

    expect(outcome).toMatchObject({ success: 1, failed: 0 });
    expect(api.calls('createCompany')[0].body).not.toHaveProperty('logoUploadId');
  });

  it('should_skipLogoImport_when_noLogoUrl', async () => {
    const { result } = renderHook(() => useCompanyBatchImport(), { wrapper });

    await act(async () => {
      await result.current.importCompanies([candidate({ logoUrl: null })]);
    });

    expect(api.allCalls().map((c) => c.key)).toEqual(['createCompany']);
  });
});
