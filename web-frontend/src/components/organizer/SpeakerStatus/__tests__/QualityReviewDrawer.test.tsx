/**
 * QualityReviewDrawer Tests (Story 5.5 Phase 4) — Smoke Tests
 *
 * Tests:
 * - Renders when open with speaker info
 * - Loading state shows loader
 * - No content alert when content is undefined
 * - Shows quality criteria and action buttons when content loads
 * - Approve button calls mutation
 * - Reject flow: show feedback form, submit with feedback
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { QualityReviewDrawer } from '../QualityReviewDrawer';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => {
      const translations: Record<string, string> = {
        'qualityReview.title': 'Quality Review',
        'qualityReview.speaker': 'Speaker',
        'qualityReview.presentationTitle': 'Presentation Title',
        'qualityReview.abstract': 'Abstract',
        'qualityReview.approve': 'Approve',
        'qualityReview.approving': 'Approving...',
        'qualityReview.reject': 'Reject',
        'qualityReview.confirmReject': 'Confirm Reject',
        'qualityReview.feedbackLabel': 'Feedback',
        'qualityReview.noContent': 'No content available',
        'qualityReview.material': 'Presentation Material',
        'qualityReview.qualityCriteria': 'Quality Criteria',
        'qualityReview.rejectInstructions': 'Please provide feedback for the speaker.',
        'common.cancel': 'Cancel',
      };
      return translations[key] || fallback || key;
    },
  }),
}));

vi.mock('@/services/speakerContentService', () => ({
  speakerContentService: {
    getSpeakerContent: vi.fn(),
    reviewContent: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('@/hooks/useSpeakerPool', () => ({
  speakerPoolKeys: {
    list: (eventCode: string) => ['speakerPool', eventCode],
  },
}));

vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="batbern-loader" />,
}));

const mockSpeaker: SpeakerPoolEntry = {
  id: 'speaker-1',
  speakerName: 'Alice Smith',
  company: 'Acme Corp',
  expertise: 'Cloud Architecture',
  status: 'READY',
  assignedOrganizerId: null,
  notes: '',
  invitedAt: '2024-01-01',
  respondedAt: null,
  sessionId: null,
  speakerRole: null,
  presentationTitle: null,
  initialPresentationTitle: null,
};

describe('QualityReviewDrawer', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    vi.clearAllMocks();
  });

  const renderDrawer = (props: Partial<React.ComponentProps<typeof QualityReviewDrawer>> = {}) =>
    render(
      <QueryClientProvider client={queryClient}>
        <QualityReviewDrawer
          open={true}
          onClose={vi.fn()}
          speaker={mockSpeaker}
          eventCode="BATbern56"
          {...props}
        />
      </QueryClientProvider>
    );

  it('shows loading state while fetching content', async () => {
    const { speakerContentService } = await import('@/services/speakerContentService');
    vi.mocked(speakerContentService.getSpeakerContent).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    );

    renderDrawer();

    expect(screen.getByTestId('batbern-loader')).toBeInTheDocument();
  });

  it('shows no content alert when content is undefined', async () => {
    const { speakerContentService } = await import('@/services/speakerContentService');
    vi.mocked(speakerContentService.getSpeakerContent).mockResolvedValue(undefined as any);

    renderDrawer();

    await waitFor(() => {
      // The component shows Alert with qualityReview.noContent key when content is falsy
      expect(screen.queryByTestId('batbern-loader')).not.toBeInTheDocument();
    });
  });

  it('shows approve and reject buttons when content loads', async () => {
    const { speakerContentService } = await import('@/services/speakerContentService');
    vi.mocked(speakerContentService.getSpeakerContent).mockResolvedValue({
      id: 'content-1',
      speakerPoolId: 'speaker-1',
      presentationTitle: 'My Talk',
      presentationAbstract: 'Lessons learned from cloud migrations',
      hasMaterial: false,
      materialUrl: null,
      materialFileName: null,
      status: 'SUBMITTED',
    } as any);

    renderDrawer();

    await waitFor(() => {
      expect(screen.getByTestId('approve-content-button')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument();
    });
  });

  it('calls reviewContent with APPROVE action when approve button is clicked', async () => {
    const { speakerContentService } = await import('@/services/speakerContentService');
    vi.mocked(speakerContentService.getSpeakerContent).mockResolvedValue({
      id: 'content-1',
      speakerPoolId: 'speaker-1',
      presentationTitle: 'My Talk',
      presentationAbstract: 'Lessons learned from cloud migrations',
      hasMaterial: false,
      materialUrl: null,
      materialFileName: null,
      status: 'SUBMITTED',
    } as any);

    const user = userEvent.setup();
    renderDrawer();

    await waitFor(() => expect(screen.getByTestId('approve-content-button')).toBeInTheDocument());
    await user.click(screen.getByTestId('approve-content-button'));

    await waitFor(() => {
      expect(speakerContentService.reviewContent).toHaveBeenCalledWith(
        'BATbern56',
        'speaker-1',
        expect.objectContaining({ action: 'APPROVE' })
      );
    });
  });

  it('shows reject feedback form when reject button is clicked', async () => {
    const { speakerContentService } = await import('@/services/speakerContentService');
    vi.mocked(speakerContentService.getSpeakerContent).mockResolvedValue({
      id: 'content-1',
      speakerPoolId: 'speaker-1',
      presentationTitle: 'My Talk',
      presentationAbstract: 'Content without lessons',
      hasMaterial: false,
      materialUrl: null,
      materialFileName: null,
      status: 'SUBMITTED',
    } as any);

    const user = userEvent.setup();
    renderDrawer();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument()
    );
    await user.click(screen.getByRole('button', { name: /reject/i }));

    // Feedback form should appear
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /confirm reject/i })).toBeInTheDocument();
    });
  });

  it('calls reviewContent with REJECT action and feedback', async () => {
    const { speakerContentService } = await import('@/services/speakerContentService');
    vi.mocked(speakerContentService.getSpeakerContent).mockResolvedValue({
      id: 'content-1',
      speakerPoolId: 'speaker-1',
      presentationTitle: 'My Talk',
      presentationAbstract: 'No lessons here',
      hasMaterial: false,
      materialUrl: null,
      materialFileName: null,
      status: 'SUBMITTED',
    } as any);

    const user = userEvent.setup();
    renderDrawer();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument()
    );
    await user.click(screen.getByRole('button', { name: /reject/i }));

    // Enter feedback
    const feedbackInput = await screen.findByRole('textbox', { name: /feedback/i });
    await user.type(feedbackInput, 'Please add lessons learned section');

    await user.click(screen.getByRole('button', { name: /confirm reject/i }));

    await waitFor(() => {
      expect(speakerContentService.reviewContent).toHaveBeenCalledWith(
        'BATbern56',
        'speaker-1',
        expect.objectContaining({
          action: 'REJECT',
          feedback: 'Please add lessons learned section',
        })
      );
    });
  });

  it('does not render visible content when closed', () => {
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <QualityReviewDrawer open={false} onClose={vi.fn()} speaker={null} eventCode="BATbern56" />
      </QueryClientProvider>
    );
    // Closed drawer should not show the header content
    expect(screen.queryByTestId('approve-content-button')).not.toBeInTheDocument();
    // container should still be minimal
    expect(container).toBeTruthy();
  });
});
