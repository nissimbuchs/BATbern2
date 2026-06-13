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
  sessions: [{ startTime: '2026-06-13T09:00:00Z' }, { startTime: null }, { startTime: null }],
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
    // agenda: 1 of the 3 hydrated sessions has a startTime → numerator & denominator
    // both come from the sessions array (internally consistent), so "1 / 3".
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
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
