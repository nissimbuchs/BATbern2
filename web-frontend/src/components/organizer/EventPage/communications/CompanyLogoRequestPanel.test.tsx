/**
 * #1246: the organizer decides a company logo request in the Mail Center: old and new logo side by
 * side, accept, or decline with an optional reason.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CompanyLogoRequestPanel } from './CompanyLogoRequestPanel';
import * as api from '@/services/api/userManagementApi';
import type { CompanyLogoRequestView } from '@/services/api/userManagementApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${JSON.stringify(opts)}` : key,
  }),
}));

vi.mock('@/services/api/userManagementApi', () => ({
  getCompanyLogoRequest: vi.fn(),
  approveCompanyLogoRequest: vi.fn(),
  declineCompanyLogoRequest: vi.fn(),
}));

const open: CompanyLogoRequestView = {
  requestId: 'r-1',
  status: 'OPEN',
  requesterUsername: 'anna.muster',
  requesterName: 'Anna Muster',
  companyName: 'acme',
  companyDisplayName: 'ACME AG',
  oldLogoUrl: 'https://cdn.example.com/old.png',
  newLogoUrl: 'https://cdn.example.com/new.png',
  note: 'Rebranding im Juni',
  createdAt: '2026-11-07T10:00:00Z',
};

const renderPanel = (onDecided = vi.fn()) =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <CompanyLogoRequestPanel requestId="r-1" onDecided={onDecided} />
    </QueryClientProvider>
  );

describe('CompanyLogoRequestPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the company, the old and the new logo and the note', async () => {
    vi.mocked(api.getCompanyLogoRequest).mockResolvedValue(open);
    renderPanel();

    expect(await screen.findByTestId('logo-request-panel')).toBeInTheDocument();
    expect(screen.getByText(/ACME AG/)).toBeInTheDocument();
    expect(screen.getByTestId('logo-request-old')).toHaveAttribute('src', open.oldLogoUrl);
    expect(screen.getByTestId('logo-request-new')).toHaveAttribute('src', open.newLogoUrl);
    expect(screen.getByTestId('logo-request-note')).toHaveTextContent('Rebranding im Juni');
  });

  it('says so when the company had no logo', async () => {
    vi.mocked(api.getCompanyLogoRequest).mockResolvedValue({ ...open, oldLogoUrl: null });
    renderPanel();

    expect(await screen.findByTestId('logo-request-no-old')).toBeInTheDocument();
    expect(screen.queryByTestId('logo-request-old')).not.toBeInTheDocument();
  });

  it('accepts the request', async () => {
    vi.mocked(api.getCompanyLogoRequest).mockResolvedValue(open);
    vi.mocked(api.approveCompanyLogoRequest).mockResolvedValue({
      ...open,
      status: 'APPROVED',
      decidedBy: 'orga.nizer',
    });
    const onDecided = vi.fn();
    renderPanel(onDecided);

    await userEvent.click(await screen.findByTestId('logo-request-approve'));

    expect(api.approveCompanyLogoRequest).toHaveBeenCalledWith('r-1');
    expect(await screen.findByTestId('logo-request-decided')).toBeInTheDocument();
    expect(onDecided).toHaveBeenCalled();
  });

  it('declines without a reason: the reason is optional', async () => {
    vi.mocked(api.getCompanyLogoRequest).mockResolvedValue(open);
    vi.mocked(api.declineCompanyLogoRequest).mockResolvedValue({ ...open, status: 'DECLINED' });
    renderPanel();

    await userEvent.click(await screen.findByTestId('logo-request-decline'));
    const confirm = screen.getByTestId('logo-request-decline');
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    expect(api.declineCompanyLogoRequest).toHaveBeenCalledWith('r-1', '');
  });

  it('declines with a reason', async () => {
    vi.mocked(api.getCompanyLogoRequest).mockResolvedValue(open);
    vi.mocked(api.declineCompanyLogoRequest).mockResolvedValue({ ...open, status: 'DECLINED' });
    renderPanel();

    await userEvent.click(await screen.findByTestId('logo-request-decline'));
    await userEvent.type(screen.getByTestId('logo-request-reason'), 'Bitte als SVG');
    await userEvent.click(screen.getByTestId('logo-request-decline'));

    expect(api.declineCompanyLogoRequest).toHaveBeenCalledWith('r-1', 'Bitte als SVG');
  });

  it('shows a decided request without buttons', async () => {
    vi.mocked(api.getCompanyLogoRequest).mockResolvedValue({
      ...open,
      status: 'DECLINED',
      decidedBy: 'orga.nizer',
      reason: 'Unscharf',
    });
    renderPanel();

    expect(await screen.findByTestId('logo-request-decided')).toHaveTextContent('Unscharf');
    expect(screen.queryByTestId('logo-request-approve')).not.toBeInTheDocument();
  });
});
