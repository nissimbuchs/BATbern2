/**
 * useSpeakerBatchImport Hook Tests
 *
 * The portrait is imported on the server (`POST /uploads/profile-picture/import`) and attached
 * with a confirm that names the user as target (Story 17.11a). The backend answers through the MSW
 * harness, which checks every request against the specs; the real services run.
 */

import type { ReactNode } from 'react';
import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { aUser } from '@/test/fixtures';
import { useSpeakerBatchImport } from '../useSpeakerBatchImport';
import type { SpeakerImportCandidate } from '@/types/speakerImport.types';

const PORTRAIT = 'https://img.example.com/jane.jpg';

const candidate = (overrides: Partial<SpeakerImportCandidate> = {}): SpeakerImportCandidate =>
  ({
    source: {},
    apiPayload: {
      firstName: 'Jane',
      lastName: 'Doe',
      email: 'jane.doe@example.com',
      initialRoles: ['SPEAKER'],
    },
    importStatus: 'pending',
    portraitUrl: PORTRAIT,
    ...overrides,
  }) as unknown as SpeakerImportCandidate;

/** The import answers with a CONFIRMED upload, the confirm with the attached picture. */
const portraitAnswers = () => {
  api.on(
    'importFileUpload',
    () => ({
      uploadId: 'up-1',
      purpose: 'profile-picture' as const,
      status: 'CONFIRMED' as const,
      fileUrl: null,
    }),
    { status: 201 }
  );
  api.on('confirmFileUpload', () => ({
    uploadId: 'up-1',
    purpose: 'profile-picture' as const,
    status: 'ASSOCIATED' as const,
    fileUrl: 'https://cdn.batbern.ch/profile-pictures/jane.doe/profile-up-1.jpeg',
  }));
};

describe('useSpeakerBatchImport', () => {
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it('should_uploadPortraitForCreatedUser_when_newSpeakerHasPortrait', async () => {
    api.on('createUser', () => aUser({ id: 'jane.doe' }), { status: 201 });
    portraitAnswers();
    const { result } = renderHook(() => useSpeakerBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importSpeakers>> | undefined;
    await act(async () => {
      outcome = await result.current.importSpeakers([candidate()]);
    });

    expect(outcome).toMatchObject({ total: 1, success: 1, failed: 0 });
    expect(api.allCalls().map((c) => c.key)).toEqual([
      'createUser',
      'importFileUpload',
      'confirmFileUpload',
    ]);
    expect(api.calls('importFileUpload')[0].params).toEqual({ purpose: 'profile-picture' });
    expect(api.calls('importFileUpload')[0].body).toEqual({
      url: PORTRAIT,
      fileName: 'Jane Doe-portrait',
    });
    expect(api.calls('confirmFileUpload')[0].params).toEqual({
      purpose: 'profile-picture',
      uploadId: 'up-1',
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { username: 'jane.doe' } });
  });

  it('should_stillSucceed_when_portraitUploadFails', async () => {
    api.on('createUser', () => aUser({ id: 'jane.doe' }), { status: 201 });
    api.on('importFileUpload', () => HttpResponse.json({ status: 502 }, { status: 502 }));
    const { result } = renderHook(() => useSpeakerBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importSpeakers>> | undefined;
    await act(async () => {
      outcome = await result.current.importSpeakers([candidate()]);
    });

    expect(outcome).toMatchObject({ success: 1, failed: 0 });
    expect(api.calls('confirmFileUpload')).toHaveLength(0);
  });

  it('should_uploadOnlyPortrait_when_existingUserChangedPortrait', async () => {
    portraitAnswers();
    const { result } = renderHook(() => useSpeakerBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importSpeakers>> | undefined;
    await act(async () => {
      outcome = await result.current.importSpeakers([
        candidate({
          existingUser: { username: 'jane.doe' } as SpeakerImportCandidate['existingUser'],
          hasChanges: true,
          changedFields: ['portrait'],
        }),
      ]);
    });

    expect(outcome).toMatchObject({ updated: 1 });
    expect(api.allCalls().map((c) => c.key)).toEqual(['importFileUpload', 'confirmFileUpload']);
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { username: 'jane.doe' } });
  });
});
