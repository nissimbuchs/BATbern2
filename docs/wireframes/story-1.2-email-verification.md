# Email Verification Wireframe

## Header Information

### Story
Epic 1, Story 1.2 - API Gateway & Authentication Service

### Screen
Email Verification (Account Confirmation)

### User Role
Public (newly registered, unconfirmed user)

### Related FR
- **FR1**: Role-based authentication with distinct interfaces for organizers, speakers, partners, and attendees
- **NFR3**: Integration with external services (AWS Cognito for verification, AWS SES for email delivery)
- **NFR4**: Platform shall support multi-language content (German, English) with internationalization framework

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                       │
│  BATbern Event Platform                             🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │   📧 Verify Your Email  │                     │
│                      │                         │                     │
│                      │  We've sent a           │                     │
│                      │  verification code to:  │                     │
│                      │                         │                     │
│                      │  john.doe@example.com   │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │                                                             │     │
│  │  Enter the 6-digit code from your email:                   │     │
│  │                                                             │     │
│  │            ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐            │     │
│  │            │ 1 │ │ 2 │ │ 3 │ │ 4 │ │ 5 │ │ 6 │            │     │
│  │            └───┘ └───┘ └───┘ └───┘ └───┘ └───┘            │     │
│  │                                                             │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │   [Verify Email] ─────► │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│                    Didn't receive the code?                          │
│                    [Resend Verification Email]                       │
│                                                                       │
│           ┌─────────────────────────────────────────────┐           │
│           │ ℹ️ The verification code expires in 24 hours.│           │
│           │   Check your spam folder if you don't see   │           │
│           │   the email.                                │           │
│           └─────────────────────────────────────────────┘           │
│                                                                       │
│                                                                       │
│                    Need help? [Contact Support]                      │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘

┌────────────────────── AFTER VERIFICATION ─────────────────────────┐
│                                                                       │
│  BATbern Event Platform                             🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │   ✅ Email Verified!    │                     │
│                      │                         │                     │
│                      │  Your account is now    │                     │
│                      │  active.                │                     │
│                      │                         │                     │
│                      │  Welcome to BATbern!    │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │  [Go to Dashboard] ───► │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│                                                                       │
│           ┌─────────────────────────────────────────────┐           │
│           │ You will be automatically redirected in 3   │           │
│           │ seconds...                                  │           │
│           └─────────────────────────────────────────────┘           │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────── ALTERNATIVE: LINK VERIFICATION ──────────────────┐
│                                                                       │
│  BATbern Event Platform                             🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │   🔄 Verifying...       │                     │
│                      │                         │                     │
│                      │  Please wait while we   │                     │
│                      │  verify your email      │                     │
│                      │  address.               │                     │
│                      │                         │                     │
│                      │  [■■■■■░░░░░] 50%       │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│           Note: You were redirected from the verification link       │
│           in your email. This may take a moment.                     │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

### Primary Verification Method (Code Entry)
- **Language Selector Dropdown**: Switch between English and German (🌐 EN ▼ | DE)
- **6-Digit Code Input Boxes**: Six individual input fields for verification code digits with auto-advance
- **Verify Email Button**: Primary action button to submit verification code
- **Resend Verification Email Link**: Allows user to request a new verification code
- **Contact Support Link**: Navigate to support/help page

### Alternative Verification Method (Email Link)
- **Auto-Verification**: When user clicks link in email, code is extracted from URL and auto-submitted
- **Loading Indicator**: Progress bar showing verification in progress
- **Success Message**: Confirmation screen after successful verification

### Common Elements
- **Email Address Display**: Shows the email address where verification was sent (partially masked for security)
- **Expiration Notice**: Information box showing code validity period (24 hours)
- **Auto-Redirect**: Countdown timer for automatic navigation to dashboard after success

## Functional Requirements Met

- **FR1**: Email verification as part of role-based authentication flow for all user roles
- **NFR3**: Integration with AWS Cognito for verification code validation and AWS SES for email delivery
- **NFR4**: ✅ **Multi-language support from MVP launch** - German (de-CH) and English (en-US) for UI and verification emails, bilingual AWS SES templates configured from day 1
- **Security**: Time-limited verification codes (24 hours) with single-use tokens
- **User Experience**: Two verification methods: manual code entry or one-click email link
- **Accessibility**: Clear instructions and error messages for verification process
- **Spam Protection**: Resend cooldown (60 seconds) to prevent email spam

