/**
 * LoginForm Component Tests (TDD - Fixed)
 * Story 1.2: Frontend Authentication Integration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginForm } from './LoginForm'
import { ThemeProvider, createTheme } from '@mui/material/styles'

// Mock useAuth hook
const mockSignIn = vi.fn()
const mockClearError = vi.fn()

const mockUseAuth = {
  signIn: mockSignIn,
  isLoading: false,
  error: null,
  clearError: mockClearError,
}

vi.mock('@hooks/useAuth', () => ({
  useAuth: () => mockUseAuth,
}))

// Create theme for MUI components
const theme = createTheme()

// Helper function to render with theme
const renderWithTheme = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={theme}>
      {component}
    </ThemeProvider>
  )
}

describe('LoginForm Component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Reset mock state
    Object.assign(mockUseAuth, {
      signIn: mockSignIn,
      isLoading: false,
      error: null,
      clearError: mockClearError,
    })
  })

  it('should_renderLoginFormFields_when_componentMounted', () => {
    // Test 9.20: should_renderLoginFormFields_when_componentMounted
    renderWithTheme(<LoginForm />)

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/remember me/i)).toBeInTheDocument()
  })

  it('should_validateEmailFormat_when_invalidEmailEntered', async () => {
    // Test 9.21: should_validateEmailFormat_when_invalidEmailEntered
    const user = userEvent.setup()
    renderWithTheme(<LoginForm />)

    const emailInput = screen.getByLabelText(/email/i)
    await user.type(emailInput, 'invalid-email')
    await user.tab() // Trigger blur event

    await waitFor(() => {
      expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument()
    })
  })

  it('should_validatePasswordRequired_when_passwordEmpty', async () => {
    // Test 9.22: should_validatePasswordRequired_when_passwordEmpty
    const user = userEvent.setup()
    renderWithTheme(<LoginForm />)

    const passwordInput = screen.getByLabelText(/password/i)
    await user.click(passwordInput)
    await user.tab() // Trigger blur event

    await waitFor(() => {
      expect(screen.getByText(/password is required/i)).toBeInTheDocument()
    })
  })

  it('should_submitCredentials_when_validFormSubmitted', async () => {
    // Test 9.23: should_submitCredentials_when_validFormSubmitted
    const user = userEvent.setup()
    mockSignIn.mockResolvedValue(true) // Mock successful sign in

    renderWithTheme(<LoginForm />)

    const emailInput = screen.getByLabelText(/email/i)
    const passwordInput = screen.getByLabelText(/password/i)

    await user.type(emailInput, 'organizer@batbern.ch')
    await user.tab() // Trigger blur for email validation
    await user.type(passwordInput, 'ValidPassword123!')
    await user.tab() // Trigger blur for password validation

    // Wait for form to be valid
    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /sign in/i })
      expect(submitButton).not.toBeDisabled()
    })

    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith({
        email: 'organizer@batbern.ch',
        password: 'ValidPassword123!',
        rememberMe: false
      })
    })
  })

  it('should_handleRememberMeOption_when_checkboxSelected', async () => {
    // Test 9.24: should_handleRememberMeOption_when_checkboxSelected
    const user = userEvent.setup()
    render(<LoginForm />)

    await user.type(screen.getByLabelText(/email/i), 'test@example.com')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByLabelText(/remember me/i))
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith(
        expect.objectContaining({ rememberMe: true })
      )
    })
  })

  it('should_displayErrorMessage_when_authenticationFails', () => {
    // Test 9.25: should_displayErrorMessage_when_authenticationFails
    // Update mock to include error
    Object.assign(mockUseAuth, {
      error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }
    })

    renderWithTheme(<LoginForm />)

    expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument()
  })

  it('should_showLoadingState_when_authenticationInProgress', () => {
    // Test 9.26: should_showLoadingState_when_authenticationInProgress
    // Update mock to show loading state
    Object.assign(mockUseAuth, {
      isLoading: true,
      error: null
    })

    renderWithTheme(<LoginForm />)

    expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled()
  })

  it('should_provideForgotPasswordLink_when_userNeedsPasswordReset', () => {
    // Test 9.27: should_provideForgotPasswordLink_when_userNeedsPasswordReset
    renderWithTheme(<LoginForm />)

    expect(screen.getByText(/forgot password/i)).toBeInTheDocument()
  })

  it('should_clearErrorOnInputChange_when_userStartsTyping', async () => {
    // Test 9.28: should_clearErrorOnInputChange_when_userStartsTyping
    const user = userEvent.setup()

    // Set up mock with an error initially
    Object.assign(mockUseAuth, {
      error: { code: 'SOME_ERROR', message: 'Some error' }
    })

    renderWithTheme(<LoginForm />)

    await user.type(screen.getByLabelText(/email/i), 'test@example.com')

    expect(mockClearError).toHaveBeenCalled()
  })
})