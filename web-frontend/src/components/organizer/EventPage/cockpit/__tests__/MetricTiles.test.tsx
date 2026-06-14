/**
 * MetricTiles tests (Story 14.B.5 / FR13).
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { MetricTiles } from '../MetricTiles';

const event = {
  eventCode: 'BAT54',
  confirmedCount: 128,
  registrationCapacity: 180,
  waitlistCount: 12,
  confirmedSpeakersCount: 4,
  maxSpeakerSlots: 8,
  sessionsWithMaterialsCount: 3,
  totalSessionsCount: 6,
  // 2 speaker sessions (1 slotted) + a moderation session that HAS a startTime but must
  // be excluded from the agenda count (it's a structural slot, not a speaker slot).
  sessions: [
    { startTime: '2026-06-13T09:00:00Z', sessionType: 'presentation' },
    { startTime: null, sessionType: 'presentation' },
    { startTime: '2026-06-13T08:55:00Z', sessionType: 'moderation' },
  ],
} as never;

const renderTiles = (onNavigate = vi.fn()) => {
  render(
    <I18nextProvider i18n={i18n}>
      <MetricTiles event={event} onNavigate={onNavigate} />
    </I18nextProvider>
  );
  return onNavigate;
};

describe('MetricTiles', () => {
  it('renders the four headline tiles with fractions', () => {
    renderTiles();
    expect(screen.getByTestId('cockpit-metric-tile-registrations')).toBeInTheDocument();
    expect(screen.getByTestId('cockpit-metric-tile-speakers')).toBeInTheDocument();
    expect(screen.getByTestId('cockpit-metric-tile-materials')).toBeInTheDocument();
    expect(screen.getByTestId('cockpit-metric-tile-agenda')).toBeInTheDocument();
    expect(screen.getByText('128 / 180')).toBeInTheDocument(); // registrations
    expect(screen.getByText('4 / 8')).toBeInTheDocument(); // speakers
    expect(screen.getByText('3 / 6')).toBeInTheDocument(); // materials (metric-based)
    // agenda: of the 2 SPEAKER sessions, 1 is slotted (has a startTime); the moderation
    // session is excluded even though it has a startTime. Denominator = maxSpeakerSlots (8).
    expect(screen.getByText('1 / 8')).toBeInTheDocument();
  });

  it('deep-links Speakers → pool sub-view', () => {
    const onNavigate = renderTiles();
    fireEvent.click(screen.getByTestId('cockpit-metric-tile-speakers'));
    expect(onNavigate).toHaveBeenCalledWith({ kind: 'tab', tab: 'speakers', view: 'pool' });
  });

  it('deep-links Registrations → registrations tab', () => {
    const onNavigate = renderTiles();
    fireEvent.click(screen.getByTestId('cockpit-metric-tile-registrations'));
    expect(onNavigate).toHaveBeenCalledWith({ kind: 'tab', tab: 'registrations' });
  });
});