## User Interactions

### Primary Flow: Manual Code Entry

1. User completes account registration (story-1.2-account-creation.md)
2. System navigates to Email Verification screen
3. System displays email address where code was sent
4. User opens email inbox
5. User finds verification email from BATbern
6. User reads 6-digit verification code (e.g., "123456")
7. User enters code digits in input boxes (auto-advance on each digit)
8. Code input auto-fills from left to right
9. User clicks [Verify Email] button (or code auto-submits when all 6 digits entered)
10. System calls Cognito ConfirmSignUp API with code
11. Cognito validates code and updates user status to "CONFIRMED"
12. System displays success screen: "✅ Email Verified!"
13. System auto-redirects to role-based dashboard after 3 seconds
14. User can click [Go to Dashboard] for immediate redirect

### Alternative Flow: Email Link Verification

1. User opens verification email
2. User clicks "Verify Email" link in email
3. Link opens in browser: `/auth/verify-email?code={code}&email={email}`
4. System displays loading screen: "🔄 Verifying..."
5. System automatically extracts code from URL
6. System calls Cognito ConfirmSignUp API
7. System displays success screen
8. System auto-redirects to role-based dashboard

### Secondary Flow: Resend Verification Code

1. User doesn't receive email or code expired
2. User clicks [Resend Verification Email] link
3. System calls Cognito ResendConfirmationCode API
4. System sends new verification email
5. System displays toast notification: "Verification email sent again"
6. System starts 60-second cooldown on resend button
7. User receives new email with fresh code
8. User continues with manual code entry flow

### Error Flow: Invalid Code

1. User enters incorrect 6-digit code
2. User clicks [Verify Email] button
3. System receives "CodeMismatchException" from Cognito
4. System displays error message: "Invalid code. Please check and try again."
5. System highlights code input boxes in red
6. System clears entered code
7. User can retry with correct code

### Error Flow: Expired Code

1. User enters verification code after 24 hours
2. User clicks [Verify Email] button
3. System receives "ExpiredCodeException" from Cognito
4. System displays error message: "This code has expired. Click 'Resend' to get a new one."
5. System clears entered code
6. User clicks [Resend Verification Email]
7. User receives fresh code and retries

### Error Flow: Too Many Failed Attempts

1. User enters wrong code multiple times (5+ attempts)
2. System receives "LimitExceededException" from Cognito
3. System displays error: "Too many failed attempts. Please wait 15 minutes or request a new code."
4. System disables [Verify Email] button temporarily
5. User waits or clicks [Resend Verification Email]

### Error Flow: Network Error

1. User enters code and clicks [Verify Email]
2. Network request fails or times out
3. System displays error: "Connection error. Please check your internet and try again."
4. Code remains in input boxes
5. User can retry immediately

## Technical Notes

### AWS Cognito Email Verification
- **Cognito API**: Use `confirmSignUp` method from AWS Cognito SDK
- **Verification Code**: 6-digit numeric code generated by Cognito
- **Code Expiration**: 24 hours from email send time
- **Single-Use**: Code invalidated after successful verification
- **Email Format**: HTML email with code and verification link
- **Language Detection**: Email sent in user's selected language during registration (from Cognito `custom:language` attribute)
- **Bilingual Templates**: AWS SES templates configured for German (de) and English (en) from day 1

### Email Templates (AWS SES)

**FROM MVP LAUNCH: Bilingual templates configured in AWS SES**

**English Version (en-US):**
```html
Subject: Verify Your BATbern Account

Hello John,

Welcome to BATbern! Please verify your email address to activate your account.

Your verification code is: 123456

Alternatively, click the link below to verify instantly:
https://app.batbern.ch/auth/verify-email?code=123456&email=john.doe@example.com

This code will expire in 24 hours.

If you didn't create a BATbern account, please ignore this email.

Best regards,
BATbern Team
```

**German Version (de-CH):**
```html
Subject: Verifizieren Sie Ihr BATbern-Konto

Hallo John,

Willkommen bei BATbern! Bitte verifizieren Sie Ihre E-Mail-Adresse, um Ihr Konto zu aktivieren.

Ihr Verifizierungscode lautet: 123456

Alternativ können Sie auf den untenstehenden Link klicken, um sofort zu verifizieren:
https://app.batbern.ch/auth/verify-email?code=123456&email=john.doe@example.com

Dieser Code läuft in 24 Stunden ab.

Falls Sie kein BATbern-Konto erstellt haben, ignorieren Sie diese E-Mail bitte.

Mit freundlichen Grüßen,
BATbern Team
```

