/**
 * EventParticipantFilters Component Tests
 *
 * TDD Tests for event participant filters component
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import EventParticipantFilters from './EventParticipantFilters';
import { useEventParticipantStore } from '../../../stores/eventParticipantStore';

// Mock the store
vi.mock('../../../stores/eventParticipantStore');

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('EventParticipantFilters', () => {
  const mockSetFilters = vi.fn();
  const mockSetSearchQuery = vi.fn();
  const mockResetFilters = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Setup default store mock
    (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
      filters: {},
      searchQuery: '',
      setFilters: mockSetFilters,
      setSearchQuery: mockSetSearchQuery,
      resetFilters: mockResetFilters,
    });
  });

  describe('Rendering', () => {
    it('should render search input', () => {
      render(<EventParticipantFilters />);

      expect(
        screen.getByPlaceholderText('eventPage.participantFilters.search.placeholder')
      ).toBeInTheDocument();
    });

    it('should render status filter', () => {
      render(<EventParticipantFilters />);

      expect(screen.getByText('eventPage.participantFilters.status.label')).toBeInTheDocument();
      expect(screen.getByText('eventPage.participantFilters.status.all')).toBeInTheDocument();
      expect(screen.getByText('eventPage.participantFilters.status.confirmed')).toBeInTheDocument();
      expect(
        screen.getByText('eventPage.participantFilters.status.registered')
      ).toBeInTheDocument();
    });

    it('should render clear filters button', () => {
      render(<EventParticipantFilters />);

      expect(screen.getByText('eventPage.participantFilters.clearAll')).toBeInTheDocument();
    });
  });

  describe('Search Input', () => {
    it('should update search value on input', async () => {
      const user = userEvent.setup();
      render(<EventParticipantFilters />);

      const searchInput = screen.getByPlaceholderText(
        'eventPage.participantFilters.search.placeholder'
      );

      await user.type(searchInput, 'john');

      expect(searchInput).toHaveValue('john');
    });

    it('should debounce search input', async () => {
      const user = userEvent.setup();

      render(<EventParticipantFilters />);

      const searchInput = screen.getByPlaceholderText(
        'eventPage.participantFilters.search.placeholder'
      );

      await user.type(searchInput, 'john');

      // Should not call immediately
      expect(mockSetSearchQuery).not.toHaveBeenCalled();

      // Wait for debounce delay (300ms) plus a small buffer
      await new Promise((resolve) => setTimeout(resolve, 350));

      // Now it should have been called
      expect(mockSetSearchQuery).toHaveBeenCalledWith('john');
    });

    it('should display current search query from store', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: {},
        searchQuery: 'existing search',
        setFilters: mockSetFilters,
        setSearchQuery: mockSetSearchQuery,
        resetFilters: mockResetFilters,
      });

      render(<EventParticipantFilters />);

      const searchInput = screen.getByPlaceholderText(
        'eventPage.participantFilters.search.placeholder'
      );
      expect(searchInput).toHaveValue('existing search');
    });
  });

  describe('Status Filter (segmented control — Epic 14 FR26)', () => {
    it('should call setFilters when a status is selected', () => {
      render(<EventParticipantFilters />);

      const confirmedToggle = screen.getByRole('button', {
        name: 'eventPage.participantFilters.status.confirmed',
      });

      fireEvent.click(confirmedToggle);

      expect(mockSetFilters).toHaveBeenCalledWith(
        expect.objectContaining({
          status: ['CONFIRMED'],
        })
      );
    });

    it('should send the canonical WAITLIST status (not WAITLISTED) for the Waitlisted segment — FR28', () => {
      render(<EventParticipantFilters />);

      const waitlistToggle = screen.getByTestId('participant-status-WAITLIST');
      fireEvent.click(waitlistToggle);

      expect(mockSetFilters).toHaveBeenCalledWith(
        expect.objectContaining({
          status: ['WAITLIST'],
        })
      );
    });

    it('should clear the status filter when "All" is selected', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: { status: ['CONFIRMED'] },
        searchQuery: '',
        setFilters: mockSetFilters,
        setSearchQuery: mockSetSearchQuery,
        resetFilters: mockResetFilters,
      });

      render(<EventParticipantFilters />);

      fireEvent.click(
        screen.getByRole('button', { name: 'eventPage.participantFilters.status.all' })
      );

      expect(mockSetFilters).toHaveBeenCalledWith(expect.objectContaining({ status: undefined }));
    });

    it('should show "All" as pressed when no status filter is set', () => {
      render(<EventParticipantFilters />);

      const allToggle = screen.getByRole('button', {
        name: 'eventPage.participantFilters.status.all',
      });
      expect(allToggle).toHaveAttribute('aria-pressed', 'true');
    });

    it('should reflect the selected status from the store as pressed', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: { status: ['CONFIRMED'] },
        searchQuery: '',
        setFilters: mockSetFilters,
        setSearchQuery: mockSetSearchQuery,
        resetFilters: mockResetFilters,
      });

      render(<EventParticipantFilters />);

      const confirmedToggle = screen.getByRole('button', {
        name: 'eventPage.participantFilters.status.confirmed',
      });
      expect(confirmedToggle).toHaveAttribute('aria-pressed', 'true');
    });
  });

  describe('Clear Filters', () => {
    it('should call resetFilters and clear search on button click', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: { status: ['CONFIRMED'] },
        searchQuery: 'test',
        setFilters: mockSetFilters,
        setSearchQuery: mockSetSearchQuery,
        resetFilters: mockResetFilters,
      });

      render(<EventParticipantFilters />);

      const clearButton = screen.getByText('eventPage.participantFilters.clearAll');
      fireEvent.click(clearButton);

      expect(mockResetFilters).toHaveBeenCalled();
    });

    it('should clear search input when clear filters is clicked', () => {
      (useEventParticipantStore as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: {},
        searchQuery: 'test search',
        setFilters: mockSetFilters,
        setSearchQuery: mockSetSearchQuery,
        resetFilters: mockResetFilters,
      });

      render(<EventParticipantFilters />);

      const searchInput = screen.getByPlaceholderText(
        'eventPage.participantFilters.search.placeholder'
      );
      expect(searchInput).toHaveValue('test search');

      const clearButton = screen.getByText('eventPage.participantFilters.clearAll');
      fireEvent.click(clearButton);

      expect(searchInput).toHaveValue('');
    });
  });
});
