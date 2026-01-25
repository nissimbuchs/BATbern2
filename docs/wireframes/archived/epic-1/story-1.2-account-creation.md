# Account Creation Wireframe

## Header Information

### Story
Epic 1, Story 1.2 - API Gateway & Authentication Service

### Screen
Account Creation (User Registration)

### User Role
Public (unauthenticated)

### Related FR
- **FR1**: Role-based authentication with distinct interfaces for organizers, speakers, partners, and attendees
- **FR22**: Event organizers shall manage user roles with capabilities to promote users to speaker or organizer roles (all new accounts created as ATTENDEE)
- **NFR3**: Integration with external services (AWS Cognito for user registration, AWS SES for verification emails)
- **NFR4**: Platform shall support multi-language content (German, English) with internationalization framework

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                       │
│  ← Back to Login      BATbern Event Platform       🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │  👤 Create Account      │                     │
│                      │                         │                     │
│                      │  Join the BATbern       │                     │
│                      │  community              │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│  ┌─── STEP 1 of 2: Personal Information ───────────────────────┐    │
│  │                                                               │    │
│  │  Full Name *                                                 │    │
│  │  [_____________________________]                             │    │
│  │                                                               │    │
│  │  Email Address *                                             │    │
│  │  [_____________________________]                             │    │
│  │                                                               │    │
│  │  Password *                                                  │    │
│  │  [_____________________________] 👁                          │    │
│  │  • At least 8 characters                                     │    │
│  │  • Include uppercase and lowercase                           │    │
│  │  • Include at least one number                               │    │
│  │                                                               │    │
│  │  Confirm Password *                                          │    │
│  │  [_____________________________] 👁                          │    │
│  │                                                               │    │
│  └───────────────────────────────────────────────────────────────┘    │
│                                                                       │
│                                                                       │
│                                  [Continue to Confirmation →]        │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘

┌────────────────────── STEP 2: CONFIRMATION ──────────────────────────┐
│                                                                       │
│  ← Back                BATbern Event Platform       🌐 EN ▼ | DE     │
│                                                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                      ┌─────────────────────────┐                     │
│                      │                         │                     │
│                      │  👤 Create Account      │                     │
│                      │                         │                     │
│                      │  Join the BATbern       │                     │
│                      │  community              │                     │
│                      │                         │                     │
│                      └─────────────────────────┘                     │
│                                                                       │
│  ┌─── STEP 2 of 2: Review & Confirm ────────────────────────────┐   │
│  │                                                                │   │
│  │  Review your information:                                     │   │
│  │                                                                │   │
│  │  Name:           John Doe                                     │   │
│  │  Email:          john.doe@example.com                         │   │
│  │                                                     [Edit]     │   │
│  │                                                                │   │
│  │  ┌──────────────────────────────────────────────┐            │   │
│  │  │ ☐ I agree to the Terms of Service and        │            │   │
│  │  │   Privacy Policy                              │            │   │
│  │  └──────────────────────────────────────────────┘            │   │
│  │                                                                │   │
│  │  ┌──────────────────────────────────────────────┐            │   │
│  │  │ ☐ I would like to receive event updates and  │            │   │
│  │  │   newsletters (optional)                      │            │   │
│  │  └──────────────────────────────────────────────┘            │   │
│  │                                                                │   │
│  └────────────────────────────────────────────────────────────────┘   │
│                                                                       │
│                                                                       │
│                          [← Back]    [Create Account]                │
│                                                                       │
│                                                                       │
│           ┌─────────────────────────────────────────────┐           │
│           │ ℹ️ You will receive a verification email at  │           │
│           │   john.doe@example.com                      │           │
│           └─────────────────────────────────────────────┘           │
│                                                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

### Step 1: Personal Information
- **Language Selector Dropdown**: Switch between English and German (🌐 EN ▼ | DE)
- **Full Name Input**: Text field for user's full name
- **Email Address Input**: Email field with format validation
- **Password Input**: Password field with strength indicator and show/hide toggle
- **Confirm Password Input**: Password confirmation field with show/hide toggle
- **Password Requirements Display**: Real-time validation feedback for password strength
- **Continue Button**: Navigate to Step 2 after validation

### Step 2: Review & Confirmation
- **Information Summary**: Display of entered data (name, email) with Edit link
- **Terms & Conditions Checkbox**: Required agreement to T&C and privacy policy
- **Newsletter Opt-in Checkbox**: Optional subscription to updates
- **Back Button**: Return to Step 1
- **Create Account Button**: Submit registration form