**Note:** Email language determined by user's language preference stored during registration (custom:language attribute in Cognito).

### Frontend Framework
- **React 18.2+** with TypeScript
- **Material-UI** for UI components (TextField, Button)
- **React Hook Form** or custom state management for code input
- **Auto-Advance Logic**: Automatically move focus to next input box on digit entry
- **Paste Support**: Allow pasting full 6-digit code that auto-distributes across boxes
- **react-i18next** for internationalization (German/English from day 1)
- **i18next-browser-languagedetector** for automatic locale detection

### Verification Methods Supported
1. **Manual Code Entry**: User types 6-digit code from email
2. **Email Link Click**: User clicks link in email, code auto-extracted from URL
3. **Auto-Submit**: When all 6 digits entered, auto-submit verification (no button click needed)

### URL Parameters
- **Email Link Format**: `/auth/verify-email?code={verification_code}&email={user_email}`
- **Code Parameter**: Verification code from Cognito
- **Email Parameter**: User's email address for validation

### Security Measures
- **Time-Limited Codes**: 24-hour expiration
- **Single-Use Codes**: Code invalidated after use
- **Rate Limiting**: Maximum 5 verification attempts before lockout
- **Resend Cooldown**: 60 seconds between resend requests
- **Code Masking**: Don't show code in URL (for manual entry method)
- **HTTPS Only**: All verification requests over TLS 1.3

### Accessibility
- **WCAG 2.1 AA Compliance**: All elements keyboard accessible
- **ARIA Labels**: Each code input box properly labeled
- **Focus Management**: Auto-advance focus between input boxes
- **Screen Reader Support**: Announce success/error messages
- **High Contrast**: Code input boxes clearly visible

### Internationalization (i18n)

**FROM MVP LAUNCH (Day 1):**
- **Language Support**: German (de-CH) and English (en-US) with language selector in header
- **Default Language**: German (de-CH), respects user's language selection from registration
- **UI Text**: All screen text, instructions, buttons, error messages translated from day 1
- **Email Templates**: Bilingual AWS SES templates (de/en) configured from day 1
- **Verification Emails**: Sent in user's selected language during registration (Cognito `custom:language` attribute)
- **Success Messages**: "Email Verified!" / "E-Mail verifiziert!"
- **Error Messages**: All validation and API errors localized (German/English)
- **Dynamic Switching**: Language change applies immediately without page reload using react-i18next
- **Language Persistence**: Respects user's language selection from previous screens (stored in localStorage)
- **Translation Keys**: `auth.verify.title`, `auth.verify.codeLabel`, `auth.verify.submitButton`, etc.
- **Framework**: react-i18next with i18next-browser-languagedetector
- **Translation Files**: `/locales/de/auth.json`, `/locales/en/auth.json`

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/auth/verification-status** (Optional)
   - Query params: `email=john.doe@example.com`
   - Returns:
     ```json
     {
       "emailVerified": false,
       "codeSentAt": "2025-10-04T12:00:00Z",
       "codeExpiresAt": "2025-10-05T12:00:00Z",
       "resendAvailableAt": "2025-10-04T12:01:00Z"
     }
     ```
   - Used for: Checking if code already sent, when it expires, and resend cooldown
   - Triggered: On page load

### User Action APIs

1. **POST /cognito/confirmSignUp** (AWS Cognito SDK method)
   - Triggered by: [Verify Email] button click or auto-submit
   - Payload:
     ```typescript
     {
       ClientId: "<cognito_client_id>",
       Username: "john.doe@example.com",
       ConfirmationCode: "123456"
     }
     ```
   - Response (Success):
     ```json
     {}
     ```
   - Response (Error - Invalid Code):
     ```json
     {
       "__type": "CodeMismatchException",
       "message": "Invalid verification code provided, please try again."
     }
     ```
   - Used for: Confirming user email and activating account

