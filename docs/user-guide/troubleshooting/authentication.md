# Authentication Troubleshooting

## Overview

This guide covers solutions to login, password, session, and access control issues. The BATbern platform uses AWS Cognito for authentication, providing enterprise-grade security with multi-factor options.

## Quick Diagnosis

Use this flowchart to identify your issue:

```
Can you access the login page?
│
├─ NO → [Network/Browser Issue](#cant-access-login-page)
│
└─ YES → Do you know your password?
    │
    ├─ NO → [Reset Password](#forgot-password)
    │
    └─ YES → Does login fail with error?
        │
        ├─ "Invalid username or password" → [Invalid Credentials](#invalid-credentials)
        ├─ "Account locked" → [Account Locked](#account-locked)
        ├─ "Session expired" → [Session Expired](#session-expired)
        └─ Other error → See [Error Messages](#error-messages)
```

---

## Login Failures

### Invalid Credentials

**Symptom**: Error message "Invalid username or password" or "Incorrect login credentials"

**Possible Causes**:
1. Typo in email or password
2. Wrong account (personal vs. work email)
3. Password recently changed (using old password)
4. Caps Lock enabled (password is case-sensitive)

**Solutions**:

**Step 1: Verify Email Address**
```
1. Check for typos (common: .com vs .ch, gmail vs gmial)
2. Verify you're using work email (not personal)
3. Try both email formats if unsure:
   - john.doe@company.ch
   - johndoe@company.ch
```

**Step 2: Check Password**
```
1. Turn off Caps Lock
2. Copy-paste password from password manager (avoids typos)
3. Toggle "Show Password" checkbox to verify characters
4. Try last known password if recently changed
```

**Step 3: Reset Password**
```
If still failing after verification:
1. Click "Forgot Password?" link on login page
2. Follow email instructions (check spam folder)
3. Create new password meeting requirements:
   - Minimum 8 characters
   - At least 1 uppercase letter
   - At least 1 lowercase letter
   - At least 1 number
   - At least 1 special character (!@#$%^&*)
```

**Prevention**:
- Use password manager (1Password, LastPass, Bitwarden)
- Save correct email address in password manager notes
- Enable "Remember Me" for trusted devices (30-day sessions)

---

### Forgot Password

**Symptom**: Don't remember password, need to reset

**Solution**: Password Reset Flow

**Step 1: Request Reset**
```
1. Go to https://app.batbern.ch/login
2. Click "Forgot Password?" link below login form
3. Enter your email address (the one you use to log in)
4. Click "Send Reset Code"
```

**Step 2: Check Email**
```
1. Check inbox for "BATbern Password Reset" email
2. Email arrives within 5 minutes (if delayed, check spam folder)
3. Add noreply@batbern.ch to contacts (prevents future spam filtering)
4. Note: Reset code expires in 15 minutes
```

**Step 3: Create New Password**
```
1. Copy 6-digit code from email
2. Return to reset page (or click link in email)
3. Enter verification code
4. Create new password meeting requirements:
   - Minimum 8 characters
   - Mix of uppercase, lowercase, number, special char
   - Cannot match last 5 passwords
5. Confirm password (type again)
6. Click "Reset Password"
```

**Step 4: Log In**
```
1. Automatic redirect to login page
2. Enter email and NEW password
3. Success! You're logged in
```

**Troubleshooting Reset Issues**:

| Problem | Cause | Solution |
|---------|-------|----------|
| "Email not found" | Account doesn't exist with that email | Verify email, contact admin if new user |
| "Code expired" | Waited >15 minutes | Request new code (can do unlimited times) |
| "Invalid code" | Typo in code entry | Copy-paste code directly from email |
| "Code already used" | Already reset with this code | Request new code if need to reset again |
| Email not arriving | Spam filter or typo in email | Check spam, verify email address |

---

### Account Locked

**Symptom**: Error message "Your account has been locked due to too many failed login attempts"

**Cause**: Security measure after 5 consecutive failed login attempts within 15 minutes

**Solution**:

**Option 1: Wait (Automatic Unlock)**
```
Account automatically unlocks after 30 minutes
- No action required
- Failed attempt counter resets
- Try logging in again after 30 minutes
```

**Option 2: Unlock via Email (Faster)**
```
1. Check email for "Account Locked - Action Required" message
2. Click "Unlock My Account" link in email
3. Complete verification (may require answering security question)
4. Account unlocked immediately
5. Reset password if you don't remember it
```

**Option 3: Contact Admin (Immediate)**
```
If urgent and can't wait:
1. Email support@batbern.ch with subject "Account Unlock Request"
2. Include your name, email, and reason for urgency
3. Admin can manually unlock within business hours (response within 2-4 hours)
```

**Prevention**:
- Use password manager to avoid typos
- Don't share passwords (prevents others from locking your account)
- Enable "Remember Me" on trusted devices
- Reset password immediately if uncertain

---

## Session Issues

