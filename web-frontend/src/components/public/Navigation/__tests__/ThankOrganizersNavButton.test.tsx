/**
 * ThankOrganizersNavButton Tests (Story 7.4 — nav "like"-style thank-you button)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThankOrganizersNavButton } from '../ThankOrganizersNavButton';

vi.mock('@/hooks/useThanks/useThanks', () => ({
  useThanksCount: vi.fn(),
  useSubmitThanks: vi.fn(),
}));

vi.mock('@/hooks/useTurnstile', () => ({
  useTurnstile: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { count?: number }) => {
      const map: Record<string, string> = {
        'thanks.widget.title': 'Thank the organizers',
        'thanks.widget.subtitle': 'BATbern is run by volunteers.',
        'thanks.widget.notePlaceholder': 'Add a note (optional)',
        'thanks.widget.button': 'Thank the organizers',
        'thanks.widget.success': 'Thank you for saying thanks!',
        'thanks.widget.error': 'Something went wrong.',
      };
      if (key === 'thanks.widget.count') return `${opts?.count ?? 0} thank-yous so far`;
      return map[key] ?? key;
    },
  }),
}));

import { useThanksCount, useSubmitThanks } from '@/hooks/useThanks/useThanks';
import { useTurnstile } from '@/hooks/useTurnstile';

const submitMutate = vi.fn();
const getToken = vi.fn(() => Promise.resolve<string | null>('tok-123'));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useThanksCount).mockReturnValue({
    data: { count: 7 },
  } as ReturnType<typeof useThanksCount>);
  vi.mocked(useSubmitThanks).mockReturnValue({
    mutate: submitMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useSubmitThanks>);
  vi.mocked(useTurnstile).mockReturnValue({
    getToken,
    resetWidget: vi.fn(),
    widgetRef: { current: null },
  } as unknown as ReturnType<typeof useTurnstile>);
});

function renderButton() {
  return render(<ThankOrganizersNavButton eventCode="BATbern57" />);
}

describe('ThankOrganizersNavButton', () => {
  it('renders the trigger with the aggregate count and no popover until clicked', () => {
    renderButton();
    expect(screen.getByTestId('thanks-nav-count')).toHaveTextContent('7');
    expect(screen.queryByTestId('thanks-note-input')).not.toBeInTheDocument();
  });

  it('opens the note popover when the trigger is clicked', async () => {
    renderButton();
    fireEvent.click(screen.getByTestId('thanks-nav-trigger'));
    await waitFor(() => expect(screen.getByTestId('thanks-note-input')).toBeInTheDocument());
    expect(screen.getByTestId('thanks-submit')).toBeInTheDocument();
  });

  it('submits a thank-you with the note and a turnstile token', async () => {
    renderButton();
    fireEvent.click(screen.getByTestId('thanks-nav-trigger'));
    await waitFor(() => expect(screen.getByTestId('thanks-note-input')).toBeInTheDocument());
    fireEvent.change(screen.getByTestId('thanks-note-input'), {
      target: { value: 'Thanks for 20 years!' },
    });
    fireEvent.click(screen.getByTestId('thanks-submit'));
    await waitFor(() => expect(submitMutate).toHaveBeenCalled());
    expect(submitMutate.mock.calls[0][0]).toEqual({
      note: 'Thanks for 20 years!',
      turnstileToken: 'tok-123',
    });
  });

  it('shows the success message after a successful submit', async () => {
    submitMutate.mockImplementation((_vars, opts) => opts.onSuccess?.());
    renderButton();
    fireEvent.click(screen.getByTestId('thanks-nav-trigger'));
    await waitFor(() => expect(screen.getByTestId('thanks-submit')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('thanks-submit'));
    await waitFor(() => expect(screen.getByTestId('thanks-success')).toBeInTheDocument());
  });

  it('shows an error message when the submit fails', async () => {
    submitMutate.mockImplementation((_vars, opts) => opts.onError?.(new Error('boom')));
    renderButton();
    fireEvent.click(screen.getByTestId('thanks-nav-trigger'));
    await waitFor(() => expect(screen.getByTestId('thanks-submit')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('thanks-submit'));
    await waitFor(() => expect(screen.getByTestId('thanks-error')).toBeInTheDocument());
  });
});
