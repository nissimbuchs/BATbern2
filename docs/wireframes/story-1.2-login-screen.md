# Login Screen Wireframe

## Header Information

### Story
Epic 1, Story 1.2 - API Gateway & Authentication Service

### Screen
Login Screen

### User Role
Public (unauthenticated), All Roles (for re-authentication)

### Related FR
- **FR1**: Role-based authentication with distinct interfaces for organizers, speakers, partners, and attendees
- **NFR2**: Database supports advanced text search across all historical content with sub-second response times
- **NFR3**: Integration with external services (email, payment processing, file storage) through configurable APIs

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                       │
│  BATbern Event Platform                             🌐 EN ▼ | DE    │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │    🔐 Welcome Back      │                     │
│                      │                         │                     │
│                      │  Sign in to continue    │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │ Email Address           │                     │
│                      │ [________________]      │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │ Password                │                     │
│                      │ [________________] 👁    │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                      ☐ Remember me                                   │
│                                                                       │
│                      [Forgot Password?]                              │
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │   [Sign In] ──────────► │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│                      ─────────  OR  ─────────                        │
│                                                                       │
│                                                                       │
│                      Don't have an account?                          │
│                      [Create Account]                                │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Language Selector Dropdown**: Switch between English and German (🌐 EN ▼ | DE)
- **Email Input Field**: Text input for user's email address with validation for proper email format
- **Password Input Field**: Password field with toggle visibility button (👁 icon) to show/hide password
- **Remember Me Checkbox**: Optional checkbox to persist authentication session
- **Forgot Password Link**: Navigate to password recovery flow
- **Sign In Button**: Primary action button to submit credentials and authenticate
- **Create Account Link**: Navigate to new account registration flow
- **Show/Hide Password Toggle**: Icon button to toggle password visibility for user convenience
- **Error Message Banner**: Displayed above form when authentication fails (not shown in default state)
- **Loading Spinner**: Shown on Sign In button during authentication request

## Functional Requirements Met

- **FR1**: Role-based authentication with AWS Cognito. All users login with the same interface; role-based dashboard redirect happens automatically after authentication based on Cognito user attributes.
- **NFR4**: ✅ **Multi-language support from MVP launch** - German (de-CH) and English (en-US) with language selector, react-i18next framework, all UI text and error messages bilingual from day 1
- **Security**: JWT bearer token authentication with secure credential transmission over HTTPS
- **User Experience**: Simple, role-agnostic login interface with clear error messaging and password recovery options
- **Session Management**: Optional "Remember Me" functionality to persist authentication tokens and language preference securely

## User Interactions

### Primary Flow: Successful Login
1. User enters email address in Email field
2. User enters password in Password field
3. (Optional) User checks "Remember me" checkbox
4. User clicks [Sign In] button
5. System validates credentials with AWS Cognito
6. System retrieves user role from Cognito custom attributes
7. System stores JWT token in secure storage (httpOnly cookie or secure localStorage)
8. System redirects to role-specific dashboard:
   - **Organizer** → `/organizer/dashboard` (Event Management Dashboard)
   - **Speaker** → `/speaker/dashboard` (Speaker Dashboard)
   - **Partner** → `/partner/dashboard` (Partner Analytics Dashboard)
   - **Attendee** → `/attendee/dashboard` (Personal Attendee Dashboard)

### Secondary Flow: Password Recovery
1. User clicks [Forgot Password?] link
2. System navigates to Forgot Password screen (story-1.2-forgot-password.md)

### Secondary Flow: New Account
1. User clicks [Create Account] link
2. System navigates to Account Creation screen (story-1.2-account-creation.md)

### Error Flow: Failed Authentication
1. User enters invalid credentials
2. User clicks [Sign In] button
3. System receives error from AWS Cognito
4. System displays error banner above form:
   - "Invalid email or password. Please try again."
   - "Account locked due to too many failed attempts. Try again in 15 minutes."
   - "Email not verified. Please check your inbox for verification link."
5. User can retry or use [Forgot Password?] link

### Field Validation
- **Email**: Real-time validation for proper email format (contains @, valid domain)
- **Password**: Minimum 8 characters (enforced on blur or submit)
- **Error States**: Red border on invalid fields with error message below field

## Technical Notes

### Authentication Implementation
- **AWS Cognito Integration**: Use AWS Amplify SDK or direct Cognito API calls for authentication
- **JWT Token Management**: Store access token, refresh token, and ID token securely
- **Custom Attributes**: Retrieve user role, company_id, and preferences from Cognito custom attributes
- **Token Refresh**: Implement automatic token refresh using refresh token before expiration
- **Session Storage**:
  - If "Remember me" checked → Store refresh token in secure localStorage with encryption
  - If unchecked → Use session storage that expires when browser closes

