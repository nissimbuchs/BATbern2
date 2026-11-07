/**
 * DeregistrationPage (#1238 E2): from the event day on (Bern time) the link no longer cancels; the
 * page says so instead of offering a button the backend refuses with 409.
 */
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DeregistrationPage from '../DeregistrationPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const verify = vi.hoisted(() => ({ eventDate: '' }));
vi.mock('@/hooks/useDeregistration', () => ({
  useVerifyDeregistrationToken: () => ({
    isSuccess: true,
    isError: false,
    data: {
      eventCode: 'BATbern60',
      eventTitle: 'BATbern60',
      eventDate: verify.eventDate,
      attendeeFirstName: 'Anna',
    },
  }),
  useDeregisterByToken: () => ({ mutate: vi.fn(), isPending: false }),
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/deregister?token=11111111-2222-3333-4444-555555555555']}>
      <DeregistrationPage />
    </MemoryRouter>
  );
}

describe('DeregistrationPage', () => {
  beforeEach(() => {
    verify.eventDate = '';
  });

  test('should offer the cancel button before the event day', async () => {
    verify.eventDate = new Date(Date.now() + 5 * 86_400_000).toISOString();
    renderPage();
    expect(await screen.findByText('deregistration.page.confirmButton')).toBeInTheDocument();
    expect(screen.queryByTestId('deregistration-closed')).not.toBeInTheDocument();
  });

  test('should say cancelling is closed on the event day', async () => {
    verify.eventDate = new Date().toISOString();
    renderPage();
    expect(await screen.findByTestId('deregistration-closed')).toHaveTextContent(
      'deregistration.page.closed'
    );
    expect(screen.queryByText('deregistration.page.confirmButton')).not.toBeInTheDocument();
  });
});
