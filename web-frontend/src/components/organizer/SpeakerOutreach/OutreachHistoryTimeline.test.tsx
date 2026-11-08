/**
 * Story 17.1: outreach history is typed from event-speakers-api (OutreachHistoryResponse),
 * where `notes` is nullable. An entry without notes must render without error.
 *
 * Runs the real hook and service; only the HTTP call is stubbed, at the axios instance
 * (Test fidelity rules: mock at the process boundary).
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';
import OutreachHistoryTimeline from './OutreachHistoryTimeline';
import type { OutreachHistoryResponse } from '@/types/speakerOutreach.types';

const HISTORY_URL = '/events/BATbern142/speakers/speaker-1/outreach';

let http: MockAdapter;
beforeEach(() => {
  http = new MockAdapter(apiClient);
});
afterEach(() => {
  http.restore();
});

const renderTimeline = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <OutreachHistoryTimeline eventCode="BATbern142" speakerId="speaker-1" />
    </QueryClientProvider>
  );
};

const entry = (overrides: Partial<OutreachHistoryResponse>): OutreachHistoryResponse => ({
  id: '550e8400-e29b-41d4-a716-446655440000',
  speakerPoolId: '123e4567-e89b-12d3-a456-426614174000',
  contactDate: '2025-12-14T14:30:00Z',
  contactMethod: 'email',
  notes: null,
  organizerUsername: 'john.doe',
  createdAt: '2025-12-14T14:30:00Z',
  updatedAt: '2025-12-14T14:30:00Z',
  ...overrides,
});

describe('OutreachHistoryTimeline', () => {
  it('should_renderEntry_when_notesAreNull', async () => {
    http.onGet(HISTORY_URL).reply(200, [entry({ notes: null })]);

    renderTimeline();

    expect(await screen.findByTestId('outreach-timeline')).toBeInTheDocument();
    expect(screen.getByText(/john\.doe/)).toBeInTheDocument();
  });

  it('should_renderNotes_when_present', async () => {
    http
      .onGet(HISTORY_URL)
      .reply(200, [entry({ id: 'b', notes: 'Follow up next Tuesday', contactMethod: 'phone' })]);

    renderTimeline();

    expect(await screen.findByText('Follow up next Tuesday')).toBeInTheDocument();
  });
});
