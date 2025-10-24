import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UserFilters from './UserFilters';
import { useUserManagementStore } from '../../../stores/userManagementStore';
import '../../../i18n/config';

// Mock the store
vi.mock('../../../stores/userManagementStore');

describe('UserFilters Component', () => {
  let queryClient: QueryClient;
  const mockSetFilters = vi.fn();
  const mockSetSearchQuery = vi.fn();
  const mockResetFilters = vi.fn();

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    // Mock the store with default values
    (useUserManagementStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      filters: {},
      searchQuery: '',
      setFilters: mockSetFilters,
      setSearchQuery: mockSetSearchQuery,
      resetFilters: mockResetFilters,
    });
  });

  const renderComponent = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <UserFilters />
      </QueryClientProvider>
    );
  };

  it('should_renderSearchInput_when_componentMounts', () => {
    renderComponent();

    const searchInput = screen.getByPlaceholderText(/search by name or email/i);
    expect(searchInput).toBeInTheDocument();
  });

  it('should_debounceSearch_when_inputTyped', async () => {
    renderComponent();

    const searchInput = screen.getByPlaceholderText(/search by name or email/i);

    // Type into search field
    fireEvent.change(searchInput, { target: { value: 'Anna' } });

    // Should not call immediately
    expect(mockSetSearchQuery).not.toHaveBeenCalled();

    // Should call after 300ms debounce
    await waitFor(
      () => {
        expect(mockSetSearchQuery).toHaveBeenCalledWith('Anna');
      },
      { timeout: 400 }
    );
  });

  it('should_renderRoleFilter_when_componentMounts', () => {
    renderComponent();

    // Check for role filter label
    const roleFilter = screen.getByLabelText(/role/i);
    expect(roleFilter).toBeInTheDocument();
  });

  it('should_updateFilters_when_roleFilterChanged', async () => {
    renderComponent();

    const roleFilter = screen.getByLabelText(/role/i);

    // Open the autocomplete and select ORGANIZER
    fireEvent.mouseDown(roleFilter);

    await waitFor(() => {
      const organizerOption = screen.getByText(/organizer/i);
      fireEvent.click(organizerOption);
    });

    expect(mockSetFilters).toHaveBeenCalledWith(
      expect.objectContaining({
        role: expect.arrayContaining(['ORGANIZER']),
      })
    );
  });

  it('should_renderStatusFilter_when_componentMounts', () => {
    renderComponent();

    // Check for status filter legend
    const statusLegend = screen.getByText(/status/i);
    expect(statusLegend).toBeInTheDocument();
  });

  it('should_updateFilters_when_statusFilterChanged', () => {
    renderComponent();

    const activeOption = screen.getByRole('radio', { name: 'Active' });
    fireEvent.click(activeOption);

    expect(mockSetFilters).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 'active',
      })
    );
  });

  it('should_resetAllFilters_when_clearButtonClicked', () => {
    // Set some filters first
    (useUserManagementStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      filters: { role: ['SPEAKER'], status: 'active' },
      searchQuery: 'test',
      setFilters: mockSetFilters,
      setSearchQuery: mockSetSearchQuery,
      resetFilters: mockResetFilters,
    });

    renderComponent();

    const clearButton = screen.getByRole('button', { name: /clear all filters/i });
    fireEvent.click(clearButton);

    expect(mockResetFilters).toHaveBeenCalled();
  });
});
