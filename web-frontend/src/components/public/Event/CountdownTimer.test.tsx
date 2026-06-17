/**
 * CountdownTimer Component Tests
 * Story 4.1.3: Event Landing Page Hero Section
 *
 * These tests use REAL date-fns + fake timers (no module mock) so they exercise the
 * actual day-difference math. The previous version mocked differenceInDays and fed it
 * hand-picked integers, which masked a real off-by-one: differenceInDays counts whole
 * 24h periods, so an event at 00:00 two calendar days out read as "Tomorrow!". The fix
 * is differenceInCalendarDays; the regression test below pins it.
 *
 * Dates are built with the local-time constructor (new Date(y, mIdx, d, h)) so the
 * calendar-day arithmetic is deterministic regardless of the test runner's timezone.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CountdownTimer } from './CountdownTimer';

describe('CountdownTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should render "Today!" when the event is later the same calendar day', () => {
    vi.setSystemTime(new Date(2026, 5, 19, 8, 0)); // 2026-06-19 08:00 local
    render(<CountdownTimer eventDate={new Date(2026, 5, 19, 20, 0)} />); // same day, 20:00

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('Today!')).toBeInTheDocument();
  });

  it('should render "Tomorrow!" when the event is the next calendar day', () => {
    vi.setSystemTime(new Date(2026, 5, 18, 9, 0)); // 2026-06-18 09:00 local
    render(<CountdownTimer eventDate={new Date(2026, 5, 19, 0, 0)} />); // next day, midnight

    expect(screen.getByText('Tomorrow!')).toBeInTheDocument();
  });

  it('should say "2 days until event" — not "Tomorrow!" — for an event two calendar days out at midnight (regression: BATbern59 on 2026-06-19 shown from 2026-06-17)', () => {
    vi.setSystemTime(new Date(2026, 5, 17, 9, 0)); // Wed 2026-06-17 09:00 local
    render(<CountdownTimer eventDate={new Date(2026, 5, 19, 0, 0)} />); // Fri 2026-06-19 00:00

    expect(screen.getByText('2 days until event')).toBeInTheDocument();
    expect(screen.queryByText('Tomorrow!')).not.toBeInTheDocument();
  });

  it('should render countdown for multiple days', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    render(<CountdownTimer eventDate={new Date(2026, 5, 16, 12, 0)} />); // +15 calendar days

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('15 days until event')).toBeInTheDocument();
  });

  it('should not render when event is more than 30 days away', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    const { container } = render(<CountdownTimer eventDate={new Date(2026, 6, 6, 12, 0)} />); // +35

    expect(container.firstChild).toBeNull();
  });

  it('should not render when event has passed', () => {
    vi.setSystemTime(new Date(2026, 5, 17, 12, 0));
    const { container } = render(<CountdownTimer eventDate={new Date(2026, 5, 16, 12, 0)} />); // -1

    expect(container.firstChild).toBeNull();
  });

  it('should render at exactly 30 days', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    render(<CountdownTimer eventDate={new Date(2026, 6, 1, 12, 0)} />); // June 1 → July 1 = 30

    expect(screen.getByText('Next Event')).toBeInTheDocument();
    expect(screen.getByText('30 days until event')).toBeInTheDocument();
  });

  it('should not render at 31 days', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    const { container } = render(<CountdownTimer eventDate={new Date(2026, 6, 2, 12, 0)} />); // 31

    expect(container.firstChild).toBeNull();
  });

  it('should render pulsing animation elements', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    const { container } = render(<CountdownTimer eventDate={new Date(2026, 5, 8, 12, 0)} />); // +7

    const pulsingContainer = container.querySelector('.relative');
    expect(pulsingContainer).toBeInTheDocument();

    const animatedDots = container.querySelectorAll('.animate-pulse, .animate-ping');
    expect(animatedDots.length).toBeGreaterThan(0);
  });

  it('should have correct styling classes', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    const { container } = render(<CountdownTimer eventDate={new Date(2026, 5, 11, 12, 0)} />); // +10

    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper).toHaveClass('flex', 'items-center', 'gap-3');
  });

  it('should display "Next Event" text with primary color', () => {
    vi.setSystemTime(new Date(2026, 5, 1, 12, 0));
    render(<CountdownTimer eventDate={new Date(2026, 5, 6, 12, 0)} />); // +5

    const nextEventText = screen.getByText('Next Event');
    expect(nextEventText).toHaveClass('text-primary', 'font-medium');
  });

  it('should display today with green color emphasis', () => {
    vi.setSystemTime(new Date(2026, 5, 19, 8, 0));
    render(<CountdownTimer eventDate={new Date(2026, 5, 19, 20, 0)} />);

    const todayText = screen.getByText('Today!');
    expect(todayText).toHaveClass('text-green-400', 'font-medium');
  });

  it('should display tomorrow with orange color emphasis', () => {
    vi.setSystemTime(new Date(2026, 5, 18, 9, 0));
    render(<CountdownTimer eventDate={new Date(2026, 5, 19, 0, 0)} />);

    const tomorrowText = screen.getByText('Tomorrow!');
    expect(tomorrowText).toHaveClass('text-orange-400', 'font-medium');
  });
});