### Frontend Framework
- **React 18.2+** with TypeScript
- **Material-UI** for form components (TextField, Button, Checkbox, Select for language)
- **React Hook Form** for form state management and validation
- **Zustand** for global auth state management
- **React Query** for API calls with automatic retry logic
- **react-i18next** for internationalization (German/English from day 1)
- **i18next-browser-languagedetector** for automatic locale detection

### Security Measures
- **HTTPS Only**: All authentication requests over TLS 1.3
- **CSRF Protection**: CSRF tokens for form submission
- **Rate Limiting**: Client-side throttling + server-side rate limiting (5 attempts per 15 minutes)
- **Password Masking**: Password field type="password" with optional visibility toggle
- **Input Sanitization**: Prevent XSS attacks by sanitizing email input
- **Secure Token Storage**: httpOnly cookies preferred, or encrypted localStorage for "Remember me"

### Accessibility
- **WCAG 2.1 AA Compliance**: All interactive elements keyboard accessible
- **ARIA Labels**: Proper labels for screen readers on all form fields
- **Focus Management**: Logical tab order (Language → Email → Password → Remember me → Sign In)
- **Error Announcements**: Screen reader announcements for validation errors
- **High Contrast**: Meets 4.5:1 contrast ratio for text and form elements

### Internationalization (i18n) Implementation

**FROM MVP LAUNCH (Day 1):**
- **Language Detection**: Auto-detect browser locale on first visit, default to German (de-CH) if no preference
- **Language Persistence**: Store selected language in localStorage as `batbern_language` key and sync with user profile after login
- **Translation Keys**: All UI text using translation keys (e.g., `auth.login.title`, `auth.login.emailLabel`)
- **Supported Languages**:
  - **German (de-CH)**: Primary language (default)
  - **English (en-US)**: Secondary language
- **Language Switcher**: Dropdown in top-right header (🌐 EN ▼ | DE)
- **Error Messages**: All error messages localized in both languages from day 1
- **Form Labels**: All form field labels, placeholders, and validation messages translated
- **Dynamic Content**: Language change applies immediately without page refresh using react-i18next
- **RTL Support**: Not required (both English and German are LTR languages)
- **Framework**: react-i18next with i18next-browser-languagedetector
- **Translation Files**: `/locales/de/auth.json`, `/locales/en/auth.json`

**Translation Examples:**
```typescript
// English (en)
{
  "auth.login.title": "Welcome Back",
  "auth.login.subtitle": "Sign in to continue",
  "auth.login.emailLabel": "Email Address",
  "auth.login.passwordLabel": "Password",
  "auth.login.rememberMe": "Remember me",
  "auth.login.forgotPassword": "Forgot Password?",
  "auth.login.signInButton": "Sign In",
  "auth.login.createAccount": "Create Account",
  "auth.login.noAccount": "Don't have an account?",
  "auth.errors.invalidCredentials": "Invalid email or password. Please try again."
}

// German (de)
{
  "auth.login.title": "Willkommen zurück",
  "auth.login.subtitle": "Melden Sie sich an, um fortzufahren",
  "auth.login.emailLabel": "E-Mail-Adresse",
  "auth.login.passwordLabel": "Passwort",
  "auth.login.rememberMe": "Angemeldet bleiben",
  "auth.login.forgotPassword": "Passwort vergessen?",
  "auth.login.signInButton": "Anmelden",
  "auth.login.createAccount": "Konto erstellen",
  "auth.login.noAccount": "Noch kein Konto?",
  "auth.errors.invalidCredentials": "Ungültige E-Mail oder Passwort. Bitte versuchen Sie es erneut."
}
```

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/auth/session-check**
   - Query params: None (reads from cookie or Authorization header)
   - Returns: `{ isAuthenticated: boolean, user: User | null, role: UserRole | null }`
   - Used for: Checking if user already has valid session on page load. If authenticated, redirect to dashboard.
   - Response Example:
     ```json
     {
       "isAuthenticated": false,
       "user": null,
       "role": null
     }
     ```

### User Action APIs

1. **POST /oauth2/token** (AWS Cognito OAuth2 endpoint)
   - Triggered by: [Sign In] button click
   - Payload:
     ```json
     {
       "grant_type": "password",
       "client_id": "<cognito_client_id>",
       "username": "user@example.com",
       "password": "userPassword123"
     }
     ```
   - Response:
     ```json
     {
       "access_token": "eyJhbGc...",
       "id_token": "eyJhbGc...",
       "refresh_token": "eyJhbGc...",
       "expires_in": 3600,
       "token_type": "Bearer"
     }
     ```
   - Used for: Authenticating user and obtaining JWT tokens from AWS Cognito

