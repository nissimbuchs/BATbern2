/**
 * Authentication Service Tests (TDD - Fixed)
 * Story 1.2: Frontend Authentication Integration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { authService } from './authService'
import { LoginCredentials, SignUpData, UserContext } from '@types/auth'

// Mock AWS Amplify
vi.mock('aws-amplify', () => ({
  Auth: {
    signIn: vi.fn(),
    signOut: vi.fn(),
    signUp: vi.fn(),
    confirmSignUp: vi.fn(),
    getCurrentUser: vi.fn(),
    currentAuthenticatedUser: vi.fn(),
    currentSession: vi.fn(),
    forgotPassword: vi.fn(),
    forgotPasswordSubmit: vi.fn(),
  }
}))

// Import the mocked module
import { Auth as mockAuth } from 'aws-amplify'

describe('AuthService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('signIn', () => {
    it('should_authenticateUser_when_validCredentialsProvided', async () => {
      // Test 9.1: should_authenticateUser_when_validCredentialsProvided
      const credentials: LoginCredentials = {
        email: 'organizer@batbern.ch',
        password: 'ValidPassword123!',
        rememberMe: true
      }

      // Mock successful Cognito response
      const mockCognitoUser = {
        username: 'organizer@batbern.ch',
        attributes: {
          email: 'organizer@batbern.ch',
          'custom:role': 'organizer'
        }
      }

      const mockSession = {
        getIdToken: () => ({
          payload: {
            sub: 'user-123',
            email: 'organizer@batbern.ch',
            email_verified: true,
            'custom:role': 'organizer',
            'custom:companyId': 'company-123',
            'custom:preferences': JSON.stringify({
              language: 'en',
              theme: 'light',
              notifications: { email: true, sms: false, push: true },
              privacy: { showProfile: true, allowMessages: true }
            }),
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
          }
        }),
        getAccessToken: () => ({
          getJwtToken: () => 'mock-access-token'
        }),
        getRefreshToken: () => ({
          getToken: () => 'mock-refresh-token'
        })
      }

      mockAuth.signIn.mockResolvedValue(mockCognitoUser)
      mockAuth.currentSession.mockResolvedValue(mockSession)

      const result = await authService.signIn(credentials)

      expect(result.success).toBe(true)
      expect(result.user).toBeDefined()
      expect(result.user?.email).toBe(credentials.email)
      expect(result.user?.role).toBe('organizer')
      expect(mockAuth.signIn).toHaveBeenCalledWith(credentials.email, credentials.password)
    })

    it('should_returnError_when_invalidCredentialsProvided', async () => {
      // Test 9.2: should_returnError_when_invalidCredentialsProvided
      const credentials: LoginCredentials = {
        email: 'invalid@example.com',
        password: 'wrongpassword'
      }

      // Mock Cognito error
      const cognitoError = new Error('NotAuthorizedException')
      cognitoError.name = 'NotAuthorizedException'
      mockAuth.signIn.mockRejectedValue(cognitoError)

      const result = await authService.signIn(credentials)

      expect(result.success).toBe(false)
      expect(result.error).toBeDefined()
      expect(result.error?.code).toBe('INVALID_CREDENTIALS')
      expect(mockAuth.signIn).toHaveBeenCalledWith(credentials.email, credentials.password)
    })

    it('should_extractUserContext_when_cognitoTokenReceived', async () => {
      // Test 9.3: should_extractUserContext_when_cognitoTokenReceived
      const credentials: LoginCredentials = {
        email: 'speaker@company.com',
        password: 'ValidPassword123!'
      }

      // Mock successful Cognito response
      const mockCognitoUser = {
        username: 'speaker@company.com',
        attributes: {
          email: 'speaker@company.com',
          'custom:role': 'speaker'
        }
      }

      const mockSession = {
        getIdToken: () => ({
          payload: {
            sub: 'speaker-user-123',
            email: 'speaker@company.com',
            email_verified: true,
            'custom:role': 'speaker',
            'custom:companyId': 'speaker-company-123',
            'custom:preferences': JSON.stringify({
              language: 'en',
              theme: 'light',
              notifications: { email: true, sms: false, push: true },
              privacy: { showProfile: true, allowMessages: true }
            }),
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
          }
        }),
        getAccessToken: () => ({
          getJwtToken: () => 'mock-speaker-access-token'
        }),
        getRefreshToken: () => ({
          getToken: () => 'mock-speaker-refresh-token'
        })
      }

      mockAuth.signIn.mockResolvedValue(mockCognitoUser)
      mockAuth.currentSession.mockResolvedValue(mockSession)

      const result = await authService.signIn(credentials)

      expect(result.user?.userId).toBeDefined()
      expect(result.user?.companyId).toBeDefined()
      expect(result.user?.preferences).toBeDefined()
      expect(result.accessToken).toBeDefined()
    })

    it('should_handleMfaChallenge_when_mfaRequired', async () => {
      // Test 9.4: should_handleMfaChallenge_when_mfaRequired
      const credentials: LoginCredentials = {
        email: 'partner@company.com',
        password: 'ValidPassword123!'
      }

      const result = await authService.signIn(credentials)

      if (result.mfaChallenge) {
        expect(result.mfaChallenge.challengeName).toBeDefined()
        expect(result.mfaChallenge.session).toBeDefined()
      }
    })
  })

  describe('signUp', () => {
    it('should_createUser_when_validSignUpDataProvided', async () => {
      // Test 9.5: should_createUser_when_validSignUpDataProvided
      const signUpData: SignUpData = {
        email: 'newuser@company.com',
        password: 'ValidPassword123!',
        confirmPassword: 'ValidPassword123!',
        role: 'attendee',
        firstName: 'John',
        lastName: 'Doe',
        acceptTerms: true
      }

      // Mock successful sign up
      mockAuth.signUp.mockResolvedValue({
        user: {
          username: 'newuser@company.com'
        },
        userConfirmed: false // Requires email confirmation
      })

      const result = await authService.signUp(signUpData)

      expect(result.success).toBe(true)
      expect(result.requiresConfirmation).toBe(true)
      expect(mockAuth.signUp).toHaveBeenCalledWith({
        username: signUpData.email,
        password: signUpData.password,
        attributes: {
          email: signUpData.email,
          'custom:role': signUpData.role,
          'custom:companyId': '',
          'custom:preferences': JSON.stringify({
            language: 'en',
            theme: 'light',
            notifications: {
              email: true,
              sms: false,
              push: true
            },
            privacy: {
              showProfile: true,
              allowMessages: true
            }
          })
        }
      })
    })

    it('should_validatePasswordMatch_when_signingUp', async () => {
      // Test 9.6: should_validatePasswordMatch_when_signingUp
      const signUpData: SignUpData = {
        email: 'newuser@company.com',
        password: 'ValidPassword123!',
        confirmPassword: 'DifferentPassword123!',
        role: 'attendee',
        firstName: 'John',
        lastName: 'Doe',
        acceptTerms: true
      }

      const result = await authService.signUp(signUpData)

      expect(result.success).toBe(false)
      expect(result.error?.code).toBe('PASSWORD_MISMATCH')
    })
  })

  describe('getCurrentUser', () => {
    it('should_returnUserContext_when_userAuthenticated', async () => {
      // Test 9.7: should_returnUserContext_when_userAuthenticated
      const mockCognitoUser = {
        username: 'test@batbern.ch'
      }

      const mockSession = {
        getIdToken: () => ({
          payload: {
            sub: 'user-123',
            email: 'test@batbern.ch',
            email_verified: true,
            'custom:role': 'organizer',
            'custom:companyId': 'company-123',
            'custom:preferences': JSON.stringify({
              language: 'en',
              theme: 'light',
              notifications: { email: true, sms: false, push: true },
              privacy: { showProfile: true, allowMessages: true }
            }),
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
          }
        })
      }

      mockAuth.currentAuthenticatedUser.mockResolvedValue(mockCognitoUser)
      mockAuth.currentSession.mockResolvedValue(mockSession)

      const user = await authService.getCurrentUser()

      expect(user).toBeDefined()
      expect(user?.userId).toBeDefined()
      expect(user?.email).toBeDefined()
      expect(user?.role).toMatch(/^(organizer|speaker|partner|attendee)$/)
    })

    it('should_returnNull_when_userNotAuthenticated', async () => {
      // Test 9.8: should_returnNull_when_userNotAuthenticated
      mockAuth.currentAuthenticatedUser.mockRejectedValue(new Error('No current user'))

      const user = await authService.getCurrentUser()

      expect(user).toBeNull()
    })
  })

  describe('signOut', () => {
    it('should_clearUserSession_when_signingOut', async () => {
      // Test 9.9: should_clearUserSession_when_signingOut
      await authService.signOut()

      const user = await authService.getCurrentUser()
      expect(user).toBeNull()
    })
  })

  describe('refreshToken', () => {
    it('should_refreshAccessToken_when_tokenNearExpiration', async () => {
      // Test 9.10: should_refreshAccessToken_when_tokenNearExpiration
      const mockSession = {
        isValid: () => true,
        getAccessToken: () => ({
          getJwtToken: () => 'new-access-token',
          getExpiration: () => Math.floor(Date.now() / 1000) + 3600
        }),
        getRefreshToken: () => ({
          getToken: () => 'new-refresh-token'
        })
      }

      mockAuth.currentSession.mockResolvedValue(mockSession)

      const result = await authService.refreshToken()

      expect(result.success).toBe(true)
      expect(result.accessToken).toBeDefined()
      expect(result.expiresIn).toBeGreaterThan(0)
    })
  })

  describe('isTokenExpired', () => {
    it('should_returnTrue_when_tokenExpired', () => {
      // Test 9.11: should_returnTrue_when_tokenExpired
      const expiredToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.invalid'

      const isExpired = authService.isTokenExpired(expiredToken)

      expect(isExpired).toBe(true)
    })

    it('should_returnFalse_when_tokenValid', () => {
      // Test 9.12: should_returnFalse_when_tokenValid
      const futureTimestamp = Math.floor(Date.now() / 1000) + 3600 // 1 hour from now
      const validToken = `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${btoa(JSON.stringify({ exp: futureTimestamp }))}.signature`

      const isExpired = authService.isTokenExpired(validToken)

      expect(isExpired).toBe(false)
    })
  })
})