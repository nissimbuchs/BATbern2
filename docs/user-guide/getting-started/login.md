# Login & Authentication

> Access your BATbern organizer account using AWS Cognito

<span class="feature-status implemented">Implemented</span>

## Overview

BATbern uses **AWS Cognito** for secure authentication. All authentication flows are implemented, including login, password reset, email verification, and account creation.

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
