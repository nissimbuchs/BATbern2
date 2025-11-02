/**
 * Authentication and authorization types for BATbern platform
 * Based on Story 1.2 specifications and AWS Cognito integration
 * Updated: 2025-10-08 - Testing deploy workflow with correct commit SHA
 */

export type UserRole = 'organizer' | 'speaker' | 'partner' | 'attendee';

export interface UserPreferences {
  language: 'en' | 'de' | 'fr' | 'it';
  theme: 'light' | 'dark';
  notifications: {
    email: boolean;
    sms: boolean;
    push: boolean;
  };
  privacy: {
    showProfile: boolean;
    allowMessages: boolean;
  };
}

export interface UserContext {
  userId: string;
  username: string; // Story 1.16.2: Public username identifier (e.g., "john.doe")
  email: string;
  emailVerified: boolean;
  role: UserRole; // Primary role (first in roles array)
  roles: UserRole[]; // All roles (Story 1.2.6: ADR-001 supports multiple roles)
  companyId?: string;
  preferences: UserPreferences;
  issuedAt: number;
  expiresAt: number;
  tokenId: string;
}

export interface AuthenticationState {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: UserContext | null;
  error: AuthError | null;
  accessToken: string | null;
  // refreshToken removed - AWS Amplify manages refresh tokens internally
}

export interface AuthError {
  code: string;
  message: string;
  field?: string;
  details?: Record<string, unknown>;
}

export interface LoginCredentials {
  email: string;
  password: string;
  rememberMe?: boolean;
}

export interface SignUpData {
  email: string;
  password: string;
  confirmPassword: string;
  role: UserRole;
  companyId?: string;
  firstName: string;
  lastName: string;
  acceptTerms: boolean;
  language?: string; // User's preferred language (de|en)
  newsletterOptIn?: boolean; // Newsletter opt-in preference
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirmation {
  email: string;
  confirmationCode: string;
  newPassword: string;
}

export interface MfaChallenge {
  challengeName: string;
  challengeParameters: Record<string, string>;
  session: string;
}

export interface MfaResponse {
  challengeResponse: string;
  session: string;
}

export interface TokenRefreshResponse {
  success?: boolean;
  accessToken: string;
  // refreshToken removed - AWS Amplify manages refresh tokens internally
  expiresIn: number;
  error?: AuthError;
}

// Route permission configuration
export interface RoutePermission {
  path: string;
  allowedRoles: UserRole[];
  requiresAuth: boolean;
  requiresVerification?: boolean;
}

// AWS Cognito specific types
export interface CognitoUserAttributes {
  sub: string;
  email: string;
  email_verified: boolean;
  'custom:companyId'?: string;
  'custom:preferences': string; // JSON string
}

export interface CognitoTokenClaims {
  sub: string;
  aud: string;
  iss: string;
  iat: number;
  exp: number;
  token_use: 'access' | 'id';
  scope?: string;
  auth_time: number;
  'cognito:username': string; // Auto-generated UUID by Cognito
  'custom:username'?: string; // Story 1.16.2: Meaningful username from database (e.g., "john.doe")
  'custom:role'?: string; // Story 1.2.6: Comma-separated string (e.g., "ORGANIZER,SPEAKER")
  'cognito:groups'?: string[]; // DEPRECATED: Legacy claim, use custom:role instead
  email: string;
  email_verified: boolean;
  'custom:companyId'?: string;
  'custom:preferences': string;
}
