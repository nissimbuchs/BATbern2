/**
 * SessionQnaThread Tests (Story 7.5 — "The Apéro Continues")
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SessionQnaThread } from '../SessionQnaThread';

vi.mock('@/hooks/useQna/useQna', () => ({
  useSessionQna: vi.fn(),
  useAddQnaPost: vi.fn(),
  useRemoveQnaPost: vi.fn(),
}));

vi.mock('@/hooks/useAuth/useAuth', () => ({
  useAuth: vi.fn(),
}));

// SpeakerDisplay (portrait + name + company logo) has its own tests and lazy-loads the portrait
// via network hooks; here we stub it to assert SessionQnaThread feeds it the right poster fields.
vi.mock('@/components/public/Event/SpeakerDisplay', () => ({
  SpeakerDisplay: ({ speaker }: { speaker: Record<string, string | undefined> }) => (
    <div data-testid="qna-post-author">
      <span>
        {speaker.firstName} {speaker.lastName}
      </span>
      {speaker.companyLogoUrl && (
        <img alt={speaker.companyDisplayName ?? ''} src={speaker.companyLogoUrl} />
      )}
    </div>
  ),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'qna.title': 'Q&A',
        'qna.statusOpen': 'Open',
        'qna.statusFrozen': 'Closed',
        'qna.empty': 'No questions yet. Be the first to ask.',
        'qna.removed': 'Removed by an organizer',
        'qna.takedown': 'Remove',
        'qna.reply': 'Reply',
        'qna.cancel': 'Cancel',
        'qna.replyPlaceholder': 'Write an answer…',
        'qna.questionPlaceholder': 'Ask a question about this session…',
        'qna.askQuestion': 'Ask a question',
        'qna.viewThread': 'View Q&A',
        'qna.loginToPost': 'Log in to ask or answer questions.',
      };
      return map[key] ?? key;
    },
  }),
}));

import { useSessionQna, useAddQnaPost, useRemoveQnaPost } from '@/hooks/useQna/useQna';
import { useAuth } from '@/hooks/useAuth/useAuth';

type Post = {
  id: string;
  parentPostId: string | null;
  postedByUsername: string | null;
  postedByFirstName?: string | null;
  postedByLastName?: string | null;
  postedByCompanyName?: string | null;
  postedByCompanyLogoUrl?: string | null;
  body: string | null;
  removed: boolean;
  createdAt: string;
};

function mockThread(status: 'OPEN' | 'FROZEN', posts: Post[]) {
  vi.mocked(useSessionQna).mockReturnValue({
    data: { status, opensAt: '', closesAt: '', posts },
    isError: false,
  } as ReturnType<typeof useSessionQna>);
}

function mockAuth(isAuthenticated: boolean, organizer = false) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated,
    hasRole: (r: string) => organizer && r === 'organizer',
  } as unknown as ReturnType<typeof useAuth>);
}

const addMutate = vi.fn();
const removeMutate = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useAddQnaPost).mockReturnValue({
    mutate: addMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useAddQnaPost>);
  vi.mocked(useRemoveQnaPost).mockReturnValue({
    mutate: removeMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useRemoveQnaPost>);
});

function renderThread() {
  return render(<SessionQnaThread eventCode="BATbern57" sessionSlug="cloud-native" />);
}

/** The thread is collapsed by default — open it before asserting on inner content. */
function expandThread() {
  fireEvent.click(screen.getByTestId('qna-toggle'));
}

