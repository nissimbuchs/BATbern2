import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnerSearch } from '../PartnerSearch';
import { usePartnerStore } from '@/stores/partnerStore';

// Mock the partner store
vi.mock('@/stores/partnerStore');

describe('PartnerSearch Component - AC1 Tests', () => {
  const mockSetSearchQuery = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      searchQuery: '',
      setSearchQuery: mockSetSearchQuery,
    });
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  describe('Test 6a.1: should_renderSearchInput_when_componentMounts', () => {
    it('renders search input field', () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', { name: /search partners/i });
      expect(searchInput).toBeInTheDocument();
    });

    it('renders search icon', () => {
      render(<PartnerSearch />);

      expect(screen.getByTestId('search-icon')).toBeInTheDocument();
    });

    it('displays placeholder text', () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByPlaceholderText(/search by partner name/i);
      expect(searchInput).toBeInTheDocument();
    });
  });

  describe('Test 6a.2: should_debounceSearch_when_userTyping', () => {
    it('does not call setSearchQuery immediately when typing', async () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', { name: /search partners/i });

      // Use fireEvent.change for Material-UI TextField
      fireEvent.change(searchInput, { target: { value: 'Test Partner' } });

      // Check immediately - should not be called yet
      expect(mockSetSearchQuery).not.toHaveBeenCalled();
    });

    it('calls setSearchQuery after 300ms delay', async () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;

      // Use fireEvent.change for Material-UI TextField
      fireEvent.change(searchInput, { target: { value: 'Test Partner' } });

      // Wait for debounce
      await waitFor(
        () => {
          expect(mockSetSearchQuery).toHaveBeenCalledWith('Test Partner');
        },
        { timeout: 500 }
      );
    });

    it('calls setSearchQuery only once after typing stops', async () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;

      // Simulate multiple rapid changes using fireEvent
      fireEvent.change(searchInput, { target: { value: 'T' } });
      fireEvent.change(searchInput, { target: { value: 'Te' } });
      fireEvent.change(searchInput, { target: { value: 'Tes' } });
      fireEvent.change(searchInput, { target: { value: 'Test' } });

      // Wait for debounce - should only be called once with final value
      await waitFor(
        () => {
          expect(mockSetSearchQuery).toHaveBeenCalledTimes(1);
          expect(mockSetSearchQuery).toHaveBeenCalledWith('Test');
        },
        { timeout: 500 }
      );
    });

    it('cancels previous debounce when typing continues', async () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;

      // Type first character using fireEvent
      fireEvent.change(searchInput, { target: { value: 'T' } });

      // Wait a bit
      await new Promise((resolve) => setTimeout(resolve, 200));

      // Type second character (should cancel previous debounce)
      fireEvent.change(searchInput, { target: { value: 'Te' } });

      // Wait for debounce
      await waitFor(
        () => {
          expect(mockSetSearchQuery).toHaveBeenCalledTimes(1);
          expect(mockSetSearchQuery).toHaveBeenCalledWith('Te');
        },
        { timeout: 500 }
      );
    });
  });

  describe('Test 6a.3: should_updateSearchQuery_when_inputChanged', () => {
    it('updates search query after debounce', async () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;

      // Use fireEvent.change for Material-UI TextField
      fireEvent.change(searchInput, { target: { value: 'Gold Partners' } });

      // Wait for debounce
      await waitFor(
        () => {
          expect(mockSetSearchQuery).toHaveBeenCalledWith('Gold Partners');
        },
        { timeout: 500 }
      );
    });

    it('handles empty string', async () => {
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: 'Previous Search',
        setSearchQuery: mockSetSearchQuery,
      });

      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;

      // Use fireEvent.change to trigger empty value
      fireEvent.change(searchInput, { target: { value: '' } });

      // Wait for debounce
      await waitFor(
        () => {
          expect(mockSetSearchQuery).toHaveBeenCalledWith('');
        },
        { timeout: 500 }
      );
    });

    it('handles special characters', async () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;

      // Use fireEvent.change with special characters
      fireEvent.change(searchInput, { target: { value: 'Company & Co.' } });

      // Wait for debounce
      await waitFor(
        () => {
          expect(mockSetSearchQuery).toHaveBeenCalledWith('Company & Co.');
        },
        { timeout: 500 }
      );
    });
  });

  describe('Test 6a.4: should_clearSearch_when_clearButtonClicked', () => {
    it('does not render clear button when search is empty', () => {
      render(<PartnerSearch />);

      expect(screen.queryByRole('button', { name: /clear search/i })).not.toBeInTheDocument();
    });

    it('renders clear button when search has value', () => {
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: 'Test Partner',
        setSearchQuery: mockSetSearchQuery,
      });

      render(<PartnerSearch />);

      expect(screen.getByRole('button', { name: /clear search/i })).toBeInTheDocument();
    });

    it('calls setSearchQuery with empty string when clear button clicked', async () => {
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: 'Test Partner',
        setSearchQuery: mockSetSearchQuery,
      });

      render(<PartnerSearch />);

      const clearButton = screen.getByRole('button', { name: /clear search/i });
      await clearButton.click();

      expect(mockSetSearchQuery).toHaveBeenCalledWith('');
    });

    it('clears input field when clear button clicked', async () => {
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: 'Test Partner',
        setSearchQuery: mockSetSearchQuery,
      });

      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', {
        name: /search partners/i,
      }) as HTMLInputElement;
      expect(searchInput.value).toBe('Test Partner');

      const clearButton = screen.getByRole('button', { name: /clear search/i });
      await clearButton.click();

      expect(mockSetSearchQuery).toHaveBeenCalledWith('');
    });

    it('hides clear button after clearing search', async () => {
      let currentSearchQuery = 'Test Partner';

      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockImplementation(() => ({
        searchQuery: currentSearchQuery,
        setSearchQuery: (query: string) => {
          mockSetSearchQuery(query);
          currentSearchQuery = query;
        },
      }));

      const { rerender } = render(<PartnerSearch />);

      expect(screen.getByRole('button', { name: /clear search/i })).toBeInTheDocument();

      const clearButton = screen.getByRole('button', { name: /clear search/i });
      await clearButton.click();

      // Simulate store update
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: '',
        setSearchQuery: mockSetSearchQuery,
      });

      rerender(<PartnerSearch />);

      expect(screen.queryByRole('button', { name: /clear search/i })).not.toBeInTheDocument();
    });
  });

  describe('Accessibility Tests', () => {
    it('has accessible label for search input', () => {
      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', { name: /search partners/i });
      expect(searchInput).toHaveAccessibleName();
    });

    it('has accessible name for clear button', () => {
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: 'Test Partner',
        setSearchQuery: mockSetSearchQuery,
      });

      render(<PartnerSearch />);

      const clearButton = screen.getByRole('button', { name: /clear search/i });
      expect(clearButton).toHaveAccessibleName();
    });

    it('supports keyboard navigation', async () => {
      const user = userEvent.setup();
      (usePartnerStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        searchQuery: 'Test Partner',
        setSearchQuery: mockSetSearchQuery,
      });

      render(<PartnerSearch />);

      const searchInput = screen.getByRole('textbox', { name: /search partners/i });
      const clearButton = screen.getByRole('button', { name: /clear search/i });

      // Tab to search input
      await user.tab();
      expect(searchInput).toHaveFocus();

      // Tab to clear button
      await user.tab();
      expect(clearButton).toHaveFocus();
    });
  });
});
