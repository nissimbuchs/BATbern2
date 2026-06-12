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

  it('shows an OPEN badge and the question form for a logged-in user', () => {
    mockThread('OPEN', []);
    mockAuth(true);
    renderThread();
    expect(screen.getByTestId('qna-status')).toHaveTextContent('Open');
    expect(screen.getByTestId('qna-question-form')).toBeInTheDocument();
    expect(screen.getByTestId('qna-empty')).toBeInTheDocument();
  });

  it('shows a login hint instead of the form when anonymous on an open window', () => {
    mockThread('OPEN', []);
    mockAuth(false);
    renderThread();
    expect(screen.getByTestId('qna-login-hint')).toBeInTheDocument();
    expect(screen.queryByTestId('qna-question-form')).not.toBeInTheDocument();
  });

  it('submits a question via the add mutation', async () => {
    mockThread('OPEN', []);
    mockAuth(true);
    renderThread();
    fireEvent.change(screen.getByTestId('qna-question-input'), {
      target: { value: 'How did the migration go?' },
    });
    fireEvent.click(screen.getByTestId('qna-question-submit'));
    await waitFor(() => expect(addMutate).toHaveBeenCalled());
    expect(addMutate.mock.calls[0][0]).toEqual({ body: 'How did the migration go?' });
  });

  it('renders a FROZEN window read-only (no form) and shows posts', () => {
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
    expect(screen.queryByTestId('qna-question-form')).not.toBeInTheDocument();
    expect(screen.getByText('Archived question')).toBeInTheDocument();
  });

  it('renders removed posts as a tombstone', () => {
    mockThread('FROZEN', [
      {
        id: 'p1',
        parentPostId: null,
        postedByUsername: null,
        body: null,
        removed: true,
        createdAt: '',
      },
    ]);
    mockAuth(false);
    renderThread();
    expect(screen.getByTestId('qna-tombstone')).toHaveTextContent('Removed by an organizer');
  });

  it('shows a takedown button for organizers and calls remove', () => {
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
    const takedown = screen.getByTestId('qna-takedown');
    fireEvent.click(takedown);
    expect(removeMutate).toHaveBeenCalledWith('p1');
  });
});
