import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { OrganizerChip } from '../OrganizerChip';

vi.mock('@/components/shared/OrganizerSelect', () => ({
  useOrganizers: () => ({
    organizers: [{ id: 'sandra.keller', name: 'Sandra Keller' }],
    isLoading: false,
  }),
}));

describe('OrganizerChip', () => {
  it('resolves the username to a display name + initials avatar', () => {
    render(<OrganizerChip username="sandra.keller" data-testid="org" />);
    const chip = screen.getByTestId('org');
    expect(chip).toHaveTextContent('Sandra Keller');
    expect(chip).toHaveTextContent('SK');
  });

  it('falls back to the raw username when not in the directory', () => {
    render(<OrganizerChip username="unknown.person" data-testid="org" />);
    expect(screen.getByTestId('org')).toHaveTextContent('unknown.person');
  });

  it('prefers a passed organizers directory over the hook', () => {
    render(
      <OrganizerChip
        username="john.doe"
        organizers={[{ id: 'john.doe', name: 'John Doe' }]}
        data-testid="org"
      />
    );
    expect(screen.getByTestId('org')).toHaveTextContent('John Doe');
  });

  it('renders nothing without a username', () => {
    const { container } = render(<OrganizerChip username={null} data-testid="org" />);
    expect(container).toBeEmptyDOMElement();
  });
});
