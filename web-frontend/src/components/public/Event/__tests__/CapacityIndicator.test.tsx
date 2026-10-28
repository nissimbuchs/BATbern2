/**
 * CapacityIndicator Component Tests
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC7)
 *
 * A visitor who already holds a registration must not be invited to join the waitlist:
 * the badge says what the hero card says ("You're registered!", or "on the waitlist").
 */

import { describe, test, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { CapacityIndicator } from '../CapacityIndicator';

// Mock i18n — return key as value for easy assertion
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('CapacityIndicator', () => {
  test('renders nothing when the event has no capacity limit', () => {
    const { container } = render(<CapacityIndicator registrationCapacity={null} />);

    expect(container).toBeEmptyDOMElement();
  });

  test('shows the remaining spots while the event is not full', () => {
    render(<CapacityIndicator registrationCapacity={220} spotsRemaining={5} />);

    expect(screen.getByText('capacity.spotsRemaining')).toBeInTheDocument();
  });

  test('invites to join the waitlist when the event is full', () => {
    render(<CapacityIndicator registrationCapacity={220} spotsRemaining={0} waitlistCount={17} />);

    expect(screen.getByText('capacity.fullJoinWaitlist')).toBeInTheDocument();
    expect(screen.getByText('capacity.waitlistCount')).toBeInTheDocument();
  });

  test.each(['CONFIRMED', 'REGISTERED'] as const)(
    'says "you are registered", like the hero card, for a %s registration',
    (status) => {
      render(
        <CapacityIndicator
          registrationCapacity={220}
          spotsRemaining={0}
          waitlistCount={17}
          registrationStatus={status}
        />
      );

      expect(screen.getByText('common:registration.alreadyRegistered.title')).toBeInTheDocument();
      expect(screen.queryByText('capacity.fullJoinWaitlist')).not.toBeInTheDocument();
    }
  );

  test('says "you are on the waitlist" for a waitlisted visitor', () => {
    render(
      <CapacityIndicator
        registrationCapacity={220}
        spotsRemaining={0}
        waitlistCount={17}
        registrationStatus="WAITLIST"
      />
    );

    expect(screen.getByText('capacity.onWaitlist')).toBeInTheDocument();
    expect(screen.queryByText('capacity.fullJoinWaitlist')).not.toBeInTheDocument();
  });

  test('invites to join the waitlist again after a cancellation', () => {
    render(
      <CapacityIndicator
        registrationCapacity={220}
        spotsRemaining={0}
        registrationStatus="CANCELLED"
      />
    );

    expect(screen.getByText('capacity.fullJoinWaitlist')).toBeInTheDocument();
  });
});
