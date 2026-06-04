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
  signInWithRedirect: vi.fn(),
}));

// Mock Cognito token provider for storage configuration
vi.mock('aws-amplify/auth/cognito', () => ({
  cognitoUserPoolsTokenProvider: {
    setKeyValueStorage: vi.fn(),
  },
}));

// Mock Amplify Hub (Story 12.8 F7 — waitForFederatedSession listens for auth events).
// listen MUST return an unsubscribe function (the implementation calls it on settle).
const mockHubListen = vi.hoisted(() => vi.fn(() => () => {}));
vi.mock('aws-amplify/utils', () => ({
  Hub: { listen: mockHubListen },
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
              'custom:role': 'ORGANIZER',
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
              'custom:role': 'SPEAKER',
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
      // Story 12.1: company is no longer sourced from the token (custom:companyId
      // dropped from extraction). AuthContext.hydrateUserFromDb fills it from /users/me.
      expect(result.user?.companyId).toBeUndefined();
      // preferences defaults to a complete object at extraction; hydration overrides it.
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
              'custom:role': 'ORGANIZER',
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

      // Verify Amplify was configured with storage adapter
      expect(cognitoUserPoolsTokenProvider.setKeyValueStorage).toHaveBeenCalledWith(
        expect.objectContaining({
          setItem: expect.any(Function),
          getItem: expect.any(Function),
          removeItem: expect.any(Function),
          clear: expect.any(Function),
        })
      );
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
              'custom:role': 'SPEAKER',
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

      // Verify Amplify was configured with storage adapter
      expect(cognitoUserPoolsTokenProvider.setKeyValueStorage).toHaveBeenCalledWith(
        expect.objectContaining({
          setItem: expect.any(Function),
          getItem: expect.any(Function),
          removeItem: expect.any(Function),
          clear: expect.any(Function),
        })
      );
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
            'custom:preferences': JSON.stringify({
              firstName: 'John',
              lastName: 'Doe',
              language: 'en',
              newsletterOptIn: false,
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

    it('should_notWriteCustomCompanyId_evenWhenCompanyIdProvided_perStory12_1', async () => {
      // Story 12.1 AC4: the `...(signUpData.companyId && { 'custom:companyId': … })`
      // spread (was authService.ts:318) is removed. Company is owned by
      // user_profiles.company_id via the user-management path, never seeded from the
      // token attribute. Even if a caller passes companyId, it must NOT reach Cognito.
      const signUpData: SignUpData = {
        email: 'withcompany@company.com',
        password: 'ValidPassword123!',
        confirmPassword: 'ValidPassword123!',
        role: 'attendee',
        companyId: 'some-company-id',
        firstName: 'Jane',
        lastName: 'Roe',
        acceptTerms: true,
      };

      mockAuth.signUp.mockResolvedValue({
        isSignUpComplete: false,
        userId: 'withcompany-id',
        nextStep: { signUpStep: 'CONFIRM_SIGN_UP' },
      });

      await authService.signUp(signUpData);

      const call = mockAuth.signUp.mock.calls[0][0] as {
        options: { userAttributes: Record<string, unknown> };
      };
      expect(call.options.userAttributes).not.toHaveProperty('custom:companyId');
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

    it('should_returnError_when_passwordDoesNotMeetRequirements', async () => {
      // Test InvalidPasswordException error case
      const signUpData: SignUpData = {
        email: 'newuser@company.com',
        password: 'weak',
        confirmPassword: 'weak',
        role: 'attendee',
        firstName: 'John',
        lastName: 'Doe',
        acceptTerms: true,
      };

      // Mock InvalidPasswordException
      mockAuth.signUp.mockRejectedValue({
        name: 'InvalidPasswordException',
        message: 'Password does not meet requirements',
      });

      const result = await authService.signUp(signUpData);

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('INVALID_PASSWORD');
      expect(result.error?.message).toBe('Password does not meet requirements');
    });

    it('should_returnError_when_emailAlreadyExists', async () => {
      // Test UsernameExistsException error case
      const signUpData: SignUpData = {
        email: 'existing@company.com',
        password: 'ValidPassword123!',
        confirmPassword: 'ValidPassword123!',
        role: 'attendee',
        firstName: 'John',
        lastName: 'Doe',
        acceptTerms: true,
      };

      // Mock UsernameExistsException
      mockAuth.signUp.mockRejectedValue({
        name: 'UsernameExistsException',
        message: 'An account with the given email already exists.',
      });

      const result = await authService.signUp(signUpData);

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('EMAIL_EXISTS');
      expect(result.error?.message).toBe('An account with this email already exists');
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
              'custom:role': 'ORGANIZER',
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

    it('should_notSourceCompanyIdOrPreferencesFromToken_perStory12_1', async () => {
      // Story 12.1 AC1: extractUserContextFromToken no longer reads custom:companyId
      // (was authService.ts:448) nor custom:preferences (was authService.ts:425).
      // Even when the token carries those claims, the UserContext must not pick them up
      // — company + preferences come from GET /users/me via AuthContext hydration.
      // Identity (sub/email) + authorization (custom:role/custom:username) stay intact.
      const mockSession = {
        tokens: {
          idToken: {
            payload: {
              sub: 'user-999',
              email: 'hygiene@batbern.ch',
              email_verified: true,
              'custom:role': 'SPEAKER',
              'custom:username': 'jane.doe',
              'custom:companyId': 'stale-company-from-token',
              'custom:preferences': JSON.stringify({
                language: 'fr',
                theme: 'dark',
                notifications: { email: true, sms: false, push: true },
                privacy: { showProfile: true, allowMessages: true },
              }),
              iat: Math.floor(Date.now() / 1000),
              exp: Math.floor(Date.now() / 1000) + 3600,
            },
            toString: () => 'mock-id-token',
          },
          accessToken: {
            payload: { exp: Math.floor(Date.now() / 1000) + 3600 },
            toString: () => 'mock-access-token',
          },
        },
      };

      mockAuth.getCurrentUser.mockResolvedValue({
        username: 'hygiene@batbern.ch',
        userId: 'user-999',
      });
      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      const user = await authService.getCurrentUser();

      expect(user).toBeDefined();
      // companyId NOT read from the token claim
      expect(user?.companyId).toBeUndefined();
      // preferences NOT read from the token claim — extraction returns the COMPLETE
      // default object (language 'en', theme 'light'), NOT the token's 'fr'/'dark'.
      // Asserting the default value (rather than undefined) proves the token claim was
      // not sourced AND that the UserPreferences type contract holds before hydration.
      expect(user?.preferences?.language).toBe('en');
      expect(user?.preferences?.theme).toBe('light');
      // identity + authorization preserved
      expect(user?.username).toBe('jane.doe');
      expect(user?.role).toBe('speaker');
      expect(user?.email).toBe('hygiene@batbern.ch');
    });

    it('should_returnNull_when_userNotAuthenticated', async () => {
      // Test 9.8: should_returnNull_when_userNotAuthenticated
      mockAuth.getCurrentUser.mockRejectedValue(new Error('No current user'));

      const user = await authService.getCurrentUser();

      expect(user).toBeNull();
    });

    it('should_returnNull_when_sessionHasNoIdToken', async () => {
      // Covers lines 266-267: session resolves but tokens.idToken is absent
      mockAuth.getCurrentUser.mockResolvedValue({
        username: 'test@batbern.ch',
        userId: 'user-123',
      });
      mockAuth.fetchAuthSession.mockResolvedValue({
        tokens: undefined,
      });

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

  describe('signInWithFederated', () => {
    it('should_callSignInWithRedirect_when_signInWithFederatedInvokedWithGoogle', async () => {
      // Story 12.7 AC1
      vi.mocked(mockAuth.signInWithRedirect).mockResolvedValue(undefined);

      await authService.signInWithFederated('Google');

      expect(mockAuth.signInWithRedirect).toHaveBeenCalledTimes(1);
      expect(mockAuth.signInWithRedirect).toHaveBeenCalledWith({ provider: 'Google' });
    });

    it('should_propagateError_when_redirectInitiationFails', async () => {
      // No catch-and-swallow — initiation errors reach the caller (AC1).
      vi.mocked(mockAuth.signInWithRedirect).mockRejectedValue(new Error('redirect failed'));

      await expect(authService.signInWithFederated('Google')).rejects.toThrow('redirect failed');
    });
  });

  // Story 12.8 F7: the /auth/callback race — wait (bounded) for Amplify's async ?code= →
  // token exchange to settle instead of checking the session once and bailing.
  describe('waitForFederatedSession', () => {
    it('should_resolveTrue_when_tokensAlreadyPresent', async () => {
      // Exchange already completed before we were called — the immediate token check wins.
      vi.mocked(mockAuth.fetchAuthSession).mockResolvedValue({
        tokens: { idToken: { toString: () => 'id-token' } },
      } as never);

      await expect(authService.waitForFederatedSession(2000)).resolves.toBe(true);
    });

    it('should_resolveTrue_when_hubReportsSignInWithRedirect', async () => {
      // No tokens yet — the Hub event signals the exchange settled.
      vi.mocked(mockAuth.fetchAuthSession).mockResolvedValue({ tokens: undefined } as never);
      let hubCallback: ((capsule: { payload: { event: string } }) => void) | undefined;
      mockHubListen.mockImplementation(((_channel: string, cb: typeof hubCallback) => {
        hubCallback = cb;
        return () => {};
      }) as never);

      const pending = authService.waitForFederatedSession(2000);
      // Let the listener attach, then fire the success event.
      await new Promise((r) => setTimeout(r, 10));
      hubCallback?.({ payload: { event: 'signInWithRedirect' } });

      await expect(pending).resolves.toBe(true);
    });

    it('should_resolveFalse_when_hubReportsRedirectFailure', async () => {
      vi.mocked(mockAuth.fetchAuthSession).mockResolvedValue({ tokens: undefined } as never);
      let hubCallback: ((capsule: { payload: { event: string } }) => void) | undefined;
      mockHubListen.mockImplementation(((_channel: string, cb: typeof hubCallback) => {
        hubCallback = cb;
        return () => {};
      }) as never);

      const pending = authService.waitForFederatedSession(2000);
      await new Promise((r) => setTimeout(r, 10));
      hubCallback?.({ payload: { event: 'signInWithRedirect_failure' } });

      await expect(pending).resolves.toBe(false);
    });

    it('should_resolveFalse_when_timeoutElapsesWithoutSession', async () => {
      // Never settles: no tokens, no Hub event → bounded false (never throws/hangs).
      vi.mocked(mockAuth.fetchAuthSession).mockResolvedValue({ tokens: undefined } as never);
      mockHubListen.mockImplementation((() => () => {}) as never);

      await expect(authService.waitForFederatedSession(80)).resolves.toBe(false);
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

    it('should_returnError_when_noTokenAvailable', async () => {
      // Test line 288: no token case
      const mockSession = {
        tokens: null,
      };

      mockAuth.fetchAuthSession.mockResolvedValue(mockSession);

      const result = await authService.refreshToken();

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('NO_TOKEN');
      expect(result.error?.message).toBe('No access token available');
    });

    it('should_returnError_when_refreshFails', async () => {
      // Test line 309: error catch block
      mockAuth.fetchAuthSession.mockRejectedValue({
        code: 'NotAuthorizedException',
        message: 'Refresh token expired',
      });

      const result = await authService.refreshToken();

      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
      expect(result.error?.code).toBe('INVALID_CREDENTIALS');
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

    it('should_returnTrue_when_tokenInvalid', () => {
      // Test catch block in isTokenExpired - invalid token format
      const invalidTokens = ['invalid-token', 'only.two', 'invalid.base64!@#.signature', ''];

      invalidTokens.forEach((token) => {
        const isExpired = authService.isTokenExpired(token);
        expect(isExpired).toBe(true);
      });
    });
  });

  describe('Error Mapping', () => {
    it('should_mapUserNotConfirmedException_when_userNotConfirmed', async () => {
      // Test UserNotConfirmedException mapping (lines 389-392)
      const credentials: LoginCredentials = {
        email: 'unconfirmed@example.com',
        password: 'Password123!',
      };

      mockAuth.signIn.mockRejectedValue({
        code: 'UserNotConfirmedException',
        message: 'User is not confirmed',
      });

      const result = await authService.signIn(credentials);

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('USER_NOT_CONFIRMED');
      expect(result.error?.message).toBe('Please confirm your email address');
    });

    it('should_mapTooManyRequestsException_when_rateLimited', async () => {
      // Covers line 399: TooManyRequestsException mapping
      const credentials: LoginCredentials = {
        email: 'user@example.com',
        password: 'Password123!',
      };

      mockAuth.signIn.mockRejectedValue({
        name: 'TooManyRequestsException',
        message: 'Rate exceeded',
      });

      const result = await authService.signIn(credentials);

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('TOO_MANY_REQUESTS');
      expect(result.error?.message).toBe('Too many attempts. Please try again later');
    });

    it('should_mapUnknownError_when_errorCodeNotRecognized', async () => {
      // Covers default case in mapCognitoError
      const credentials: LoginCredentials = {
        email: 'user@example.com',
        password: 'Password123!',
      };

      mockAuth.signIn.mockRejectedValue({
        name: 'SomeUnknownException',
        message: 'Something unexpected happened',
      });

      const result = await authService.signIn(credentials);

      expect(result.success).toBe(false);
      expect(result.error?.code).toBe('SomeUnknownException');
      expect(result.error?.message).toBe('Something unexpected happened');
    });
  });
});
