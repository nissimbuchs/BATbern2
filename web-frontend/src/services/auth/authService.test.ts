/**
 * Authentication Service Tests (TDD - Fixed)
 * Story 1.2: Frontend Authentication Integration
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { authService } from './authService';
import { LoginCredentials, SignUpData } from '@/types/auth';

// Mock AWS Amplify v6
vi.mock('aws-amplify/auth', () => ({
  signIn: vi.fn(),
  signOut: vi.fn(),
  signUp: vi.fn(),
  confirmSignUp: vi.fn(),
  getCurrentUser: vi.fn(),
  fetchAuthSession: vi.fn(),
  resetPassword: vi.fn(),
  confirmResetPassword: vi.fn(),
}));

// Mock Cognito token provider for storage configuration
vi.mock('aws-amplify/auth/cognito', () => ({
  cognitoUserPoolsTokenProvider: {
    setKeyValueStorage: vi.fn(),
  },
}));

// Import the mocked modules
import * as amplifyAuth from 'aws-amplify/auth';
import { cognitoUserPoolsTokenProvider } from 'aws-amplify/auth/cognito';

const mockAuth = vi.mocked(amplifyAuth);

describe('AuthService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Clear storage before each test
    localStorage.clear();
    sessionStorage.clear();
  });

  describe('signIn', () => {
    it('should_authenticateUser_when_validCredentialsProvided', async () => {
      // Test 9.1: should_authenticateUser_when_validCredentialsProvided
      const credentials: LoginCredentials = {
        email: 'organizer@batbern.ch',
        password: 'ValidPassword123!',
        rememberMe: true,
      };

      const mockSession = {
        tokens: {
          idToken: {
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
                privacy: { showProfile: true, allowMessages: true },
              }),
              iat: Math.floor(Date.now() / 1000),
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-id-token',
          },
          accessToken: {
            payload: {
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-access-token',
          },
        },
      };

      mockAuth.signIn.mockResolvedValue({
        isSignedIn: true,
        nextStep: { signInStep: 'DONE' },
      });
      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      const result = await authService.signIn(credentials);

      expect(result.success).toBe(true);
      expect(result.user).toBeDefined();
      expect(result.user?.email).toBe(credentials.email);
      expect(result.user?.role).toBe('organizer');
      expect(mockAuth.signIn).toHaveBeenCalledWith({
        username: credentials.email,
        password: credentials.password,
      });
    });

    it('should_returnError_when_invalidCredentialsProvided', async () => {
      // Test 9.2: should_returnError_when_invalidCredentialsProvided
      const credentials: LoginCredentials = {
        email: 'invalid@example.com',
        password: 'wrongpassword',
      };

      // Mock Cognito error
      const cognitoError = new Error('NotAuthorizedException');
      cognitoError.name = 'NotAuthorizedException';
      mockAuth.signIn.mockRejectedValue(cognitoError);

      const result = await authService.signIn(credentials);

      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
      expect(result.error?.code).toBe('INVALID_CREDENTIALS');
      expect(mockAuth.signIn).toHaveBeenCalledWith({
        username: credentials.email,
        password: credentials.password,
      });
    });

    it('should_extractUserContext_when_cognitoTokenReceived', async () => {
      // Test 9.3: should_extractUserContext_when_cognitoTokenReceived
      const credentials: LoginCredentials = {
        email: 'speaker@company.com',
        password: 'ValidPassword123!',
      };

      const mockSession = {
        tokens: {
          idToken: {
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
                privacy: { showProfile: true, allowMessages: true },
              }),
              iat: Math.floor(Date.now() / 1000),
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-speaker-id-token',
          },
          accessToken: {
            payload: {
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-speaker-access-token',
          },
        },
      };

      mockAuth.signIn.mockResolvedValue({
        isSignedIn: true,
        nextStep: { signInStep: 'DONE' },
      });
      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      const result = await authService.signIn(credentials);

      expect(result.user?.userId).toBeDefined();
      expect(result.user?.companyId).toBeDefined();
      expect(result.user?.preferences).toBeDefined();
      expect(result.accessToken).toBeDefined();
    });

    it('should_handleMfaChallenge_when_mfaRequired', async () => {
      // Test 9.4: should_handleMfaChallenge_when_mfaRequired
      const credentials: LoginCredentials = {
        email: 'partner@company.com',
        password: 'ValidPassword123!',
      };

      const result = await authService.signIn(credentials);

      if (result.mfaChallenge) {
        expect(result.mfaChallenge.challengeName).toBeDefined();
        expect(result.mfaChallenge.session).toBeDefined();
      }
    });

    it('should_configureLocalStorage_when_rememberMeIsTrue', async () => {
      // Story 1.2.1: Verify Amplify storage is configured for persistence
      const credentials: LoginCredentials = {
        email: 'organizer@batbern.ch',
        password: 'ValidPassword123!',
        rememberMe: true,
      };

      const mockSession = {
        tokens: {
          idToken: {
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
                privacy: { showProfile: true, allowMessages: true },
              }),
              iat: Math.floor(Date.now() / 1000),
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-id-token',
          },
          accessToken: {
            payload: {
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-access-token',
          },
        },
      };

      mockAuth.signIn.mockResolvedValue({
        isSignedIn: true,
        nextStep: { signInStep: 'DONE' },
      });
      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      await authService.signIn(credentials);

      // Verify Amplify was configured to use localStorage
      expect(cognitoUserPoolsTokenProvider.setKeyValueStorage).toHaveBeenCalledWith(localStorage);
    });

    it('should_configureSessionStorage_when_rememberMeIsFalse', async () => {
      // Story 1.2.1: Verify Amplify storage is configured for temporary session
      const credentials: LoginCredentials = {
        email: 'speaker@company.com',
        password: 'ValidPassword123!',
        rememberMe: false,
      };

      const mockSession = {
        tokens: {
          idToken: {
            payload: {
              sub: 'user-456',
              email: 'speaker@company.com',
              email_verified: true,
              'custom:role': 'speaker',
              'custom:companyId': 'company-456',
              'custom:preferences': JSON.stringify({
                language: 'en',
                theme: 'light',
                notifications: { email: true, sms: false, push: true },
                privacy: { showProfile: true, allowMessages: true },
              }),
              iat: Math.floor(Date.now() / 1000),
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-id-token-session',
          },
          accessToken: {
            payload: {
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-access-token-session',
          },
        },
      };

      mockAuth.signIn.mockResolvedValue({
        isSignedIn: true,
        nextStep: { signInStep: 'DONE' },
      });
      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      await authService.signIn(credentials);

      // Verify Amplify was configured to use sessionStorage
      expect(cognitoUserPoolsTokenProvider.setKeyValueStorage).toHaveBeenCalledWith(sessionStorage);
    });
  });

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
        acceptTerms: true,
      };

      // Mock successful sign up
      mockAuth.signUp.mockResolvedValue({
        isSignUpComplete: false, // Requires email confirmation
        userId: 'newuser-id',
        nextStep: {
          signUpStep: 'CONFIRM_SIGN_UP',
          codeDeliveryDetails: {
            deliveryMedium: 'EMAIL',
            destination: 'n***@c***.com',
          },
        },
      });

      const result = await authService.signUp(signUpData);

      expect(result.success).toBe(true);
      expect(result.requiresConfirmation).toBe(true);
      expect(mockAuth.signUp).toHaveBeenCalledWith({
        username: signUpData.email,
        password: signUpData.password,
        options: {
          userAttributes: {
            email: signUpData.email,
            'custom:role': signUpData.role,
            'custom:companyId': '',
            'custom:preferences': JSON.stringify({
              language: 'en',
              theme: 'light',
              notifications: {
                email: true,
                sms: false,
                push: true,
              },
              privacy: {
                showProfile: true,
                allowMessages: true,
              },
            }),
          },
        },
      });
    });

    it('should_validatePasswordMatch_when_signingUp', async () => {
      // Test 9.6: should_validatePasswordMatch_when_signingUp
      const signUpData: SignUpData = {
        email: 'newuser@company.com',
        password: 'ValidPassword123!',
        confirmPassword: 'DifferentPassword123!',
        role: 'attendee',
        firstName: 'John',
        lastName: 'Doe',
        acceptTerms: true,
      };

      const result = await authService.signUp(signUpData);

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('PASSWORD_MISMATCH');
    });
  });

  describe('getCurrentUser', () => {
    it('should_returnUserContext_when_userAuthenticated', async () => {
      // Test 9.7: should_returnUserContext_when_userAuthenticated
      const mockSession = {
        tokens: {
          idToken: {
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
                privacy: { showProfile: true, allowMessages: true },
              }),
              iat: Math.floor(Date.now() / 1000),
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-id-token',
          },
          accessToken: {
            payload: {
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-access-token',
          },
        },
      };

      mockAuth.getCurrentUser.mockResolvedValue({
        username: 'test@batbern.ch',
        userId: 'user-123',
      });
      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      const user = await authService.getCurrentUser();

      expect(user).toBeDefined();
      expect(user?.userId).toBeDefined();
      expect(user?.email).toBeDefined();
      expect(user?.role).toMatch(/^(organizer|speaker|partner|attendee)$/);
    });

    it('should_returnNull_when_userNotAuthenticated', async () => {
      // Test 9.8: should_returnNull_when_userNotAuthenticated
      mockAuth.getCurrentUser.mockRejectedValue(new Error('No current user'));

      const user = await authService.getCurrentUser();

      expect(user).toBeNull();
    });
  });

  describe('signOut', () => {
    it('should_signOutUser_when_called', async () => {
      await authService.signOut();
      expect(mockAuth.signOut).toHaveBeenCalled();
    });
  });

  describe('refreshToken', () => {
    it('should_refreshAccessToken_when_tokenNearExpiration', async () => {
      // Test 9.10: should_refreshAccessToken_when_tokenNearExpiration
      const mockSession = {
        tokens: {
          accessToken: {
            payload: {
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'new-access-token',
          },
        },
      };

      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      const result = await authService.refreshToken();

      expect(result.success).toBe(true);
      expect(result.accessToken).toBeDefined();
      expect(result.expiresIn).toBeGreaterThan(0);
    });
  });

  describe('isTokenExpired', () => {
    it('should_returnTrue_when_tokenExpired', () => {
      // Test 9.11: should_returnTrue_when_tokenExpired
      const expiredToken =
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.invalid';

      const isExpired = authService.isTokenExpired(expiredToken);

      expect(isExpired).toBe(true);
    });

    it('should_returnFalse_when_tokenValid', () => {
      // Test 9.12: should_returnFalse_when_tokenValid
      const futureTimestamp = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now
      const validToken = `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${btoa(JSON.stringify({ exp: futureTimestamp }))}.signature`;

      const isExpired = authService.isTokenExpired(validToken);

      expect(isExpired).toBe(false);
    });
  });
});
