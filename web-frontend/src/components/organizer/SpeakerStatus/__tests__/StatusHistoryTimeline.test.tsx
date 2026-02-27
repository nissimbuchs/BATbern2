/**
 * StatusHistoryTimeline Tests (Story 5.4) — Smoke Tests
 *
 * Tests:
 * - Loading state shows loading text
 * - Error state shows error alert
 * - Empty state shows no history message
 * - Timeline renders with history items
 * - Status transitions are displayed
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatusHistoryTimeline } from '../StatusHistoryTimeline';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => {
      const translations: Record<string, string> = {
        'common:loading': 'Loading...',
        'common:errors.loadFailed': 'Failed to load',
        'organizer:speakerStatus.noHistory': 'No status history available',
        'organizer:speakerStatus.historyTitle': 'Status History',
        'organizer:speakerStatus.changedBy': 'Changed by',
        'organizer:speakerStatus.IDENTIFIED': 'Identified',
        'organizer:speakerStatus.CONTACTED': 'Contacted',
        'organizer:speakerStatus.ACCEPTED': 'Accepted',
      };
      return translations[key] || fallback || key;
    },
  }),
}));

vi.mock('@/services/speakerStatusService', () => ({
  speakerStatusService: {
    getStatusHistory: vi.fn(),
  },
}));

const mockHistoryItems = [
  {
    id: 'hist-1',
    speakerPoolId: 'speaker-1',
    previousStatus: 'IDENTIFIED',
    newStatus: 'CONTACTED',
    changedAt: '2024-02-01T10:00:00Z',
    changedByUsername: 'organizer@test.com',
    changeReason: 'Sent invitation email',
  },
  {
    id: 'hist-2',
    speakerPoolId: 'speaker-1',
    previousStatus: 'CONTACTED',
    newStatus: 'ACCEPTED',
    changedAt: '2024-02-05T14:30:00Z',
    changedByUsername: 'organizer@test.com',
    changeReason: null,
  },
];

describe('StatusHistoryTimeline', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    });
    vi.clearAllMocks();
  });

  const renderTimeline = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <StatusHistoryTimeline speakerId="speaker-1" eventCode="BATbern56" />
      </QueryClientProvider>
    );

  it('shows loading state while fetching history', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    );

    renderTimeline();

    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('shows error alert when fetch fails', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockRejectedValue(new Error('Network error'));

    renderTimeline();

    await waitFor(() => {
      expect(screen.getByText('Failed to load')).toBeInTheDocument();
    });
  });

  it('shows no history message when history is empty', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockResolvedValue([]);

    renderTimeline();

    await waitFor(() => {
      expect(screen.getByText('No status history available')).toBeInTheDocument();
    });
  });

  it('renders history title when items exist', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockResolvedValue(mockHistoryItems as any);

    renderTimeline();

    await waitFor(() => {
      expect(screen.getByText('Status History')).toBeInTheDocument();
    });
  });

  it('renders status transitions for each history item', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockResolvedValue(mockHistoryItems as any);

    renderTimeline();

    await waitFor(() => {
      // Should show the organizer who made the change
      const changedByElements = screen.getAllByText(/Changed by/i);
      expect(changedByElements.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('renders change reason when provided', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockResolvedValue(mockHistoryItems as any);

    renderTimeline();

    await waitFor(() => {
      expect(screen.getByText('"Sent invitation email"')).toBeInTheDocument();
    });
  });

  it('calls getStatusHistory with correct parameters', async () => {
    const { speakerStatusService } = await import('@/services/speakerStatusService');
    vi.mocked(speakerStatusService.getStatusHistory).mockResolvedValue([]);

    renderTimeline();

    await waitFor(() => {
      expect(speakerStatusService.getStatusHistory).toHaveBeenCalledWith('BATbern56', 'speaker-1');
    });
  });
});
