/**
 * EventVenueTab Component Tests
 *
 * The tab is now a thin wrapper that hosts only the VenueCoordinationComposer.
 * The composer has its own tests; here we just verify the wrapper mounts it
 * with the right eventCode.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import { EventVenueTab } from '../EventVenueTab';
import type { Event } from '@/types/event.types';

// Mock the composer — its real behaviour is covered by its own tests.
vi.mock('../VenueCoordinationComposer', () => ({
  VenueCoordinationComposer: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="venue-coordination-composer-stub">{eventCode}</div>
  ),
}));

const mockEvent: Event = {
  eventId: '123e4567-e89b-12d3-a456-426614174000',
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Spring Conference 2025',
  description: 'Advanced microservices architecture',
  date: '2025-03-15T09:00:00Z',
  registrationDeadline: '2025-03-10T23:59:59Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, 3013 Bern',
  venueCapacity: 200,
  status: 'published',
  workflowState: 'SPEAKER_CONFIRMATION',
  organizerUsername: 'john.doe',
  currentAttendeeCount: 87,
  createdAt: '2024-12-01T10:00:00Z',
  updatedAt: '2025-01-15T14:30:00Z',
};

const renderWithProviders = (ui: React.ReactElement) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

describe('EventVenueTab', () => {
  it('mounts the VenueCoordinationComposer with the event code', () => {
    renderWithProviders(<EventVenueTab event={mockEvent} />);
    const stub = screen.getByTestId('venue-coordination-composer-stub');
    expect(stub).toBeInTheDocument();
    expect(stub).toHaveTextContent('BAT54');
  });
});
