/**
 * Story 15.5 — ActiveSessionCard renders each speaker's company logo (on a white LogoBadge chip)
 * on the live-control surface, with a graceful name-only fallback when there is no logo.
 * #1219 — every label follows the UI language (real i18next, en by default in tests).
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import i18n from '@/i18n/config';
import { ActiveSessionCard } from './ActiveSessionCard';
import { AgendaList } from './AgendaList';
import type { components } from '@/types/generated/event-watch-api.types';

type WatchSessionDetail = components['schemas']['WatchSessionDetail'];
type WatchSpeaker = WatchSessionDetail['speakers'][number];

function makeSession(speakers: WatchSpeaker[]): WatchSessionDetail {
  return {
    sessionSlug: 'talk',
    title: 'Cloud Native Foundations',
    sessionType: 'presentation',
    scheduledStartTime: '2026-06-20T10:00:00Z',
    scheduledEndTime: '2026-06-20T11:00:00Z',
    durationMinutes: 60,
    speakers,
    status: 'live',
  } as unknown as WatchSessionDetail;
}

const baseProps = {
  nextSession: null,
  remainingSeconds: 600,
  elapsedSeconds: 60,
  shouldShowExtend: false,
  shouldShowDelay: false,
  isActionInFlight: false,
  sendExtend: vi.fn(),
  sendDelay: vi.fn(),
};

describe('ActiveSessionCard — Story 15.5 speaker company logo', () => {
  it('renders the company logo (white chip) next to a speaker who has one (AC1/AC2)', () => {
    render(
      <ActiveSessionCard
        activeSession={makeSession([
          {
            username: 'anna.b',
            firstName: 'Anna',
            lastName: 'B',
            company: 'ACME Corp',
            companyLogoUrl: 'https://cdn.batbern.ch/logos/acme.png',
          } as WatchSpeaker,
        ])}
        {...baseProps}
      />
    );

    const img = screen.getByAltText('ACME Corp') as HTMLImageElement;
    expect(img).toBeInTheDocument();
    expect(img.src).toContain('acme.png');
    expect(screen.getByText('Anna B')).toBeInTheDocument();
  });

  it('renders name only, no broken image, when the speaker has no company logo (AC3)', () => {
    render(
      <ActiveSessionCard
        activeSession={makeSession([
          {
            username: 'carl.d',
            firstName: 'Carl',
            lastName: 'D',
            company: null,
            companyLogoUrl: null,
          } as WatchSpeaker,
        ])}
        {...baseProps}
      />
    );

    expect(screen.getByText('Carl D')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});

describe('ActiveSessionCard — labels follow the UI language (#1219)', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  const live = {
    ...baseProps,
    activeSession: makeSession([]),
    shouldShowExtend: true,
    shouldShowDelay: true,
  };

  it('shows English labels in en, including the time sheets', async () => {
    const user = userEvent.setup();
    render(<ActiveSessionCard {...live} />);

    expect(screen.getByText('On time')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '← Delay' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '↔ Adjust time' }));
    expect(await screen.findByText('Adjust session time')).toBeInTheDocument();
    expect(screen.getByText('Extend')).toBeInTheDocument();
    expect(screen.getByText('Shorten')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+5 min' })).toBeInTheDocument();
    expect(screen.queryByText(/Verlängern|Verzögern|Abbrechen/)).not.toBeInTheDocument();
  });

  it('opens the delay sheet in English', async () => {
    const user = userEvent.setup();
    render(<ActiveSessionCard {...live} />);

    await user.click(screen.getByRole('button', { name: '← Delay' }));
    expect(await screen.findByText('Give the previous session more time?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+10 min' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  it('shows the next session in English when no session is active', () => {
    render(<ActiveSessionCard {...baseProps} activeSession={null} nextSession={makeSession([])} />);

    expect(screen.getByText('No active session')).toBeInTheDocument();
    expect(screen.getByText('Next session')).toBeInTheDocument();
  });

  it('keeps the German labels in de', async () => {
    await i18n.changeLanguage('de');
    render(<ActiveSessionCard {...live} remainingSeconds={0} elapsedSeconds={3700} />);

    expect(screen.getByText('Überzeit')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '← Verzögern' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '↔ Zeit anpassen' })).toBeInTheDocument();
  });

  it('labels the agenda in English', () => {
    const { rerender } = render(<AgendaList sessions={[makeSession([])]} />);
    expect(screen.getByText('Programme')).toBeInTheDocument();
    expect(screen.getByText(/· 60 min/)).toBeInTheDocument();

    rerender(<AgendaList sessions={[]} />);
    expect(screen.getByText('No sessions available')).toBeInTheDocument();
  });
});
