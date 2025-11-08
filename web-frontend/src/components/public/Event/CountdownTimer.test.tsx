/**
 * CountdownTimer Component Tests
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CountdownTimer } from './CountdownTimer';

// Mock date-fns
vi.mock('date-fns', () => ({
  differenceInDays: vi.fn(),
}));

import { differenceInDays } from 'date-fns';

describe('CountdownTimer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render countdown when event is today', () => {
    vi.mocked(differenceInDays).mockReturnValue(0);
    const futureDate = new Date('2025-03-15');

    render(<CountdownTimer eventDate={futureDate} />);

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('Today!')).toBeInTheDocument();
  });

  it('should render countdown when event is tomorrow', () => {
    vi.mocked(differenceInDays).mockReturnValue(1);
    const futureDate = new Date('2025-03-16');

    render(<CountdownTimer eventDate={futureDate} />);

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('Tomorrow!')).toBeInTheDocument();
  });

  it('should render countdown for multiple days', () => {
    vi.mocked(differenceInDays).mockReturnValue(15);
    const futureDate = new Date('2025-03-30');

    render(<CountdownTimer eventDate={futureDate} />);

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('15 days until event')).toBeInTheDocument();
  });

  it('should render countdown for 1 day with singular form', () => {
    vi.mocked(differenceInDays).mockReturnValue(1);
    const futureDate = new Date('2025-03-16');

    render(<CountdownTimer eventDate={futureDate} />);

    // Should say "Tomorrow!" for 1 day
    expect(screen.getByText('Tomorrow!')).toBeInTheDocument();
  });

  it('should not render when event is more than 30 days away', () => {
    vi.mocked(differenceInDays).mockReturnValue(35);
    const futureDate = new Date('2025-04-20');

    const { container } = render(<CountdownTimer eventDate={futureDate} />);

    expect(container.firstChild).toBeNull();
  });

  it('should not render when event has passed', () => {
    vi.mocked(differenceInDays).mockReturnValue(-1);
    const pastDate = new Date('2025-01-01');

    const { container } = render(<CountdownTimer eventDate={pastDate} />);

    expect(container.firstChild).toBeNull();
  });

  it('should render at exactly 30 days', () => {
    vi.mocked(differenceInDays).mockReturnValue(30);
    const futureDate = new Date('2025-04-14');

    render(<CountdownTimer eventDate={futureDate} />);

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('30 days until event')).toBeInTheDocument();
  });

  it('should render pulsing animation elements', () => {
    vi.mocked(differenceInDays).mockReturnValue(7);
    const futureDate = new Date('2025-03-22');

    const { container } = render(<CountdownTimer eventDate={futureDate} />);

    // Check for pulsing dot container
    const pulsingContainer = container.querySelector('.relative');
    expect(pulsingContainer).toBeInTheDocument();

    // Check for animated elements
    const animatedDots = container.querySelectorAll('.animate-pulse, .animate-ping');
    expect(animatedDots.length).toBeGreaterThan(0);
  });

  it('should have correct styling classes', () => {
    vi.mocked(differenceInDays).mockReturnValue(10);
    const futureDate = new Date('2025-03-25');

    const { container } = render(<CountdownTimer eventDate={futureDate} />);

    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper).toHaveClass('flex', 'items-center', 'gap-3');
  });

  it('should display "Next Event" text with primary color', () => {
    vi.mocked(differenceInDays).mockReturnValue(5);
    const futureDate = new Date('2025-03-20');

    render(<CountdownTimer eventDate={futureDate} />);

    const nextEventText = screen.getByText('Next Event');
    expect(nextEventText).toHaveClass('text-primary', 'font-medium');
  });

  it('should handle edge case of 31 days (should not render)', () => {
    vi.mocked(differenceInDays).mockReturnValue(31);
    const futureDate = new Date('2025-04-15');

    const { container } = render(<CountdownTimer eventDate={futureDate} />);

    expect(container.firstChild).toBeNull();
  });

  it('should display today with green color emphasis', () => {
    vi.mocked(differenceInDays).mockReturnValue(0);
    const futureDate = new Date('2025-03-15');

    render(<CountdownTimer eventDate={futureDate} />);

    const todayText = screen.getByText('Today!');
    expect(todayText).toHaveClass('text-green-400', 'font-medium');
  });

  it('should display tomorrow with orange color emphasis', () => {
    vi.mocked(differenceInDays).mockReturnValue(1);
    const futureDate = new Date('2025-03-16');

    render(<CountdownTimer eventDate={futureDate} />);

    const tomorrowText = screen.getByText('Tomorrow!');
    expect(tomorrowText).toHaveClass('text-orange-400', 'font-medium');
  });
});
