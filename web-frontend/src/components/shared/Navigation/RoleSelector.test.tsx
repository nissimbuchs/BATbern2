import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '../../../i18n/config';
import { RoleSelector } from './RoleSelector';
import type { UserRole } from '@/types/auth';

describe('RoleSelector', () => {
  const defaultProps = {
    roles: ['organizer', 'partner'] as UserRole[],
    activeRole: 'organizer' as UserRole,
    onChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns null when roles has 0 or 1 entries', () => {
    const { container: empty } = render(
      <RoleSelector roles={[]} activeRole={'organizer'} onChange={defaultProps.onChange} />
    );
    expect(empty.innerHTML).toBe('');

    const { container: single } = render(
      <RoleSelector
        roles={['organizer']}
        activeRole={'organizer'}
        onChange={defaultProps.onChange}
      />
    );
    expect(single.innerHTML).toBe('');
  });

  it('renders a toggle button for each role', () => {
    render(<RoleSelector {...defaultProps} />);
    expect(screen.getByRole('button', { name: /organizer/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /partner/i })).toBeInTheDocument();
  });

  it('calls onChange when a different role button is clicked', () => {
    render(<RoleSelector {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /partner/i }));
    expect(defaultProps.onChange).toHaveBeenCalledWith('partner');
  });

  it('does NOT call onChange when the active role is re-clicked', () => {
    render(<RoleSelector {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /organizer/i }));
    expect(defaultProps.onChange).not.toHaveBeenCalled();
  });

  it('renders correct labels for all four roles', () => {
    const allRoles: UserRole[] = ['organizer', 'partner', 'speaker', 'attendee'];
    render(
      <RoleSelector roles={allRoles} activeRole={'organizer'} onChange={defaultProps.onChange} />
    );
    for (const role of allRoles) {
      expect(screen.getByRole('button', { name: new RegExp(role, 'i') })).toBeInTheDocument();
    }
  });

  it('has correct aria-label on the toggle group', () => {
    render(<RoleSelector {...defaultProps} />);
    expect(screen.getByRole('group')).toHaveAttribute('aria-label');
  });
});
