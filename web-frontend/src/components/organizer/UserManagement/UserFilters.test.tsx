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
    // Without this the shared mockSetFilters/mockSetSearchQuery accumulate calls across
    // tests, so any toHaveBeenCalledTimes() assertion here counts earlier tests' calls.
    // That is what made #820's already-working per-chip delete look like a 3-call bug.
    vi.clearAllMocks();

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

  // #820: each role chip's "x" must remove only that role. Reported as: clicking the per-chip
  // delete does nothing, and only the field-level Clear button works — which wipes ALL roles.
  describe('#820 — per-chip role removal', () => {
    const withRoles = (roles: string[]) => {
      (useUserManagementStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
        filters: { role: roles },
        searchQuery: '',
        setFilters: mockSetFilters,
        setSearchQuery: mockSetSearchQuery,
        resetFilters: mockResetFilters,
      });
    };

    it('should_renderOneChipPerSelectedRole_when_multipleRolesFiltered', () => {
      withRoles(['SPEAKER', 'ORGANIZER']);
      renderComponent();
      // MUI renders each Autocomplete tag as a Chip with a delete button.
      expect(screen.getAllByTestId('CancelIcon').length).toBe(2);
    });

    it('should_removeOnlyThatRole_when_singleChipDeleteClicked', () => {
      withRoles(['SPEAKER', 'ORGANIZER']);
      renderComponent();

      // Chip order follows roleOptions (ORGANIZER, SPEAKER, PARTNER, ATTENDEE), NOT the order
      // in filters.role — so chip[0] is ORGANIZER here.
      fireEvent.click(screen.getAllByTestId('CancelIcon')[0]);

      expect(mockSetFilters).toHaveBeenCalledTimes(1);
      const next = mockSetFilters.mock.calls[0][0];
      // Exactly one role removed and the other survives — not a full clear, which is the
      // behaviour #820 reported as broken.
      expect(next.role).toHaveLength(1);
      expect(next.role).not.toContain('ORGANIZER');
      expect(next.role).toContain('SPEAKER');
    });
  });
});
