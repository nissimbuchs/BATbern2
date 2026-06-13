/**
 * TestimonialSection Tests (Story 7.7 — curated thank-you notes intermingled into the marquee)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestimonialSection } from '../TestimonialSection';

vi.mock('@/hooks/usePublicPartners', () => ({ usePublicPartners: vi.fn() }));
vi.mock('@/hooks/useRecentEventPhotos', () => ({ useRecentEventPhotos: vi.fn() }));
vi.mock('@/hooks/useFeaturedThanks/useFeaturedThanks', () => ({ useFeaturedThanks: vi.fn() }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => (key === 'thanks.marquee.badge' ? 'Thank you' : key),
  }),
}));

import { usePublicPartners } from '@/hooks/usePublicPartners';
import { useRecentEventPhotos } from '@/hooks/useRecentEventPhotos';
import { useFeaturedThanks } from '@/hooks/useFeaturedThanks/useFeaturedThanks';

const partner = (name: string) => ({
  companyName: name,
  partnershipLevel: 'GOLD',
  partnershipStartDate: '2021-01-01',
  // logoUrl present → the card renders an <img> whose alt is the company name (queryable).
  company: { displayName: name, logoUrl: `https://cdn.batbern.ch/${name}.png`, website: undefined },
});

const thanks = (note: string, firstName: string) => ({
  note,
  eventCode: 'BATbern57',
  thankedByFirstName: firstName,
  thankedByLastName: 'X',
  thankedByCompanyName: 'ACME',
  thankedByCompanyLogoUrl: null,
});

beforeEach(() => {
  vi.mocked(useRecentEventPhotos).mockReturnValue({ data: [] } as never);
});

describe('TestimonialSection', () => {
  it('intermingles curated thank-you notes with partner cards', () => {
    vi.mocked(usePublicPartners).mockReturnValue({
      data: { data: [partner('Swisscom')] },
    } as never);
    vi.mocked(useFeaturedThanks).mockReturnValue({ data: [thanks('20 years!', 'Alice')] } as never);

    render(<TestimonialSection />);

    // Both a partner and a thank-you card render (InfiniteMarquee duplicates children → getAll*).
    expect(screen.getAllByAltText('Swisscom').length).toBeGreaterThan(0);
    expect(screen.getAllByText('"20 years!"').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('Thank you').length).toBeGreaterThan(0);
  });

  it('degrades to partners-only when no notes are featured', () => {
    vi.mocked(usePublicPartners).mockReturnValue({
      data: { data: [partner('PostFinance')] },
    } as never);
    vi.mocked(useFeaturedThanks).mockReturnValue({ data: [] } as never);

    render(<TestimonialSection />);

    expect(screen.getAllByAltText('PostFinance').length).toBeGreaterThan(0);
    // No thank-you card (no 🙏 marker) when there are no featured notes.
    expect(screen.queryByLabelText('Thank you')).toBeNull();
  });
});
