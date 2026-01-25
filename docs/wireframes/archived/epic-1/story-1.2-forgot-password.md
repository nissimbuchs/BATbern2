# Forgot Password Flow Wireframe

## Header Information

### Story
Epic 1, Story 1.2 - API Gateway & Authentication Service

### Screen
Forgot Password Flow (Password Reset Request)

### User Role
Public (unauthenticated)

### Related FR
- **FR1**: Role-based authentication with distinct interfaces for organizers, speakers, partners, and attendees
- **NFR3**: Integration with external services (email, payment processing, file storage) through configurable APIs (AWS SES for password reset emails)

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                       │
│  ← Back to Login      BATbern Event Platform       🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │    🔑 Reset Password    │                     │
│                      │                         │                     │
│                      │  Enter your email and   │                     │
│                      │  we'll send you a       │                     │
│                      │  password reset link    │                     │
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
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │ [Send Reset Link] ────► │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│           ┌─────────────────────────────────────────────┐           │
│           │ ℹ️ You will receive an email with           │           │
│           │   instructions to reset your password.      │           │
│           │   The link will expire in 1 hour.          │           │
│           └─────────────────────────────────────────────┘           │
│                                                                       │
│                                                                       │
│                    Remember your password?                           │
│                    [Back to Sign In]                                 │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────── AFTER SUBMISSION ──────────────────────────┐
│                                                                       │
│  ← Back to Login      BATbern Event Platform       🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │    ✅ Check Your Email  │                     │
│                      │                         │                     │
│                      │  We've sent a password  │                     │
│                      │  reset link to:         │                     │
│                      │                         │                     │
│                      │  user@example.com       │                     │
│                      │                         │                     │
│                      │  Click the link to      │                     │
│                      │  reset your password.   │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │   [Back to Sign In]     │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│                    Didn't receive the email?                         │
│                    [Resend Reset Link]                               │
│                                                                       │
│           ┌─────────────────────────────────────────────┐           │
│           │ ℹ️ If you don't see the email, check your   │           │
│           │   spam folder or try again with a different │           │
│           │   email address.                            │           │
│           └─────────────────────────────────────────────┘           │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Language Selector Dropdown**: Switch between English and German (🌐 EN ▼ | DE)
- **← Back to Login Link**: Navigate back to login screen
- **Email Input Field**: Text input for user's email address with validation
- **Send Reset Link Button**: Primary action button to request password reset email
- **Back to Sign In Link**: Alternative navigation back to login screen (shown in both states)
- **Resend Reset Link Button**: Allows user to resend email if not received (shown only after submission)
- **Loading Spinner**: Shown on Send Reset Link button during API request
- **Success Message**: Confirmation display after successful submission showing the email address
- **Error Message Banner**: Displayed above form if email not found or request fails

## Functional Requirements Met

- **FR1**: Password recovery functionality for all user roles
- **NFR3**: Integration with AWS SES for sending password reset emails with secure reset links
- **NFR4**: ✅ **Multi-language support from MVP launch** - German (de-CH) and English (en-US) for UI and password reset emails, bilingual AWS SES templates configured from day 1
- **Security**: Time-limited (1 hour) password reset tokens generated by AWS Cognito
- **User Experience**: Clear two-step process: (1) Request reset link, (2) Confirmation with instructions
- **Email Delivery**: Transactional email sent via AWS SES with bilingual HTML templates (German/English) based on user's preferred language or browser locale

## User Interactions

### Primary Flow: Request Password Reset

1. User navigates to Forgot Password screen from Login screen
2. User enters email address in Email field
3. User clicks [Send Reset Link] button
4. System validates email format
5. System calls Cognito Forgot Password API
6. System sends password reset email via AWS SES (regardless of whether email exists - security best practice)
7. System displays success confirmation screen
8. User receives email with password reset link
9. User clicks link in email (opens in browser)
10. System redirects to Password Reset Completion screen (separate screen, not in this wireframe)
11. User enters new password and confirms
12. System updates password in Cognito
13. User redirected to Login screen with success message

### Secondary Flow: Return to Login

1. User clicks [← Back to Login] link (header) or [Back to Sign In] link (footer)
2. System navigates to Login Screen (story-1.2-login-screen.md)

