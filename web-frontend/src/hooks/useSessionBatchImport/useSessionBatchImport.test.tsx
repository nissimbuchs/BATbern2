/**
 * useSessionBatchImport on the MSW harness (Story 4.10b): when the second chunk of an event's
 * import fails, the result still counts the 100 sessions the first chunk saved, and only the item
 * after them is marked failed.
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import React from 'react';
import { api } from '@/test/msw/api';
import { createSessionImportCandidates } from '@/utils/sessionImport';
import type { LegacySession } from '@/types/sessionImport.types';
import { useSessionBatchImport } from './useSessionBatchImport';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>
);

describe('useSessionBatchImport', () => {
  it('should_keepTheSavedChunk_when_theSecondChunkFails', async () => {
    const sessions = Array.from({ length: 101 }, (_, i) => ({
      bat: 142,
      title: `Session ${i + 1}`,
    })) as LegacySession[];
    api.on(
      'createSession',
      ({ body }) => {
        const items = body?.imports ?? [];
        if (items.length === 1) {
          return HttpResponse.json({ message: 'Import failed' }, { status: 500 });
        }
        return {
          totalProcessed: items.length,
          successfullyCreated: items.length,
          updated: 0,
          skipped: 0,
          failed: 0,
          details: items.map((item) => ({
            title: item.title,
            status: 'success' as const,
            message: 'Created',
            sessionSlug: null,
          })),
        };
      },
      { status: 200 }
    );
    const { result } = renderHook(() => useSessionBatchImport(), { wrapper });

    let imported: Awaited<ReturnType<typeof result.current.importSessions>> | undefined;
    await act(async () => {
      imported = await result.current.importSessions(createSessionImportCandidates(sessions));
    });

    expect(imported?.successfullyCreated).toBe(100);
    expect(imported?.failed).toBe(1);
    expect(imported?.totalProcessed).toBe(101);
    expect(imported?.details[100]).toMatchObject({ title: 'Session 101', status: 'failed' });
    expect(result.current.candidates[0].importStatus).toBe('success');
    expect(result.current.candidates[100].importStatus).toBe('error');
  });
});
