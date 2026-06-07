/**
 * SpeakerDisplay — company label rendering
 *
 * Regression for the company-name bug: the public event page must show the
 * company's human-readable displayName, never the raw slug. The slug stays the
 * key for logo lookup (useCompany), so we mock that hook out here.
 */
import { render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import type { SessionSpeaker } from '@/types/event.types';

// Mock the data hooks — this test is about the company label, not logos/portraits.
vi.mock('@/hooks/useCompany/useCompany', () => ({
  useCompany: () => ({ data: undefined }),
}));
vi.mock('@/hooks/useUserPortrait', () => ({
  useUserPortrait: () => ({ data: undefined }),
}));
vi.mock('react-intersection-observer', () => ({
  useInView: () => ({ ref: vi.fn(), inView: false }),
}));

import { SpeakerDisplay } from '../SpeakerDisplay';

const baseSpeaker: SessionSpeaker = {
  username: 'anna.meier',
  firstName: 'Anna',
  lastName: 'Meier',
  speakerRole: 'PRIMARY_SPEAKER',
  isConfirmed: true,
};

describe('SpeakerDisplay — company label', () => {
  test('should_renderCompanyDisplayName_when_present', () => {
    render(
      <SpeakerDisplay
        speaker={{
          ...baseSpeaker,
          company: 'swisscomZH',
          companyDisplayName: 'Swisscom (Schweiz) AG',
        }}
      />
    );

    expect(screen.getByText('Swisscom (Schweiz) AG')).toBeInTheDocument();
    expect(screen.queryByText('swisscomZH')).not.toBeInTheDocument();
  });

  test('should_fallBackToSlug_when_companyDisplayNameMissing', () => {
    render(<SpeakerDisplay speaker={{ ...baseSpeaker, company: 'postfinance' }} />);

    expect(screen.getByText('postfinance')).toBeInTheDocument();
  });
});
