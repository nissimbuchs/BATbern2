# AWS SES Configuration for Password Reset Emails

**Story 1.2.2** - Email template configuration for forgot password flow

## Overview

AWS Simple Email Service (SES) is used to send bilingual password reset emails to users. This guide covers:

- SES account setup and verification
- Email template creation (German & English)
- Domain verification
- DKIM configuration
- Bounce and complaint handling
- Testing and monitoring

## Prerequisites

- AWS CLI configured with `batbern-mgmt` profile
- SES access in AWS account
- Verified sender email or domain
- AWS region: `eu-central-1` (Frankfurt)

## SES Account Setup

### 1. Verify Sender Email Address

```bash
# Verify batbern.ch domain (recommended)
aws ses verify-domain-identity \
  --domain batbern.ch \
  --region eu-central-1 \
  --profile batbern-mgmt

# Or verify specific email address
aws ses verify-email-identity \
  --email-address noreply@batbern.ch \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Note:** You'll receive a verification email. Click the link to complete verification.

### 2. Request Production Access

SES starts in **Sandbox mode** with limitations:
- Can only send to verified email addresses
- Maximum 200 emails per day
- Maximum 1 email per second

**To request production access:**

```bash
# Create support case via AWS Console
# Service: SES Sending Limits Increase
# Limit: Desired Daily Sending Quota
# New limit: 50,000 (adjust based on needs)
```

Or use AWS Console:
1. Navigate to SES > Account Dashboard
2. Click "Request production access"
3. Fill out the form explaining your use case
4. Submit request (usually approved within 24 hours)

### 3. Configure DKIM (Email Authentication)

DKIM improves email deliverability and prevents spoofing.

```bash
# Generate DKIM tokens
aws ses verify-domain-dkim \
  --domain batbern.ch \
  --region eu-central-1 \
  --profile batbern-mgmt
```

This returns 3 CNAME records. Add them to your DNS:

```
Name: token1._domainkey.batbern.ch
Type: CNAME
Value: token1.dkim.amazonses.com

Name: token2._domainkey.batbern.ch
Type: CNAME
Value: token2.dkim.amazonses.com

Name: token3._domainkey.batbern.ch
Type: CNAME
Value: token3.dkim.amazonses.com
```

## Email Template Creation

### German Template (PasswordResetDE)

**Create template file: `templates/password-reset-de.json`**

```json
{
  "Template": {
    "TemplateName": "PasswordResetDE",
    "SubjectPart": "BATbern Passwort zurücksetzen",
    "HtmlPart": "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body{font-family:Arial,sans-serif;line-height:1.6;color:#333;max-width:600px;margin:0 auto;padding:20px}.header{background-color:#1976d2;color:white;padding:20px;text-align:center}.content{padding:30px;background-color:#f9f9f9}.button{display:inline-block;padding:12px 30px;background-color:#1976d2;color:white;text-decoration:none;border-radius:4px;margin:20px 0}.footer{padding:20px;text-align:center;font-size:12px;color:#666}</style></head><body><div class=\"header\"><h1>BATbern</h1></div><div class=\"content\"><h2>Passwort zurücksetzen</h2><p>Hallo,</p><p>Sie haben eine Zurücksetzung Ihres Passworts für Ihr BATbern-Konto angefordert.</p><p>Klicken Sie auf den untenstehenden Link, um Ihr Passwort zurückzusetzen:</p><p style=\"text-align:center\"><a href=\"{{resetLink}}\" class=\"button\">Passwort zurücksetzen</a></p><p><strong>Dieser Link läuft in 1 Stunde ab.</strong></p><p>Falls Sie diese Anfrage nicht gestellt haben, ignorieren Sie diese E-Mail bitte.</p><p>Mit freundlichen Grüßen,<br>BATbern Team</p></div><div class=\"footer\"><p>Diese E-Mail wurde automatisch gesendet. Bitte antworten Sie nicht auf diese E-Mail.</p><p>© 2025 BATbern. Alle Rechte vorbehalten.</p></div></body></html>",
    "TextPart": "Passwort zurücksetzen\n\nHallo,\n\nSie haben eine Zurücksetzung Ihres Passworts für Ihr BATbern-Konto angefordert.\n\nKlicken Sie auf den untenstehenden Link, um Ihr Passwort zurückzusetzen:\n{{resetLink}}\n\nDieser Link läuft in 1 Stunde ab.\n\nFalls Sie diese Anfrage nicht gestellt haben, ignorieren Sie diese E-Mail bitte.\n\nMit freundlichen Grüßen,\nBATbern Team\n\n---\nDiese E-Mail wurde automatisch gesendet.\n© 2025 BATbern"
  }
}
```

**Upload template:**

```bash
aws ses create-template \
  --cli-input-json file://templates/password-reset-de.json \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### English Template (PasswordResetEN)

