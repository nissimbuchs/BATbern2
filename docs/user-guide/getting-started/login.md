# Login & Authentication

> Access your BATbern organizer account using AWS Cognito

<span class="feature-status implemented">Implemented</span>

## Overview

BATbern supports **two authentication flows** depending on your role:

| Role | Authentication Method | Entry Point |
|------|-----------------------|-------------|
| **Organizer / Admin** | Email + password (AWS Cognito) | Standard login page |
| **Partner** | Email + password (AWS Cognito) | Standard login page (redirects to Partner Portal) |
| **Speaker** | Magic link (JWT email link) | Invitation email → auto-login |
| **Attendee** | Email + password (AWS Cognito) | Event registration page |

All Cognito flows (login, password reset, email verification) are fully implemented.

## Logging In

### Standard Login Flow

<div class="step" data-step="1">

**Navigate to the Login Page**

Visit the BATbern application:
- **Local**: http://localhost:3000
- **Staging**: https://staging.batbern.ch
- **Production**: https://www.batbern.ch

The login screen will appear automatically if you're not authenticated.
</div>

<div class="step" data-step="2">

**Enter Your Credentials**

Enter your:
- Email address (e.g., `organizer@batbern.ch`)
- Password (must meet complexity requirements)

</div>

<div class="step" data-step="3">

**Submit**

Click the "Sign In" button. You'll be authenticated and redirected to the organizer dashboard.
</div>

### First-Time Login

If this is your first time logging in, you may need to:

1. **Verify your email** - Check your inbox for a verification code
2. **Complete your profile** - Add your name and preferences
3. **Accept terms** - Review and accept the platform terms of use

### Partner Login

Partners log in using the same email/password flow as organizers. After authentication, the platform detects the PARTNER role and redirects automatically to the **Partner Portal** (analytics, topic voting, meeting coordination) instead of the organizer dashboard.

> **Note**: Partners see only their own company's data. If you are expecting organizer-level access, contact an administrator to verify your role.

---

## Speaker Authentication: Magic Link

<span class="feature-status implemented">Implemented</span>

Speakers authenticate using a **magic link** — a secure, time-limited URL emailed to them as part of the invitation process. No account creation or password is required.

### How It Works

<div class="step" data-step="1">

**Organizer Sends Invitation**

The organizer sends a speaker invitation from the event's Speaker Outreach tab. BATbern emails the speaker with a unique magic link.
</div>

<div class="step" data-step="2">

**Speaker Clicks the Link**

The speaker clicks the link in the email. No password entry required — the link itself is the authentication credential.
</div>

<div class="step" data-step="3">

**Auto-Login & Portal Access**

The speaker is automatically logged in and redirected to the Speaker Portal, where they can accept/decline the invitation and submit presentation materials.
</div>

### Magic Link Properties

| Property | Value |
|----------|-------|
| **Format** | JWT (RS256-signed) |
| **Session duration** | 30 days |
| **Reusable** | Yes — same link works throughout the 30-day window |
| **Scope** | Speaker's own events only |
| **Cookie** | HTTP-only, secure |

### Troubleshooting Magic Links

**Link expired?**
- Magic links are valid for 30 days from invitation
- Ask the organizer to resend the invitation to generate a fresh link

**Link not working?**
- Ensure you're clicking the link from the original invitation email (not a forwarded copy — links are personalized)
- Try opening in a private/incognito browser window
- Contact the organizer at info@berner-architekten-treffen.ch

**Received a new invitation but old link still works?**
- Both links remain valid until their respective 30-day windows expire

---

## Forgot Password

<span class="feature-status implemented">Implemented</span>

If you've forgotten your password, follow the password reset flow:

<div class="step" data-step="1">

**Click "Forgot Password?"**

On the login screen, click the "Forgot Password?" link below the password field.
</div>

<div class="step" data-step="2">

**Enter Your Email**

Enter the email address associated with your BATbern account.
</div>

