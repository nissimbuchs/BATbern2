/**
 * CompanyAutocomplete Component Tests
 *
 * Tests the reworked selection-locked combobox: search-only input, locked chip
 * once a company is chosen, and an explicit "Create …" affordance. The component
 * reports selections as { name, displayName } objects (or null when cleared).
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CompanyAutocomplete } from '../CompanyAutocomplete';
import * as companyApi from '@/services/api/companyApi';
import type { components } from '@/types/generated/company-api.types';

type Company = components['schemas']['CompanyResponse'];

vi.mock('@/services/api/companyApi');

describe('CompanyAutocomplete Component', () => {
  const mockCompanies: Company[] = [
    {
      name: 'techcorpag',
      displayName: 'TechCorp AG',
      industry: 'IT',
    } as Company,
    {
      name: 'swissdatagmbh',
      displayName: 'SwissData GmbH',
      industry: 'Data Analytics',
    } as Company,
  ];

  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
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

  describe('Basic Rendering (unselected)', () => {
    it('should_renderSearchInput_when_noSelection', () => {
      renderWithProvider();
      expect(screen.getByPlaceholderText('TechCorp AG')).toBeInTheDocument();
    });

    it('should_displayCustomPlaceholder_when_provided', () => {
      renderWithProvider({ placeholder: 'Enter company name' });
      expect(screen.getByPlaceholderText('Enter company name')).toBeInTheDocument();
    });

    it('should_disableInput_when_disabled', () => {
      renderWithProvider({ disabled: true });
      expect(screen.getByPlaceholderText('TechCorp AG')).toBeDisabled();
    });

    it('should_displayError_when_errorProvided', () => {
      renderWithProvider({ error: 'Company is required' });
      expect(screen.getByText('Company is required')).toBeInTheDocument();
    });
  });

  describe('Selected: locked chip', () => {
    it('should_renderChipWithDisplayName_when_valueLabelProvided', () => {
      renderWithProvider({ value: 'infowellgmbh', valueLabel: 'Infowell GmbH' });

      // Shows the human display name, NOT the slug — and no editable input.
      expect(screen.getByTestId('registration-company-chip')).toBeInTheDocument();
      expect(screen.getByText('Infowell GmbH')).toBeInTheDocument();
      expect(screen.queryByPlaceholderText('TechCorp AG')).not.toBeInTheDocument();
    });

    it('should_fallBackToValue_when_noValueLabel', () => {
      renderWithProvider({ value: 'someco' });
      expect(screen.getByText('someco')).toBeInTheDocument();
    });

    it('should_showChip_when_newCompanyHasLabelButNoSlug', () => {
      // A brand-new (not yet materialised) company: slug empty, display name set.
      renderWithProvider({ value: '', valueLabel: 'Brand New Co' });
      expect(screen.getByTestId('registration-company-chip')).toBeInTheDocument();
      expect(screen.getByText('Brand New Co')).toBeInTheDocument();
    });

    it('should_clearSelection_when_clearClicked', async () => {
      const onCompanySelect = vi.fn();
      renderWithProvider({ value: 'infowellgmbh', valueLabel: 'Infowell GmbH', onCompanySelect });

      fireEvent.click(screen.getByTestId('registration-company-clear'));
      expect(onCompanySelect).toHaveBeenCalledWith(null);
    });

    it('should_hideClearButton_when_disabled', () => {
      renderWithProvider({ value: 'infowellgmbh', valueLabel: 'Infowell GmbH', disabled: true });
      expect(screen.queryByTestId('registration-company-clear')).not.toBeInTheDocument();
    });
  });

  describe('Search behavior', () => {
    it('should_notCallOnSelect_when_userTypes', async () => {
      const onCompanySelect = vi.fn();
      renderWithProvider({ onCompanySelect });

      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'Tech');
      await waitFor(() => expect(companyApi.searchCompanies).toHaveBeenCalled());

      // Typing is search-only — it must NOT submit a value.
      expect(onCompanySelect).not.toHaveBeenCalled();
    });

    it('should_search_when_typingMinChars', async () => {
      renderWithProvider();
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'Tech');
      await waitFor(() => expect(companyApi.searchCompanies).toHaveBeenCalledWith('Tech', 10));
    });

    it('should_notSearch_when_inputLessThan2Chars', async () => {
      renderWithProvider();
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'T');
      expect(companyApi.searchCompanies).not.toHaveBeenCalled();
    });

    it('should_displayResultsByDisplayName_when_resultsLoaded', async () => {
      renderWithProvider();
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'data');
      await waitFor(() => {
        expect(screen.getByText('TechCorp AG')).toBeInTheDocument();
        expect(screen.getByText('SwissData GmbH')).toBeInTheDocument();
        expect(screen.getByText('IT')).toBeInTheDocument();
      });
    });

    it('should_displayNoResults_when_noCompaniesFound', async () => {
      vi.mocked(companyApi.searchCompanies).mockResolvedValue([]);
      renderWithProvider({ allowCreate: false });
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'XYZ');
      await waitFor(() => {
        expect(screen.getByText(/No existing company found/i)).toBeInTheDocument();
      });
    });
  });

  describe('Company selection', () => {
    it('should_reportSelection_when_companyClicked', async () => {
      const onCompanySelect = vi.fn();
      renderWithProvider({ onCompanySelect });

      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'Tech');
      await waitFor(() => expect(screen.getByText('TechCorp AG')).toBeInTheDocument());

      fireEvent.click(screen.getByText('TechCorp AG'));

      // Reports the slug as name + the display name — never echoes the slug into a box.
      expect(onCompanySelect).toHaveBeenCalledWith({
        name: 'techcorpag',
        displayName: 'TechCorp AG',
      });
    });
  });

  describe('Create new company', () => {
    it('should_showCreateOption_when_noExactMatch', async () => {
      vi.mocked(companyApi.searchCompanies).mockResolvedValue([]);
      renderWithProvider();
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'Newco GmbH');
      await waitFor(() =>
        expect(screen.getByTestId('registration-company-create-option')).toBeInTheDocument()
      );
    });

    it('should_reportNewCompany_when_createClicked', async () => {
      const onCompanySelect = vi.fn();
      vi.mocked(companyApi.searchCompanies).mockResolvedValue([]);
      renderWithProvider({ onCompanySelect });

      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'Newco GmbH');
      await waitFor(() =>
        expect(screen.getByTestId('registration-company-create-option')).toBeInTheDocument()
      );
      fireEvent.click(screen.getByTestId('registration-company-create-option'));

      // New company: empty slug (parent materialises it), typed display name.
      expect(onCompanySelect).toHaveBeenCalledWith({ name: '', displayName: 'Newco GmbH' });
    });

    it('should_notShowCreateOption_when_allowCreateFalse', async () => {
      vi.mocked(companyApi.searchCompanies).mockResolvedValue([]);
      renderWithProvider({ allowCreate: false });
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'Newco GmbH');
      await waitFor(() => expect(companyApi.searchCompanies).toHaveBeenCalled());
      expect(screen.queryByTestId('registration-company-create-option')).not.toBeInTheDocument();
    });

    it('should_suppressCreateOption_when_exactDisplayNameMatch', async () => {
      renderWithProvider();
      // Exact (case-insensitive) match for an existing display name.
      await userEvent.type(screen.getByPlaceholderText('TechCorp AG'), 'techcorp ag');
      await waitFor(() => expect(screen.getByText('TechCorp AG')).toBeInTheDocument());
      expect(screen.queryByTestId('registration-company-create-option')).not.toBeInTheDocument();
    });
  });
});
