/**
 * EventAppreciationTab Tests (Story 7.7 — organizer ★ feature toggle)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EventAppreciationTab } from '../EventAppreciationTab';

vi.mock('@/hooks/useThanks/useThanks', () => ({
  useEventThanks: vi.fn(),
  useSetThanksFeatured: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { count?: number }) => {
      if (key === 'appreciation.subtitle') return `${opts?.count ?? 0} thank-yous`;
      return key;
    },
  }),
}));

import { useEventThanks, useSetThanksFeatured } from '@/hooks/useThanks/useThanks';

const mutate = vi.fn();

beforeEach(() => {
  mutate.mockReset();
  vi.mocked(useSetThanksFeatured).mockReturnValue({ mutate, isPending: false } as never);
});

describe('EventAppreciationTab', () => {
  it('lists notes and lets the organizer feature a logged-in note', () => {
    vi.mocked(useEventThanks).mockReturnValue({
      data: {
        count: 1,
        notes: [
          {
            id: 'n1',
            note: 'Great event!',
            thankedByUsername: 'alice',
            thankedByFirstName: 'Alice',
            thankedByLastName: 'Aebi',
            thankedByCompanyName: 'ELCA',
            featured: false,
          },
        ],
      },
      isLoading: false,
      isError: false,
    } as never);

    render(<EventAppreciationTab eventCode="BATbern57" />);

    expect(screen.getByText('“Great event!”')).toBeInTheDocument();
    const toggle = screen.getByTestId('appreciation-feature-toggle-n1').querySelector('input')!;
    expect(toggle).not.toBeDisabled();
    fireEvent.click(toggle);
    expect(mutate).toHaveBeenCalledWith({ id: 'n1', featured: true });
  });

  it('disables the feature toggle for anonymous notes', () => {
    vi.mocked(useEventThanks).mockReturnValue({
      data: {
        count: 1,
        notes: [
          {
            id: 'a1',
            note: 'anon clap',
            thankedByUsername: null,
            featured: false,
          },
        ],
      },
      isLoading: false,
      isError: false,
    } as never);

    render(<EventAppreciationTab eventCode="BATbern57" />);

    const toggle = screen.getByTestId('appreciation-feature-toggle-a1').querySelector('input')!;
    expect(toggle).toBeDisabled();
  });

  it('shows the empty state when there are no notes', () => {
    vi.mocked(useEventThanks).mockReturnValue({
      data: { count: 0, notes: [] },
      isLoading: false,
      isError: false,
    } as never);

    render(<EventAppreciationTab eventCode="BATbern57" />);
    expect(screen.getByTestId('appreciation-empty')).toBeInTheDocument();
  });
});
