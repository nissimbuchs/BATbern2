# Authentication API

## Overview

User authentication, session management, password reset, and email verification.

**Base Path**: `/api/v1/auth`

**Related Wireframes**:
- `story-1.2-login-screen.md` - Login functionality
- `story-1.2-forgot-password.md` - Password reset flow
- `story-1.2-account-creation.md` - User registration
- `story-1.2-email-verification.md` - Email verification

**Authentication Required**: No (public endpoints)

---

## Endpoints Summary

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/auth/login` | Authenticate user | ❌ |
| POST | `/api/v1/auth/logout` | End user session | ✅ |
| POST | `/api/v1/auth/refresh` | Refresh access token | ❌ (refresh token) |
| POST | `/api/v1/auth/register` | Create new account | ❌ |
| POST | `/api/v1/auth/forgot-password` | Request password reset | ❌ |
| POST | `/api/v1/auth/reset-password` | Reset password with code | ❌ |
| POST | `/api/v1/auth/verify-email` | Verify email with code | ❌ |
| POST | `/api/v1/auth/resend-verification` | Resend verification email | ❌ |
| GET | `/api/v1/auth/session` | Check session validity | ✅ |

**Total Endpoints**: 9

---

## 1. Login

**Endpoint**: `POST /api/v1/auth/login`

**Purpose**: Authenticate user and obtain JWT tokens

**Request**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response** (`200 OK`):
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "expiresIn": 3600,
  "user": {
    "id": "uuid-123",
    "email": "user@example.com",
    "role": "ORGANIZER",
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

**Errors**:
- `400` - Invalid email/password format
- `401` - Invalid credentials
- `403` - Account locked or unverified
- `429` - Rate limit exceeded (3 attempts per 5 minutes)

**Integration**: AWS Cognito `initiateAuth`

---

## 2. Logout

**Endpoint**: `POST /api/v1/auth/logout`

**Purpose**: Invalidate current session

**Headers**: `Authorization: Bearer {token}`

**Request**: No body

**Response** (`204 No Content`): No body

**Errors**:
- `401` - Invalid or expired token

**Integration**: AWS Cognito `globalSignOut`, invalidate refresh token

---

## 3. Refresh Token

**Endpoint**: `POST /api/v1/auth/refresh`

**Purpose**: Obtain new access token using refresh token

**Request**:
```json
{
  "refreshToken": "..."
}
```

**Response** (`200 OK`):
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600
}
```

**Errors**:
- `401` - Invalid or expired refresh token

**Integration**: AWS Cognito `refreshToken`

---

## 4. Register

**Endpoint**: `POST /api/v1/auth/register`

**Purpose**: Create new user account

**Request**:
```json
{
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "language": "de"
}
```

**Validation**:
- Email: Valid format, max 255 chars
- Password: Min 8 chars, uppercase, lowercase, number, symbol
- firstName/lastName: Min 2 chars, max 100 chars
- language: `de` or `en`

**Response** (`201 Created`):
```json
{
  "userId": "uuid-123",
  "email": "user@example.com",
  "verificationRequired": true,
  "message": "Verification email sent"
}
```

**Errors**:
- `400` - Validation failed
- `409` - Email already exists
- `429` - Rate limit exceeded

**Integration**: AWS Cognito `signUp`, AWS SES verification email

---

## 5. Forgot Password

**Endpoint**: `POST /api/v1/auth/forgot-password`

**Purpose**: Request password reset link via email

**Request**:
```json
{
  "email": "user@example.com"
}
```

**Response** (`200 OK`):
```json
{
  "success": true,
  "message": "If an account exists with this email, you will receive a password reset link."
}
```

**Security**: Always returns success to prevent email enumeration

**Errors**:
- `400` - Invalid email format
- `429` - Rate limit exceeded (3 requests per hour per email)

**Integration**: AWS Cognito `forgotPassword`, AWS SES bilingual email (de/en)

---

## 6. Reset Password

**Endpoint**: `POST /api/v1/auth/reset-password`

**Purpose**: Reset password using code from email

**Request**:
```json
{
  "email": "user@example.com",
  "code": "123456",
  "newPassword": "newPassword123"
}
```

**Validation**:
- Code: 6-digit numeric
- newPassword: Same rules as registration

**Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Password reset successful"
}
```

**Errors**:
- `400` - Invalid code or password format
- `401` - Code expired (1 hour expiration)
- `422` - Code already used

**Integration**: AWS Cognito `confirmForgotPassword`

---

## 7. Verify Email

**Endpoint**: `POST /api/v1/auth/verify-email`

**Purpose**: Verify email address with code

**Request**:
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

**Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Email verified successfully"
}
```

**Errors**:
- `400` - Invalid code format
- `401` - Code expired or invalid
- `409` - Email already verified

**Integration**: AWS Cognito `confirmSignUp`

---

## 8. Resend Verification

**Endpoint**: `POST /api/v1/auth/resend-verification`

**Purpose**: Resend email verification code

**Request**:
```json
{
  "email": "user@example.com"
}
```

**Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Verification email sent"
}
```

**Errors**:
- `400` - Invalid email
- `409` - Email already verified
- `429` - Rate limit exceeded (60-second cooldown)

**Integration**: AWS Cognito `resendConfirmationCode`, AWS SES

---

## 9. Session Check

**Endpoint**: `GET /api/v1/auth/session`

**Purpose**: Validate current session and get user info

**Headers**: `Authorization: Bearer {token}`

**Response** (`200 OK`):
```json
{
  "valid": true,
  "user": {
    "id": "uuid-123",
    "email": "user@example.com",
    "role": "ORGANIZER"
  },
  "expiresAt": "2024-10-04T11:30:00Z"
}
```

**Errors**:
- `401` - Invalid or expired token

---

## Security Notes

### Password Policy
- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 number
- At least 1 special character

### Rate Limiting
- Login: 3 attempts per 5 minutes per IP
- Forgot Password: 3 requests per hour per email
- Resend Verification: 60-second cooldown

### Email Enumeration Prevention
- Forgot Password always returns success
- Consistent response times
- No indication if email exists

### Token Expiration
- Access Token: 1 hour
- Refresh Token: 30 days
- Password Reset Code: 1 hour
- Email Verification Code: 24 hours

---

## Integration Details

### AWS Cognito User Pool
- Authentication provider
- Password policy enforcement
- MFA support (future)
- Custom attributes: `language`, `role`

### AWS SES
- Bilingual email templates (German/English)
- Password reset emails
- Email verification
- Language detection via user preference or browser locale

### CloudWatch
- All authentication events logged
- Failed login attempts tracked
- Security audit trail

---

## Related Documentation
- [API Design Principles](../design-principles.md)
- [Story 1.2.2: Forgot Password](../../../stories/1.2.2-implement-forgot-password-flow.md)
- [Story 1.2.3: Account Creation](../../../stories/1.2.3-implement-account-creation-flow.md)