### Common Elements
- **← Back to Login Link**: Navigate back to login screen (both steps)
- **Step Progress Indicator**: Shows current step (1 of 2, 2 of 2)
- **Loading Spinner**: Shown during account creation API call
- **Error Messages**: Field-specific and form-level error messages

## Functional Requirements Met

- **FR1**: Role-based authentication - all self-registered accounts created as ATTENDEE role
- **FR22**: Role promotion by organizers - users cannot select their own role during registration; only organizers can promote users to Speaker, Partner, or Organizer roles post-registration via Story 1.20 workflows
- **NFR3**: Integration with AWS Cognito for user registration and AWS SES for email verification
- **NFR4**: ✅ **Multi-language support from MVP launch** - German (de-CH) and English (en-US) with language selector, react-i18next framework, bilingual email templates
- **Security**: Secure password requirements following AWS Cognito security policies
- **Email Verification**: AWS Cognito triggers email verification flow after registration (bilingual emails)
- **User Experience**: Simple 2-step wizard for streamlined registration
- **Data Collection**: Collects minimum required information (name, email, password, language preference, newsletter opt-in)

## User Interactions

### Primary Flow: Successful Account Creation

**Step 1: Personal Information**
1. User navigates to Account Creation from Login screen
2. User selects language preference if needed (defaults to browser locale)
3. User enters full name
4. User enters email address
5. System validates email format in real-time
6. User enters password
7. System displays password strength indicator
8. User enters password confirmation
9. System validates password match in real-time
10. User clicks [Continue to Confirmation →]
11. System validates all fields
12. System transitions to Step 2

**Step 2: Review & Confirmation**
13. User reviews entered information (name, email)
14. User checks "I agree to Terms of Service" checkbox
15. (Optional) User checks newsletter opt-in checkbox
16. User clicks [Create Account] button
17. System submits registration to AWS Cognito with:
    - User attributes: name, email, language preference
    - Custom attribute: `custom:role` = "ATTENDEE" (automatically assigned by backend)
