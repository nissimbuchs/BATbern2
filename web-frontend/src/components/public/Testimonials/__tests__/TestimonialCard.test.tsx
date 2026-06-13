/**
 * TestimonialCard Tests (Story 7.7 — company logo + badge on the curated thank-you card)
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestimonialCard } from '../TestimonialCard';

describe('TestimonialCard', () => {
  it('renders the company logo when companyLogoUrl is provided', () => {
    render(
      <TestimonialCard
        name="Alice"
        quote="Great event!"
        company="ELCA"
        companyLogoUrl="https://cdn.batbern.ch/elca.png"
      />
    );
    const logo = screen.getByAltText('ELCA') as HTMLImageElement;
    expect(logo.tagName).toBe('IMG');
    // company text is NOT rendered when a logo is shown
    expect(screen.queryByText('ELCA')).toBeNull();
  });

  it('falls back to company text when no logo is provided', () => {
    render(<TestimonialCard name="Bob" quote="Loved it" company="PostFinance" />);
    expect(screen.getByText('PostFinance')).toBeInTheDocument();
  });

  it('renders the 🙏 marker with the badge as an accessible label (no visible text)', () => {
    render(<TestimonialCard name="Carol" quote="Danke" badge="Thank you" />);
    expect(screen.getByLabelText('Thank you')).toBeInTheDocument();
    // the badge word itself is NOT shown as visible text
    expect(screen.queryByText('Thank you')).toBeNull();
  });

  it('shows the quote and name', () => {
    render(<TestimonialCard name="Dora" quote="Inspiring talks" />);
    expect(screen.getByText('"Inspiring talks"')).toBeInTheDocument();
    expect(screen.getByText('Dora')).toBeInTheDocument();
  });
});