2. **GET /oauth2/userInfo** (AWS Cognito OAuth2 endpoint)
   - Triggered by: After successful token acquisition
   - Headers: `Authorization: Bearer <access_token>`
   - Returns:
     ```json
     {
       "sub": "user-uuid",
       "email": "user@example.com",
       "email_verified": true,
       "custom:role": "ORGANIZER",
       "custom:company_id": "company-uuid",
       "custom:preferences": "{...}",
       "name": "John Doe"
     }
     ```
   - Used for: Retrieving user profile and role information to determine dashboard redirect

3. **POST /api/v1/auth/login** (Optional wrapper endpoint)
   - Triggered by: [Sign In] button click (alternative to direct Cognito calls)
   - Payload:
     ```json
     {
       "email": "user@example.com",
       "password": "userPassword123",
       "rememberMe": true
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "user": {
         "id": "user-uuid",
         "email": "user@example.com",
         "name": "John Doe",
         "role": "ORGANIZER",
         "companyId": "company-uuid"
       },
       "tokens": {
         "accessToken": "eyJhbGc...",
         "refreshToken": "eyJhbGc...",
         "expiresIn": 3600
       },
       "redirectTo": "/organizer/dashboard"
     }
     ```
   - Used for: Simplified authentication flow with backend handling Cognito integration

## Navigation Map

### Primary Navigation Actions

1. **[Sign In] Button** → Navigate to Role-Based Dashboard
   - Target: Dynamic based on user role
     - `ORGANIZER` → `/organizer/dashboard` (Event Management Dashboard - story-1.16-event-management-dashboard.md)
     - `SPEAKER` → `/speaker/dashboard` (Speaker Dashboard - wireframes-speaker.md)
     - `PARTNER` → `/partner/dashboard` (Partner Analytics Dashboard - story-6.1-partner-analytics-dashboard.md)
     - `ATTENDEE` → `/attendee/dashboard` (Personal Attendee Dashboard - story-5.2-personal-dashboard.md)
   - Navigation Type: Full page redirect
   - Context Passed: User object, role, JWT tokens stored in global state
   - Validation: Email and password must be non-empty and valid format

2. **[Forgot Password?] Link** → Navigate to `Forgot Password Flow`
   - Target: `/auth/forgot-password` (story-1.2-forgot-password.md)
   - Navigation Type: Full page navigation
   - Context Passed: Email pre-filled if user entered it before clicking link
   - Validation: None required

3. **[Create Account] Link** → Navigate to `Account Creation`
   - Target: `/auth/register` (story-1.2-account-creation.md)
   - Navigation Type: Full page navigation
   - Context Passed: None
   - Validation: None required

### Secondary Navigation (Data Interactions)

None - this is a simple form screen with no data-driven navigation elements.

### Event-Driven Navigation