### Secondary Flow: Resend Reset Link

1. User clicks [Resend Reset Link] button (only visible after successful submission)
2. System calls Cognito Forgot Password API again
3. System sends another password reset email
4. System shows toast notification: "Reset link sent again"
5. User remains on confirmation screen

### Error Flow: Invalid Email Format

1. User enters invalid email (e.g., missing @, invalid domain)
2. User clicks [Send Reset Link] button
3. System validates email format client-side
4. System displays error message below email field: "Please enter a valid email address"
5. User corrects email and retries

### Error Flow: Rate Limit Exceeded

1. User submits multiple reset requests in short time
2. System receives 429 Too Many Requests from Cognito
3. System displays error banner: "Too many reset requests. Please wait 5 minutes and try again."
4. [Send Reset Link] button disabled with countdown timer

### Error Flow: Network Error

1. User clicks [Send Reset Link] button
2. Network request fails or times out
3. System displays error banner: "Connection error. Please check your internet and try again."
4. User can retry immediately

### Security Note: Email Not Found Behavior

For security reasons, even if the email address is not found in the system, we display the same success message to prevent email enumeration attacks. The user sees: "Check your email" regardless of whether the account exists.

## Technical Notes

### AWS Cognito Forgot Password Flow
- **Cognito API**: Use `forgotPassword` method from AWS Cognito SDK
- **Reset Token**: Cognito generates secure, time-limited reset code (1-hour expiration)
- **Email Template**: Custom AWS SES bilingual templates (de/en) with BATbern branding and reset link
- **Language Detection**: Email sent in user's preferred language (from Cognito `custom:language` attribute) or browser locale fallback
- **Reset Link Format**: `https://app.batbern.ch/auth/reset-password?code={reset_code}&email={email}&lang={de|en}`
- **Security**: Reset codes are single-use and expire after 1 hour

### Frontend Framework
- **React 18.2+** with TypeScript
- **Material-UI** for form components (TextField, Button)
- **React Hook Form** for form state management
- **Zustand** for global state (if needed)
- **React Query** for API calls
- **react-i18next** for internationalization (German/English from day 1)
- **i18next-browser-languagedetector** for automatic locale detection

### Email Templates (AWS SES)

**FROM MVP LAUNCH: Bilingual templates configured in AWS SES**

**English Version (en-US):**
```html
Subject: Reset Your BATbern Password

Hello,

You requested to reset your password for your BATbern account.

Click the link below to reset your password:
{reset_link}

This link will expire in 1 hour.

If you didn't request this, please ignore this email.

Best regards,
BATbern Team
```

**German Version:**
```html
Subject: BATbern Passwort zurücksetzen

Hallo,

Sie haben eine Zurücksetzung Ihres Passworts für Ihr BATbern-Konto angefordert.

Klicken Sie auf den untenstehenden Link, um Ihr Passwort zurückzusetzen:
{reset_link}

Dieser Link läuft in 1 Stunde ab.

Falls Sie diese Anfrage nicht gestellt haben, ignorieren Sie diese E-Mail bitte.

Mit freundlichen Grüßen,
BATbern Team
```

**Note:** Email language determined by user's language preference stored in Cognito attributes or from language selection on forgot password screen.

### Security Measures
- **Time-Limited Tokens**: 1-hour expiration on reset links
- **Single-Use Tokens**: Reset code invalidated after successful password reset
- **Email Enumeration Prevention**: Always show success message, never reveal if email exists
- **Rate Limiting**: Maximum 3 reset requests per email per hour
- **Secure Link**: HTTPS-only reset link with unique token
- **CSRF Protection**: Reset form includes CSRF token validation

### Accessibility
- **WCAG 2.1 AA Compliance**: All elements keyboard accessible
- **ARIA Labels**: Proper labels on email input and buttons
- **Focus Management**: Focus moves to email field on page load
- **Screen Reader Announcements**: Success/error messages announced to screen readers
- **High Contrast**: Meets 4.5:1 contrast ratio

