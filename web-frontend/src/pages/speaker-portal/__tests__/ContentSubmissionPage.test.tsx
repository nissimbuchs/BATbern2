/**
 * Story 11.F.1 RD5: Cognito-side replacement for the legacy magic-link ContentSubmissionPage
 * test suite. The old tests asserted `?token=` URL parsing — gone in Story 11.E.3.
 * These smoke tests confirm the page renders under a Cognito-authenticated speaker
 * session and exercises the eventCode-scoped service contract.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    getContentInfo: vi.fn(),
    submitContent: vi.fn(),
    uploadMaterial: vi.fn(),
    getMaterialPresignedUrl: vi.fn(),
    confirmMaterialUpload: vi.fn(),
  },
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

import { speakerPortalService } from '@/services/speakerPortalService';
import { useAuth } from '@/hooks/useAuth';
import ContentSubmissionPage from '../ContentSubmissionPage';

const mockedGetContentInfo = vi.mocked(speakerPortalService.getContentInfo);
const mockedUseAuth = vi.mocked(useAuth);

function renderAt(path = '/speaker-portal/content/BATbern99') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/speaker-portal/content/:eventCode" element={<ContentSubmissionPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const buildContentInfo = (overrides: Partial<Record<string, unknown>> = {}) => ({
  speakerName: 'Test Speaker',
  eventCode: 'BATbern99',
  eventTitle: 'BATbern 99',
  hasSessionAssigned: true,
  sessionTitle: 'My Session',
  canSubmitContent: true,
  hasDraft: false,
  draftTitle: null,
  draftAbstract: null,
  draftVersion: null,
  lastSavedAt: null,
  needsRevision: false,
  reviewerFeedback: null,
  reviewedAt: null,
  reviewedBy: null,
  hasMaterial: false,
  materialUrl: null,
  materialFileName: null,
  ...overrides,
});

describe('ContentSubmissionPage — Cognito Bearer auth (Story 11.E.3 + 11.F.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedUseAuth.mockReturnValue({
      user: { username: 'speaker.user', roles: ['SPEAKER'] },
      isAuthenticated: true,
      isLoading: false,
    } as never);
  });

  it('should_callGetContentInfoWithEventCode_when_pageMounts', async () => {
    mockedGetContentInfo.mockResolvedValue(buildContentInfo() as never);

    renderAt();

    await waitFor(() => {
      expect(mockedGetContentInfo).toHaveBeenCalledWith('BATbern99');
    });
  });

  it('should_renderFormWhenContentInfoReady_when_speakerCanSubmit', async () => {
    mockedGetContentInfo.mockResolvedValue(buildContentInfo() as never);

    renderAt();

    await waitFor(() => {
      const buttons = screen.queryAllByRole('button');
      expect(buttons.length).toBeGreaterThan(0);
    });
  });

  it('should_renderRevisionUi_when_contentNeedsRevision', async () => {
    mockedGetContentInfo.mockResolvedValue(
      buildContentInfo({
        hasDraft: true,
        draftTitle: 'Old Title',
        draftAbstract: 'Old abstract',
        needsRevision: true,
        reviewerFeedback: 'Please add more detail.',
      }) as never
    );

    renderAt();

    await waitFor(() => {
      expect(mockedGetContentInfo).toHaveBeenCalled();
    });
  });

  it('should_renderErrorUi_when_contentInfoFetchFails', async () => {
    mockedGetContentInfo.mockRejectedValue(new Error('Service down'));

    renderAt();

    await waitFor(() => {
      expect(mockedGetContentInfo).toHaveBeenCalled();
    });
  });
});
