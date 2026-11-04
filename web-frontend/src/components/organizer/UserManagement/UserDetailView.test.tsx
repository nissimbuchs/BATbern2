import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '../../../i18n/config';
import { UserDetailView } from './UserDetailView';

vi.mock('react-router-dom', () => ({
  useLocation: vi.fn(() => ({ state: null, pathname: '/organizer/users/user-1' })),
}));
vi.mock('@/hooks/useCompany/useCompany', () => ({
  useCompany: vi.fn(() => ({ data: null })),
}));
vi.mock('@/components/shared/Breadcrumbs/Breadcrumbs', () => ({
  Breadcrumbs: ({ items }: any) => (
    <nav data-testid="breadcrumbs">{items.map((i: any) => i.label).join(' > ')}</nav>
  ),
}));
// Story 16.10: the Mail tab's list (its own tests live with it)
vi.mock('@/components/organizer/MailCenter/ParticipantMailList', () => ({
  ParticipantMailList: ({ participant }: { participant: { participantUsername: string } }) => (
    <div data-testid="participant-mail-mock">{participant.participantUsername}</div>
  ),
}));
vi.mock('./EventsParticipatedTable', () => ({
  EventsParticipatedTable: ({ userId }: any) => <div data-testid="events-table">{userId}</div>,
}));

// #1183 C: the dialog has its own test; here only whether the page opens it
vi.mock('./MergeProfileDialog', () => ({
  MergeProfileDialog: ({ open, username }: any) =>
    open ? <div data-testid="merge-dialog-open">{username}</div> : null,
}));

const mockUser = {
  id: 'user-1',
  firstName: 'Alice',
  lastName: 'Smith',
  email: 'alice@example.com',
  active: true,
  hasCognitoAccount: true,
  profilePictureUrl: null,
  company: { name: 'Acme', displayName: 'Acme Corp', website: 'https://acme.com' },
  companyId: 'comp-1',
  roles: ['ORGANIZER', 'SPEAKER'],
  bio: 'Test bio content',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
} as any;

const defaultProps = {
  user: mockUser,
  onBack: vi.fn(),
};

describe('UserDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders data-testid="user-detail-view"', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByTestId('user-detail-view')).toBeInTheDocument();
  });

  it('renders user full name', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByText('Alice Smith')).toBeInTheDocument();
  });

  it('renders user email', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByText('alice@example.com')).toBeInTheDocument();
  });

  it('shows active status chip', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('shows role badges', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByText('Event Organizer')).toBeInTheDocument();
    expect(screen.getByText('Speaker')).toBeInTheDocument();
  });

  it('shows company info', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('https://acme.com')).toBeInTheDocument();
  });

  it('shows bio section', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByText('Test bio content')).toBeInTheDocument();
  });

  it('shows edit button when canEdit=true and onEdit provided', () => {
    const onEdit = vi.fn();
    render(<UserDetailView {...defaultProps} canEdit={true} onEdit={onEdit} />);
    expect(screen.getByText('Edit')).toBeInTheDocument();
  });

  it('does NOT show delete button when canDelete=false', () => {
    render(<UserDetailView {...defaultProps} canDelete={false} onDelete={vi.fn()} />);
    expect(screen.queryByText('Delete')).not.toBeInTheDocument();
  });

  it('shows delete button when canDelete=true and onDelete provided', () => {
    const onDelete = vi.fn();
    render(<UserDetailView {...defaultProps} canDelete={true} onDelete={onDelete} />);
    expect(screen.getByText('Delete')).toBeInTheDocument();
  });

  it('calls onEdit when edit clicked', () => {
    const onEdit = vi.fn();
    render(<UserDetailView {...defaultProps} canEdit={true} onEdit={onEdit} />);
    fireEvent.click(screen.getByText('Edit'));
    expect(onEdit).toHaveBeenCalledWith(mockUser);
  });

  it('opens the merge dialog for this profile (#1183 C)', () => {
    render(<UserDetailView {...defaultProps} canEdit={true} />);
    expect(screen.queryByTestId('merge-dialog-open')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('merge-profile-button'));
    expect(screen.getByTestId('merge-dialog-open')).toHaveTextContent(mockUser.id);
  });

  it('shows breadcrumbs', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.getByTestId('breadcrumbs')).toBeInTheDocument();
  });

  it('shows dates (createdAt, updatedAt)', () => {
    render(<UserDetailView {...defaultProps} />);
    const createdDate = new Date('2026-01-01T00:00:00Z').toLocaleDateString();
    const updatedDate = new Date('2026-02-01T00:00:00Z').toLocaleDateString();
    expect(screen.getByText(createdDate)).toBeInTheDocument();
    expect(screen.getByText(updatedDate)).toBeInTheDocument();
  });

  // Story 16.10 AC1: the Mail tab lists the user's mail, mounted only when opened
  it('mounts the mail list only when the Mail tab is opened', () => {
    render(<UserDetailView {...defaultProps} />);
    expect(screen.queryByTestId('participant-mail-mock')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /^mail$/i }));

    expect(screen.getByTestId('participant-mail-mock')).toHaveTextContent(defaultProps.user.id);
  });
});