**Create template file: `templates/password-reset-en.json`**

```json
{
  "Template": {
    "TemplateName": "PasswordResetEN",
    "SubjectPart": "Reset Your BATbern Password",
    "HtmlPart": "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body{font-family:Arial,sans-serif;line-height:1.6;color:#333;max-width:600px;margin:0 auto;padding:20px}.header{background-color:#1976d2;color:white;padding:20px;text-align:center}.content{padding:30px;background-color:#f9f9f9}.button{display:inline-block;padding:12px 30px;background-color:#1976d2;color:white;text-decoration:none;border-radius:4px;margin:20px 0}.footer{padding:20px;text-align:center;font-size:12px;color:#666}</style></head><body><div class=\"header\"><h1>BATbern</h1></div><div class=\"content\"><h2>Reset Your Password</h2><p>Hello,</p><p>You requested to reset your password for your BATbern account.</p><p>Click the link below to reset your password:</p><p style=\"text-align:center\"><a href=\"{{resetLink}}\" class=\"button\">Reset Password</a></p><p><strong>This link will expire in 1 hour.</strong></p><p>If you didn't request this, please ignore this email.</p><p>Best regards,<br>BATbern Team</p></div><div class=\"footer\"><p>This email was sent automatically. Please do not reply to this email.</p><p>© 2025 BATbern. All rights reserved.</p></div></body></html>",
    "TextPart": "Reset Your Password\n\nHello,\n\nYou requested to reset your password for your BATbern account.\n\nClick the link below to reset your password:\n{{resetLink}}\n\nThis link will expire in 1 hour.\n\nIf you didn't request this, please ignore this email.\n\nBest regards,\nBATbern Team\n\n---\nThis email was sent automatically.\n© 2025 BATbern"
  }
}
```

**Upload template:**

```bash
aws ses create-template \
  --cli-input-json file://templates/password-reset-en.json \
  --region eu-central-1 \
  --profile batbern-mgmt
```

## Verify Templates

```bash
# List all templates
aws ses list-templates \
  --region eu-central-1 \
  --profile batbern-mgmt

# Get specific template
aws ses get-template \
  --template-name PasswordResetDE \
  --region eu-central-1 \
  --profile batbern-mgmt
```

## Test Email Sending

### Test German Template

```bash
aws ses send-templated-email \
  --source "BATbern <noreply@batbern.ch>" \
  --destination "ToAddresses=test@example.com" \
  --template PasswordResetDE \
  --template-data '{
    "resetLink": "https://app.batbern.ch/auth/reset-password?code=TEST123&email=test@example.com&lang=de"
  }' \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### Test English Template

```bash
aws ses send-templated-email \
  --source "BATbern <noreply@batbern.ch>" \
  --destination "ToAddresses=test@example.com" \
  --template PasswordResetEN \
  --template-data '{
    "resetLink": "https://app.batbern.ch/auth/reset-password?code=TEST123&email=test@example.com&lang=en"
  }' \
  --region eu-central-1 \
  --profile batbern-mgmt
```

## Update Template

If you need to modify a template:

```bash
# Delete existing template
aws ses delete-template \
  --template-name PasswordResetDE \
  --region eu-central-1 \
  --profile batbern-mgmt

# Create updated template
aws ses create-template \
  --cli-input-json file://templates/password-reset-de-updated.json \
  --region eu-central-1 \
  --profile batbern-mgmt
```

## Bounce and Complaint Handling

### Configure SNS Topics

```bash
# Create SNS topic for bounces
aws sns create-topic \
  --name ses-bounces \
  --region eu-central-1 \
  --profile batbern-mgmt

# Create SNS topic for complaints
aws sns create-topic \
  --name ses-complaints \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### Configure SES Notifications

```bash
# Set bounce notification
aws ses set-identity-notification-topic \
  --identity batbern.ch \
  --notification-type Bounce \
  --sns-topic arn:aws:sns:eu-central-1:ACCOUNT_ID:ses-bounces \
  --region eu-central-1 \
  --profile batbern-mgmt

# Set complaint notification
aws ses set-identity-notification-topic \
  --identity batbern.ch \
  --notification-type Complaint \
  --sns-topic arn:aws:sns:eu-central-1:ACCOUNT_ID:ses-complaints \
  --region eu-central-1 \
  --profile batbern-mgmt
```

