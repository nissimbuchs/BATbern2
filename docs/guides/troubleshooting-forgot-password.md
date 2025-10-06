# Troubleshooting: Forgot Password Flow

Common issues and solutions for the password reset functionality

## Table of Contents

1. [Email Not Received](#email-not-received)
2. [Rate Limit Errors](#rate-limit-errors)
3. [Network Errors](#network-errors)
4. [Form Validation Issues](#form-validation-issues)
5. [Backend Errors](#backend-errors)
6. [AWS SES Issues](#aws-ses-issues)
7. [Cognito Issues](#cognito-issues)
8. [Testing Issues](#testing-issues)

---

## Email Not Received

### Problem
User submitted form but didn't receive password reset email.

### Possible Causes & Solutions

#### 1. Email in Spam Folder
**Solution:** Check spam/junk folder
- Gmail: Check "Spam" and "Promotions" tabs
- Outlook: Check "Junk Email" folder
- Mark as "Not Spam" if found

#### 2. Email Address Doesn't Exist
**Behavior:** No email sent (security feature - enumeration prevention)
**Solution:**
```bash
# Verify user exists in Cognito
aws cognito-idp admin-get-user \
  --user-pool-id eu-central-1_XXXXXX \
  --username user@example.com \
  --region eu-central-1 \
  --profile batbern-mgmt
```

#### 3. SES Sender Not Verified
**Check:**
```bash
aws ses get-identity-verification-attributes \
  --identities noreply@batbern.ch \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Solution:** Verify sender email or domain (see [AWS SES Configuration](./aws-ses-configuration.md))

#### 4. SES in Sandbox Mode
**Check:**
```bash
aws ses get-account-sending-enabled \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Solution:** Request production access via AWS Console

#### 5. Email Template Missing
**Check:**
```bash
aws ses get-template \
  --template-name PasswordResetDE \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Solution:** Create templates (see [AWS SES Configuration](./aws-ses-configuration.md))

#### 6. Email Bounced
**Check CloudWatch Logs:**
```bash
aws logs tail /aws/apigateway/batbern-dev \
  --follow \
  --filter-pattern "Password reset" \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Check SNS bounce notifications:**
- Navigate to AWS Console > SNS > Topics > ses-bounces

#### 7. High Bounce Rate
If bounce rate > 5%, SES may pause sending.

**Check send statistics:**
```bash
aws ses get-send-statistics \
  --region eu-central-1 \
  --profile batbern-mgmt | jq '.SendDataPoints[] | select(.Bounces > 0)'
```

**Solution:** Review bounced emails and remove invalid addresses

---

## Rate Limit Errors

### Problem
User sees "Too many requests" error message.

### Behavior
- Frontend displays countdown timer
- Submit button disabled
- Error message: "Too many requests. Please wait X seconds."

### Cause
User (or IP address) made 3+ password reset requests within 1 hour.

### Solutions

#### For Users
**Wait for rate limit to expire:**
- Rate limit: 3 requests per hour
- Combined limit for forgot-password + resend
- Countdown timer shows remaining time

**Workaround:**
- Wait 1 hour from first request
- Contact support if urgent

#### For Developers
**Check rate limit status:**
```java
// In RateLimitService.java
boolean allowed = rateLimitService.allowPasswordReset(email);
```

**Reset rate limit (dev only):**
```bash
# Restart API Gateway to clear in-memory cache
docker-compose restart api-gateway
```

**Increase rate limit (if needed):**
```java
// In RateLimitService.java
private static final int MAX_REQUESTS = 3;  // Increase if needed
private static final long WINDOW_MS = 60 * 60 * 1000;  // 1 hour
```

#### For DevOps
**Monitor rate limit violations:**
```bash
# Check CloudWatch logs for RATE_LIMIT_EXCEEDED
aws logs filter-log-events \
  --log-group-name /aws/apigateway/batbern-prod \
  --filter-pattern "RATE_LIMIT_EXCEEDED" \
  --start-time $(date -u -d '1 hour ago' +%s)000 \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Alert on high rate limit violations:**
- Create CloudWatch alarm if > 50 violations per hour
- May indicate brute force attack

---

## Network Errors

### Problem
Frontend displays "Connection error. Please try again."

### Possible Causes

#### 1. API Gateway Down
**Check:**
```bash
curl -X POST https://api.batbern.ch/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'
```

**Solution:** Check API Gateway logs and health endpoint

#### 2. CORS Issues
**Symptoms:**
- Network tab shows preflight OPTIONS request failed
- Console error: "CORS policy blocked"

**Solution:** Verify CORS configuration in API Gateway

#### 3. SSL/TLS Issues
**Symptoms:**
- "SSL handshake failed" error
- Certificate warnings

**Check certificate:**
```bash
openssl s_client -connect api.batbern.ch:443 -servername api.batbern.ch
```

#### 4. Client Offline
**Symptoms:**
- All API calls fail
- Browser shows "offline" indicator

**Solution:** User needs to check internet connection

#### 5. Firewall/Proxy Blocking
**Symptoms:**
- Works on mobile network but not company Wi-Fi
- Specific error codes (403, etc.)

**Solution:** Check firewall rules or proxy settings

---

## Form Validation Issues

### Problem 1: Submit Button Disabled
**Cause:** Email validation hasn't passed

**Solutions:**
- Email must be valid format (user@domain.com)
- Email must not exceed 255 characters
- Email field must not be empty
- Click outside email field to trigger validation

### Problem 2: "Invalid email" Error Persists
**Cause:** Email format validation regex mismatch

**Regex used:**
```typescript
/^[^\s@]+@[^\s@]+\.[^\s@]+$/
```

**Valid examples:**
- user@example.com ✅
- test.user@company.co.uk ✅
- name+tag@domain.com ✅

**Invalid examples:**
- user@domain (no TLD) ❌
- @domain.com (no local part) ❌
- user@.com (invalid domain) ❌
- user@domain. (trailing dot) ❌

### Problem 3: Form Not Submitting
**Check:**
- React Hook Form validation state
- Console errors
- Network tab for API call

**Debug:**
```typescript
// In ForgotPasswordForm.tsx
console.log('Form valid?', isValid);
console.log('Form errors:', errors);
console.log('Is loading?', isLoading);
```

---

## Backend Errors

### Error: "User pool client does not exist"
**Cause:** Cognito client ID not configured

**Solution:**
```yaml
# api-gateway/src/main/resources/application.yml
aws:
  cognito:
    userPoolClientId: ${COGNITO_CLIENT_ID}
    userPoolId: ${COGNITO_USER_POOL_ID}
```

### Error: "Access Denied" from Cognito
**Cause:** IAM role missing Cognito permissions

**Solution:**
```json
{
  "Effect": "Allow",
  "Action": [
    "cognito-idp:ForgotPassword",
    "cognito-idp:AdminGetUser"
  ],
  "Resource": "arn:aws:cognito-idp:eu-central-1:*:userpool/*"
}
```

### Error: "SES template not found"
**Cause:** Template doesn't exist in SES

**Check:**
```bash
aws ses list-templates --region eu-central-1 --profile batbern-mgmt
```

**Solution:** Create templates (see [AWS SES Configuration](./aws-ses-configuration.md))

### Error: 500 Internal Server Error
**Check backend logs:**
```bash
docker-compose logs -f api-gateway
```

**Common causes:**
- Exception in PasswordResetService
- Database connection issue
- Missing environment variables

---

## AWS SES Issues

### Problem: "Email address is not verified"
**Cause:** SES in sandbox mode, recipient not verified

**Solution:**
```bash
# Verify recipient email (sandbox mode)
aws ses verify-email-identity \
  --email-address test@example.com \
  --region eu-central-1 \
  --profile batbern-mgmt

# Or request production access (recommended)
```

### Problem: "MessageRejected: Email address is not verified"
**Cause:** Sender email not verified

**Check:**
```bash
aws ses get-identity-verification-attributes \
  --identities noreply@batbern.ch \
  --region eu-central-1 \
  --profile batbern-mgmt
```

**Solution:** Verify sender domain or email

### Problem: "Daily sending quota exceeded"
**Check quota:**
```bash
aws ses get-send-quota --region eu-central-1 --profile batbern-mgmt
```

**Output:**
```json
{
  "Max24HourSend": 200.0,
  "SentLast24Hours": 205.0,  // Exceeded!
  "MaxSendRate": 1.0
}
```

**Solution:** Request quota increase via AWS Support

---

## Cognito Issues

### Problem: "User not found" in Logs
**Behavior:** Normal - part of enumeration prevention

**Explanation:**
- Backend logs "USER_NOT_FOUND"
- Frontend shows success message (security feature)
- No email sent

**To verify user exists:**
```bash
aws cognito-idp admin-get-user \
  --user-pool-id eu-central-1_XXXXXX \
  --username user@example.com \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### Problem: "Invalid parameters: username"
**Cause:** Cognito expects username, not email

**Solution:** Configure Cognito to use email as username

### Problem: "LimitExceededException" from Cognito
**Cause:** Too many requests to Cognito API

**Solution:**
- Implement exponential backoff
- Use rate limiting (already implemented)

---

## Testing Issues

### E2E Tests Timing Out
**Cause:** MailHog not running

**Solution:**
```bash
# Start MailHog
brew install mailhog
mailhog

# Or with Docker
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# Verify MailHog is running
curl http://localhost:8025/api/v2/messages
```

### Tests Fail with "Template not found"
**Cause:** SES templates not deployed

**Solution:**
```bash
cd infrastructure
npx cdk deploy SESStack --profile batbern-mgmt
```

### Unit Tests Fail with AWS Mocking Issues
**Known Issue:** 6/15 backend tests passing (AWS SDK mocking issues)

**Workaround:**
- Mock AWS SDK calls properly
- Use Testcontainers for integration tests
- Focus on business logic tests

---

## Debugging Tips

### Enable Debug Logging

**Backend:**
```yaml
# application-dev.yml
logging:
  level:
    ch.batbern.gateway.auth: DEBUG
    software.amazon.awssdk: DEBUG
```

**Frontend:**
```typescript
// In useForgotPassword.ts
console.log('Mutation called with:', email);
console.log('Response:', response);
console.log('Error:', error);
```

### Check CloudWatch Logs

```bash
# API Gateway logs
aws logs tail /aws/apigateway/batbern-dev \
  --follow \
  --filter-pattern "forgot-password" \
  --region eu-central-1 \
  --profile batbern-mgmt

# Lambda logs (if using Lambda)
aws logs tail /aws/lambda/PasswordResetFunction \
  --follow \
  --region eu-central-1 \
  --profile batbern-mgmt
```

### Use curl for Testing

```bash
# Test forgot password endpoint
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -H "Accept-Language: de-CH" \
  -d '{"email":"test@batbern.ch"}' \
  -v

# Test with invalid email
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid"}' \
  -v

# Test rate limiting (run 4 times rapidly)
for i in {1..4}; do
  curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
    -H "Content-Type: application/json" \
    -d '{"email":"test@batbern.ch"}'
  echo "\nRequest $i"
done
```

---

## Getting Help

### Check Documentation
1. [Forgot Password Flow](./forgot-password-flow.md)
2. [AWS SES Configuration](./aws-ses-configuration.md)
3. [Rate Limiting Rules](./rate-limiting.md)
4. [API Documentation](../api/auth-endpoints.openapi.yml)

### Check Logs
- Frontend: Browser console
- Backend: `docker-compose logs -f api-gateway`
- AWS: CloudWatch Logs

### Report Issues
If you encounter an issue not covered here:

1. **Gather information:**
   - Error message
   - Steps to reproduce
   - Browser/environment details
   - Relevant logs

2. **Create GitHub issue:**
   - Repository: https://github.com/nisimbuchs/BATbern2
   - Use issue template
   - Tag with `bug` label

3. **Contact support:**
   - Email: support@batbern.ch
   - Include all gathered information

---

## Prevention Checklist

Before deploying:
- [ ] All email templates created and tested
- [ ] Sender email/domain verified
- [ ] SES out of sandbox mode (production)
- [ ] Rate limiting tested
- [ ] Error handling tested
- [ ] CloudWatch alarms configured
- [ ] Bounce/complaint handling configured
- [ ] Frontend error messages translated
- [ ] E2E tests passing
- [ ] Documentation updated