### Session Expired

**Symptom**: Error "Your session has expired. Please log in again." or automatic redirect to login page

**Cause**: Security tokens expire after period of inactivity or absolute time limit

**Session Duration**:
- **Active session**: 8 hours from login (with activity)
- **Idle timeout**: 2 hours of no activity
- **"Remember Me"**: 30 days (refreshes on each login)

**Solutions**:

**Immediate Fix**:
```
1. Click "OK" on expiration message (auto-redirects to login)
2. Log in again with your credentials
3. Return to previous page (browser history maintained)
```

**Prevent Future Expirations**:
```
1. Enable "Remember Me" at login
   - Extends session to 30 days
   - Safe on personal/trusted devices only

2. Keep browser tab open and active
   - Session refreshes with activity
   - Activity = clicks, form edits, page navigation

3. Use multiple browser tabs
   - Session shared across tabs
   - Any tab activity refreshes all tabs

4. Set calendar reminder
   - Reminder at 1.5 hours to click something
   - Prevents idle timeout
```

**Work in Progress Protection**:
```
If working on long forms:
1. Platform auto-saves draft every 2 minutes
2. On session expiry + re-login, draft auto-restores
3. Manual save: Click "Save Draft" button (top-right)
4. Drafts retained for 7 days
```

---

### Stuck on "Logging In..." (Infinite Spinner)

**Symptom**: Login form submitted, spinner shows, but never completes

**Possible Causes**:
1. Network timeout (slow connection or firewall blocking)
2. Browser cookie/storage issues
3. AWS Cognito service disruption (rare)
4. Corporate proxy interfering

**Solutions**:

**Step 1: Check Network**
```
1. Verify internet connection (open google.com in new tab)
2. Check status page: https://status.batbern.ch
3. Try different network (mobile hotspot vs WiFi)
```

**Step 2: Browser Cleanup**
```
1. Open browser console (F12 → Console tab)
2. Look for errors (red text)
3. Clear browser cache/cookies:
   - Settings → Privacy → Clear Browsing Data
   - Select "Cookies" and "Cached images"
   - Time range: "Last 24 hours"
4. Hard refresh: Ctrl+Shift+R (Cmd+Shift+R on Mac)
5. Try login again
```

**Step 3: Try Different Browser**
```
1. Open incognito/private window in same browser
2. If works → Browser extension issue
3. If still fails → Try different browser (Chrome recommended)
4. If works in other browser → Clear first browser completely
```

**Step 4: Bypass Proxy (Corporate Networks)**
```
If on corporate network:
1. Contact IT to whitelist these domains:
   - app.batbern.ch
   - cognito-idp.eu-central-1.amazonaws.com
   - *.amazoncognito.com
2. Try VPN or mobile hotspot as workaround
```

**If Still Failing**:
```
Contact support with:
- Browser console screenshot (F12 → Console)
- Network tab screenshot (F12 → Network)
- Browser name and version
- Network type (corporate, home, mobile)
```

---

## Access Control Issues

### "Access Denied" or "Permission Error"

**Symptom**: Error message "You do not have permission to access this resource" or "Access denied"

**Possible Causes**:
1. Insufficient role permissions (wrong role assigned)
2. Trying to access another organizer's private event
3. Feature not enabled for your organization
4. Account not yet approved by admin

**Solutions**:

**Verify Your Role**:
```
1. Go to Settings → Profile
2. Check "Role" field:
   - ORGANIZER → Full access to assigned events
   - ADMIN → Full access to all events + user management
   - SPEAKER → Limited access to own materials only
   - ATTENDEE → Read-only access to published events
3. If wrong role → Contact admin to update
```

**Check Feature Permissions**:
```
Some features restricted by role:
- Create Events → ORGANIZER or ADMIN only
- Manage Users → ADMIN only
- Delete Entities → ADMIN only
- Workflow Advancement → ORGANIZER (assigned to event) or ADMIN

If you need higher permissions:
1. Email admin with justification
2. Include specific feature you need access to
3. Admin will review and update role if appropriate
```

**Event-Specific Access**:
```
If error on specific event:
1. Check if you're assigned organizer for that event
2. Go to Events → [Event] → Settings → Organizers
3. Verify your name in organizer list
4. If not listed → Contact admin to be added
```

---

### Multi-Factor Authentication (MFA) Issues `[PLANNED]`

**Symptom**: Can't complete MFA setup or MFA code not working

> **Note**: MFA is planned for future release. Currently not required.

**When Available**:
- Optional for all users, required for ADMIN role
- Supports authenticator apps (Google Authenticator, Authy)
- SMS backup codes available
- Recovery codes for lost device scenarios

---

## Error Messages Reference

### Common Error Codes