## Monitoring

### Check Send Statistics

```bash
# Get send statistics for last 2 weeks
aws ses get-send-statistics \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### Check Send Quota

```bash
# Check current send quota
aws ses get-send-quota \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### CloudWatch Metrics

Monitor these metrics in CloudWatch:
- `Sends` - Total emails sent
- `Deliveries` - Successfully delivered emails
- `Bounces` - Bounced emails
- `Complaints` - Spam complaints
- `Rejects` - Rejected emails

## Infrastructure as Code (CDK)

The SES templates are deployed via AWS CDK:

**File:** `infrastructure/lib/stacks/ses-stack.ts`

```typescript
import * as ses from 'aws-cdk-lib/aws-ses';

// German template
new ses.CfnTemplate(this, 'PasswordResetDE', {
  template: {
    templateName: 'PasswordResetDE',
    subjectPart: 'BATbern Passwort zurücksetzen',
    htmlPart: '...',
    textPart: '...',
  },
});

// English template
new ses.CfnTemplate(this, 'PasswordResetEN', {
  template: {
    templateName: 'PasswordResetEN',
    subjectPart: 'Reset Your BATbern Password',
    htmlPart: '...',
    textPart: '...',
  },
});
```

**Deploy:**

```bash
cd infrastructure
npm install
npx cdk deploy SESStack --profile batbern-mgmt
```

## Environment Configuration

### Backend Configuration

**File:** `api-gateway/src/main/resources/application.yml`

```yaml
aws:
  ses:
    region: eu-central-1
    sender-email: noreply@batbern.ch
    sender-name: BATbern
    templates:
      password-reset-de: PasswordResetDE
      password-reset-en: PasswordResetEN
```

### Environment Variables

```bash
# Set in deployment environment
AWS_REGION=eu-central-1
SES_SENDER_EMAIL=noreply@batbern.ch
SES_SENDER_NAME=BATbern
```

## Troubleshooting

### Email Not Received

1. **Check SES send statistics:**
   ```bash
   aws ses get-send-statistics --region eu-central-1 --profile batbern-mgmt
   ```

2. **Check email in spam folder**

3. **Verify sender email:**
   ```bash
   aws ses get-identity-verification-attributes \
     --identities noreply@batbern.ch \
     --region eu-central-1 \
     --profile batbern-mgmt
   ```

4. **Check bounce/complaint notifications in SNS**

### Template Not Found Error

```bash
# Verify template exists
aws ses get-template \
  --template-name PasswordResetDE \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### Send Quota Exceeded

```bash
# Check current quota
aws ses get-send-quota \
  --region eu-central-1 \
  --profile batbern-mgmt

# Request quota increase via AWS Support
```

## Best Practices

1. **Use Verified Domain** - Better deliverability than verified email
2. **Enable DKIM** - Improves email authentication
3. **Monitor Bounce Rate** - Keep below 5%
4. **Handle Complaints** - Unsubscribe mechanism
5. **Use SPF Records** - Add to DNS for better deliverability
6. **Test Regularly** - Verify templates work in all email clients
7. **Keep Templates Updated** - Match brand guidelines
8. **Monitor Metrics** - Track delivery success rates

## Security Considerations

- **Never expose SES credentials** in frontend code
- **Use IAM roles** for EC2/ECS instances (not access keys)
- **Implement rate limiting** to prevent abuse
- **Validate email addresses** before sending
- **Log all email sends** for audit trail
- **Use HTTPS** for reset links
- **Expire tokens** after 1 hour
- **Monitor for unusual activity** (spike in sends)

## Cost Optimization

SES Pricing (as of 2025):
- First 62,000 emails/month: **FREE** (if sent from EC2)
- After that: **$0.10 per 1,000 emails**
- Data transfer: Standard AWS rates

**Estimated monthly cost for BATbern:**
- 1,000 password reset emails: **FREE**
- 10,000 password reset emails: **FREE**
- 100,000 password reset emails: **~$4**

## Related Documentation

- [Forgot Password Flow](./forgot-password-flow.md)
- [Troubleshooting Guide](./troubleshooting-forgot-password.md)
- [API Documentation](../api/auth-endpoints.openapi.yml)