2. **POST /api/v1/auth/verify-email** (Optional wrapper endpoint)
   - Triggered by: [Verify Email] button click
   - Payload:
     ```json
     {
       "email": "john.doe@example.com",
       "code": "123456"
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "user": {
         "id": "user-uuid",
         "email": "john.doe@example.com",
         "role": "SPEAKER",
         "emailVerified": true
       },
       "redirectTo": "/speaker/dashboard"
     }
     ```
   - Used for: Simplified verification with backend handling Cognito integration

3. **POST /cognito/resendConfirmationCode** (AWS Cognito SDK method)
   - Triggered by: [Resend Verification Email] link click
   - Payload:
     ```typescript
     {
       ClientId: "<cognito_client_id>",
       Username: "john.doe@example.com"
     }
     ```
   - Response:
     ```json
     {
       "CodeDeliveryDetails": {
         "Destination": "j***@example.com",
         "DeliveryMedium": "EMAIL",
         "AttributeName": "email"
       }
     }
     ```
   - Used for: Resending verification email with new code

4. **POST /api/v1/auth/resend-verification** (Optional wrapper endpoint)
   - Triggered by: [Resend Verification Email] link click
   - Payload:
     ```json
     {
       "email": "john.doe@example.com"
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "message": "Verification email sent again.",
       "nextResendAvailableAt": "2025-10-04T12:02:00Z"
     }
     ```
   - Used for: Resending verification with cooldown enforcement

## Navigation Map

### Primary Navigation Actions

1. **[Verify Email] Button** → Navigate to Role-Based Dashboard
   - Target: Dynamic based on user role (extracted from Cognito attributes)
     - `ORGANIZER` → `/organizer/dashboard` (Event Management Dashboard - story-1.16-event-management-dashboard.md)
     - `SPEAKER` → `/speaker/dashboard` (Speaker Dashboard)
     - `PARTNER` → `/partner/dashboard` (Partner Analytics Dashboard - story-6.1-partner-analytics-dashboard.md)
     - `ATTENDEE` → `/attendee/dashboard` (Personal Attendee Dashboard - story-5.2-personal-dashboard.md)
   - Navigation Type: Full page redirect after 3-second success screen
   - Context Passed: User object with verified email status, JWT tokens
   - Validation: 6-digit code must be valid

2. **[Go to Dashboard] Button** → Navigate to Role-Based Dashboard (Immediate)
   - Target: Same as above, based on role
   - Navigation Type: Immediate redirect (skip countdown)
   - Context Passed: User object, tokens
   - Validation: None (only shown after successful verification)

3. **[Resend Verification Email] Link** → Refresh Page State
   - Target: Same screen, refreshed state
   - Navigation Type: In-page action (no navigation)
   - Context Passed: None
   - Side Effects: New verification email sent

4. **[Contact Support] Link** → Navigate to Support Page
   - Target: `/support` or `mailto:support@batbern.ch`
   - Navigation Type: Full page or open email client
   - Context Passed: None
   - Validation: None

### Secondary Navigation (Data Interactions)

None - this screen focuses on verification action only.

### Event-Driven Navigation

1. **Successful Verification Event** → Navigate to Role Dashboard
   - Trigger: Successful response from Cognito `confirmSignUp`
   - Target: Role-based dashboard
   - Context: User profile with verified status, authentication tokens
   - Side Effects:
     - User status in Cognito updated to "CONFIRMED"
     - User can now log in
     - Analytics event tracked
     - Welcome email sent (optional)

2. **Email Link Auto-Verification** → Navigate to Dashboard After Processing
   - Trigger: Page load with `code` and `email` URL parameters
   - Target: Auto-verify, then redirect to dashboard
   - Context: Code and email from URL
   - Behavior: Show loading screen → verify → show success → redirect

3. **Auto-Submit on Complete Code** → Trigger Verification
   - Trigger: All 6 digits entered in code input boxes
   - Target: Call verification API automatically
   - Behavior: No need to click [Verify Email] button

### Error States & Redirects

1. **Invalid Code Error** → Show Error Message (Stay on Screen)
   - Trigger: "CodeMismatchException" from Cognito
   - Action: Display error message, highlight inputs, clear code
   - Message: "Invalid code. Please check and try again."
   - User Action: Re-enter correct code

2. **Expired Code Error** → Show Error + Suggest Resend
   - Trigger: "ExpiredCodeException" from Cognito
   - Action: Display error message with resend suggestion
   - Message: "This code has expired. Click 'Resend' to get a new one."
   - User Action: Click [Resend Verification Email]

