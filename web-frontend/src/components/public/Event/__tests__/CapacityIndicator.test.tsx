/**
 * CapacityIndicator Component Tests
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC7)
 *
 * A visitor who already holds a registration must not be invited to join the waitlist:
 * the hero card says "registered", so the badge below must not contradict it.
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

  test('says only "full" when the visitor is already registered', () => {
    render(
      <CapacityIndicator
        registrationCapacity={220}
        spotsRemaining={0}
        waitlistCount={17}
        alreadyRegistered
      />
    );

    expect(screen.getByText('capacity.full')).toBeInTheDocument();
    expect(screen.queryByText('capacity.fullJoinWaitlist')).not.toBeInTheDocument();
  });
});
