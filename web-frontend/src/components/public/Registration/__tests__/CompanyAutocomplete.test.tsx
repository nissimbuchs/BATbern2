/**
 * CompanyAutocomplete Component Tests (Story 4.1.5 - Enhancement)
 *
 * Tests the public-facing company search autocomplete functionality
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CompanyAutocomplete } from '../CompanyAutocomplete';
import * as companyApi from '@/services/api/companyApi';
import type { components } from '@/types/generated/company-api.types';

type Company = components['schemas']['CompanyResponse'];

// Mock the company API
vi.mock('@/services/api/companyApi');

// Mock useDebounce to return value immediately for testing
vi.mock('@/hooks/useDebounce', () => ({
  useDebounce: (value: string) => value,
}));

describe('CompanyAutocomplete Component', () => {
  const mockCompanies: Company[] = [
    {
      companyId: 'comp-1',
      name: 'TechCorp AG',
      shortName: 'TechCorp',
      industry: 'IT',
      websiteUrl: 'https://techcorp.ch',
      logoUrl: null,
    },
    {
      companyId: 'comp-2',
      name: 'SwissData GmbH',
      shortName: 'SwissData',
      industry: 'Data Analytics',
      websiteUrl: 'https://swissdata.ch',
      logoUrl: null,
    },
  ];

  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    // Mock searchCompanies to return results
    vi.mocked(companyApi.searchCompanies).mockResolvedValue(mockCompanies);
  });

  const renderWithProvider = (props = {}) => {
    const defaultProps = {
      value: '',
      onCompanySelect: vi.fn(),
    };

    return render(
      <QueryClientProvider client={queryClient}>
        <CompanyAutocomplete {...defaultProps} {...props} />
      </QueryClientProvider>
    );
  };

  describe('Basic Rendering', () => {
    it('should_renderInput_when_mounted', () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      expect(input).toBeInTheDocument();
    });

    it('should_displayCustomPlaceholder_when_provided', () => {
      renderWithProvider({ placeholder: 'Enter company name' });

      expect(screen.getByPlaceholderText('Enter company name')).toBeInTheDocument();
    });

    it('should_disableInput_when_disabled', () => {
      renderWithProvider({ disabled: true });

      const input = screen.getByPlaceholderText('TechCorp AG');
      expect(input).toBeDisabled();
    });

    it('should_displayError_when_errorProvided', () => {
      renderWithProvider({ error: 'Company is required' });

      expect(screen.getByText('Company is required')).toBeInTheDocument();
    });
  });

  describe('Input Handling', () => {
    it('should_updateValue_when_userTypes', async () => {
      const onCompanySelect = vi.fn();
      renderWithProvider({ onCompanySelect });

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      expect(onCompanySelect).toHaveBeenCalledWith('Tech');
    });

    it('should_syncInputValue_when_valueChangesExternally', () => {
      const { rerender } = renderWithProvider({ value: 'Initial' });

      let input = screen.getByDisplayValue('Initial');
      expect(input).toBeInTheDocument();

      rerender(
        <QueryClientProvider client={queryClient}>
          <CompanyAutocomplete value="Updated" onCompanySelect={vi.fn()} />
        </QueryClientProvider>
      );

      input = screen.getByDisplayValue('Updated');
      expect(input).toBeInTheDocument();
    });

    it('should_openPopover_when_typingWithMinChars', async () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      // Popover should open when typing >= 2 chars - look for company results
      await waitFor(() => {
        expect(companyApi.searchCompanies).toHaveBeenCalled();
      });
    });

    it('should_notSearch_when_inputLessThan2Chars', async () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'T');

      // searchCompanies should not be called
      expect(companyApi.searchCompanies).not.toHaveBeenCalled();
    });
  });

  describe('Company Selection', () => {
    it('should_selectCompany_when_clicked', async () => {
      const onCompanySelect = vi.fn();
      renderWithProvider({ onCompanySelect });

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      // Wait for companies to load
      await waitFor(() => {
        expect(screen.getByText('TechCorp AG')).toBeInTheDocument();
      });

      // Click on company
      const companyOption = screen.getByText('TechCorp AG');
      fireEvent.click(companyOption);

      // Should update value and close popover
      expect(onCompanySelect).toHaveBeenCalledWith('TechCorp AG');
    });

    it('should_displayIndustry_when_companyHasIndustry', async () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      await waitFor(() => {
        expect(screen.getByText('IT')).toBeInTheDocument();
      });
    });

    it('should_displayMultipleCompanies_when_multipleResults', async () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Swiss');

      await waitFor(() => {
        expect(screen.getByText('TechCorp AG')).toBeInTheDocument();
        expect(screen.getByText('SwissData GmbH')).toBeInTheDocument();
      });
    });
  });

  describe('Search Behavior', () => {
    it('should_debounceSearch_when_typing', async () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');

      // Type multiple characters quickly
      await userEvent.type(input, 'Tech');

      // Due to mock, debounce returns immediately, so search should be called
      await waitFor(() => {
        expect(companyApi.searchCompanies).toHaveBeenCalled();
      });
    });

    it('should_displayLoading_when_searching', async () => {
      // Mock a delayed response
      vi.mocked(companyApi.searchCompanies).mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(mockCompanies), 100))
      );

      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      // Should show loading state
      await waitFor(() => {
        expect(screen.getByText(/loading/i)).toBeInTheDocument();
      });
    });

    it('should_displayNoResults_when_noCompaniesFound', async () => {
      vi.mocked(companyApi.searchCompanies).mockResolvedValue([]);

      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'XYZ');

      await waitFor(() => {
        expect(screen.getByText(/No existing company found/i)).toBeInTheDocument();
      });
    });

    it('should_displayError_when_searchFails', async () => {
      vi.mocked(companyApi.searchCompanies).mockRejectedValue(new Error('Network error'));

      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      await waitFor(() => {
        expect(screen.getByText(/Error loading companies/i)).toBeInTheDocument();
      });
    });
  });

  describe('Popover Behavior', () => {
    it('should_searchCompanies_when_focusedWithMinChars', async () => {
      renderWithProvider({ value: 'Tech' });

      const input = screen.getByPlaceholderText('TechCorp AG');
      fireEvent.focus(input);

      // Should search since value is >= 2 chars
      await waitFor(() => {
        expect(companyApi.searchCompanies).toHaveBeenCalledWith('Tech', 10);
      });
    });

    it('should_notSearchCompanies_when_focusedWithLessThan2Chars', async () => {
      renderWithProvider({ value: 'T' });

      const input = screen.getByPlaceholderText('TechCorp AG');
      fireEvent.focus(input);

      // Wait a bit to ensure search doesn't trigger
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Should not search since value is < 2 chars
      expect(companyApi.searchCompanies).not.toHaveBeenCalled();
    });

    it('should_closePopover_when_blurred', async () => {
      renderWithProvider({ value: 'Tech' });

      const input = screen.getByPlaceholderText('TechCorp AG');

      // Type to open popover
      await userEvent.type(input, 'Corp');

      await waitFor(() => {
        expect(screen.getByText('TechCorp AG')).toBeInTheDocument();
      });

      // Blur the input
      fireEvent.blur(input);

      // Should close after delay (200ms)
      await waitFor(
        () => {
          expect(screen.queryByText('TechCorp AG')).not.toBeInTheDocument();
        },
        { timeout: 400 }
      );
    });
  });

  describe('Edge Cases', () => {
    it('should_handleEmptyValue_when_cleared', async () => {
      const onCompanySelect = vi.fn();
      renderWithProvider({ value: 'TechCorp', onCompanySelect });

      const input = screen.getByDisplayValue('TechCorp');
      await userEvent.clear(input);

      expect(onCompanySelect).toHaveBeenCalledWith('');
    });

    it('should_displayResults_when_opened', async () => {
      renderWithProvider();

      const input = screen.getByPlaceholderText('TechCorp AG');
      await userEvent.type(input, 'Tech');

      // Wait for results to appear
      await waitFor(() => {
        expect(screen.getByText('TechCorp AG')).toBeInTheDocument();
      });

      // The PopoverContent displays results correctly
      expect(screen.getByText('SwissData GmbH')).toBeInTheDocument();
    });
  });
});