3. **Too Many Attempts Error** → Show Error + Disable Submit
   - Trigger: "LimitExceededException" from Cognito
   - Action: Display error, disable button with cooldown
   - Message: "Too many failed attempts. Please wait 15 minutes or request a new code."
   - User Action: Wait or request new code

4. **Network Error** → Show Error + Retry
   - Trigger: Network timeout or connection error
   - Action: Display error banner
   - Message: "Connection error. Please check your internet and try again."
   - User Action: Click [Verify Email] to retry

5. **User Already Verified** → Redirect to Login
   - Trigger: User navigates to verification screen but already verified
   - Action: Display message "Your email is already verified" + redirect
   - Message: "Your email is already verified. Redirecting to sign in..."
   - User Action: Auto-redirected to Login screen

## Responsive Design Considerations

### Mobile Layout Changes

**Screen Width < 768px (Mobile):**
- Code input boxes maintain 44x44px touch target size
- Reduce spacing between input boxes for mobile
- Buttons expand to full width
- Information box text size reduced
- Success screen maintains centered layout
- Auto-redirect countdown more prominent

### Tablet Layout Changes

**Screen Width 768px - 1024px (Tablet):**
- Code input boxes slightly larger (48x48px)
- Maintain horizontal code entry layout
- Adequate padding around content

### Mobile-Specific Interactions

- **Auto-focus First Input**: Auto-focus first code input box on page load
- **Numeric Keyboard**: Use `inputmode="numeric"` to trigger numeric keyboard
- **Paste Support**: Detect paste event and distribute digits across boxes
- **Auto-Submit**: Submit verification when all 6 digits entered (no button tap needed)
- **Haptic Feedback**: Vibrate on error (invalid code) for mobile devices

## Accessibility Notes

- **Keyboard Navigation**: Full keyboard support for code input boxes with arrow key navigation
- **ARIA Labels**:
  - Code input container: `role="group"` with `aria-label="Verification code input"`
  - Each input box: `aria-label="Digit {1-6}"` and `aria-required="true"`
  - Success screen: `role="status"` with `aria-live="polite"`
- **Focus Management**:
  - Auto-focus first input on page load
  - Auto-advance focus on digit entry
  - Move focus to [Verify Email] button when all digits entered
- **Screen Reader Announcements**:
  - "Verification code sent to {email}"
  - "Digit 1 of 6", "Digit 2 of 6", etc. as user types
  - "Email verified successfully" on success
  - Error messages announced with `aria-live="assertive"`
- **Color Contrast**: 4.5:1 minimum for all text and input borders
- **Loading States**: "Verifying your email..." announced to screen readers

## State Management

### Local Component State

- **Verification State**:
  - `codeDigits: string[]` - Array of 6 digits (e.g., ["1", "2", "3", "4", "5", "6"])
  - `isVerifying: boolean` - Loading state during verification
  - `isVerified: boolean` - Success state after verification
  - `error: string | null` - Current error message
  - `resendCooldown: number` - Countdown timer for resend button (seconds)
  - `redirectCountdown: number` - Auto-redirect countdown (3 seconds)

- **UI State**:
  - `currentInputIndex: number` - Currently focused input box (0-5)
  - `showSuccess: boolean` - Toggle success screen view

### Global State (Zustand Store)

Update auth store after successful verification:

```typescript
interface AuthState {
  user: User | null;
  isEmailVerified: boolean;
  updateEmailVerified: (verified: boolean) => void;
}
```

### Server State (React Query)

- **Verification Mutation**:
  - Mutation Key: `['auth', 'verifyEmail']`
  - Mutator: `POST /api/v1/auth/verify-email` or Cognito `confirmSignUp`
  - On Success: Set `isVerified = true`, start redirect countdown
  - On Error: Display error message, clear code inputs
  - Retry: 1 time for network errors only

- **Resend Verification Mutation**:
  - Mutation Key: `['auth', 'resendVerification']`
  - Mutator: `POST /api/v1/auth/resend-verification` or Cognito `resendConfirmationCode`
  - On Success: Show toast "Email sent again", start cooldown
  - On Error: Display error message
  - Retry: 0 times (user can manually retry)

### Real-Time Updates

