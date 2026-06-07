/**
 * EventOverviewTab Component Tests
 *
 * Bug fix: capacity section should display registrationCapacity (organizer-set limit)
 * instead of venueCapacity (fire-code limit) when registrationCapacity is set.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { EventOverviewTab } from './EventOverviewTab';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (_key: string, fallback?: string) => fallback ?? _key,
    i18n: { language: 'en' },
  }),
}));

vi.mock('@/services/topicService', () => ({
  topicService: {
    getTopicById: vi.fn().mockResolvedValue(null),
  },
}));

vi.mock('@/hooks/useFeatureFlags', () => ({
  useFeatureFlags: () => ({ aiContentEnabled: false }),
}));

vi.mock('./AiAssistDrawer', () => ({
  AiAssistDrawer: () => null,
}));

vi.mock('@/components/organizer/EventManagement', () => ({
  WorkflowProgressBar: () => null,
}));

const baseEvent = {
  eventCode: 'BATbern58',
  title: 'BATbern 58',
  eventNumber: 58,
  date: '2023-05-15T09:00:00Z',
  registrationDeadline: '2023-05-08T00:00:00Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3, Bern',
  venueCapacity: 200,
  organizerUsername: 'admin',
  workflowState: 'EVENT_COMPLETED',
  currentAttendeeCount: 256,
};

const renderTab = (event: object) =>
  render(
    <MemoryRouter>
      <EventOverviewTab event={event as never} eventCode="BATbern58" />
    </MemoryRouter>
  );

describe('EventOverviewTab — capacity display', () => {
  it('shows venueCapacity when registrationCapacity is not set', () => {
    renderTab({ ...baseEvent });

    // Should display "256 / 200 registered" (venueCapacity fallback)
    expect(screen.getByText(/256/)).toBeInTheDocument();
    expect(screen.getByText(/200/)).toBeInTheDocument();
  });

  it('shows registrationCapacity instead of venueCapacity when registrationCapacity is set', () => {
    renderTab({ ...baseEvent, registrationCapacity: 220 });

    // Should display "256 / 220 registered" — uses registrationCapacity not venueCapacity
    expect(screen.getByText(/256/)).toBeInTheDocument();
    expect(screen.getByText(/220/)).toBeInTheDocument();
    // venueCapacity (200) should NOT appear in the capacity section
    const capacitySection = screen.getByText(/registered/i).closest('p');
    expect(capacitySection?.textContent).not.toContain('200');
  });

  it('shows 0 capacity when neither registrationCapacity nor venueCapacity is set', () => {
    renderTab({
      ...baseEvent,
      currentAttendeeCount: 0,
      venueCapacity: 0,
      registrationCapacity: null,
    });

    // Both count and capacity show 0
    const registeredText = screen.getByText(/registered/i);
    expect(registeredText.textContent).toMatch(/0 \/ 0/);
  });
});
