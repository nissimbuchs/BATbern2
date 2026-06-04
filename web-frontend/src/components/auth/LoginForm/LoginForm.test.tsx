/**
 * LoginForm Component Tests (TDD - Fixed)
 * Story 1.2.1: Frontend Authentication Integration with i18n
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from './LoginForm';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import { MemoryRouter } from 'react-router-dom';
import i18n from '@/i18n/config';
import { ConfigProvider } from '@/contexts/ConfigContext';
import type { AppConfig } from '@/config/runtime-config';

// Mock useAuth hook
const mockSignIn = vi.fn();
const mockClearError = vi.fn();

const mockUseAuth = {
  signIn: mockSignIn,
  isLoading: false,
  error: null,
  clearError: mockClearError,
};

vi.mock('@hooks/useAuth', () => ({
  useAuth: () => mockUseAuth,
}));

// Mock authService (Story 12.9: the Google SSO button calls authService.signInWithFederated,
// NOT Amplify directly — the component must use the service layer). vi.hoisted is required
// because the mock factory references the fn eagerly (it's hoisted above normal consts).
const mockSignInWithFederated = vi.hoisted(() => vi.fn());
vi.mock('@/services/auth/authService', () => ({
  authService: { signInWithFederated: mockSignInWithFederated },
}));

// Create theme for MUI components
const theme = createTheme();

// Mock config for testing
const mockConfig: AppConfig = {
  environment: 'development',
  apiBaseUrl: 'http://localhost:8080/api/v1',
  cognito: {
    userPoolId: 'eu-central-1_XXXXXXXXX',
    clientId: 'XXXXXXXXXXXXXXXXXXXXXXXXXX',
    region: 'eu-central-1',
  },
  features: {
    notifications: true,
    analytics: false,
    pwa: false,
    turnstile: false,
    sso: false,
  },
};

// Helper function to render with theme, i18n, config, and router.
// MemoryRouter is required because LoginForm reads ?reason=account_deactivated via
// useSearchParams (Story 12.7 / G1); `initialEntries` lets tests drive that query.
// `config` override (Story 12.9) lets a test toggle features.sso to exercise the gated button.
const renderWithTheme = (
  component: React.ReactElement,
  { route = '/login', config = mockConfig }: { route?: string; config?: AppConfig } = {}
) => {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ConfigProvider config={config}>
        <I18nextProvider i18n={i18n}>
          <ThemeProvider theme={theme}>{component}</ThemeProvider>
        </I18nextProvider>
      </ConfigProvider>
    </MemoryRouter>
  );
};

// Config with the SSO feature flag ON (Story 12.9).
const ssoOnConfig: AppConfig = {
  ...mockConfig,
  features: { ...mockConfig.features, sso: true },
};

describe('LoginForm Component', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    // Reset i18n to English for consistent test expectations
    await i18n.changeLanguage('en');
    // Reset mock state
    Object.assign(mockUseAuth, {
      signIn: mockSignIn,
      isLoading: false,
      error: null,
      clearError: mockClearError,
    });
  });

  it('should_renderLoginFormFields_when_componentMounted', async () => {
    // Test 9.20: should_renderLoginFormFields_when_componentMounted
    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/enter your password/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
      expect(screen.getByLabelText(/remember me/i)).toBeInTheDocument();
    });
  });

  it('should_validateEmailFormat_when_invalidEmailEntered', async () => {
    // Test 9.21: should_validateEmailFormat_when_invalidEmailEntered
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);

    await act(async () => {
      await user.type(emailInput, 'invalid-email');
      await user.tab(); // Trigger blur event
    });

    await waitFor(() => {
      expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument();
    });
  });

  it('should_validatePasswordRequired_when_passwordEmpty', async () => {
    // Test 9.22: should_validatePasswordRequired_when_passwordEmpty
    // Note: Form uses mode: 'onChange', so validation requires a change event
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    const passwordInput = screen.getByPlaceholderText(/enter your password/i);

    await act(async () => {
      // Type something to trigger onChange, then clear it
      await user.type(passwordInput, 'a');
      await user.clear(passwordInput);
      await user.tab(); // Move focus away
    });

    await waitFor(() => {
      expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    });
  });

  it('should_submitCredentials_when_validFormSubmitted', async () => {
    // Test 9.23: should_submitCredentials_when_validFormSubmitted
    const user = userEvent.setup();
    mockSignIn.mockResolvedValue(true); // Mock successful sign in

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    const emailInput = screen.getByLabelText(/email address/i);
    const passwordInput = screen.getByPlaceholderText(/enter your password/i);

    await act(async () => {
      await user.type(emailInput, 'organizer@batbern.ch');
      await user.tab(); // Trigger blur for email validation
      await user.type(passwordInput, 'ValidPassword123!');
      await user.tab(); // Trigger blur for password validation
    });

    // Wait for form to be valid
    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).not.toBeDisabled();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /sign in/i }));
    });

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith({
        email: 'organizer@batbern.ch',
        password: 'ValidPassword123!',
        rememberMe: false,
      });
    });
  });

  it('should_handleRememberMeOption_when_checkboxSelected', async () => {
    // Test 9.24: should_handleRememberMeOption_when_checkboxSelected
    const user = userEvent.setup();
    mockSignIn.mockResolvedValue(true); // Mock successful sign in

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await act(async () => {
      await user.type(screen.getByLabelText(/email address/i), 'test@example.com');
      await user.tab(); // Trigger validation
      await user.type(screen.getByPlaceholderText(/enter your password/i), 'password123');
      await user.tab(); // Trigger validation
      await user.click(screen.getByLabelText(/remember me/i));
    });

    // Wait for form to be valid
    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).not.toBeDisabled();
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: /sign in/i }));
    });

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: true }));
    });
  });

  it('should_displayErrorMessage_when_authenticationFails', async () => {
    // Test 9.25: should_displayErrorMessage_when_authenticationFails
    // Update mock to include error
    Object.assign(mockUseAuth, {
      error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });
  });

  it('should_showLoadingState_when_authenticationInProgress', async () => {
    // Test 9.26: should_showLoadingState_when_authenticationInProgress
    // Update mock to show loading state
    Object.assign(mockUseAuth, {
      isLoading: true,
      error: null,
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i });
      expect(submitButton).toBeDisabled();
      // Check for loading spinner
      expect(submitButton.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
    });
  });

  it('should_provideForgotPasswordLink_when_userNeedsPasswordReset', async () => {
    // Test 9.27: should_provideForgotPasswordLink_when_userNeedsPasswordReset
    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    expect(screen.getByText(/forgot password/i)).toBeInTheDocument();
  });

  it('should_notClearError_when_errorAppearsWithoutUserTyping', async () => {
    // Regression (2026-06-04): the clear-on-typing effect listed `error` in its
    // dependency array and cleared unconditionally — the moment AuthContext set
    // INVALID_CREDENTIALS after a wrong password, the effect wiped it before the
    // Alert could ever render against the real (stateful) provider. The static
    // mock here kept the Alert visible, so test 9.25 never caught it. Guard:
    // with an error present and NO typing, clearError must NOT be invoked.
    Object.assign(mockUseAuth, {
      error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });
    expect(mockClearError).not.toHaveBeenCalled();
  });

  it('should_clearErrorOnInputChange_when_userStartsTyping', async () => {
    // Test 9.28: should_clearErrorOnInputChange_when_userStartsTyping
    const user = userEvent.setup();

    // Set up mock with an error initially
    Object.assign(mockUseAuth, {
      error: { code: 'SOME_ERROR', message: 'Some error' },
    });

    await act(async () => {
      renderWithTheme(<LoginForm />);
    });

    await act(async () => {
      await user.type(screen.getByLabelText(/email address/i), 'test@example.com');
    });

    await waitFor(() => {
      expect(mockClearError).toHaveBeenCalled();
    });
  });

  // Story 12.7 / G1: deactivated-account notice
  it('should_showDeactivatedNotice_when_reasonQueryParamPresent', async () => {
    await act(async () => {
      renderWithTheme(<LoginForm />, { route: '/login?reason=account_deactivated' });
    });

    await waitFor(() => {
      expect(screen.getByText(/has been deactivated/i)).toBeInTheDocument();
    });
  });

  it('should_notShowDeactivatedNotice_when_noReasonQueryParam', async () => {
    await act(async () => {
      renderWithTheme(<LoginForm />, { route: '/login' });
    });

    expect(screen.queryByText(/has been deactivated/i)).not.toBeInTheDocument();
  });

  it('should_showDeactivatedNotice_when_storedLogoutReasonPresent', async () => {
    // Story 12.8 F5: a failed federated hydration lands on plain `/login` (no query param —
    // AuthCallbackPage's replace navigation wipes it). The sessionStorage hand-off set by
    // the apiClient 403 interceptor must still surface (and consume) the notice.
    sessionStorage.setItem('batbern.logout-reason', 'account_deactivated');

    await act(async () => {
      renderWithTheme(<LoginForm />, { route: '/login' });
    });

    await waitFor(() => {
      expect(screen.getByText(/has been deactivated/i)).toBeInTheDocument();
    });
    // Consumed — a later unrelated visit must not re-show it.
    expect(sessionStorage.getItem('batbern.logout-reason')).toBeNull();
  });

  it('should_dismissDeactivatedNotice_when_closeClicked', async () => {
    const user = userEvent.setup();

    await act(async () => {
      renderWithTheme(<LoginForm />, { route: '/login?reason=account_deactivated' });
    });

    const notice = await screen.findByText(/has been deactivated/i);
    expect(notice).toBeInTheDocument();

    // MUI Alert onClose renders a close button with aria-label "Close".
    await act(async () => {
      await user.click(screen.getByRole('button', { name: /close/i }));
    });

    await waitFor(() => {
      expect(screen.queryByText(/has been deactivated/i)).not.toBeInTheDocument();
    });
  });

  // Story 12.9: "Continue with Google" button, gated on features.sso.
  describe('Continue with Google button (features.sso)', () => {
    it('should_renderGoogleButton_when_ssoFeatureEnabled', async () => {
      await act(async () => {
        renderWithTheme(<LoginForm />, { config: ssoOnConfig });
      });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /continue with google/i })).toBeInTheDocument();
      });
    });

    it('should_notRenderGoogleButton_when_ssoFeatureDisabled', async () => {
      await act(async () => {
        renderWithTheme(<LoginForm />); // default mockConfig has sso: false
      });

      // The email/password form is present...
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });
      // ...but the Google button is not.
      expect(
        screen.queryByRole('button', { name: /continue with google/i })
      ).not.toBeInTheDocument();
    });

    it('should_callSignInWithFederatedGoogle_when_googleButtonClicked', async () => {
      const user = userEvent.setup();
      await act(async () => {
        renderWithTheme(<LoginForm />, { config: ssoOnConfig });
      });

      const googleButton = await screen.findByRole('button', {
        name: /continue with google/i,
      });
      await user.click(googleButton);

      expect(mockSignInWithFederated).toHaveBeenCalledTimes(1);
      expect(mockSignInWithFederated).toHaveBeenCalledWith('Google');
    });
  });
});
