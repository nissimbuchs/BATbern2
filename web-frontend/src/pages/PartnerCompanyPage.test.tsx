/**
 * PartnerCompanyPage tests
 * Story 8.0: AC1, AC5
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock useAuth
const mockUseAuth = vi.fn();
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => mockUseAuth(),
}));

// Mock PartnerDetailScreen
vi.mock('@/components/organizer/PartnerManagement/PartnerDetailScreen', () => ({
  PartnerDetailScreen: ({ companyName }: { companyName: string }) => (
    <div data-testid="partner-detail-screen" data-company-name={companyName}>
      PartnerDetailScreen for {companyName}
    </div>
  ),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'portal.noCompanyLinked': 'Your account is not linked to a company',
        'portal.noCompanyLinked.detail': 'Please contact your organizer',
      };
      return translations[key] ?? key;
    },
  }),
}));

const renderPage = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        {/* lazy import workaround — import directly in test */}
        <PartnerCompanyPageDirect />
      </MemoryRouter>
    </QueryClientProvider>
  );
};

// Import after mocks are set up
import PartnerCompanyPageDirect from './PartnerCompanyPage';

describe('PartnerCompanyPage', () => {
  it('should_renderErrorAlert_when_noCompanyNameInAuth', () => {
    mockUseAuth.mockReturnValue({ user: { role: 'partner', companyName: undefined } });

    render(
      <MemoryRouter>
        <PartnerCompanyPageDirect />
      </MemoryRouter>
    );

    expect(screen.getByTestId('no-company-linked-alert')).toBeInTheDocument();
    expect(screen.getByText('Your account is not linked to a company')).toBeInTheDocument();
  });

  it('should_renderPartnerDetailScreen_when_companyNameInAuth', () => {
    mockUseAuth.mockReturnValue({ user: { role: 'partner', companyName: 'Acme Corp' } });

    render(
      <MemoryRouter>
        <PartnerCompanyPageDirect />
      </MemoryRouter>
    );

    const detail = screen.getByTestId('partner-detail-screen');
    expect(detail).toBeInTheDocument();
    expect(detail).toHaveAttribute('data-company-name', 'Acme Corp');
  });

  it('should_renderErrorAlert_when_userIsNull', () => {
    mockUseAuth.mockReturnValue({ user: null });

    render(
      <MemoryRouter>
        <PartnerCompanyPageDirect />
      </MemoryRouter>
    );

    expect(screen.getByTestId('no-company-linked-alert')).toBeInTheDocument();
  });
});