### Internationalization (i18n)
- **Language Support**: German and English with language selector
- **UI Text**: All screen text translated (title, labels, buttons, messages)
- **Email Templates**: Separate AWS SES templates for German and English
- **Language Persistence**: Respects user's language selection from login screen or browser locale
- **Dynamic Switching**: Language change applies immediately without page refresh
- **Error Messages**: All validation and API errors localized in both languages
- **Translation Keys**: `auth.forgot.title`, `auth.forgot.emailLabel`, `auth.forgot.submitButton`, `auth.forgot.successMessage`, etc.

## API Requirements

### Initial Page Load APIs

None - this screen does not require data fetching on initial load.

### User Action APIs

1. **POST /cognito/forgotPassword** (AWS Cognito SDK method)
   - Triggered by: [Send Reset Link] button click
   - Payload:
     ```typescript
     {
       ClientId: "<cognito_client_id>",
       Username: "user@example.com"
     }
     ```
   - Response:
     ```json
     {
       "CodeDeliveryDetails": {
         "Destination": "u***@example.com",
         "DeliveryMedium": "EMAIL",
         "AttributeName": "email"
       }
     }
     ```
   - Used for: Initiating password reset flow and triggering email delivery

2. **POST /api/v1/auth/forgot-password** (Optional wrapper endpoint)
   - Triggered by: [Send Reset Link] button click
   - Payload:
     ```json
     {
       "email": "user@example.com"
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "message": "If an account exists with this email, you will receive a password reset link.",
       "emailSent": true
     }
     ```
   - Used for: Simplified forgot password flow with backend handling Cognito integration and email delivery

3. **POST /api/v1/auth/resend-reset-link** (Resend functionality)
   - Triggered by: [Resend Reset Link] button click
   - Payload:
     ```json
     {
       "email": "user@example.com"
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "message": "Reset link sent again to your email."
     }
     ```
   - Used for: Resending password reset email if user didn't receive it

## Navigation Map

### Primary Navigation Actions

1. **[← Back to Login] Link** → Navigate to `Login Screen`
   - Target: `/auth/login` (story-1.2-login-screen.md)
   - Navigation Type: Full page navigation
   - Context Passed: None
   - Validation: None required

2. **[Send Reset Link] Button** → Show Confirmation Screen (same page, different view state)
   - Target: Same component, different view state
   - Navigation Type: In-page state transition
   - Context Passed: Email address to display in confirmation
   - Validation: Email must be valid format and non-empty

3. **[Back to Sign In] Link** → Navigate to `Login Screen`
   - Target: `/auth/login` (story-1.2-login-screen.md)
   - Navigation Type: Full page navigation
   - Context Passed: None
   - Validation: None required

### Secondary Navigation (Data Interactions)

None - this is a simple form screen with no data-driven navigation.

### Event-Driven Navigation

1. **Successful Reset Request Event** → Transition to Confirmation View
   - Trigger: Successful response from Cognito `forgotPassword` API
   - Target: Confirmation view (same page component, different state)
   - Context: Email address to display
   - Side Effects: Email sent to user with reset link

2. **Password Reset Link Clicked (From Email)** → Navigate to Reset Password Completion Screen
   - Trigger: User clicks reset link in email
   - Target: `/auth/reset-password?code={code}&email={email}` (separate screen, not in this wireframe)
   - Context: Reset code and email from URL query parameters
   - Note: This navigation happens from email, not from this screen

### Error States & Redirects

1. **Invalid Email Format Error** → Show Error Message (Stay on Screen)
   - Trigger: Client-side email validation fails
   - Action: Display error message below email field
   - Message: "Please enter a valid email address"
   - User Action: Correct email format and retry

2. **Rate Limit Exceeded Error** → Show Error Banner (Stay on Screen)
   - Trigger: 429 Too Many Requests from Cognito
   - Action: Display error banner above form
   - Message: "Too many reset requests. Please wait 5 minutes and try again."
   - User Action: Wait for cooldown period

3. **Network Error** → Show Error Banner + Retry
   - Trigger: Network timeout or connection error
   - Action: Display error banner with retry option
   - Message: "Connection error. Please check your internet and try again."
   - User Action: Click [Send Reset Link] again to retry

4. **Server Error** → Show Error Banner
   - Trigger: 500 Internal Server Error
   - Action: Display error banner
   - Message: "An unexpected error occurred. Please try again later."
   - User Action: Retry later or contact support

