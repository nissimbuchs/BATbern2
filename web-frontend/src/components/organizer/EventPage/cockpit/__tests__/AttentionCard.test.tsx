/**
 * AttentionCard tests — focus on the event-day (live) due chip (14.B.4).
 *
 * Event-day cards are due ON the event date: before that day the chip shows the
 * date ("Event day · …"); on/after it the event is actually live.
 */
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { AttentionCard } from '../AttentionCard';
import type { CockpitCard } from '../cockpitCards';

vi.mock('@/components/shared/OrganizerChip', () => ({ OrganizerChip: () => null }));

const liveCard = (over: Partial<CockpitCard> = {}): CockpitCard => ({
  id: 'start-presentation',
  labelKey: 'startPresentation',
  severity: 'live',
  pinned: true,
  target: { kind: 'route', path: '/present/BAT54', newTab: true },
  ...over,
});

const renderCard = (card: CockpitCard) =>
  render(
    <I18nextProvider i18n={i18n}>
      <AttentionCard card={card} onNavigate={vi.fn()} />
    </I18nextProvider>
  );

describe('AttentionCard — event-day due chip', () => {
  it('shows the event date (not "live now") for an event still in the future', () => {
    renderCard(liveCard({ dueDate: '2099-09-18T17:00:00Z' }));
    expect(screen.getByText(/Event day/i)).toBeInTheDocument();
    expect(screen.queryByText(/event is live/i)).not.toBeInTheDocument();
  });

  it('shows "Now · event is live" once the event day has arrived/passed', () => {
    renderCard(liveCard({ dueDate: '2000-01-01T00:00:00Z' }));
    expect(screen.getByText(/event is live/i)).toBeInTheDocument();
  });

  it('falls back to the live text when the card carries no event date', () => {
    renderCard(liveCard({ dueDate: undefined }));
    expect(screen.getByText(/event is live/i)).toBeInTheDocument();
  });
});
