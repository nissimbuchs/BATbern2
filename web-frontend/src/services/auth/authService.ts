/**
 * Authentication Service Implementation
 * Story 1.2: AWS Cognito Integration
 */

import {
  signIn as amplifySignIn,
  signUp as amplifySignUp,
  signOut as amplifySignOut,
  getCurrentUser as amplifyGetCurrentUser,
  fetchAuthSession,
} from 'aws-amplify/auth';
import {
  UserContext,
  LoginCredentials,
  SignUpData,
  AuthError,
  TokenRefreshResponse,
  MfaChallenge,
  UserPreferences,
  CognitoTokenClaims,
  UserRole,
} from '@/types/auth';

interface SignInResult {
  success: boolean;
  user?: UserContext;
  accessToken?: string;
  refreshToken?: string;
  error?: AuthError;
  mfaChallenge?: MfaChallenge;
}

interface SignUpResult {
  success: boolean;
  requiresConfirmation: boolean;
  error?: AuthError;
}

class AuthService {
  /**
   * Sign in user with Cognito
   */
  async signIn(credentials: LoginCredentials): Promise<SignInResult> {
    try {
      const result = await amplifySignIn({
        username: credentials.email,
        password: credentials.password,
      });

      // Handle MFA challenge or other next steps
      if (result.nextStep && result.nextStep.signInStep !== 'DONE') {
        return {
          success: false,
          mfaChallenge: {
            challengeName: result.nextStep.signInStep,
            challengeParameters: {},
            session: '',
          },
        };
      }

      // Extract user context from Cognito session
      const session = await fetchAuthSession();
      const tokens = session.tokens;

      if (!tokens?.idToken) {
        throw new Error('No ID token found in session');
      }

      const userContext = this.extractUserContextFromToken(
        tokens.idToken.payload as unknown as CognitoTokenClaims
      );

      return {
        success: true,
        user: userContext,
        accessToken: tokens.accessToken?.toString() || '',
        refreshToken: tokens.accessToken?.toString() || '',
      };
    } catch (error: any) {
      return {
        success: false,
        error: this.mapCognitoError(error),
      };
    }
  }

  /**
   * Sign up new user with Cognito
   */
  async signUp(signUpData: SignUpData): Promise<SignUpResult> {
    try {
      // Validate password match
      if (signUpData.password !== signUpData.confirmPassword) {
        return {
          success: false,
          requiresConfirmation: false,
          error: {
            code: 'PASSWORD_MISMATCH',
            message: 'Passwords do not match',
          },
        };
      }

      const result = await amplifySignUp({
        username: signUpData.email,
        password: signUpData.password,
        options: {
          userAttributes: {
            email: signUpData.email,
            'custom:role': signUpData.role,
            'custom:companyId': signUpData.companyId || '',
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

      return {
        success: true,
        requiresConfirmation: !result.isSignUpComplete,
      };
    } catch (error: any) {
      return {
        success: false,
        requiresConfirmation: false,
        error: this.mapCognitoError(error),
      };
    }
  }

  /**
   * Get current authenticated user
   */
  async getCurrentUser(): Promise<UserContext | null> {
    try {
      await amplifyGetCurrentUser();
      const session = await fetchAuthSession();
      const tokens = session.tokens;

      if (!tokens?.idToken) {
        return null;
      }

      return this.extractUserContextFromToken(
        tokens.idToken.payload as unknown as CognitoTokenClaims
      );
    } catch (error) {
      return null;
    }
  }

  /**
   * Sign out current user
   */
  async signOut(): Promise<void> {
    try {
      await amplifySignOut();
    } catch (error) {
      console.error('Sign out error:', error);
    }
  }

  /**
   * Refresh access token
   */
  async refreshToken(): Promise<TokenRefreshResponse> {
    try {
      const session = await fetchAuthSession({ forceRefresh: true });
      const tokens = session.tokens;

      if (!tokens?.accessToken) {
        return {
          success: false,
          accessToken: '',
          expiresIn: 0,
          error: {
            code: 'NO_TOKEN',
            message: 'No access token available',
          },
        };
      }

      const expiresIn = tokens.accessToken.payload.exp
        ? tokens.accessToken.payload.exp - Math.floor(Date.now() / 1000)
        : 0;

      return {
        success: true,
        accessToken: tokens.accessToken.toString(),
        refreshToken: tokens.accessToken.toString(),
        expiresIn,
      };
    } catch (error: any) {
      return {
        success: false,
        accessToken: '',
        expiresIn: 0,
        error: this.mapCognitoError(error),
      };
    }
  }

  /**
   * Check if JWT token is expired
   */
  isTokenExpired(token: string): boolean {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return true;

      const payload = JSON.parse(atob(parts[1]));
      const currentTime = Math.floor(Date.now() / 1000);

      return payload.exp <= currentTime;
    } catch (error) {
      return true;
    }
  }

  /**
   * Extract user context from Cognito ID token
   */
  private extractUserContextFromToken(tokenPayload: CognitoTokenClaims): UserContext {
    const preferences: UserPreferences = JSON.parse(tokenPayload['custom:preferences'] || '{}');

    return {
      userId: tokenPayload.sub,
      email: tokenPayload.email,
      emailVerified: tokenPayload.email_verified,
      role: tokenPayload['custom:role'] as UserRole,
      companyId: tokenPayload['custom:companyId'],
      preferences,
      issuedAt: tokenPayload.iat,
      expiresAt: tokenPayload.exp,
      tokenId: tokenPayload.sub,
    };
  }

  /**
   * Map Cognito errors to application errors
   */
  private mapCognitoError(error: any): AuthError {
    const errorCode = error.code || error.name || 'UNKNOWN_ERROR';

    switch (errorCode) {
      case 'NotAuthorizedException':
      case 'UserNotFoundException':
        return {
          code: 'INVALID_CREDENTIALS',
          message: 'Invalid email or password',
        };
      case 'UserNotConfirmedException':
        return {
          code: 'USER_NOT_CONFIRMED',
          message: 'Please confirm your email address',
        };
      case 'TooManyRequestsException':
        return {
          code: 'TOO_MANY_REQUESTS',
          message: 'Too many attempts. Please try again later',
        };
      case 'InvalidPasswordException':
        return {
          code: 'INVALID_PASSWORD',
          message: 'Password does not meet requirements',
        };
      case 'UsernameExistsException':
        return {
          code: 'EMAIL_EXISTS',
          message: 'An account with this email already exists',
        };
      default:
        return {
          code: errorCode,
          message: error.message || 'An unexpected error occurred',
        };
    }
  }
}

export const authService = new AuthService();
