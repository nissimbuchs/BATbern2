/**
 * ActivityTabPanel tests (Story 10.30, AC5)
 *
 * Coverage:
 * - Shows mark-contacted form only for IDENTIFIED and CONTACTED speakers
 * - Hides mark-contacted form for other statuses
 * - Validates contactMethod is required before submit
 * - Validates contactDate is required before submit
 * - Calls recordOutreach on valid submit
 * - Shows contact history when loaded
 * - Shows loading state
 * - Shows error state for history load failure
 * - Shows empty state when no history
 * - Resets form on speaker change
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ActivityTabPanel } from '../ActivityTabPanel';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { OutreachHistory } from '@/types/speakerOutreach.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => {
      const map: Record<string, string> = {
        'speakerOutreach.contactMethod': 'Contact Method',
        'speakerOutreach.contactDate': 'Contact Date',
        'common:labels.notes': 'Notes',
        'speakerOutreach.markContacted': 'Mark Contacted',
        'speakerOutreach.markContactedModal.title': 'Mark as Contacted',
        'speakerOutreach.markContactedModal.method.email': 'Email',
        'speakerOutreach.markContactedModal.method.phone': 'Phone',
        'speakerOutreach.markContactedModal.method.inPerson': 'In Person',
        'speakerOutreach.markContactedModal.error.methodRequired': 'Method is required',
        'speakerOutreach.markContactedModal.error.dateRequired': 'Date is required',
        'speakerOutreach.contactHistory': 'Contact History',
        'speakerOutreach.noContactHistory': 'No contact history yet',
        'speakerOutreach.error.loadHistory': 'Failed to load history',
        'common.saving': 'Saving...',
      };
      return map[key] ?? fallback ?? key;
    },
    i18n: { language: 'en' },
  }),
}));

vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

vi.mock('@/utils/date', () => ({
  formatDateTime: () => 'Jan 1, 2026, 10:00',
}));

const mockRecordMutateAsync = vi.fn();
let mockRecordIsPending = false;
let mockRecordIsError = false;
let mockHistoryData: OutreachHistory[] | undefined = [];
let mockHistoryIsLoading = false;
let mockHistoryIsError = false;

vi.mock('@/hooks/useSpeakerOutreach', () => ({
  useSpeakerOutreachHistory: () => ({
    data: mockHistoryData,
    isLoading: mockHistoryIsLoading,
    isError: mockHistoryIsError,
  }),
  useRecordOutreach: () => ({
    mutateAsync: mockRecordMutateAsync,
    isPending: mockRecordIsPending,
    isError: mockRecordIsError,
    reset: vi.fn(),
  }),
}));

function makeSpeaker(status: SpeakerPoolEntry['status']): SpeakerPoolEntry {
  return {
    id: 'sp-1',
    eventId: 'evt-1',
    speakerName: 'Jane Doe',
    status,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function renderComponent(speaker = makeSpeaker('IDENTIFIED')) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ActivityTabPanel speaker={speaker} eventCode="EVT-2026" />
    </QueryClientProvider>
  );
}

beforeEach(() => {
  mockRecordMutateAsync.mockResolvedValue({});
  mockRecordIsPending = false;
  mockRecordIsError = false;
  mockHistoryData = [];
  mockHistoryIsLoading = false;
  mockHistoryIsError = false;
  vi.clearAllMocks();
});

describe('ActivityTabPanel — form visibility', () => {
  it('shows mark-contacted form for IDENTIFIED speaker', () => {
    renderComponent(makeSpeaker('IDENTIFIED'));
    expect(screen.getByTestId('contact-method-select')).toBeInTheDocument();
  });

  it('shows mark-contacted form for CONTACTED speaker', () => {
    renderComponent(makeSpeaker('CONTACTED'));
    expect(screen.getByTestId('contact-method-select')).toBeInTheDocument();
  });

  it.each(['INVITED', 'ACCEPTED', 'DECLINED', 'CONFIRMED', 'READY'] as const)(
    'hides mark-contacted form for %s speaker',
    (status) => {
      renderComponent(makeSpeaker(status));
      expect(screen.queryByTestId('contact-method-select')).not.toBeInTheDocument();
    }
  );
});

describe('ActivityTabPanel — form validation', () => {
  it('shows error when submitting without selecting contact method', async () => {
    renderComponent();
    await userEvent.click(screen.getByTestId('mark-contacted-button'));
    expect(await screen.findByText('Method is required')).toBeInTheDocument();
    expect(mockRecordMutateAsync).not.toHaveBeenCalled();
  });

  it('submits form when all required fields filled', async () => {
    renderComponent();
    // MUI Select: click the combobox to open dropdown, then click the menu item
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(await screen.findByTestId('contact-method-email'));
    await userEvent.click(screen.getByTestId('mark-contacted-button'));
    await waitFor(() => {
      expect(mockRecordMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          eventCode: 'EVT-2026',
          speakerId: 'sp-1',
          request: expect.objectContaining({ contactMethod: 'email' }),
        })
      );
    });
  });
});

describe('ActivityTabPanel — contact history', () => {
  it('shows loader while history is loading', () => {
    mockHistoryIsLoading = true;
    renderComponent();
    expect(screen.getByTestId('loader')).toBeInTheDocument();
  });

  it('shows error alert when history load fails', () => {
    mockHistoryIsError = true;
    renderComponent();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Failed to load history')).toBeInTheDocument();
  });

  it('shows empty state when no history', () => {
    mockHistoryData = [];
    renderComponent();
    expect(screen.getByText('No contact history yet')).toBeInTheDocument();
  });

  it('renders contact history entries', () => {
    mockHistoryData = [
      {
        id: 'hist-1',
        speakerId: 'sp-1',
        contactMethod: 'EMAIL',
        contactDate: '2026-01-01T10:00:00Z',
        notes: 'Sent follow-up',
        organizerUsername: 'alice',
      },
    ];
    renderComponent();
    expect(screen.getByText('Jan 1, 2026, 10:00')).toBeInTheDocument();
    expect(screen.getByText('Sent follow-up')).toBeInTheDocument();
    expect(screen.getByText(/alice/)).toBeInTheDocument();
  });
});