None required for verification screen.

## Form Validation Rules

### Field-Level Validations

- **Code Digits (each box)**:
  - Required: Yes (all 6 digits required)
  - Format: Single numeric digit (0-9) per box
  - Length: Exactly 1 character per box
  - Pattern: `/^[0-9]$/`
  - Validation Timing: On each keystroke (real-time)

### Form-Level Validations

- **Complete Code**: All 6 digits must be entered before submission
- **Auto-Submit**: When all 6 boxes filled, auto-submit verification
- **Button State**: [Verify Email] button disabled until all 6 digits entered

## Edge Cases & Error Handling

- **Empty State**: Page loads with 6 empty input boxes, focus on first box
- **Loading State - Auto-Verification** (from email link):
  - Loading screen: "🔄 Verifying..."
  - Progress bar animation
  - Auto-submit verification with code from URL
- **Loading State - Manual Submission**:
  - [Verify Email] button disabled with spinner
  - Input boxes disabled during verification
- **Success State**:
  - Success screen: "✅ Email Verified!"
  - Welcome message
  - [Go to Dashboard] button enabled
  - Auto-redirect countdown: "Redirecting in 3... 2... 1..."
- **Error State - Invalid Code**:
  - Error message above inputs: "Invalid code. Please check and try again."
  - All input boxes outlined in red
  - Code inputs cleared
  - Focus returns to first input box
- **Error State - Expired Code**:
  - Error message: "This code has expired. Click 'Resend' to get a new one."
  - Highlight [Resend Verification Email] link
  - Clear code inputs
- **Error State - Too Many Attempts**:
  - Error message: "Too many failed attempts. Please wait 15 minutes or request a new code."
  - [Verify Email] button disabled
  - Show countdown timer if cooldown active
- **Already Verified**: If user already verified email and navigates to this page:
  - Show message: "Your email is already verified."
  - Auto-redirect to Login or Dashboard
- **Paste Handling**:
  - User pastes full 6-digit code (e.g., "123456")
  - System detects paste event
  - System distributes digits across 6 input boxes
  - System auto-submits verification
- **Backspace Handling**:
  - User presses backspace in empty box
  - Focus moves to previous box
  - Previous digit is cleared
- **Resend Cooldown**:
  - After resending, disable link for 60 seconds
  - Show countdown: "Resend available in 45 seconds"
  - Re-enable link when cooldown expires
- **Email Not Found** (rare edge case):
  - User manually navigates to page without registration
  - Show error: "No pending verification found. Please register an account first."
  - Provide link to Account Creation screen

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation | Sally (UX Expert) |
| 2025-10-04 | 1.1 | Added language selector (EN/DE per NFR4); Added bilingual email templates (German/English); Added i18n implementation details | Sally (UX Expert) |

## Review Notes

### Stakeholder Feedback

*To be completed after stakeholder review*

### Design Iterations

*To be completed as design evolves*

### Open Questions

All open questions have been resolved:

1. ✅ **Auto-Submit**: Should we auto-submit verification when all 6 digits are entered, or require explicit button click?
   - **DECISION: Auto-submit** for better UX, but keep button visible for manual submission option

2. ✅ **Multi-Language Support**: Should verification emails and UI be sent in German or English?
   - ✅ **DECISION: YES - Bilingual from MVP launch** (NFR4)
   - Verification emails sent in user's selected language during registration
   - Bilingual HTML email templates: German (de-CH) and English (en-US)
   - All UI text on verification screens bilingual from day 1

3. ✅ **Code Masking**: Should we mask the verification code digits (like password fields)?
   - **DECISION: No**, verification codes are temporary and not sensitive like passwords

4. ✅ **Resend Limit**: How many times can a user resend verification email within 24 hours?
   - **DECISION: Maximum 10 resends** per 24 hours to prevent spam

5. ✅ **Email Link vs Code Entry**: Should we prioritize email link verification and make code entry secondary?
   - **DECISION: Support both equally** - some users prefer manual code entry

6. ✅ **Redirect Delay**: How long should the auto-redirect countdown be after successful verification?
   - **DECISION: 3 seconds** - enough time to read success message, short enough not to be annoying

7. ✅ **Welcome Email**: Should we send a welcome email after successful verification?
   - **DECISION: Yes**, send welcome email with getting started guide (separate from verification email)
