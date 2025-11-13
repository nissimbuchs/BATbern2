/**
 * CompanyAutocomplete Component Tests (TDD - Story 2.8.3)
 *
 * RED Phase Tests for Company Autocomplete
 * Test AC3: Company Autocomplete provides company search
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CompanyAutocomplete } from './CompanyAutocomplete';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import * as companyApi from '@/services/api/companyApi';

// Create theme for MUI components
const theme = createTheme();

// Create a new QueryClient for each test
const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

// Wrapper component with providers
const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = createTestQueryClient();

  return (
    <I18nextProvider i18n={i18n}>
      <ThemeProvider theme={theme}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </ThemeProvider>
    </I18nextProvider>
  );
};

describe('CompanyAutocomplete Component - Story 2.8.3 AC3', () => {
  const mockOnChange = vi.fn();
  const mockCompanies = [
    {
      name: 'acme-corp',
      displayName: 'Acme Corporation',
      industry: 'Technology',
      logoUrl: 'https://cdn.batbern.ch/logos/acme-corp.png',
    },
    {
      name: 'tech-solutions',
      displayName: 'Tech Solutions AG',
      industry: 'Technology',
      logoUrl: 'https://cdn.batbern.ch/logos/tech-solutions.png',
    },
  ];

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
  });

  // Test 3.1: should_searchCompanies_when_inputChanged
  it('should_searchCompanies_when_inputChanged', async () => {
    const user = userEvent.setup();
    const searchSpy = vi.spyOn(companyApi, 'searchCompanies').mockResolvedValue(mockCompanies);

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'acme');

    await waitFor(() => {
      expect(searchSpy).toHaveBeenCalledWith('acme', expect.any(Number));
    });
  });

  // Test 3.2: should_debounceSearch_when_userTyping
  it('should_debounceSearch_when_userTyping', async () => {
    const user = userEvent.setup();
    const searchSpy = vi.spyOn(companyApi, 'searchCompanies').mockResolvedValue(mockCompanies);

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');

    // Type multiple characters quickly
    await user.type(input, 'a');
    await user.type(input, 'c');
    await user.type(input, 'm');
    await user.type(input, 'e');

    // Wait for debounce (300ms)
    await waitFor(
      () => {
        // Should only call once after debounce
        expect(searchSpy).toHaveBeenCalledTimes(1);
      },
      { timeout: 500 }
    );
  });

  // Test 3.3: should_displayCompanyOptions_when_searchCompletes
  it('should_displayCompanyOptions_when_searchCompletes', async () => {
    const user = userEvent.setup();
    vi.spyOn(companyApi, 'searchCompanies').mockResolvedValue(mockCompanies);

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'acme');

    await waitFor(() => {
      expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
      expect(screen.getByText('Tech Solutions AG')).toBeInTheDocument();
    });

    // Check that industry is displayed
    expect(screen.getAllByText(/Technology/).length).toBeGreaterThan(0);
  });

  // Test 3.4: should_selectCompany_when_optionClicked
  it('should_selectCompany_when_optionClicked', async () => {
    const user = userEvent.setup();
    vi.spyOn(companyApi, 'searchCompanies').mockResolvedValue(mockCompanies);

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'acme');

    await waitFor(() => {
      expect(screen.getByText('Acme Corporation')).toBeInTheDocument();
    });

    await user.click(screen.getByText('Acme Corporation'));

    await waitFor(() => {
      expect(mockOnChange).toHaveBeenCalledWith(mockCompanies[0]);
    });
  });

  // Test 3.5: should_displayError_when_searchFails
  it('should_displayError_when_searchFails', async () => {
    const user = userEvent.setup();
    vi.spyOn(companyApi, 'searchCompanies').mockRejectedValue(new Error('Network error'));

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'acme');

    await waitFor(() => {
      expect(screen.getByText(/error/i)).toBeInTheDocument();
    });
  });

  // Additional AC3 tests: Min 2 characters to trigger search
  it('should_notSearch_when_inputLessThan2Characters', async () => {
    const user = userEvent.setup();
    const searchSpy = vi.spyOn(companyApi, 'searchCompanies').mockResolvedValue(mockCompanies);

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'a');

    // Wait a bit to ensure no call is made
    await new Promise((resolve) => setTimeout(resolve, 400));

    expect(searchSpy).not.toHaveBeenCalled();
  });

  // Additional AC3 tests: Loading indicator
  it('should_showLoadingIndicator_when_searching', async () => {
    const user = userEvent.setup();
    vi.spyOn(companyApi, 'searchCompanies').mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(mockCompanies), 1000))
    );

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'acme');

    await waitFor(() => {
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  // Additional AC3 tests: Empty state
  it('should_showEmptyState_when_noCompaniesFound', async () => {
    const user = userEvent.setup();
    vi.spyOn(companyApi, 'searchCompanies').mockResolvedValue([]);

    await act(async () => {
      render(
        <TestWrapper>
          <CompanyAutocomplete value={null} onChange={mockOnChange} />
        </TestWrapper>
      );
    });

    const input = screen.getByRole('combobox');
    await user.type(input, 'nonexistent');

    await waitFor(() => {
      expect(screen.getByText(/no companies found/i)).toBeInTheDocument();
    });
  });
});