<div class="step" data-step="3">

**Check Your Email**

You'll receive an email with a verification code (valid for 1 hour).
</div>

<div class="step" data-step="4">

**Enter Verification Code**

Enter the 6-digit code from the email.
</div>

<div class="step" data-step="5">

**Set New Password**

Create a new password meeting these requirements:
- Minimum 8 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one number (0-9)
- At least one special character (!@#$%^&*)

</div>

<div class="step" data-step="6">

**Log In**

Return to the login page and sign in with your new password.
</div>

### Password Reset Troubleshooting

**Code expired?**
- Verification codes expire after 1 hour
- Request a new code by starting the forgot password flow again

**Email not received?**
- Check your spam/junk folder
- Verify you entered the correct email address
- Contact support if issue persists: info@berner-architekten-treffen.ch

**Password requirements not met?**
- Ensure your password includes all required character types
- Avoid common passwords or dictionary words
- Don't reuse recent passwords

## Session Management

### Session Duration

Your BATbern session:
- **Lasts**: 1 hour of inactivity
- **Refreshes**: Automatically with activity
- **Expires**: After 24 hours regardless of activity

### Staying Logged In

To avoid frequent re-authentication:
- ✅ Keep your browser tab open
- ✅ Interact with the platform regularly
- ✅ Save your work frequently (auto-saves where available)

### Logging Out

To log out manually:

1. Click your profile avatar in the top-right corner
2. Select "Logout" from the dropdown menu
3. You'll be redirected to the login screen

**Tip**: Always log out when using shared computers.

## Multi-Factor Authentication (MFA)

<span class="feature-status planned">Planned</span>

Multi-factor authentication will be available in a future release, providing an additional layer of security beyond password authentication.

## Security Best Practices

### Password Management

✅ **Do**:
- Use a unique password for BATbern
- Use a password manager (e.g., 1Password, LastPass)
- Change your password if you suspect compromise
- Use the full allowed length (8+ characters)

❌ **Don't**:
- Share your password with others
- Use the same password as other services
- Write down your password
- Use easily guessable passwords (e.g., "Password123!")

### Account Security

- 🔒 **Monitor account activity** - Report suspicious logins immediately
- 🔒 **Use secure networks** - Avoid public Wi-Fi for sensitive operations
- 🔒 **Keep software updated** - Use the latest browser version
- 🔒 **Log out on shared devices** - Always log out on public computers

## Troubleshooting

### "Invalid username or password"

This error means:
- Your email or password is incorrect
- Your account may be disabled
- You may need to complete email verification

**Solution**: Use the "Forgot Password?" flow to reset your credentials, or contact support.

### "User is not confirmed"

This error means you haven't verified your email address.

**Solution**:
1. Check your email for the verification link
2. Click the link to verify your account
3. Try logging in again

### "Password attempts exceeded"

This error means you've made too many failed login attempts.

**Solution**: Wait 15 minutes before trying again, or use the "Forgot Password?" flow.

### Session Expired

Your session expired due to inactivity.

**Solution**: Simply log in again. Your work should be auto-saved (where applicable).

## Related Topics

- [Dashboard Navigation →](dashboard.md) - What you see after logging in
- [User Management →](../entity-management/users.md) - Managing user accounts and roles
- [Speaker Portal →](../speaker-portal/README.md) - Speaker magic link authentication and self-service
- [Partner Portal →](../partner-portal/README.md) - Partner login and portal capabilities
- [Troubleshooting Authentication →](../troubleshooting/authentication.md) - Detailed troubleshooting guide

## API Access (Advanced)

<span class="feature-status implemented">Implemented</span>

Developers and automation tools can authenticate programmatically:

```bash
# Get authentication token for API access
./scripts/auth/get-token.sh staging your-email@example.com your-password

# Token is saved to: /tmp/batbern-auth-token.txt
# Use in API requests: Authorization: Bearer <token>
```

See the [API Documentation](../../api/) for details on programmatic access.
