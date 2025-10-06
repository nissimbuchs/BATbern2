/**
 * Authentication and authorization types for BATbern platform
 * Based on Story 1.2 specifications and AWS Cognito integration
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
  email: string;
  emailVerified: boolean;
  role: UserRole;
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
  refreshToken: string | null;
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
  refreshToken: string;
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
  'custom:role': UserRole;
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
  'cognito:username': string;
  'cognito:groups'?: string[];
  email: string;
  email_verified: boolean;
  'custom:role': UserRole;
  'custom:companyId'?: string;
  'custom:preferences': string;
}
