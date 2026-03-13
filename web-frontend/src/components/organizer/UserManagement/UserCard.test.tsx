import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../../../i18n/config';
import { UserCard } from './UserCard';
import type { User } from '@/types/user.types';

// Mock CompanyCell
vi.mock('./CompanyCell', () => ({
  default: ({ companyId }: { companyId?: string }) => (
    <div data-testid="company-cell">{companyId || 'N/A'}</div>
  ),
}));

// Mock ROLE_ICONS
vi.mock('@/types/user.types', async () => {
  const actual = await vi.importActual<typeof import('@/types/user.types')>('@/types/user.types');
  return {
    ...actual,
    ROLE_ICONS: {
      ORGANIZER: 'O',
      SPEAKER: 'S',
      PARTNER: 'P',
      ATTENDEE: 'A',
    } as Record<string, string>,
  };
});

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

const mockUser: User = {
  id: 'user-1',
  firstName: 'Alice',
  lastName: 'Smith',
  email: 'alice@example.com',
  active: true,
  hasCognitoAccount: true,
  profilePictureUrl: 'https://example.com/avatar.jpg',
  companyId: 'comp-1',
  company: { name: 'Acme', displayName: 'Acme Corp' },
  roles: ['ORGANIZER', 'SPEAKER'],
  bio: 'Test bio',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

const renderUserCard = (user: User = mockUser, onClick = vi.fn()) => {
  const onClickFn = onClick;
  render(
    <QueryClientProvider client={queryClient}>
      <UserCard user={user} onClick={onClickFn} />
    </QueryClientProvider>
  );
  return { onClick: onClickFn };
};

describe('UserCard', () => {
  beforeEach(() => {
    queryClient.clear();
  });

  it('renders user name', () => {
    renderUserCard();
    expect(screen.getByText('Alice Smith')).toBeInTheDocument();
  });

  it('renders email', () => {
    renderUserCard();
    expect(screen.getByText('alice@example.com')).toBeInTheDocument();
  });

  it('shows active status chip', () => {
    renderUserCard();
    expect(screen.getByText('status.active')).toBeInTheDocument();
  });

  it('shows inactive status when user is inactive', () => {
    renderUserCard({ ...mockUser, active: false });
    expect(screen.getByText('status.inactive')).toBeInTheDocument();
  });

  it('shows Cognito cloud icon when hasCognitoAccount is true', () => {
    renderUserCard();
    expect(screen.getByLabelText('cognito.linked')).toBeInTheDocument();
  });

  it('does not show Cognito icon when hasCognitoAccount is false', () => {
    renderUserCard({ ...mockUser, hasCognitoAccount: false });
    expect(screen.queryByLabelText('cognito.linked')).not.toBeInTheDocument();
  });

  it('renders role badges', () => {
    renderUserCard();
    expect(screen.getByText('filters.role.organizer')).toBeInTheDocument();
    expect(screen.getByText('filters.role.speaker')).toBeInTheDocument();
  });

  it('calls onClick when card is clicked', () => {
    const { onClick } = renderUserCard();
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledWith(mockUser);
  });

  it('calls onClick on Enter key', () => {
    const { onClick } = renderUserCard();
    const actionArea = screen.getByRole('button');
    fireEvent.keyDown(actionArea, { key: 'Enter' });
    expect(onClick).toHaveBeenCalledWith(mockUser);
  });

  it('calls onClick on Space key', () => {
    const { onClick } = renderUserCard();
    const actionArea = screen.getByRole('button');
    fireEvent.keyDown(actionArea, { key: ' ' });
    expect(onClick).toHaveBeenCalledWith(mockUser);
  });

  it('shows company info when companyId is present', () => {
    renderUserCard();
    expect(screen.getByTestId('company-cell')).toBeInTheDocument();
    expect(screen.getByTestId('company-cell')).toHaveTextContent('comp-1');
  });

  it('does not show company section when no companyId', () => {
    renderUserCard({ ...mockUser, companyId: undefined });
    expect(screen.queryByTestId('company-cell')).not.toBeInTheDocument();
  });

  it('shows avatar with initials when no profilePictureUrl', () => {
    renderUserCard({ ...mockUser, profilePictureUrl: null });
    expect(screen.getByText('AS')).toBeInTheDocument();
  });
});
