/**
 * Speaker Status Dashboard Tests (Story 5.4)
 *
 * Tests for SpeakerStatusDashboard component
 * Coverage:
 * - Status summary display
 * - Progress bar calculations
 * - Threshold indicators
 * - Overflow warnings
 * - React Query integration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerStatusDashboard } from '../SpeakerStatusDashboard';
import { speakerStatusService } from '@/services/speakerStatusService';
import { speakerPoolService } from '@/services/speakerPoolService';
import type { components } from '@/types/generated/speakers-api.types';

type StatusSummaryResponse = components['schemas']['StatusSummaryResponse'];

// Mock services
vi.mock('@/services/speakerStatusService', () => ({
  speakerStatusService: {
    getStatusSummary: vi.fn(),
  },
}));

vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    getSpeakerPool: vi.fn(),
  },
}));

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'organizer:speakerStatus.title': 'Speaker Status',
        'organizer:speakerStatus.statusSummary': 'Status Summary',
        'organizer:speakerStatus.progress': `${params?.accepted} / ${params?.required} speakers accepted`,
        'organizer:speakerStatus.thresholdMet': 'Minimum threshold met ✓',
        'organizer:speakerStatus.thresholdNotMet': `Minimum threshold not met (${params?.count} / ${params?.required})`,
        'organizer:speakerStatus.overflowDetected': `Overflow detected (${params?.count} > ${params?.max})`,
        'organizer:speakerStatus.open': 'Open',
        'organizer:speakerStatus.contacted': 'Contacted',
        'organizer:speakerStatus.ready': 'Ready',
        'organizer:speakerStatus.accepted': 'Accepted',
        'organizer:speakerStatus.declined': 'Declined',
        'common:loading': 'Loading...',
        'common:errors.loadFailed': 'Failed to load',
      };
      return translations[key] || key;
    },
  }),
}));

describe('SpeakerStatusDashboard', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    vi.clearAllMocks();

    // Default mock for speaker pool (empty array)
    vi.mocked(speakerPoolService.getSpeakerPool).mockResolvedValue([]);
  });

  const renderWithQuery = (component: React.ReactElement) => {
    return render(<QueryClientProvider client={queryClient}>{component}</QueryClientProvider>);
  };

  describe('Loading State', () => {
    it('should show loading indicator while fetching data', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should show error message when fetch fails', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockRejectedValue(
        new Error('Network error')
      );

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Failed to load')).toBeInTheDocument();
      });
    });
  });

  describe('Success State - Normal Scenario', () => {
    const mockSummary: StatusSummaryResponse = {
      eventCode: 'BATbern56',
      statusCounts: {
        open: 2,
        contacted: 5,
        ready: 3,
        accepted: 6,
        declined: 1,
      },
      totalSpeakers: 17,
      acceptedCount: 6,
      declinedCount: 1,
      pendingCount: 10,
      acceptanceRate: 35.29,
      minSlotsRequired: 6,
      maxSlotsAllowed: 8,
      thresholdMet: true,
      overflowDetected: false,
    };

    it('should display status summary when data loads', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Speaker Status')).toBeInTheDocument();
        expect(screen.getByText('Status Summary')).toBeInTheDocument();
      });
    });

    it('should display acceptance rate', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('35.3%')).toBeInTheDocument();
      });
    });

    it('should display progress with accepted and required counts', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('6 / 6 speakers accepted')).toBeInTheDocument();
      });
    });

    it('should show threshold met indicator when minimum reached', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Minimum threshold met ✓')).toBeInTheDocument();
      });
    });

    it('should display status counts for each status', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Open')).toBeInTheDocument();
        expect(screen.getByText('2')).toBeInTheDocument();
        expect(screen.getByText('Contacted')).toBeInTheDocument();
        expect(screen.getByText('5')).toBeInTheDocument();
        expect(screen.getByText('Ready')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByText('Accepted')).toBeInTheDocument();
        expect(screen.getByText('6')).toBeInTheDocument();
        expect(screen.getByText('Declined')).toBeInTheDocument();
        expect(screen.getByText('1')).toBeInTheDocument();
      });
    });

    it('should not show overflow warning when within limits', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.queryByText(/Overflow detected/)).not.toBeInTheDocument();
      });
    });
  });

  describe('Threshold Not Met Scenario', () => {
    const mockSummary: StatusSummaryResponse = {
      eventCode: 'BATbern56',
      statusCounts: {
        open: 5,
        contacted: 3,
        ready: 2,
        accepted: 4,
        declined: 1,
      },
      totalSpeakers: 15,
      acceptedCount: 4,
      declinedCount: 1,
      pendingCount: 10,
      acceptanceRate: 26.67,
      minSlotsRequired: 6,
      maxSlotsAllowed: 8,
      thresholdMet: false,
      overflowDetected: false,
    };

    it('should show threshold not met warning', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Minimum threshold not met (4 / 6)')).toBeInTheDocument();
      });
    });

    it('should not show threshold met indicator', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.queryByText('Minimum threshold met ✓')).not.toBeInTheDocument();
      });
    });
  });

  describe('Overflow Detected Scenario', () => {
    const mockSummary: StatusSummaryResponse = {
      eventCode: 'BATbern56',
      statusCounts: {
        open: 1,
        contacted: 2,
        ready: 1,
        accepted: 9,
        declined: 2,
      },
      totalSpeakers: 15,
      acceptedCount: 9,
      declinedCount: 2,
      pendingCount: 4,
      acceptanceRate: 60.0,
      minSlotsRequired: 6,
      maxSlotsAllowed: 8,
      thresholdMet: true,
      overflowDetected: true,
    };

    it('should show overflow warning when exceeded', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Overflow detected (9 > 8)')).toBeInTheDocument();
      });
    });

    it('should still show threshold met indicator', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('Minimum threshold met ✓')).toBeInTheDocument();
      });
    });
  });

  describe('Empty State', () => {
    const mockSummary: StatusSummaryResponse = {
      eventCode: 'BATbern56',
      statusCounts: {},
      totalSpeakers: 0,
      acceptedCount: 0,
      declinedCount: 0,
      pendingCount: 0,
      acceptanceRate: 0.0,
      minSlotsRequired: 6,
      maxSlotsAllowed: 8,
      thresholdMet: false,
      overflowDetected: false,
    };

    it('should handle zero speakers gracefully', async () => {
      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern56" />);

      await waitFor(() => {
        expect(screen.getByText('0.0%')).toBeInTheDocument();
        expect(screen.getByText('0 / 6 speakers accepted')).toBeInTheDocument();
      });
    });
  });

  describe('React Query Integration', () => {
    it('should call getStatusSummary with correct event code', async () => {
      const mockSummary: StatusSummaryResponse = {
        eventCode: 'BATbern99',
        statusCounts: {},
        totalSpeakers: 0,
        acceptedCount: 0,
        declinedCount: 0,
        pendingCount: 0,
        acceptanceRate: 0.0,
        minSlotsRequired: 6,
        maxSlotsAllowed: 8,
        thresholdMet: false,
        overflowDetected: false,
      };

      vi.mocked(speakerStatusService.getStatusSummary).mockResolvedValue(mockSummary);

      renderWithQuery(<SpeakerStatusDashboard eventCode="BATbern99" />);

      await waitFor(() => {
        expect(speakerStatusService.getStatusSummary).toHaveBeenCalledWith('BATbern99');
      });
    });
  });
});
