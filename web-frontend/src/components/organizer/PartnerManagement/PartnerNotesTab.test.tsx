import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PartnerNotesTab from './PartnerNotesTab';
import { usePartnerNotes } from '@/hooks/usePartnerNotes';
import { usePartnerDetailStore } from '@/stores/partnerDetailStore';

// Mock the hooks
vi.mock('@/hooks/usePartnerNotes');
vi.mock('@/stores/partnerDetailStore');

const mockUsePartnerNotes = vi.mocked(usePartnerNotes);
const mockUsePartnerDetailStore = vi.mocked(usePartnerDetailStore);

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const renderWithQueryClient = (ui: React.ReactElement) => {
  const queryClient = createTestQueryClient();
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
};

const mockNotes = [
  {
    id: 'note-1',
    title: 'Q1 Planning Discussion',
    content: '<p>Discussed partnership goals for Q1 2025</p>',
    authorUsername: 'john.doe',
    createdAt: '2025-01-09T10:30:00Z',
    updatedAt: '2025-01-09T10:30:00Z',
  },
  {
    id: 'note-2',
    title: 'Contract Renewal',
    content: '<p>Partner expressed interest in upgrading tier</p>',
    authorUsername: 'jane.smith',
    createdAt: '2025-01-08T14:00:00Z',
    updatedAt: '2025-01-08T14:00:00Z',
  },
];