| Error Code | Message | Cause | Solution |
|------------|---------|-------|----------|
| `AUTH_001` | "Invalid username or password" | Wrong credentials | Verify email/password, reset if needed |
| `AUTH_002` | "Account locked" | Too many failed attempts | Wait 30 min or use email unlock |
| `AUTH_003` | "Session expired" | Inactivity timeout | Log in again |
| `AUTH_004` | "Access denied" | Insufficient permissions | Verify role with admin |
| `AUTH_005` | "Email not verified" | Account email not confirmed | Check inbox for verification email |
| `AUTH_006` | "Password does not meet requirements" | Weak password on reset | Use stronger password (uppercase, number, special char) |
| `AUTH_007` | "Account not found" | Email not registered | Contact admin to create account |
| `AUTH_008` | "Too many requests" | Rate limiting (>10 attempts/min) | Wait 5 minutes, then retry |

### AWS Cognito Error Messages

| AWS Error | Plain English | What to Do |
|-----------|---------------|------------|
| `UserNotFoundException` | Account doesn't exist | Verify email or contact admin |
| `NotAuthorizedException` | Wrong password or account disabled | Reset password or contact admin |
| `UserNotConfirmedException` | Email not verified | Check inbox for verification email |
| `LimitExceededException` | Too many attempts | Wait 15 minutes |
| `InvalidPasswordException` | Password too weak | Use stronger password |
| `ExpiredCodeException` | Reset code expired | Request new reset code |

---

## Browser Console Errors

If experiencing authentication issues, browser console may show helpful errors.

**How to Access Console**:
```
Chrome/Firefox/Edge:
- Press F12
- Click "Console" tab
- Look for red error messages

Safari (macOS):
- Enable Developer menu: Safari → Preferences → Advanced → Show Develop menu
- Develop → Show JavaScript Console
```

**Common Console Errors**:

**"NetworkError: Failed to fetch"**
```
Cause: Network connectivity or CORS issue
Solution:
1. Check internet connection
2. Disable VPN temporarily
3. Contact IT to whitelist app.batbern.ch
```

**"CORS policy: No 'Access-Control-Allow-Origin' header"**
```
Cause: Browser extension blocking requests or outdated cache
Solution:
1. Disable browser extensions
2. Clear cache and hard refresh
3. Try incognito mode
```

**"401 Unauthorized"**
```
Cause: Session expired or invalid auth token
Solution:
1. Log out completely (Settings → Log Out)
2. Clear cookies for app.batbern.ch
3. Log in again
```

---

## Prevention Best Practices

### Password Management

1. **Use a Password Manager**
   - Recommended: 1Password, Bitwarden, LastPass
   - Auto-generates strong passwords
   - Auto-fills login forms (reduces typos)
   - Syncs across devices

2. **Create Strong Passwords**
   - Minimum 12 characters (longer is better)
   - Mix of uppercase, lowercase, numbers, symbols
   - Avoid common words or personal info
   - Unique per site (don't reuse BATbern password)

3. **Secure Password Storage**
   - Never write down passwords
   - Don't share passwords via email/chat
   - Don't store in browser notes or documents
   - Use password manager's secure notes feature

### Session Management

1. **"Remember Me" Usage**
   - Enable only on trusted, personal devices
   - Never on shared or public computers
   - Extends session to 30 days (more convenient)

2. **Logging Out**
   - Always log out on shared computers
   - Use "Log Out Everywhere" if suspect unauthorized access
   - Settings → Security → Log Out All Sessions

3. **Session Security**
   - Don't leave logged-in sessions unattended
   - Lock computer when away (Win+L or Cmd+Control+Q)
   - Close browser completely at end of day

### Account Security

1. **Email Verification**
   - Verify email immediately on account creation
   - Add noreply@batbern.ch to contacts (prevents spam filtering)
   - Update email if changing work address

2. **Regular Password Updates**
   - Change password every 6-12 months
   - Change immediately if suspect compromise
   - Never reuse old passwords

3. **Monitor Account Activity**
   - Review "Recent Activity" log (Settings → Security)
   - Unrecognized logins → change password immediately
   - Report suspicious activity to support@batbern.ch

---

## Getting Additional Help

### Before Contacting Support

1. Try solutions in this guide
2. Clear browser cache and try incognito mode
3. Test on different browser
4. Gather error details (screenshots, console errors)

### Contact Support

**Email**: support@batbern.ch

**Include**:
- Your email address (the one you use to log in)
- Error message screenshot
- Browser console screenshot (F12 → Console)
- Steps you've already tried
- Urgency level (if critical, say so)

**Response Times**:
- Critical (can't access at all): 2-4 hours
- High priority: 8-12 hours
- Standard: 1-2 business days

---

## Related Resources

- **[Getting Started → Login](../getting-started/login.md)** - Initial authentication setup
- **[User Management](../entity-management/users.md)** - Roles and permissions
- **[Security Settings](../getting-started/dashboard.md#security-settings)** - Managing your account security
- **[Troubleshooting Overview](README.md)** - Other common issues

---

**Next**: Explore [File Upload Troubleshooting →](uploads.md)
