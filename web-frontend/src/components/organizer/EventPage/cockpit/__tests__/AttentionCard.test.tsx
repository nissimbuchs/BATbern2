/**
 * AttentionCard tests — focus on the event-day (live) due chip (14.B.4).
 *
 * Event-day cards are due ON the event date: before that day the chip shows the
 * date ("Event day · …"); on/after it the event is actually live.
 *
 * Real i18next, the real OrganizerChip (its organizer directory answers from the MSW harness).
 */
import { screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { renderWithAuth } from '@/test/auth';
import { AttentionCard } from '../AttentionCard';
import type { CockpitCard } from '../cockpitCards';

const liveCard = (over: Partial<CockpitCard> = {}): CockpitCard => ({
  id: 'start-presentation',
  labelKey: 'startPresentation',
  severity: 'live',
  pinned: true,
  target: { kind: 'route', path: '/present/BAT54', newTab: true },
  ...over,
});

const renderCard = (card: CockpitCard) =>
  renderWithAuth(<AttentionCard card={card} onNavigate={vi.fn()} />);

const taskCard = (over: Partial<CockpitCard>): CockpitCard => ({
  id: 'task:x',
  labelKey: '',
  labelVars: { name: 'Partner Meeting' },
  severity: 'dueSoon',
  taskBacked: true,
  ...over,
});

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

describe('AttentionCard — overdue cards without a due date and notes', () => {
  it('shows "Overdue" (not "Upcoming") for an overdue card with no day count', () => {
    renderCard({
      id: 'speakers-not-reviewed',
      labelKey: 'speakersNotReviewed',
      labelVars: { content: 1, review: 0 },
      severity: 'overdue',
      target: { kind: 'tab', tab: 'speakers', view: 'pool' },
    });
    expect(screen.getByText(/^Overdue$/i)).toBeInTheDocument();
    expect(screen.queryByText(/Upcoming/i)).not.toBeInTheDocument();
  });

  it('renders the "trigger not reached" note on a safety-net task card', () => {
    renderCard({
      id: 'task:cat',
      labelKey: '',
      labelVars: { name: 'Catering Coordination' },
      severity: 'overdue',
      dueDays: -17,
      taskBacked: true,
      noteKey: 'triggerNotReached',
    });
    expect(screen.getByText(/trigger not reached/i)).toBeInTheDocument();
  });

  it('renders no note on a regular card', () => {
    renderCard({
      id: 'task:x',
      labelKey: '',
      labelVars: { name: 'Partner Meeting' },
      severity: 'upcoming',
      dueDays: 9,
      taskBacked: true,
    });
    expect(screen.queryByText(/trigger not reached/i)).not.toBeInTheDocument();
  });
});

describe('AttentionCard — due chip wording (#1223)', () => {
  it.each([
    [0, 'Due today'],
    [1, 'Due in 1 day'],
    [5, 'Due in 5 days'],
  ])('a card due in %i days reads "%s"', (dueDays, text) => {
    renderCard(taskCard({ severity: 'dueSoon', dueDays }));
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it.each([
    [-1, 'Overdue · 1 day'],
    [-17, 'Overdue · 17 days'],
  ])('a card %i days past due reads "%s"', (dueDays, text) => {
    renderCard(taskCard({ severity: 'overdue', dueDays }));
    expect(screen.getByText(text)).toBeInTheDocument();
  });
});

describe('AttentionCard — action line (#1223)', () => {
  it.each([
    ['a tab target', { kind: 'tab', tab: 'registrations' } as const, 'Open Registrations'],
    ['a route target', { kind: 'route', path: '/organizer/events' } as const, 'Open'],
  ])('shows one arrow for %s', (_name, target, text) => {
    renderCard(taskCard({ target }));
    const card = screen.getByTestId('cockpit-attention-card-task');
    // The icon is the arrow; the text carries none ("Open Registrations → →" before).
    expect(within(card).getByText(text)).toBeInTheDocument();
    expect(card.textContent).not.toContain('→');
    expect(within(card).getAllByTestId('ArrowForwardIcon')).toHaveLength(1);
  });
});
