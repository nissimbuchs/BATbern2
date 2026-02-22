/**
 * PartnerAttendanceDashboard tests
 * Story 8.1: AC1, AC2, AC3, AC7, AC8 — Task 11
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PartnerAttendanceDashboard } from './PartnerAttendanceDashboard';
import * as analyticsApi from '@/services/api/partnerAnalyticsApi';

// ─── Mocks ───────────────────────────────────────────────────────────────────

vi.mock('@/services/api/partnerAnalyticsApi');

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'portal.analytics.title': 'Attendance Dashboard',
        'portal.analytics.table.event': 'Event',
        'portal.analytics.table.date': 'Date',
        'portal.analytics.table.yourAttendees': 'Your Attendees',
        'portal.analytics.table.totalAttendees': 'Total Attendees',
        'portal.analytics.table.percentage': 'Percentage (%)',
        'portal.analytics.kpi.attendanceRate': 'Avg. Attendance Rate',
        'portal.analytics.kpi.costPerAttendee': 'Cost Per Attendee',
        'portal.analytics.kpi.currency': 'CHF',
        'portal.analytics.range.last5years': 'Last 5 Years',
        'portal.analytics.range.allHistory': 'All History',
        'portal.analytics.export.button': 'Export Excel',
        'portal.analytics.noData': 'No attendance data found for the selected period',
        'portal.analytics.error': 'Failed to load attendance data',
      };
      return map[key] ?? key;
    },
  }),
}));

// Mock export button to avoid DOM anchor complexity in unit tests
vi.mock('./AttendanceExportButton', () => ({
  AttendanceExportButton: ({ companyName }: { companyName: string }) => (
    <button data-testid="export-button">Export Excel ({companyName})</button>
  ),
}));

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const mockData: analyticsApi.PartnerDashboardData = {
  attendanceSummary: [
    {
      eventCode: 'BATbern57',
      eventDate: '2024-06-01T00:00:00Z',
      totalAttendees: 100,
      companyAttendees: 10,
    },
    {
      eventCode: 'BATbern56',
      eventDate: '2023-06-01T00:00:00Z',
      totalAttendees: 80,
      companyAttendees: 8,
    },
  ],
  costPerAttendee: 555.56,
};

// ─── Test helpers ─────────────────────────────────────────────────────────────

function renderDashboard(companyName = 'GoogleZH') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <PartnerAttendanceDashboard companyName={companyName} />
    </QueryClientProvider>
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('PartnerAttendanceDashboard', () => {
  beforeEach(() => {
    vi.mocked(analyticsApi.getAttendanceDashboard).mockResolvedValue(mockData);
  });

  it('renders table with mocked data (AC1)', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId('attendance-dashboard')).toBeInTheDocument();
    });

    expect(screen.getByText('BATbern57')).toBeInTheDocument();
    expect(screen.getByText('BATbern56')).toBeInTheDocument();
  });

  it('shows loading skeleton while fetching (AC7)', () => {
    vi.mocked(analyticsApi.getAttendanceDashboard).mockReturnValue(new Promise(() => {}));
    renderDashboard();

    expect(screen.getByTestId('analytics-loading')).toBeInTheDocument();
  });

  it('shows empty state when no data (AC1)', async () => {
    vi.mocked(analyticsApi.getAttendanceDashboard).mockResolvedValue({
      attendanceSummary: [],
      costPerAttendee: null,
    });

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId('no-data-message')).toBeInTheDocument();
    });

    expect(
      screen.getByText('No attendance data found for the selected period')
    ).toBeInTheDocument();
  });

  it('shows error alert when API fails', async () => {
    vi.mocked(analyticsApi.getAttendanceDashboard).mockRejectedValue(new Error('Network error'));

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId('analytics-error')).toBeInTheDocument();
    });
  });

  it('fires correct API call when toggling to All History (AC2)', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId('attendance-dashboard')).toBeInTheDocument();
    });

    const allHistoryBtn = screen.getByTestId('range-allhistory');
    fireEvent.click(allHistoryBtn);

    await waitFor(() => {
      expect(analyticsApi.getAttendanceDashboard).toHaveBeenCalledWith(
        'GoogleZH',
        expect.any(Number)
      );
    });
  });

  it('displays cost per attendee KPI (AC3)', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId('kpi-cost-per-attendee')).toBeInTheDocument();
    });

    expect(screen.getByText(/555\.56/)).toBeInTheDocument();
  });

  it('shows N/A when costPerAttendee is null (AC3)', async () => {
    vi.mocked(analyticsApi.getAttendanceDashboard).mockResolvedValue({
      attendanceSummary: [],
      costPerAttendee: null,
    });

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByTestId('kpi-cost-per-attendee')).toBeInTheDocument();
    });

    expect(screen.getByText('N/A')).toBeInTheDocument();
  });

  it('renders i18n strings in English (AC8)', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Attendance Dashboard')).toBeInTheDocument();
    });

    expect(screen.getByText('Your Attendees')).toBeInTheDocument();
    expect(screen.getByText('Total Attendees')).toBeInTheDocument();
    expect(screen.getByText('Last 5 Years')).toBeInTheDocument();
    expect(screen.getByText('All History')).toBeInTheDocument();
  });
});