## Responsive Design Considerations

### Mobile Layout Changes

**Screen Width < 768px (Mobile):**
- Single-column layout with full-width form elements
- Email input expands to full width minus padding
- [Send Reset Link] button expands to full width
- Information box text size reduced for mobile
- Increase touch target sizes to minimum 44x44px
- ← Back to Login link remains in header but with larger touch target

### Tablet Layout Changes

**Screen Width 768px - 1024px (Tablet):**
- Form container maintains centered position with max-width of 500px
- Increase padding around form
- Touch targets maintain 44x44px minimum

### Mobile-Specific Interactions

- **Auto-focus Email Field**: Focus email input on page load (only on hardware keyboard devices)
- **Email Keyboard**: Use `type="email"` to trigger email-optimized keyboard
- **Tap to Navigate**: All links have adequate touch targets (minimum 44x44px)
- **Form Submission**: Support "Enter" key on virtual keyboards to submit

## Accessibility Notes

- **Keyboard Navigation**: Full keyboard support with tab order: Email → Send Reset Link → Back to Sign In
- **ARIA Labels**:
  - Email input: `aria-label="Email address for password reset" aria-required="true"`
  - Send Reset Link button: `aria-label="Send password reset link"`
- **Focus Indicators**: Clear, visible focus rings (2px solid blue outline) on all interactive elements
- **Error Announcements**: Use `aria-live="polite"` region for error/success messages
- **Screen Reader Support**: Form landmarks with `<main>` and `<form role="form">`
- **Loading States**: Button shows "Sending..." text with `aria-busy="true"` during request
- **Success Confirmation**: Entire confirmation view announced to screen readers with `role="status"` and `aria-live="polite"`

## State Management

### Local Component State

- **View State**:
  - `currentView: 'request' | 'confirmation'` - Toggle between request and confirmation views
  - `email: string` - User's email input
  - `isSubmitting: boolean` - Loading state during API request
  - `error: string | null` - Current error message
  - `resendCooldown: number` - Cooldown timer for resend button (seconds remaining)

- **Validation State**:
  - `emailError: string | null` - Email field validation error

### Global State (Zustand Store)

None required for this screen. This is a standalone flow that doesn't need global state persistence.

### Server State (React Query)

- **Forgot Password Mutation**:
  - Mutation Key: `['auth', 'forgotPassword']`
  - Mutator: `POST /api/v1/auth/forgot-password` or Cognito SDK `forgotPassword`
  - On Success: Transition to confirmation view, store email for display
  - On Error: Set error state, display error message
  - Retry: 1 time for network errors only

- **Resend Reset Link Mutation**:
  - Mutation Key: `['auth', 'resendResetLink']`
  - Mutator: `POST /api/v1/auth/resend-reset-link`
  - On Success: Show toast notification "Reset link sent again"
  - On Error: Display error message
  - Retry: 0 times (user can manually retry)

### Real-Time Updates

None required for this screen.

## Form Validation Rules

### Field-Level Validations

- **Email Address**:
  - Required: Yes - "Email address is required"
  - Format: Valid email format (regex: `/^[^\s@]+@[^\s@]+\.[^\s@]+$/`) - "Please enter a valid email address"
  - Max Length: 255 characters - "Email address is too long"
  - Trim: Remove leading/trailing whitespace
  - Validation Timing: On blur and on submit

### Form-Level Validations

- **Submit Validation**: Email must be valid before enabling form submission
- **Rate Limiting**: After 3 requests within 5 minutes, disable submit button with cooldown timer
- **Button State**: Disable [Send Reset Link] button while `isSubmitting === true`

## Edge Cases & Error Handling

- **Empty State**: Default view when page loads - email field empty, no errors
- **Loading State**: During password reset request:
  - [Send Reset Link] button disabled with spinner + "Sending..." text
  - Email field disabled to prevent editing
- **Success State**: Confirmation view displayed:
  - Shows email address where reset link was sent
  - Provides [Resend Reset Link] option
  - Clear instructions for next steps
- **Error State - Invalid Email**:
  - Red error message below email field
  - Email field outlined in red
  - Focus remains on email field
