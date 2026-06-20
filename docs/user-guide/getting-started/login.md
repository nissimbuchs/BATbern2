# Login & Authentication

> Access your BATbern account using AWS Cognito email/password or "Continue with Google"

<span class="feature-status implemented">Implemented</span>

> **Last Updated**: 2026-06-13

## Overview

BATbern uses a **single unified login** for all roles. Every account authenticates through the same screen, and the platform routes you to the right experience based on your role(s). There is no longer a separate login per role.

You can sign in with either of two methods:

| Method | How It Works | Availability |
|--------|--------------|--------------|
| **Email + password (AWS Cognito)** | Enter your BATbern email and password on the standard login screen | <span class="feature-status implemented">Implemented</span> |
| **Continue with Google (SSO)** | Federated login via `auth.batbern.ch` using your Google account | <span class="feature-status implemented">Implemented</span> |

Both methods work for **all roles** — Organizer, Speaker, Partner, Attendee, and Admin. The role(s) attached to your account determine what you see after login, not which method you used to sign in.

> **Apple / generic OIDC SSO** is the only authentication method **not yet available** — it is planned but not released. <span class="feature-status planned">Planned</span>

All Cognito flows (login, password reset, email verification) and Google SSO are fully implemented and live.

### One Session for All Your Roles

If your account holds more than one role (for example you are both an **Organizer** and a **Speaker**), you log in **once** and get a **single session**. The navigation groups the features for each of your roles under section dividers — you no longer log out and back in to switch "hats". See [Dashboard Navigation](dashboard.md) for how the grouped navigation looks.

## Logging In

### Standard Login Flow

<div class="step" data-step="1">

**Navigate to the Login Page**

Visit the BATbern application:
- **Local**: http://localhost:3000
- **Staging**: https://www.batbern.ch
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

Click the "Sign In" button. You'll be authenticated and routed to the experience for your role — the organizer dashboard, the Partner Portal, the Speaker Portal, or the public attendee experience.
</div>

### First-Time Login

If this is your first time logging in, you may need to:

1. **Verify your email** - Check your inbox for a verification code
2. **Complete your profile** - Add your name and preferences
3. **Accept terms** - Review and accept the platform terms of use

---

## Continue with Google (SSO)

<span class="feature-status implemented">Implemented</span>

Instead of an email/password, you can sign in with your Google account. This federated login is live for all roles and is provided through BATbern's hosted identity domain `auth.batbern.ch`.

### How It Works

<div class="step" data-step="1">

**Click "Continue with Google"**

On the login screen, click the **Continue with Google** button. You'll be redirected to `auth.batbern.ch`, which hands off to Google's sign-in page.
</div>

<div class="step" data-step="2">

**Authenticate with Google**

Sign in with your Google account (or pick an already-signed-in account). Google returns you to BATbern.
</div>

<div class="step" data-step="3">

**Account Linking**

If a BATbern account already exists with the same email address, the Google identity is **transparently linked** to it — you keep your existing roles and data. If no account exists, one is provisioned automatically (just-in-time) with the default **Attendee** role.
</div>

<div class="step" data-step="4">

**Terms-of-Service Consent (first federated login only)**

The first time you sign in with Google, you're shown a short **onboarding completion** step: accept the Terms of Service, optionally confirm your company, and choose your newsletter preference. This consent gate appears only once.
</div>

<div class="step" data-step="5">

**Avatar Import & Access**

Your **Google profile picture is imported** as your BATbern avatar, and you're routed to the experience for your role. On subsequent logins, "Continue with Google" signs you straight in with no extra steps.
</div>

> **Note**: SSO is governed by a runtime kill-switch. In the rare event it is temporarily disabled, the email/password method remains fully available.

---

## Partner Login

Partners log in through the same unified screen — email/password **or** "Continue with Google". After authentication, the platform detects the PARTNER role and routes to the **Partner Portal** (analytics, topic voting, meeting coordination).

> **Note**: Partners see only their own company's data. If you are expecting organizer-level access, contact an administrator to verify your role.

---

## Speaker Login

<span class="feature-status implemented">Implemented</span>

Speakers log in through the **same unified screen** as everyone else — email/password **or** "Continue with Google". The earlier email "magic link" auto-login was removed during the unified speaker workflow refactor (Epic 11); speakers now have a normal **AWS Cognito** account.

A speaker's Cognito account is provisioned by the organizer workflow when the speaker reaches the `READY` state. The speaker then signs in (or uses "Continue with Google" if the email matches their Google account) and lands in the **Speaker Portal**, where they can accept/decline the invitation and submit presentation materials.

> **Migrating from magic links?** If you previously used an emailed magic link, that flow no longer exists. Use your email/password or "Continue with Google" on the standard login screen. If you don't yet have a password, use **Forgot Password** to set one, or sign in with Google.

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
- Contact support if issue persists: info@batbern.ch

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
- [Speaker Portal →](../speaker-portal/README.md) - Speaker Cognito authentication and self-service
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
