/**
 * Authentication Service Implementation
 * Story 1.2: AWS Cognito Integration
 */

import { Auth } from 'aws-amplify'
import {
  UserContext,
  LoginCredentials,
  SignUpData,
  AuthError,
  TokenRefreshResponse,
  MfaChallenge,
  UserPreferences,
  CognitoTokenClaims,
  UserRole
} from '@types/auth'

interface SignInResult {
  success: boolean
  user?: UserContext
  accessToken?: string
  refreshToken?: string
  error?: AuthError
  mfaChallenge?: MfaChallenge
}

interface SignUpResult {
  success: boolean
  requiresConfirmation: boolean
  error?: AuthError
}

class AuthService {
  /**
   * Sign in user with Cognito
   */
  async signIn(credentials: LoginCredentials): Promise<SignInResult> {
    try {
      const cognitoUser = await Auth.signIn(credentials.email, credentials.password)

      // Handle MFA challenge
      if (cognitoUser.challengeName) {
        return {
          success: false,
          mfaChallenge: {
            challengeName: cognitoUser.challengeName,
            challengeParameters: cognitoUser.challengeParam || {},
            session: cognitoUser.Session
          }
        }
      }

      // Extract user context from Cognito session
      const session = await Auth.currentSession()
      const idToken = session.getIdToken()
      const accessToken = session.getAccessToken()
      const refreshToken = session.getRefreshToken()

      const userContext = this.extractUserContextFromToken(idToken.payload as CognitoTokenClaims)

      return {
        success: true,
        user: userContext,
        accessToken: accessToken.getJwtToken(),
        refreshToken: refreshToken.getToken()
      }
    } catch (error: any) {
      return {
        success: false,
        error: this.mapCognitoError(error)
      }
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
            message: 'Passwords do not match'
          }
        }
      }

      const result = await Auth.signUp({
        username: signUpData.email,
        password: signUpData.password,
        attributes: {
          email: signUpData.email,
          'custom:role': signUpData.role,
          'custom:companyId': signUpData.companyId || '',
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

      return {
        success: true,
        requiresConfirmation: !result.userConfirmed
      }
    } catch (error: any) {
      return {
        success: false,
        requiresConfirmation: false,
        error: this.mapCognitoError(error)
      }
    }
  }

  /**
   * Get current authenticated user
   */
  async getCurrentUser(): Promise<UserContext | null> {
    try {
      const user = await Auth.currentAuthenticatedUser()
      const session = await Auth.currentSession()
      const idToken = session.getIdToken()

      return this.extractUserContextFromToken(idToken.payload as CognitoTokenClaims)
    } catch (error) {
      return null
    }
  }

  /**
   * Sign out current user
   */
  async signOut(): Promise<void> {
    try {
      await Auth.signOut()
    } catch (error) {
      console.error('Sign out error:', error)
    }
  }

  /**
   * Refresh access token
   */
  async refreshToken(): Promise<TokenRefreshResponse> {
    try {
      const session = await Auth.currentSession()

      if (session.isValid()) {
        const accessToken = session.getAccessToken()
        const refreshToken = session.getRefreshToken()

        return {
          success: true,
          accessToken: accessToken.getJwtToken(),
          refreshToken: refreshToken.getToken(),
          expiresIn: accessToken.getExpiration() - Math.floor(Date.now() / 1000)
        }
      }

      // Force refresh if current session is invalid
      const cognitoUser = await Auth.currentAuthenticatedUser()
      const newSession = await Auth.currentSession()
      const newAccessToken = newSession.getAccessToken()
      const newRefreshToken = newSession.getRefreshToken()

      return {
        success: true,
        accessToken: newAccessToken.getJwtToken(),
        refreshToken: newRefreshToken.getToken(),
        expiresIn: newAccessToken.getExpiration() - Math.floor(Date.now() / 1000)
      }
    } catch (error: any) {
      return {
        success: false,
        error: this.mapCognitoError(error)
      }
    }
  }

  /**
   * Check if JWT token is expired
   */
  isTokenExpired(token: string): boolean {
    try {
      const parts = token.split('.')
      if (parts.length !== 3) return true

      const payload = JSON.parse(atob(parts[1]))
      const currentTime = Math.floor(Date.now() / 1000)

      return payload.exp <= currentTime
    } catch (error) {
      return true
    }
  }

  /**
   * Extract user context from Cognito ID token
   */
  private extractUserContextFromToken(tokenPayload: CognitoTokenClaims): UserContext {
    const preferences: UserPreferences = JSON.parse(tokenPayload['custom:preferences'] || '{}')

    return {
      userId: tokenPayload.sub,
      email: tokenPayload.email,
      emailVerified: tokenPayload.email_verified,
      role: tokenPayload['custom:role'] as UserRole,
      companyId: tokenPayload['custom:companyId'],
      preferences,
      issuedAt: tokenPayload.iat,
      expiresAt: tokenPayload.exp,
      tokenId: tokenPayload.sub
    }
  }

  /**
   * Map Cognito errors to application errors
   */
  private mapCognitoError(error: any): AuthError {
    const errorCode = error.code || error.name || 'UNKNOWN_ERROR'

    switch (errorCode) {
      case 'NotAuthorizedException':
      case 'UserNotFoundException':
        return {
          code: 'INVALID_CREDENTIALS',
          message: 'Invalid email or password'
        }
      case 'UserNotConfirmedException':
        return {
          code: 'USER_NOT_CONFIRMED',
          message: 'Please confirm your email address'
        }
      case 'TooManyRequestsException':
        return {
          code: 'TOO_MANY_REQUESTS',
          message: 'Too many attempts. Please try again later'
        }
      case 'InvalidPasswordException':
        return {
          code: 'INVALID_PASSWORD',
          message: 'Password does not meet requirements'
        }
      case 'UsernameExistsException':
        return {
          code: 'EMAIL_EXISTS',
          message: 'An account with this email already exists'
        }
      default:
        return {
          code: errorCode,
          message: error.message || 'An unexpected error occurred'
        }
    }
  }
}

export const authService = new AuthService()