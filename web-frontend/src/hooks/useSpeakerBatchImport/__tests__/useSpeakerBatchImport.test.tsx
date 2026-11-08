/**
 * useSpeakerBatchImport Hook Tests
 *
 * The portrait upload-from-url moved into userApi.uploadProfilePictureFromUrl. HTTP is stubbed
 * at the boundary with axios-mock-adapter on apiClient; the real services run.
 */

import type { ReactNode } from 'react';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';
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

describe('useSpeakerBatchImport', () => {
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

  it('should_uploadPortraitForCreatedUser_when_newSpeakerHasPortrait', async () => {
    http.onPost('/users').reply(201, { id: 'jane.doe' });
    http
      .onPost('/users/jane.doe/profile-picture/upload-from-url')
      .reply(200, { profilePictureUrl: 'https://cdn.example.com/jane.jpg' });
    const { result } = renderHook(() => useSpeakerBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importSpeakers>> | undefined;
    await act(async () => {
      outcome = await result.current.importSpeakers([candidate()]);
    });

    expect(outcome).toMatchObject({ total: 1, success: 1, failed: 0 });
    expect(http.history.post.map((r) => r.url)).toEqual([
      '/users',
      '/users/jane.doe/profile-picture/upload-from-url',
    ]);
    expect(JSON.parse(http.history.post[1].data)).toEqual({
      url: PORTRAIT,
      filename: 'Jane Doe-portrait',
    });
  });

  it('should_stillSucceed_when_portraitUploadFails', async () => {
    http.onPost('/users').reply(201, { id: 'jane.doe' });
    http.onPost('/users/jane.doe/profile-picture/upload-from-url').reply(502);
    const { result } = renderHook(() => useSpeakerBatchImport(), { wrapper });

    let outcome: Awaited<ReturnType<typeof result.current.importSpeakers>> | undefined;
    await act(async () => {
      outcome = await result.current.importSpeakers([candidate()]);
    });

    expect(outcome).toMatchObject({ success: 1, failed: 0 });
  });

  it('should_uploadOnlyPortrait_when_existingUserChangedPortrait', async () => {
    http
      .onPost('/users/jane.doe/profile-picture/upload-from-url')
      .reply(200, { profilePictureUrl: 'https://cdn.example.com/jane.jpg' });
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
    expect(http.history.post.map((r) => r.url)).toEqual([
      '/users/jane.doe/profile-picture/upload-from-url',
    ]);
  });
});
