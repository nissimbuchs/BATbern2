/**
 * Story 15.5 — ActiveSessionCard renders each speaker's company logo (on a white LogoBadge chip)
 * on the live-control surface, with a graceful name-only fallback when there is no logo.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ActiveSessionCard } from './ActiveSessionCard';
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