describe('PartnerNotesTab Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUsePartnerDetailStore.mockReturnValue({
      showNoteModal: false,
      setShowNoteModal: vi.fn(),
    } as any);
  });

  // AC7 Test 7.1: should_renderNotesTab_when_tabActivated
  it('should_renderNotesTab_when_tabActivated', () => {
    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    expect(screen.getByText(/notes/i)).toBeInTheDocument();
    expect(screen.getByText(/Q1 Planning Discussion/i)).toBeInTheDocument();
  });

  // AC7 Test 7.2: should_displayNotesList_when_notesLoaded
  it('should_displayNotesList_when_notesLoaded', () => {
    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    // Verify all notes are displayed
    expect(screen.getByText(/Q1 Planning Discussion/i)).toBeInTheDocument();
    expect(screen.getByText(/Contract Renewal/i)).toBeInTheDocument();

    // Verify authors are displayed
    expect(screen.getByText(/john.doe/i)).toBeInTheDocument();
    expect(screen.getByText(/jane.smith/i)).toBeInTheDocument();

    // Verify dates are displayed
    expect(screen.getByText(/Jan 9, 2025/i)).toBeInTheDocument();
    expect(screen.getByText(/Jan 8, 2025/i)).toBeInTheDocument();
  });

  // AC7 Test 7.3: should_openNoteModal_when_addButtonClicked
  it('should_openNoteModal_when_addButtonClicked', () => {
    const mockSetShowNoteModal = vi.fn();
    mockUsePartnerDetailStore.mockReturnValue({
      showNoteModal: false,
      setShowNoteModal: mockSetShowNoteModal,
    } as any);

    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    const addButton = screen.getByRole('button', { name: /add note/i });
    fireEvent.click(addButton);

    expect(mockSetShowNoteModal).toHaveBeenCalledWith(true);
  });

  // AC7 Test 7.4: should_createNote_when_noteSubmitted
  it('should_createNote_when_noteSubmitted', async () => {
    const mockCreateNote = vi.fn();
    mockUsePartnerDetailStore.mockReturnValue({
      showNoteModal: true,
      setShowNoteModal: vi.fn(),
    } as any);

    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: mockCreateNote,
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    // Find the modal (should be visible when showNoteModal is true)
    const titleInput = screen.getByLabelText(/title/i);
    const contentInput = screen.getByLabelText(/content/i);
    const saveButton = screen.getByRole('button', { name: /save/i });

    // Fill in the form
    fireEvent.change(titleInput, { target: { value: 'New Note' } });
    fireEvent.change(contentInput, { target: { value: '<p>New note content</p>' } });

    // Submit the form
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockCreateNote).toHaveBeenCalledWith({
        title: 'New Note',
        content: '<p>New note content</p>',
      });
    });
  });

  // AC7 Test 7.5: should_editNote_when_editButtonClicked
  it('should_editNote_when_editButtonClicked', async () => {
    const mockUpdateNote = vi.fn();
    const mockSetShowNoteModal = vi.fn();

    mockUsePartnerDetailStore.mockReturnValue({
      showNoteModal: false,
      setShowNoteModal: mockSetShowNoteModal,
    } as any);

    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: mockUpdateNote,
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    // Find edit button for first note
    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    fireEvent.click(editButtons[0]);

    // Verify modal opens
    expect(mockSetShowNoteModal).toHaveBeenCalledWith(true);
  });

  // AC7 Test 7.6: should_deleteNote_when_deleteConfirmed
  it('should_deleteNote_when_deleteConfirmed', async () => {
    const mockDeleteNote = vi.fn();

    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: mockDeleteNote,
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    // Find delete button for first note (Delete icon buttons, then the dialog confirm)
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);

    // Confirmation dialog should appear — click the confirm button
    const confirmButton = await screen.findByTestId('confirm-delete-note');
    fireEvent.click(confirmButton);

    expect(mockDeleteNote).toHaveBeenCalledWith('note-1');
  });

  // AC7 Test 7.7: should_showAuthorAndTimestamp_when_noteDisplayed
  it('should_showAuthorAndTimestamp_when_noteDisplayed', () => {
    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    // Verify author usernames are displayed
    expect(screen.getByText(/john.doe/i)).toBeInTheDocument();
    expect(screen.getByText(/jane.smith/i)).toBeInTheDocument();

    // Verify timestamps are displayed
    expect(screen.getByText(/Jan 9, 2025/i)).toBeInTheDocument();
    expect(screen.getByText(/Jan 8, 2025/i)).toBeInTheDocument();
  });

  // AC7 Test 7.8: should_displayEmptyState_when_noNotes
  it('should_displayEmptyState_when_noNotes', () => {
    mockUsePartnerNotes.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    expect(screen.getByText(/no notes yet/i)).toBeInTheDocument();
  });

  // AC7 Test 7.9: should_displayLoadingState_when_dataFetching
  it('should_displayLoadingState_when_dataFetching', () => {
    mockUsePartnerNotes.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  // AC7 Test 7.10: should_displayError_when_fetchFails
  it('should_displayError_when_fetchFails', () => {
    const error = new Error('Failed to fetch notes');
    mockUsePartnerNotes.mockReturnValue({
      data: undefined,
      isLoading: false,
      error,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    expect(screen.getByText(/failed to load notes/i)).toBeInTheDocument();
    expect(screen.getByText(/failed to fetch notes/i)).toBeInTheDocument();
  });

  // AC7 Test 7.11: should_closeModal_when_cancelClicked
  it('should_closeModal_when_cancelClicked', () => {
    const mockSetShowNoteModal = vi.fn();
    mockUsePartnerDetailStore.mockReturnValue({
      showNoteModal: true,
      setShowNoteModal: mockSetShowNoteModal,
    } as any);

    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: vi.fn(),
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelButton);

    expect(mockSetShowNoteModal).toHaveBeenCalledWith(false);
  });

  // AC7 Test 7.12: should_cancelDelete_when_confirmationDenied
  it('should_cancelDelete_when_confirmationDenied', async () => {
    const mockDeleteNote = vi.fn();

    mockUsePartnerNotes.mockReturnValue({
      data: mockNotes,
      isLoading: false,
      error: null,
      createNote: vi.fn(),
      updateNote: vi.fn(),
      deleteNote: mockDeleteNote,
    } as any);

    renderWithQueryClient(<PartnerNotesTab companyName="GoogleZH" />);

    // Find delete button for first note
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);

    // Confirmation dialog should appear — click Cancel to dismiss
    await screen.findByTestId('confirm-delete-note');
    const cancelButton = screen.getByRole('button', { name: /^cancel$/i });
    fireEvent.click(cancelButton);

    expect(mockDeleteNote).not.toHaveBeenCalled();
  });
});