18. Cognito creates ATTENDEE user account
19. Cognito sends verification email via AWS SES (in user's selected language)
20. System navigates to Email Verification screen (story-1.2-email-verification.md)
21. System displays success message: "Account created! Please check your email."

**Post-Registration (Future Role Promotion via Story 1.20):**
22. User verifies email and logs in → Attendee Dashboard
23. If user needs different role (Speaker/Partner/Organizer):
    - Organizer promotes user via Role Management Panel
    - User receives role change notification
    - Next login redirects to new role-appropriate dashboard

### Secondary Flow: Edit Information
1. From Step 2 (Review & Confirmation), user clicks [Edit] link
2. System returns to Step 1 with pre-filled values
3. User makes changes
4. User clicks [Continue to Confirmation →] to return to Step 2

### Secondary Flow: Back Navigation
1. User clicks [← Back] button at any step
2. System returns to previous step with values preserved
3. User can continue forward again or make changes

### Secondary Flow: Return to Login
1. User clicks [← Back to Login] link
2. System navigates to Login Screen (story-1.2-login-screen.md)
3. User data is cleared (not saved)

### Error Flow: Email Already Exists
1. User completes all steps and clicks [Create Account]
2. AWS Cognito returns "UsernameExistsException"
3. System displays error banner: "An account with this email already exists. [Sign In]"
4. User can click [Sign In] to go to Login screen

### Error Flow: Password Requirements Not Met
1. User enters weak password in Step 1
2. User clicks [Continue]
3. System highlights password field in red
4. System displays error: "Password must meet all requirements"
5. User updates password and retries

### Error Flow: Terms Not Accepted
1. User reaches Step 2 (Review & Confirmation)
2. User clicks [Create Account] without checking T&C checkbox
3. System displays error: "You must agree to the Terms of Service to continue"
4. T&C checkbox outlined in red
5. User checks checkbox and retries

### Error Flow: Network Error
1. User clicks [Create Account]
2. Network request fails
3. System displays error banner: "Connection error. Please try again."
4. User can retry immediately

## Technical Notes

### AWS Cognito User Registration
- **Cognito API**: Use `signUp` method from AWS Cognito SDK
- **Custom Attributes**: Store language preference in Cognito user attributes (`custom:language` = "de" or "en"); `custom:role` automatically set to "ATTENDEE" by backend
- **Password Policy**: Enforce Cognito password policy (min 8 chars, uppercase, lowercase, number)
- **Email Verification**: Cognito automatically sends verification email with confirmation code **in user's selected language** (AWS SES bilingual templates configured from day 1)
- **User Status**: New users have "UNCONFIRMED" status until email verified
- **Default Role**: All self-registered users created as ATTENDEE; role promotion handled via Story 1.20
- **Language Preference**: User's selected language (de/en) stored in Cognito `custom:language` attribute and used for all future communications

### Frontend Framework
- **React 18.2+** with TypeScript
- **Material-UI** for form components and wizard stepper
- **React Hook Form** for multi-step form state management
- **Zustand** for wizard state persistence across steps
- **React Query** for API calls
- **react-i18next** for internationalization (German/English from day 1)
- **i18next-browser-languagedetector** for automatic locale detection

### Multi-Step Form Management
- **State Persistence**: Store form data in Zustand store to preserve values across steps
- **Validation**: Field-level validation on blur, form-level validation on Continue/Submit
- **Step Transitions**: Smooth transitions with validation gates
- **Browser History**: Each step updates URL (e.g., `/auth/register?step=2`) for bookmarking

### Email Verification Flow
- AWS Cognito sends verification email with 6-digit code
- User clicks link in email or enters code manually
- See story-1.2-email-verification.md for detailed flow

### Security Measures
- **Password Strength**: Client-side and server-side password validation
- **Email Validation**: Format validation + existence check (bounce handling)
- **CSRF Protection**: CSRF tokens on registration form
- **Rate Limiting**: Maximum 5 registration attempts per IP per hour
- **Spam Prevention**: Honeypot field (hidden from users) to catch bots
- **Input Sanitization**: XSS prevention on all text inputs

### Accessibility
- **WCAG 2.1 AA Compliance**: Full keyboard navigation
- **ARIA Labels**: All form inputs properly labeled
- **Step Navigation**: Announce step changes to screen readers
- **Progress Indicator**: `aria-current="step"` on current step
- **Error Announcements**: `aria-live` regions for validation errors

### Internationalization (i18n)

**FROM MVP LAUNCH (Day 1):**
- **Language Support**: German (de-CH) and English (en-US) with language selector in header
- **Default Language**: German (de-CH) with fallback to browser locale detection
- **UI Text**: All screen text, labels, buttons, error messages translated from day 1
- **Step Indicators**: "Step 1 of 2" / "Schritt 1 von 2"
- **Password Requirements**: Translated requirement text (German/English)
- **Terms & Conditions**: Links to language-specific T&C documents (de/en)
- **Validation Messages**: All field-level and form-level errors localized
- **Language Persistence**: Selected language stored in localStorage and passed to backend (Cognito attribute)
- **Email Language**: Verification email sent in user's selected language (AWS SES templates: de/en)
- **Translation Keys**: `auth.register.step1.title`, `auth.register.fullNameLabel`, etc.
- **Dynamic Switching**: Language change applies immediately to all visible text without page reload
- **Framework**: react-i18next with i18next-browser-languagedetector
- **Translation Files**: `/locales/de/auth.json`, `/locales/en/auth.json`

## API Requirements

### Initial Page Load APIs

None - this screen does not require data fetching on initial load.

### User Action APIs

1. **POST /cognito/signUp** (AWS Cognito SDK method)
   - Triggered by: [Create Account] button click in Step 2
   - Payload:
     ```typescript
     {
       ClientId: "<cognito_client_id>",
       Username: "john.doe@example.com",
       Password: "SecurePass123",
       UserAttributes: [
         { Name: "email", Value: "john.doe@example.com" },
         { Name: "name", Value: "John Doe" },
         { Name: "custom:role", Value: "ATTENDEE" },  // Always ATTENDEE for self-registration
         { Name: "custom:language", Value: "en" },    // User's selected language (en|de)
         { Name: "custom:newsletter_optin", Value: "true" }
       ]
     }
     ```
   - Response:
     ```json
     {
       "UserConfirmed": false,
       "CodeDeliveryDetails": {
         "Destination": "j***@example.com",
         "DeliveryMedium": "EMAIL",
         "AttributeName": "email"
       },
       "UserSub": "user-uuid-1234"
     }
     ```
   - Used for: Creating ATTENDEE user account in AWS Cognito
   - Note: Backend automatically sets role to ATTENDEE; role promotions handled via Story 1.20

2. **POST /api/v1/auth/register** (Optional wrapper endpoint)
   - Triggered by: [Create Account] button click
   - Payload:
     ```json
     {
       "fullName": "John Doe",
       "email": "john.doe@example.com",
       "password": "SecurePass123",
       "language": "en",
       "agreedToTerms": true,
       "newsletterOptIn": true
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "userId": "user-uuid-1234",
       "email": "john.doe@example.com",
       "role": "ATTENDEE",
       "language": "en",
       "requiresVerification": true,
       "message": "Account created successfully. Please check your email for verification."
     }
     ```
   - Used for: Simplified registration with backend handling Cognito integration
   - Note: Backend always creates ATTENDEE accounts; no role parameter accepted

3. **GET /api/v1/auth/check-email-availability** (Email uniqueness check)
   - Triggered by: Email field on blur in Step 1
   - Query params: `email=john.doe@example.com`
   - Response:
     ```json
     {
       "available": true
     }
     ```
   - Used for: Real-time feedback if email already registered (optional, nice-to-have)

## Navigation Map

### Primary Navigation Actions

1. **[← Back to Login] Link** → Navigate to `Login Screen`
   - Target: `/auth/login` (story-1.2-login-screen.md)
   - Navigation Type: Full page navigation
   - Context Passed: None
   - Validation: Confirmation dialog if user has entered data

2. **[Continue to Confirmation →] Button** → Transition to Step 2
   - Target: Same component, Step 2 view
   - Navigation Type: In-page state transition
   - URL Update: `/auth/register?step=2`
   - Context Passed: Form data (name, email, password, language) stored in state
   - Validation: All Step 1 fields must be valid

4. **[← Back] Button** → Return to Previous Step
   - Target: Previous step in wizard
   - Navigation Type: In-page state transition
   - Context Passed: Form data preserved
   - Validation: None required

5. **[Create Account] Button** → Navigate to `Email Verification Screen`
   - Target: `/auth/verify-email` (story-1.2-email-verification.md)
   - Navigation Type: Full page navigation
   - Context Passed: Email address for verification
   - Validation: T&C checkbox must be checked, all data must be valid

6. **[Edit] Link** → Return to Step 1
   - Target: Step 1 with pre-filled data
   - Navigation Type: In-page state transition
   - Context Passed: Current form values
   - Validation: None required

### Secondary Navigation (Data Interactions)

None - this is a form wizard with no data-driven navigation.

### Event-Driven Navigation

1. **Successful Account Creation Event** → Navigate to Email Verification
   - Trigger: Successful response from Cognito `signUp` API
   - Target: `/auth/verify-email?email={email}` (story-1.2-email-verification.md)
   - Context: Email address and user ID
   - Side Effects:
     - Verification email sent to user
     - User account created in Cognito with "UNCONFIRMED" status
     - Analytics event tracked

2. **Browser Back Button** → Navigate to Previous Step or Login
   - Trigger: User presses browser back button
   - Behavior: Respect browser history (URL param `step={n}`)
   - Data: Form data preserved in state

### Error States & Redirects

1. **Email Already Exists Error** → Show Error Banner + Link to Login
   - Trigger: "UsernameExistsException" from Cognito
   - Action: Display error banner with Sign In link
   - Message: "An account with this email already exists. [Sign In]"
   - User Action: Click [Sign In] to navigate to Login screen

2. **Invalid Password Error** → Show Error at Password Field (Step 1)
   - Trigger: Client-side password validation fails
   - Action: Highlight password field, display error message
   - Message: "Password must meet all requirements"
   - User Action: Update password to meet requirements

3. **Terms Not Accepted Error** → Show Error at Checkbox (Step 3)
   - Trigger: Form submission without T&C checkbox checked
   - Action: Highlight checkbox, display error message
   - Message: "You must agree to the Terms of Service to continue"
   - User Action: Check T&C checkbox and retry

4. **Network Error** → Show Error Banner + Retry
   - Trigger: Network timeout or connection error
   - Action: Display error banner
   - Message: "Connection error. Please check your internet and try again."
   - User Action: Click [Create Account] again to retry

5. **Server Error** → Show Error Banner
   - Trigger: 500 Internal Server Error
   - Action: Display error banner
   - Message: "An unexpected error occurred. Please try again later."
   - User Action: Retry later or contact support

6. **Rate Limit Error** → Show Error Banner with Cooldown
   - Trigger: Too many registration attempts from same IP
   - Action: Display error banner with wait time
   - Message: "Too many registration attempts. Please wait 15 minutes and try again."
   - User Action: Wait for cooldown period

## Responsive Design Considerations

### Mobile Layout Changes

**Screen Width < 768px (Mobile):**
- Single-column layout with full-width form elements
- All inputs expand to full width minus padding
- Step progress indicator shown as "Step 1 of 3" text instead of visual stepper
- Role selection cards stack vertically
- Buttons stack vertically ([Back] above [Continue])
- Reduce padding and font sizes for mobile
- Increase touch targets to minimum 44x44px

### Tablet Layout Changes

**Screen Width 768px - 1024px (Tablet):**
- Form container max-width of 600px, centered
- Maintain horizontal layout for buttons
- Adequate padding around form
- Touch targets 44x44px minimum

### Mobile-Specific Interactions

- **Auto-focus**: Don't auto-focus first field on mobile (prevents keyboard pop-up)
- **Email Keyboard**: Use `type="email"` for email-optimized keyboard
- **Password Show/Hide**: Larger touch target for visibility toggle
- **Step Navigation**: Swipe gestures to move between steps (optional enhancement)
- **Form Submission**: Support "Enter" key on virtual keyboard

## Accessibility Notes

- **Keyboard Navigation**: Full keyboard support with logical tab order across all steps
- **ARIA Labels**:
  - Inputs: `aria-label` and `aria-required="true"` on required fields
  - Step indicator: `aria-current="step"` on active step
  - Role radio buttons: `aria-describedby` pointing to role descriptions
  - Form: `role="form"` and `aria-label="Account registration form"`
- **Focus Management**: Focus moves to first field of next step on Continue
- **Step Announcements**: Screen reader announces "Step 2 of 3: Select Your Role" on transition
- **Error Announcements**: `aria-live="polite"` for validation errors
- **Progress Indicator**: `<ol>` list with proper semantic HTML
- **Color Contrast**: 4.5:1 minimum for all text and UI elements
- **Loading States**: Button shows "Creating account..." with `aria-busy="true"`

## State Management

### Local Component State

- **Wizard State**:
  - `currentStep: 1 | 2` - Active step in wizard (2-step flow)
  - `formData: RegistrationFormData` - All form field values
  - `isSubmitting: boolean` - Loading state during registration
  - `errors: Record<string, string>` - Validation errors per field

- **Form Data**:
  ```typescript
  interface RegistrationFormData {
    fullName: string;
    email: string;
    password: string;
    confirmPassword: string;
    language: 'en' | 'de';  // User's selected language
    agreedToTerms: boolean;
    newsletterOptIn: boolean;
    // Note: role is NOT stored in form state
    // Backend automatically assigns role = "ATTENDEE"
  }
  ```

### Global State (Zustand Store)

Persist wizard data in case of accidental navigation away:

```typescript
interface RegistrationState {
  draftRegistration: RegistrationFormData | null;
  saveDraft: (data: RegistrationFormData) => void;
  clearDraft: () => void;
}
```

### Server State (React Query)

- **Registration Mutation**:
  - Mutation Key: `['auth', 'register']`
  - Mutator: `POST /api/v1/auth/register` or Cognito `signUp`
  - On Success: Navigate to email verification screen
  - On Error: Display error message based on error type
  - Retry: 1 time for network errors only

- **Email Availability Query** (Optional):
  - Query Key: `['auth', 'emailAvailable', email]`
  - Fetcher: `GET /api/v1/auth/check-email-availability`
  - Enabled: Only when email field has valid format
  - Stale Time: Infinity (emails don't change availability frequently)

### Real-Time Updates

None required for registration screen.

## Form Validation Rules

### Field-Level Validations

**Step 1: Personal Information**

- **Full Name**:
  - Required: Yes - "Full name is required"
  - Min Length: 2 characters - "Name must be at least 2 characters"
  - Max Length: 100 characters - "Name is too long"
  - Pattern: Letters, spaces, hyphens only - "Name contains invalid characters"
  - Validation Timing: On blur

- **Email Address**:
  - Required: Yes - "Email address is required"
  - Format: Valid email (regex: `/^[^\s@]+@[^\s@]+\.[^\s@]+$/`) - "Please enter a valid email"
  - Max Length: 255 characters - "Email is too long"
  - Uniqueness: Not already registered (optional check) - "Email already in use"
  - Validation Timing: On blur

- **Password**:
  - Required: Yes - "Password is required"
  - Min Length: 8 characters - "Password must be at least 8 characters"
  - Max Length: 128 characters - "Password is too long"
  - Uppercase: At least one - "Include at least one uppercase letter"
  - Lowercase: At least one - "Include at least one lowercase letter"
  - Number: At least one - "Include at least one number"
  - Special Character: Optional but recommended - "For stronger security, add a special character"
  - Validation Timing: Real-time strength indicator

- **Confirm Password**:
  - Required: Yes - "Please confirm your password"
  - Match: Must match Password field - "Passwords do not match"
  - Validation Timing: On blur and real-time after first blur

**Step 2: Review & Confirmation**

- **Terms & Conditions**:
  - Required: Yes - "You must agree to the Terms of Service"
  - Validation Timing: On Create Account button click

- **Newsletter Opt-in**:
  - Required: No (optional)

### Form-Level Validations

- **Step Transitions**: All fields in current step must be valid before proceeding to next step
- **Submit Validation**: All required fields across all steps must be valid before account creation
- **Password Strength**: Overall password strength must be "Medium" or higher
- **Email Format**: Final validation of email before submission

## Edge Cases & Error Handling

- **Empty State**: Step 1 loads with empty fields, no errors shown
- **Loading State - Step Transition**: Brief loading animation when transitioning between steps
- **Loading State - Account Creation**:
  - [Create Account] button disabled with spinner + "Creating account..." text
  - All form inputs disabled
  - Prevent navigation during submission
- **Success State**:
  - Success message: "Account created! Please check your email for verification."
  - Automatic redirect to Email Verification screen after 2 seconds
- **Error State - Email Exists**:
  - Error banner: "An account with this email already exists. [Sign In]"
  - Highlight email field in Step 1
  - Provide direct link to Login screen
- **Error State - Weak Password**:
  - Password field outlined in red
  - Display all unmet requirements
  - Password strength indicator shows "Weak" or "Too Weak"
- **Error State - Password Mismatch**:
  - Confirm Password field outlined in red
  - Error message: "Passwords do not match"
  - Clear Confirm Password field on error
- **Error State - Terms Not Accepted**:
  - T&C checkbox outlined in red
  - Error message: "You must agree to the Terms of Service"
  - [Create Account] button remains disabled
- **Browser Back/Forward**: Wizard state synchronized with URL parameters (step=1, step=2)
- **Page Refresh**: Show confirmation dialog "Are you sure? Your progress will be lost"
- **Session Timeout**: No session yet (unauthenticated), no timeout concerns
- **Duplicate Submission Prevention**: Disable submit button and form after first submission
- **Password Visibility Toggle**: Each password field has independent show/hide state

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation | Sally (UX Expert) |
| 2025-10-04 | 2.0 | MAJOR UPDATE per FR22: Removed Step 2 (Role Selection) - all accounts created as ATTENDEE; Changed to 2-step flow; Added language selector (EN/DE per NFR4); Updated API to remove role parameter and add language; Added i18n implementation details; Role promotion deferred to Story 1.20 | Sally (UX Expert) |

## Review Notes

### Stakeholder Feedback

*To be completed after stakeholder review*

### Design Iterations

*To be completed as design evolves*

### Open Questions

1. **Company Selection for Partners**: Should we add company selection in the registration flow, or defer it to post-verification profile setup?
   - Decision: Defer to profile setup after email verification - simpler registration flow

2. **Role Approval Workflow**: How should we notify users when Organizer role is approved?
   - Decision: Email notification + in-app notification when they log in

3. **Social Registration**: Should we support "Sign up with Google" or other OAuth providers?
   - Decision: Not in MVP - can be added as enhancement

4. **CAPTCHA**: Should we add reCAPTCHA to prevent automated bot registrations?
   - Decision: Yes, add reCAPTCHA in Step 3 before final submission (non-MVP)

5. **Multi-Language**: Should registration form support German language?
   - ✅ **DECISION: YES - German and English from MVP launch** (NFR4)
   - Language selector in registration form (Step 1)
   - All UI text, labels, validation messages, and email templates bilingual from day 1
   - Default: German (de-CH), with English (en-US) option

6. **Email Verification Timeout**: How long is the verification code valid?
   - Decision: 24 hours, consistent with Cognito default

7. **Password Strength Indicator**: Should we show a visual strength meter (weak/medium/strong)?
   - Decision: Yes, show color-coded strength indicator below password field