describe('SessionQnaThread', () => {
  it('renders nothing when there is no window (404 / isError)', () => {
    vi.mocked(useSessionQna).mockReturnValue({
      data: undefined,
      isError: true,
    } as ReturnType<typeof useSessionQna>);
    mockAuth(false);
    const { container } = renderThread();
    expect(container).toBeEmptyDOMElement();
  });

  it('is collapsed by default — status visible in the toggle, panel hidden', () => {
    mockThread('OPEN', []);
    mockAuth(true);
    renderThread();
    expect(screen.getByTestId('qna-toggle')).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByTestId('qna-status')).toHaveTextContent('Open');
    expect(screen.queryByTestId('qna-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('qna-question-form')).not.toBeInTheDocument();
  });

  it('shows the question count badge in the collapsed header', () => {
    mockThread('OPEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: 'jane',
        body: 'A question',
        removed: false,
        createdAt: '',
      },
    ]);
    mockAuth(false);
    renderThread();
    expect(screen.getByTestId('qna-count')).toHaveTextContent('1');
  });

  it('excludes removed (tombstoned) questions from the count badge', () => {
    mockThread('FROZEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: 'jane',
        body: 'A live question',
        removed: false,
        createdAt: '',
      },
      {
        id: 'p2',
        parentPostId: null,
        postedByUsername: null,
        body: null,
        removed: true,
        createdAt: '',
      },
    ]);
    mockAuth(false);
    renderThread();
    expect(screen.getByTestId('qna-count')).toHaveTextContent('1');
  });

  it('shows an OPEN badge and the question form for a logged-in user once expanded', () => {
    mockThread('OPEN', []);
    mockAuth(true);
    renderThread();
    expect(screen.getByTestId('qna-status')).toHaveTextContent('Open');
    expandThread();
    expect(screen.getByTestId('qna-question-form')).toBeInTheDocument();
    expect(screen.getByTestId('qna-empty')).toBeInTheDocument();
  });

  it('shows a login hint instead of the form when anonymous on an open window', () => {
    mockThread('OPEN', []);
    mockAuth(false);
    renderThread();
    expandThread();
    expect(screen.getByTestId('qna-login-hint')).toBeInTheDocument();
    expect(screen.queryByTestId('qna-question-form')).not.toBeInTheDocument();
  });

  it('submits a question via the add mutation', async () => {
    mockThread('OPEN', []);
    mockAuth(true);
    renderThread();
    expandThread();
    fireEvent.change(screen.getByTestId('qna-question-input'), {
      target: { value: 'How did the migration go?' },
    });
    fireEvent.click(screen.getByTestId('qna-question-submit'));
    await waitFor(() => expect(addMutate).toHaveBeenCalled());
    expect(addMutate.mock.calls[0][0]).toEqual({ body: 'How did the migration go?' });
  });

  it('renders a FROZEN window read-only (no form) and shows posts once expanded', () => {
    mockThread('FROZEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: 'jane',
        body: 'Archived question',
        removed: false,
        createdAt: '',
      },
    ]);
    mockAuth(true);
    renderThread();
    expect(screen.getByTestId('qna-status')).toHaveTextContent('Closed');
    expandThread();
    expect(screen.queryByTestId('qna-question-form')).not.toBeInTheDocument();
    expect(screen.getByText('Archived question')).toBeInTheDocument();
  });

  it('does not render removed posts at all (completely dropped, not tombstoned)', () => {
    mockThread('FROZEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: 'jane',
        body: 'A live question',
        removed: false,
        createdAt: '',
      },
      {
        id: 'p2',
        parentPostId: null,
        postedByUsername: null,
        body: null,
        removed: true,
        createdAt: '',
      },
    ]);
    mockAuth(false);
    renderThread();
    expandThread();
    expect(screen.queryByTestId('qna-tombstone')).not.toBeInTheDocument();
    expect(screen.getByText('A live question')).toBeInTheDocument();
    expect(screen.getAllByTestId('qna-post')).toHaveLength(1);
  });

  it('shows a takedown button for organizers and calls remove once expanded', () => {
    mockThread('OPEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: 'jane',
        body: 'A question',
        removed: false,
        createdAt: '',
      },
    ]);
    mockAuth(true, true);
    renderThread();
    expandThread();
    const takedown = screen.getByTestId('qna-takedown');
    fireEvent.click(takedown);
    expect(removeMutate).toHaveBeenCalledWith('p1');
  });

  it('shows the poster name + company logo, not the raw username', () => {
    mockThread('FROZEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: 'jane.doe',
        postedByFirstName: 'Jane',
        postedByLastName: 'Doe',
        postedByCompanyName: 'BKW',
        postedByCompanyLogoUrl: 'https://cdn.batbern.ch/logos/bkw.png',
        body: 'Great talk',
        removed: false,
        createdAt: '',
      },
    ]);
    mockAuth(false);
    renderThread();
    expandThread();
    const author = screen.getByTestId('qna-post-author');
    expect(author).toHaveTextContent('Jane Doe');
    expect(author).not.toHaveTextContent('jane.doe');
    expect(author.querySelector('img')).toHaveAttribute('alt', 'BKW');
  });
});
