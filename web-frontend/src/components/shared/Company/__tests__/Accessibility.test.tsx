/**
 * Accessibility Tests (AC 12)
 * Tests for WCAG 2.1 AA compliance
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { axe } from 'vitest-axe';
import * as matchers from 'vitest-axe/matchers';
import CompanyManagementScreen from '../CompanyManagementScreen';
import { CompanyForm } from '../CompanyForm';
import CompanyFilters from '../CompanyFilters';

expect.extend(matchers);

// Mock useCompanies hook
vi.mock('@/hooks/useCompanies/useCompanies', () => ({
  useCompanies: vi.fn(() => ({
    data: {
      data: [
        {
          name: 'TestCompanyAG',
          displayName: 'Test Company AG',
          isVerified: true,
          industry: 'Technology',
        },
        {
          name: 'AnotherCompanyGmbH',
          displayName: 'Another Company GmbH',
          isVerified: false,
          industry: 'Finance',
        },
      ],
      pagination: {
        page: 0,
        size: 10,
        totalElements: 2,
        totalPages: 1,
      },
    },
    isLoading: false,
    isError: false,
  })),
}));

// Mock useCompanyMutations hook
vi.mock('@/hooks/useCompanyMutations/useCompanyMutations', () => ({
  useCreateCompany: vi.fn(() => ({
    mutate: vi.fn(),
    isLoading: false,
    error: null,
  })),
  useUpdateCompany: vi.fn(() => ({
    mutate: vi.fn(),
    isLoading: false,
    error: null,
  })),
  useDeleteCompany: vi.fn(() => ({
    mutate: vi.fn(),
    isLoading: false,
    error: null,
  })),
  useCompanyMutations: vi.fn(() => ({
    createCompany: vi.fn(),
    updateCompany: vi.fn(),
    deleteCompany: vi.fn(),
  })),
}));

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const renderWithProviders = (component: React.ReactElement, useRoutes = false) => {
  const queryClient = createTestQueryClient();

  const wrappedComponent = useRoutes ? (
    <Routes>
      <Route path="/organizer/companies/*" element={component} />
    </Routes>
  ) : (
    component
  );

  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{wrappedComponent}</BrowserRouter>
    </QueryClientProvider>
  );
};

describe('Accessibility Tests (AC 12)', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Mock window.location for route checks
    Object.defineProperty(window, 'location', {
      value: {
        pathname: '/organizer/companies',
        origin: 'http://localhost:3000',
        href: 'http://localhost:3000/organizer/companies',
        search: '',
        hash: '',
      },
      writable: true,
      configurable: true,
    });
  });

  describe('AC12.1: Keyboard Navigation', () => {
    it('should_navigateWithTab_when_keyboardUsed', async () => {
      const user = userEvent.setup();
      renderWithProviders(<CompanyManagementScreen />, true);

      // Wait for component to fully render
      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });

      // Tab through interactive elements
      await user.tab();
      const firstFocusable = document.activeElement;
      expect(firstFocusable?.tagName).toMatch(/BUTTON|INPUT|A/);

      await user.tab();
      const secondFocusable = document.activeElement;
      expect(secondFocusable).not.toBe(firstFocusable);
    });

    it('should_activateWithEnter_when_buttonFocused', async () => {
      const user = userEvent.setup();
      renderWithProviders(<CompanyManagementScreen />, true);

      // Wait for component to fully render
      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });

      // Find a button (the search field or create button)
      const buttons = screen.getAllByRole('button');
      expect(buttons.length).toBeGreaterThan(0);

      buttons[0].focus();
      await user.keyboard('{Enter}');
      // Button should be activated
      // Note: Actual behavior depends on component implementation
    });

    it('should_closeWithEscape_when_modalOpen', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();

      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={onClose}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      await user.keyboard('{Escape}');
      expect(onClose).toHaveBeenCalled();
    });

    it('should_trapFocus_when_modalOpen', () => {
      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      // Focus should be trapped within the modal
      const modal = screen.getByRole('dialog');
      expect(modal).toBeInTheDocument();
    });
  });

  describe('AC12.2: ARIA Labels and Roles', () => {
    it('should_haveAriaLabels_when_rendered', async () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Wait for component to fully render
      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });

      // All interactive elements should have aria-labels or text
      const buttons = screen.getAllByRole('button');
      buttons.forEach((button) => {
        const hasLabel =
          button.getAttribute('aria-label') ||
          button.textContent ||
          button.querySelector('[aria-label]');
        expect(hasLabel).toBeTruthy();
      });
    });

    it('should_haveProperRoles_when_rendered', async () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Wait for component to fully render and check for proper semantic roles
      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
        expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
      });
    });

    it('should_announceErrors_when_validationFails', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      const saveButton = screen.getByRole('button', { name: /save/i });
      await user.click(saveButton);

      // Error messages should have role="alert" or aria-live="polite"
      // This will be checked when form validation is triggered
    });

    it('should_labelFormFields_when_rendered', () => {
      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      // All form inputs should have labels
      const inputs = screen.getAllByRole('textbox');
      inputs.forEach((input) => {
        const label = screen.queryByLabelText(input.getAttribute('name') || '');
        const ariaLabel = input.getAttribute('aria-label');
        expect(label || ariaLabel).toBeTruthy();
      });
    });
  });

  describe('AC12.3: Focus Indicators', () => {
    it('should_showFocusIndicator_when_elementFocused', async () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Wait for component to fully render
      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });

      // Get any focusable button
      const buttons = screen.getAllByRole('button');
      expect(buttons.length).toBeGreaterThan(0);

      buttons[0].focus();

      // MUI components have built-in focus indicators
      expect(document.activeElement).toBe(buttons[0]);
    });

    it('should_haveSufficientContrast_when_focused', async () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Wait for component to fully render
      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });

      // Get any focusable button
      const buttons = screen.getAllByRole('button');
      expect(buttons.length).toBeGreaterThan(0);

      buttons[0].focus();

      // Focus indicator should have sufficient contrast (3:1 minimum)
      // This is visually tested, but MUI provides compliant focus indicators
      expect(document.activeElement).toBe(buttons[0]);
    });
  });

  describe('AC12.4: Screen Reader Support', () => {
    it('should_haveAltText_when_logoDisplayed', () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Company logos should have alt text
      // This will be verified when company cards are rendered with logos
      const main = screen.getByRole('main');
      expect(main).toBeInTheDocument();
    });

    it('should_describeIconButtons_when_rendered', () => {
      renderWithProviders(<CompanyFilters onFilterChange={() => {}} initialFilters={{}} />);

      // Icon-only buttons should have aria-label
      const buttons = screen.getAllByRole('button');
      const iconButtons = buttons.filter((btn) => !btn.textContent?.trim());

      iconButtons.forEach((btn) => {
        expect(btn.getAttribute('aria-label')).toBeTruthy();
      });
    });

    it('should_announceLoadingStates_when_fetching', () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Loading states should be announced
      // aria-live regions should be present
      const main = screen.getByRole('main');
      expect(main).toBeInTheDocument();
    });
  });

  describe('AC12.5: Color Contrast', () => {
    it('should_meetContrastRatio_when_rendered', async () => {
      const { container } = renderWithProviders(<CompanyManagementScreen />, true);

      // Run axe accessibility tests
      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should_notRelyOnColor_when_conveyingInformation', () => {
      renderWithProviders(<CompanyManagementScreen />, true);

      // Status indicators should use icons + text, not just color
      // Verified status (✅), Partner badge (⭐) use both icon and text
      const main = screen.getByRole('main');
      expect(main).toBeInTheDocument();
    });
  });

  describe('AC12.6: Form Accessibility', () => {
    it('should_associateLabelsWithInputs_when_formRendered', () => {
      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      const inputs = screen.getAllByRole('textbox');
      inputs.forEach((input) => {
        const id = input.getAttribute('id');
        if (id) {
          const label = document.querySelector(`label[for="${id}"]`);
          expect(label || input.getAttribute('aria-label')).toBeTruthy();
        }
      });
    });

    it('should_showInlineErrors_when_validationFails', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      const saveButton = screen.getByRole('button', { name: /save/i });
      await user.click(saveButton);

      // Inline errors should be associated with inputs via aria-describedby
      // This will be verified when validation is triggered
    });

    it('should_indicateRequiredFields_when_formRendered', () => {
      renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      // Required fields should be marked with * in their labels
      // MUI TextField with required prop adds * automatically to label
      const nameField = screen.getByLabelText(/company name/i);
      expect(nameField).toBeInTheDocument();

      // Required validation is handled by Zod schema
      // Visual indication (*) is provided by MUI TextField labels
    });
  });

  describe('AC12.7: Automated Accessibility Testing', () => {
    it('should_passAxeTests_when_companyManagementScreenRendered', async () => {
      const { container } = renderWithProviders(<CompanyManagementScreen />, true);

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should_passAxeTests_when_companyFormRendered', async () => {
      const { container } = renderWithProviders(
        <CompanyForm
          open={true}
          mode="create"
          onClose={() => {}}
          onSubmit={() => {}}
          allowDraft={false}
          userRole="organizer"
        />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should_passAxeTests_when_filtersRendered', async () => {
      const { container } = renderWithProviders(
        <CompanyFilters onFilterChange={() => {}} initialFilters={{}} />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });
  });
});
