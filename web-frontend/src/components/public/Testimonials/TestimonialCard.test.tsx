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

  it('should render avatar image when provided', () => {
    render(<TestimonialCard {...mockTestimonial} avatar="https://example.com/avatar.jpg" />);

    const avatar = screen.getByAltText('John Doe');
    expect(avatar).toBeInTheDocument();
    expect(avatar).toHaveAttribute('src', 'https://example.com/avatar.jpg');
  });

  it('should render fallback avatar when no image provided', () => {
    render(<TestimonialCard {...mockTestimonial} />);

    // Should render first and last initials
    expect(screen.getByText('JD')).toBeInTheDocument();
  });

  it('should have proper styling classes', () => {
    const { container } = render(<TestimonialCard {...mockTestimonial} />);

    const card = container.querySelector('.flex-shrink-0');
    expect(card).toHaveClass('w-80');
  });
});
