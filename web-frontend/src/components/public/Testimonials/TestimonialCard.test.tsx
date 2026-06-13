/**
 * Tests for TestimonialCard component
 */

import { render, screen } from '@testing-library/react';
import { TestimonialCard } from './TestimonialCard';

describe('TestimonialCard', () => {
  const mockTestimonial = {
    name: 'John Doe',
    quote: 'This is an excellent event!',
    company: 'Acme Corp',
  };

  it('should render testimonial content', () => {
    render(<TestimonialCard {...mockTestimonial} />);

    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText(/This is an excellent event!/i)).toBeInTheDocument();
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
  });

  it('should render the company logo when companyLogoUrl is provided', () => {
    render(
      <TestimonialCard
        {...mockTestimonial}
        companyLogoUrl="https://cdn.example.com/logos/acme.png"
      />
    );

    // Story 7.7: the logo image uses the company name as its alt text.
    const logo = screen.getByAltText('Acme Corp');
    expect(logo.tagName).toBe('IMG');
    expect(logo.getAttribute('src')).toBeTruthy();
    // The plain company-name text is replaced by the logo when a logo is present.
    expect(screen.queryByText('Acme Corp')).not.toBeInTheDocument();
  });

  it('should render the company name as text when no logo is provided', () => {
    const { container } = render(<TestimonialCard {...mockTestimonial} />);

    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it('should render the thank-you badge with an accessible label when provided', () => {
    render(<TestimonialCard {...mockTestimonial} badge="Thank you" />);

    expect(screen.getByRole('img', { name: 'Thank you' })).toBeInTheDocument();
  });

  it('should have proper styling classes', () => {
    const { container } = render(<TestimonialCard {...mockTestimonial} />);

    const card = container.querySelector('.flex-shrink-0');
    expect(card).toHaveClass('w-80');
  });
});