- **Error State - Rate Limited**:
  - Error banner: "Too many reset requests. Please wait 5 minutes and try again."
  - [Send Reset Link] button disabled with countdown timer
  - Countdown shows remaining time (e.g., "Wait 4:32")
- **Error State - Network Error**:
  - Error banner: "Connection error. Please check your internet and try again."
  - Email field value preserved
  - User can retry immediately
- **Email Not in System** (Security):
  - Show same success confirmation to prevent email enumeration
  - User sees "Check your email" message
  - No email is actually sent (handled by backend)
- **Spam Folder Note**: Confirmation view includes reminder to check spam folder
- **Resend Cooldown**: After resending, disable [Resend Reset Link] button for 60 seconds to prevent spam
- **Link Expiration Notice**: Clear messaging that reset link expires in 1 hour

## API Consolidation Notes

**AWS Cognito Managed Endpoints**: The following endpoints are part of AWS Cognito's managed authentication service and are intentionally not consolidated:
- `POST /cognito/forgotPassword` (SDK method) - AWS Cognito API for initiating password reset flow
- `POST /cognito/confirmForgotPassword` (SDK method) - AWS Cognito API for completing password reset with verification code

These endpoints are part of AWS Cognito's specialized password reset flow and follow AWS SDK patterns. They are managed by AWS and provide infrastructure-level authentication capabilities.

**Consolidated Custom Endpoints**: Custom wrapper endpoints follow the consolidated API patterns from Story 1.16 (API Consolidation Foundation):
- `POST /api/v1/auth/forgot-password` (Story 1.16) - Wrapper endpoint that follows consolidated patterns and handles Cognito integration
- `POST /api/v1/auth/resend-reset-link` (Story 1.16) - Wrapper endpoint for resending password reset emails

**Rationale**:
- **AWS Cognito endpoints** are specialized managed services that handle password reset flows, token generation, email delivery coordination, and security features (rate limiting, expiration, single-use tokens). These are infrastructure-level authentication services, not application domain APIs, and therefore exist outside the scope of API consolidation (Stories 1.16-1.27).
- **Wrapper endpoints** provide a simplified interface that abstracts AWS Cognito complexity while following the consolidated API patterns established in Story 1.16 (consistent error handling, request/response formats, logging, monitoring).
- **Separation of concerns**: Password reset flows are specialized authentication operations handled by Cognito's managed service. The consolidation effort (Stories 1.16-1.27) focuses on application domain APIs (events, partners, speakers, users, etc.), not managed authentication infrastructure.
- **Security best practices**: AWS Cognito's password reset implementation includes built-in security features (time-limited tokens, rate limiting, email enumeration prevention) that would need to be reimplemented if not using the managed service.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation | Sally (UX Expert) |
| 2025-10-04 | 1.1 | Added language selector (EN/DE per NFR4); Added bilingual email templates (German/English); Added i18n implementation details | Sally (UX Expert) |
| 2025-10-04 | 1.2 | Added API Consolidation Notes section explaining AWS Cognito password reset endpoints and their relationship to consolidated APIs | Claude (AI Assistant) |

## Review Notes

### Stakeholder Feedback

*To be completed after stakeholder review*

### Design Iterations

*To be completed as design evolves*

### Open Questions

1. **Email Template Customization**: Should we allow custom email templates per deployment environment (dev/staging/prod)?
   - Decision: Use same template across environments, just different branding/URLs

2. **Multi-Language Support**: Should password reset emails be sent in German or English based on user preference?
   - ✅ **DECISION: YES - Bilingual from MVP launch** (NFR4)
   - Password reset emails sent in user's preferred language (from user profile or browser language)
   - Bilingual HTML email templates: German (de-CH) and English (en-US)
   - All UI text on forgot password screens bilingual from day 1

3. **Alternative Recovery Methods**: Should we support SMS-based password reset for users without email access?
   - Decision: Email-only in MVP, SMS can be added as enhancement

4. **Resend Limit**: What is the maximum number of resend attempts allowed per hour?
   - Decision: 3 resends per hour per email address

5. **Admin Override**: Should admins have ability to manually reset user passwords?
   - Decision: Yes, but separate admin interface - not part of this user-facing flow
