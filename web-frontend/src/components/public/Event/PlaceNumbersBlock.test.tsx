/**
 * PlaceNumbersBlock (#1238): one block, total = taken + free, waitlist on top.
 */
import { describe, test, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PlaceNumbersBlock } from './PlaceNumbersBlock';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

describe('PlaceNumbersBlock', () => {
  test('should show total, taken, free and waitlist with the BATbern60 live values', () => {
    render(
      <PlaceNumbersBlock places={{ total: 253, taken: 245, free: 8, waitlist: 0, note: null }} />
    );

    expect(screen.getByTestId('places-total')).toHaveTextContent('253 public.places.total');
    expect(screen.getByTestId('places-taken')).toHaveTextContent('245 public.places.taken');
    expect(screen.getByTestId('places-free')).toHaveTextContent('8 public.places.free');
    expect(screen.getByTestId('places-waitlist')).toHaveTextContent('0 public.places.waitlist');
    expect(screen.queryByTestId('places-note')).not.toBeInTheDocument();
  });

  test('should show only the taken places without a capacity', () => {
    render(
      <PlaceNumbersBlock places={{ total: null, taken: 80, free: null, waitlist: 0, note: null }} />
    );

    expect(screen.getByTestId('places-taken')).toHaveTextContent('80');
    expect(screen.queryByTestId('places-total')).not.toBeInTheDocument();
    expect(screen.queryByTestId('places-free')).not.toBeInTheDocument();
    expect(screen.queryByTestId('places-waitlist')).not.toBeInTheDocument();
  });

  test('should say "registration closed" after the deadline, without a call to action', () => {
    render(
      <PlaceNumbersBlock
        places={{ total: 253, taken: 245, free: 8, waitlist: 0, note: 'CLOSED' }}
      />
    );

    expect(screen.getByTestId('places-note')).toHaveTextContent('public.places.closed');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  test('should say "fully booked" when full', () => {
    render(
      <PlaceNumbersBlock places={{ total: 253, taken: 253, free: 0, waitlist: 4, note: 'FULL' }} />
    );

    expect(screen.getByTestId('places-note')).toHaveTextContent('public.places.full');
  });
});
