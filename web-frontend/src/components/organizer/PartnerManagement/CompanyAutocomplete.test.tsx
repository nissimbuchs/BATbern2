/**
 * CompanyAutocomplete Component Tests (Story 2.8.3, AC3: company search)
 *
 * The real companyApi, useCompany and React Query run against the MSW harness (17.0c2):
 * `GET /companies/search` and `POST /companies/get-or-create` are checked against
 * companies-api, answered with typed bodies, and the test reads what was sent.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse, delay } from 'msw';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { CompanyAutocomplete } from './CompanyAutocomplete';

const theme = createTheme();

type SearchResult = ResponseBody<'searchCompanies', 200>[number];

const renderAutocomplete = (props: Partial<Parameters<typeof CompanyAutocomplete>[0]> = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onChange = vi.fn();
  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <CompanyAutocomplete value={null} onChange={onChange} {...props} />
      </QueryClientProvider>
    </ThemeProvider>
  );
  return { onChange, input: screen.getByRole('combobox') as HTMLInputElement };
};

describe('CompanyAutocomplete Component - Story 2.8.3 AC3', () => {
  const companies: SearchResult[] = [
    {
      name: 'bruennimann',
      displayName: 'Brünnimann Architektur AG',
      industry: 'Technology',
      isVerified: true,
      logo: { url: 'https://cdn.batbern.ch/logos/bruennimann.png' },
    },
    {
      name: 'bruennbau',
      displayName: 'Brünn Bau GmbH',
      industry: 'Technology',
      isVerified: false,
    },
  ];

  beforeEach(() => {
    api.on('searchCompanies', () => companies);
  });

  it('should_searchCompanies_when_inputChanged', async () => {
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'brünn');

    await waitFor(() => expect(api.calls('searchCompanies')).toHaveLength(1));
    const { query } = api.calls('searchCompanies')[0];
    expect(query.get('query')).toBe('brünn');
    expect(query.get('limit')).toBe('20');
    expect(query.get('include')).toBe('logo');
  });

  it('should_debounceSearch_when_userTyping', async () => {
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    // Type several characters quickly: one search after the 300 ms debounce, for the full term.
    await user.type(input, 'b');
    await user.type(input, 'r');
    await user.type(input, 'ü');
    await user.type(input, 'n');

    await waitFor(() => expect(api.calls('searchCompanies')).toHaveLength(1));
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(api.calls('searchCompanies').map((c) => c.query.get('query'))).toEqual(['brün']);
  });

  it('should_displayCompanyOptions_when_searchCompletes', async () => {
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'brünn');

    expect(await screen.findByText('Brünnimann Architektur AG')).toBeInTheDocument();
    expect(screen.getByText('Brünn Bau GmbH')).toBeInTheDocument();
    // Industry is displayed under the name
    expect(screen.getAllByText('Technology')).toHaveLength(2);
  });

  it('should_selectCompany_when_optionClicked', async () => {
    const user = userEvent.setup();
    const { input, onChange } = renderAutocomplete();

    await user.type(input, 'brünn');
    await user.click(await screen.findByText('Brünnimann Architektur AG'));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(companies[0]));
  });

  it('should_displayError_when_searchFails', async () => {
    api.on('searchCompanies', () =>
      HttpResponse.json({ error: 'INTERNAL_ERROR', message: 'down' }, { status: 500 })
    );
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'brünn');

    expect(await screen.findByText('Error loading companies')).toBeInTheDocument();
    // An error never offers "create": it would hide that the search failed.
    expect(screen.queryByTestId('company-create-option')).not.toBeInTheDocument();
  });

  it('should_notSearch_when_inputLessThan2Characters', async () => {
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'b');
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(api.calls('searchCompanies')).toHaveLength(0);

    // Positive control: the second character starts the search.
    await user.type(input, 'r');
    await waitFor(() => expect(api.calls('searchCompanies')).toHaveLength(1));
  });

  it('should_showLoadingIndicator_when_searching', async () => {
    api.on('searchCompanies', async () => {
      await delay(1000);
      return companies;
    });
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'brünn');

    expect(await screen.findByRole('progressbar')).toBeInTheDocument();
  });

  it('should_showEmptyState_when_noCompaniesFound', async () => {
    api.on('searchCompanies', () => []);
    const user = userEvent.setup();
    const { input } = renderAutocomplete({ allowCreate: false });

    await user.type(input, 'nirgendwo');

    expect(await screen.findByText(/no companies found/i)).toBeInTheDocument();
  });

  // Selection-locked combobox: the input shows the DISPLAY NAME, never the slug.
  it('should_showDisplayName_when_companySelected', async () => {
    const { input } = renderAutocomplete({ value: companies[0] });

    expect(input.value).toBe('Brünnimann Architektur AG'); // displayName, not 'bruennimann'
    // The selected company's logo is resolved by its slug.
    await waitFor(() => expect(api.calls('getCompany')).toHaveLength(1));
    expect(api.calls('getCompany')[0].params.name).toBe('bruennimann');
  });

  // Create flow: when no existing company matches, an explicit "Create" row appears
  // and selecting it materialises the company via get-or-create.
  it('should_offerCreateOption_when_noExactMatch', async () => {
    api.on('searchCompanies', () => []);
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'Hông Ân Studio');

    expect(await screen.findByTestId('company-create-option')).toBeInTheDocument();
    expect(screen.getByText('Create new company "Hông Ân Studio"')).toBeInTheDocument();
  });

  it('should_notOfferCreate_when_aResultMatchesExactly', async () => {
    const user = userEvent.setup();
    const { input } = renderAutocomplete();

    await user.type(input, 'brünn bau gmbh');

    expect(await screen.findByText('Brünn Bau GmbH')).toBeInTheDocument();
    expect(screen.queryByTestId('company-create-option')).not.toBeInTheDocument();
  });

  it('should_materialiseCompany_when_createOptionSelected', async () => {
    api.on('searchCompanies', () => []);
    const created = {
      name: 'honganstudio',
      displayName: 'Hông Ân Studio',
      isVerified: false,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      createdBy: 'lead.organizer',
    };
    api.on('getOrCreateCompany', () => created, { status: 201 });
    const user = userEvent.setup();
    const { input, onChange } = renderAutocomplete();

    await user.type(input, 'Hông Ân Studio');
    await user.click(await screen.findByTestId('company-create-option'));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(created));
    const [call] = api.calls('getOrCreateCompany');
    expect(call.method).toBe('POST');
    expect(call.body).toEqual({ displayName: 'Hông Ân Studio' });
  });

  it('should_reportNull_when_getOrCreateFails', async () => {
    api.on('searchCompanies', () => []);
    api.on('getOrCreateCompany', () =>
      HttpResponse.json({ error: 'INTERNAL_ERROR', message: 'down' }, { status: 500 })
    );
    const user = userEvent.setup();
    const { input, onChange } = renderAutocomplete();

    await user.type(input, 'Hông Ân Studio');
    await user.click(await screen.findByTestId('company-create-option'));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(null));
    expect(api.calls('getOrCreateCompany')).toHaveLength(1);
  });
});
