# Authentication APIs

User authentication, session management, password reset, and email verification APIs.

## Endpoints

**Total**: 9 endpoints

| Endpoint | Description | Documentation |
|----------|-------------|---------------|
| `POST /api/v1/auth/login` | User login | [authentication.md](./authentication.md#1-login) |
| `POST /api/v1/auth/logout` | User logout | [authentication.md](./authentication.md#2-logout) |
| `POST /api/v1/auth/refresh` | Refresh access token | [authentication.md](./authentication.md#3-refresh-token) |
| `POST /api/v1/auth/register` | Create account | [authentication.md](./authentication.md#4-register) |
| `POST /api/v1/auth/forgot-password` | Request password reset | [authentication.md](./authentication.md#5-forgot-password) |
| `POST /api/v1/auth/reset-password` | Reset password | [authentication.md](./authentication.md#6-reset-password) |
| `POST /api/v1/auth/verify-email` | Verify email | [authentication.md](./authentication.md#7-verify-email) |
| `POST /api/v1/auth/resend-verification` | Resend verification | [authentication.md](./authentication.md#8-resend-verification) |
| `GET /api/v1/auth/session` | Check session | [authentication.md](./authentication.md#9-session-check) |

## Quick Start

### 1. Register Account
```bash
POST /api/v1/auth/register
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "language": "de"
}
```

### 2. Verify Email
Check email for verification code, then:
```bash
POST /api/v1/auth/verify-email
{
  "email": "user@example.com",
  "code": "123456"
}
```

### 3. Login
```bash
POST /api/v1/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}

Response:
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "...",
  "expiresIn": 3600,
  "user": {...}
}
```

### 4. Use Access Token
```bash
GET /api/v1/events
Authorization: Bearer eyJhbGci...
```

## Related Wireframes

- [story-1.2-login-screen.md](../../../wireframes/story-1.2-login-screen.md)
- [story-1.2-forgot-password.md](../../../wireframes/story-1.2-forgot-password.md)
- [story-1.2-account-creation.md](../../../wireframes/story-1.2-account-creation.md)

## Related Stories

- [Story 1.2: API Gateway & Authentication](../../../stories/1.2.api-gateway-authentication-service.md)
- [Story 1.2.2: Forgot Password Flow](../../../stories/1.2.2-implement-forgot-password-flow.md)
- [Story 1.2.3: Account Creation Flow](../../../stories/1.2.3-implement-account-creation-flow.md)
- [Story 1.2.4: Email Verification Flow](../../../stories/1.2.4-implement-email-verification-flow.md)