1. **Successful Authentication Event** → Redirect to Role Dashboard
   - Trigger: Successful response from `/oauth2/token` and `/oauth2/userInfo`
   - Target: Role-specific dashboard (see Primary Navigation #1)
   - Context: User profile and tokens stored in Zustand auth store
   - Side Effects:
     - Update global auth state
     - Start token refresh timer
     - Track login event in analytics

2. **Session Already Active Event** → Redirect to Role Dashboard
   - Trigger: On page load, if session check returns `isAuthenticated: true`
   - Target: Role-specific dashboard based on stored role
   - Context: Existing user session from cookies/localStorage

### Error States & Redirects

1. **Invalid Credentials Error** → Show Error Banner (Stay on Login Screen)
   - Trigger: 401 Unauthorized response from Cognito
   - Action: Display error message banner above form
   - Message: "Invalid email or password. Please try again."
   - User Action: Retry login or click [Forgot Password?]

2. **Email Not Verified Error** → Show Error Banner + Verification Option
   - Trigger: 403 Forbidden response with error code "UserNotConfirmedException"
   - Action: Display error banner with resend verification link
   - Message: "Email not verified. [Resend verification email]"
   - User Action: Click link to trigger verification email resend

3. **Account Locked Error** → Show Error Banner with Timeout
   - Trigger: 429 Too Many Requests or Cognito "TooManyRequestsException"
   - Action: Display error banner with lockout duration
   - Message: "Account temporarily locked due to too many failed attempts. Try again in 15 minutes."
   - User Action: Wait or use [Forgot Password?] to reset

4. **Network Error** → Show Error Banner + Retry
   - Trigger: Network timeout or connection error
   - Action: Display error banner with retry button
   - Message: "Connection error. Please check your internet and try again. [Retry]"
   - User Action: Click [Retry] to re-attempt authentication

5. **Server Error** → Show Error Banner + Support Contact
   - Trigger: 500 Internal Server Error response
   - Action: Display error banner with support information
   - Message: "An unexpected error occurred. Please try again later or contact support."
   - User Action: Retry later or contact support

## Responsive Design Considerations

### Mobile Layout Changes

**Screen Width < 768px (Mobile):**
- Single-column layout with full-width form elements
- Increase touch target sizes to minimum 44x44px
- Email and Password inputs expand to full width minus padding
- [Sign In] button expands to full width for easier tapping
- Logo and welcome message remain centered but reduce font size
- Information box at bottom collapses to smaller text
- Remove side padding, use only top/bottom padding for better use of screen space

### Tablet Layout Changes

**Screen Width 768px - 1024px (Tablet):**
- Form container maintains centered position with max-width of 500px
- Increase padding around form for better visual balance
- Font sizes remain same as desktop
- Touch targets maintain 44x44px minimum

### Mobile-Specific Interactions

- **Auto-focus Email Field**: On page load, focus email input automatically (only on devices with hardware keyboard)
- **Email Keyboard**: Use `type="email"` to trigger email-optimized keyboard on mobile
- **Password Show/Hide**: Larger touch target for password visibility toggle (👁 icon)
- **Tap to Navigate**: All links ([Forgot Password?], [Create Account]) have adequate touch targets
- **Form Submission**: Support "Enter" key on virtual keyboards to submit form
- **Pull-to-Refresh**: Disable pull-to-refresh to prevent accidental page reloads during form interaction

## Accessibility Notes

- **Keyboard Navigation**: Full keyboard support with logical tab order: Email → Password → Remember me → Forgot Password → Sign In → Create Account
- **ARIA Labels**:
  - Email input: `aria-label="Email address" aria-required="true"`
  - Password input: `aria-label="Password" aria-required="true"`
  - Show/Hide password toggle: `aria-label="Toggle password visibility"`
  - Sign In button: `aria-label="Sign in to your account"`
- **Focus Indicators**: Clear, visible focus rings (2px solid blue outline) on all interactive elements
- **Error Announcements**: Use `aria-live="polite"` region for error messages to announce validation errors to screen readers
- **Color Contrast**:
  - Text on background: 4.5:1 minimum contrast ratio
  - Error messages: Red with sufficient contrast (#D32F2F on white = 5.2:1)
  - Links: Blue with underline for non-color identification
- **Label Association**: All form inputs have associated `<label>` elements with `for` attribute matching input `id`
- **Screen Reader Support**: Form landmarks with `<main>` and `<form role="form">` for navigation
- **Loading States**: When submitting, button shows "Signing in..." text and spinner with `aria-busy="true"`

## State Management

### Local Component State

- **Form Values**:
  - `email: string` - User's email input
  - `password: string` - User's password input
  - `rememberMe: boolean` - Remember me checkbox state
  - `showPassword: boolean` - Password visibility toggle state
  - `isSubmitting: boolean` - Loading state during authentication request
  - `error: string | null` - Current error message to display

- **Validation State**:
  - `emailError: string | null` - Email field validation error
  - `passwordError: string | null` - Password field validation error

### Global State (Zustand Store)

- **Auth State** (`useAuthStore`):
  ```typescript
  interface AuthState {
    user: User | null;
    currentRole: UserRole | null;
    availableRoles: UserRole[];
    isAuthenticated: boolean;
    tokens: {
      accessToken: string | null;
      refreshToken: string | null;
      expiresAt: number | null;
    };
    login: (email: string, password: string, rememberMe: boolean) => Promise<void>;
    logout: () => void;
    refreshToken: () => Promise<void>;
  }
  ```

### Server State (React Query)

- **Authentication Query**:
  - Query Key: `['auth', 'session']`
  - Fetcher: `GET /api/v1/auth/session-check`
  - Stale Time: 5 minutes
  - Cache Time: 10 minutes
  - Retry: false (don't retry on 401)

- **Login Mutation**:
  - Mutation Key: `['auth', 'login']`
  - Mutator: `POST /oauth2/token` or `POST /api/v1/auth/login`
  - On Success: Update auth store, redirect to dashboard
  - On Error: Set error state, display error message
  - Retry: 1 time for network errors only

### Real-Time Updates

None required for login screen. Real-time updates apply post-authentication in dashboard screens.

## Form Validation Rules

### Field-Level Validations

- **Email Address**:
  - Required: Yes - "Email address is required"
  - Format: Valid email format (regex: `/^[^\s@]+@[^\s@]+\.[^\s@]+$/`) - "Please enter a valid email address"
  - Max Length: 255 characters - "Email address is too long"
  - Trim: Remove leading/trailing whitespace before validation
  - Validation Timing: On blur and on submit

- **Password**:
  - Required: Yes - "Password is required"
  - Min Length: 8 characters (for user convenience, only enforced on submit, not during typing) - "Password must be at least 8 characters"
  - Max Length: 128 characters - "Password is too long"
  - Validation Timing: On submit (not on blur to avoid annoying users while typing)

- **Remember Me**:
  - Required: No (optional checkbox)
  - Default: Unchecked

### Form-Level Validations

- **Submit Validation**: All required fields must be filled and valid before enabling form submission
- **Rate Limiting**: Client-side throttling after 5 failed attempts - disable submit button for 30 seconds with countdown message
- **Network Validation**: Check internet connectivity before submitting (optional, nice-to-have)
- **Button State**: Disable [Sign In] button while `isSubmitting === true` to prevent double-submission

## Edge Cases & Error Handling

- **Empty State**: Default state when page loads - all fields empty, no errors shown
- **Loading State**: During authentication request:
  - [Sign In] button disabled and shows spinner + "Signing in..." text
  - Form inputs disabled to prevent editing during submission
  - Focus trapped to prevent interaction with form
- **Error State - Invalid Credentials**:
  - Red error banner above form: "Invalid email or password. Please try again."
  - Email and password fields outlined in red
  - Password field cleared for security
  - Focus returns to email field
- **Error State - Email Not Verified**:
  - Orange warning banner: "Email not verified. [Resend verification email]"
  - Provide inline link to resend verification email
  - Show success toast when verification email sent
- **Error State - Account Locked**:
  - Red error banner: "Account locked due to too many failed login attempts. Try again in 15 minutes."
  - Disable [Sign In] button
  - Show countdown timer
  - Suggest using [Forgot Password?] link
- **Error State - Network Error**:
  - Yellow warning banner: "Connection error. Please check your internet and try again. [Retry]"
  - Provide [Retry] button to re-attempt login
  - Maintain form values during retry
- **Success State**:
  - Brief success message: "Signed in successfully! Redirecting..."
  - Show loading spinner during redirect
  - Immediately redirect to role dashboard
- **Already Authenticated State**:
  - On page load, if user already has valid session, immediately redirect to dashboard
  - Show brief "Already signed in, redirecting..." message
- **Session Expired State**:
  - If user navigates to login after session expiry, show info message: "Your session has expired. Please sign in again."
  - Pre-fill email if available from expired session
- **Password Manager Integration**:
  - Support browser password managers with proper `autocomplete` attributes
  - Email field: `autocomplete="username email"`
  - Password field: `autocomplete="current-password"`
- **CAPTCHA/Bot Protection** (Future Enhancement):
  - After 3 failed attempts, show reCAPTCHA to prevent brute force attacks
  - Currently handled by AWS Cognito rate limiting

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation | Sally (UX Expert) |
| 2025-10-04 | 1.1 | Added language selector (EN/DE per NFR4); Removed role information box (align with FR1/FR22 - role-agnostic login); Added i18n implementation details | Sally (UX Expert) |

## Review Notes

### Stakeholder Feedback

*To be completed after stakeholder review*

### Design Iterations

*To be completed as design evolves*

### Open Questions

All open questions have been resolved:

1. ✅ **MFA Support**: Should we add support for multi-factor authentication (MFA) on the login screen for Organizer and Partner roles?
   - **DECISION: Defer to Phase 2** - implement basic login first, add MFA as enhancement (Story 1.11 security essentials)

2. ✅ **Multi-Language Support**: Should login screen and error messages support German and English?
   - ✅ **DECISION: YES - German and English from MVP launch** (NFR4)
   - Language selector on login screen
   - All UI text, labels, validation messages, error messages bilingual from day 1
   - Default: German (de-CH), with English (en-US) option
   - Language preference persisted after login to user profile

3. ✅ **Social Login**: Should we add "Sign in with Google" or other social login options?
   - **DECISION: Not in MVP scope** - can be added post-launch based on user demand

4. ✅ **Password Requirements Display**: Should we show password requirements on hover/focus (8 characters minimum, etc.)?
   - **DECISION: Yes**, add tooltip on password field focus showing requirements

5. ✅ **Branding**: What logo, colors, and branding should we use for BATbern?
   - **DECISION: Pending brand guidelines** from marketing team
