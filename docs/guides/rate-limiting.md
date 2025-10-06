# Rate Limiting Rules

Security and abuse prevention through rate limiting

## Overview

Rate limiting prevents abuse of the password reset functionality by limiting the number of requests a user can make within a time window.

**Key Benefits:**
- Prevents brute force attacks
- Reduces email spam
- Protects infrastructure from abuse
- Improves system stability

## Forgot Password Rate Limits

### Rules

| Endpoint | Limit | Window | Scope |
|----------|-------|--------|-------|
| `/api/v1/auth/forgot-password` | 3 requests | 1 hour | Per email address |
| `/api/v1/auth/resend-reset-link` | 3 requests | 1 hour | Per email address (combined) |

**Important:** The limits are **combined** for both endpoints. If a user makes 2 forgot-password requests and 1 resend request, they've reached the limit.

### Implementation

**Backend:** In-memory sliding window algorithm

**Location:** `api-gateway/src/main/java/ch/batbern/gateway/auth/service/RateLimitService.java`

```java
@Service
public class RateLimitService {
    private static final int MAX_REQUESTS = 3;
    private static final long WINDOW_MS = 60 * 60 * 1000; // 1 hour

    // ConcurrentHashMap<email, List<Timestamp>>
    private final ConcurrentHashMap<String, List<Long>> requestTimestamps;

    public boolean allowPasswordReset(String email) {
        List<Long> timestamps = requestTimestamps.computeIfAbsent(
            email,
            k -> new CopyOnWriteArrayList<>()
        );

        // Remove expired timestamps
        long now = System.currentTimeMillis();
        timestamps.removeIf(ts -> now - ts > WINDOW_MS);

        // Check if under limit
        if (timestamps.size() >= MAX_REQUESTS) {
            return false;
        }

        // Add current timestamp
        timestamps.add(now);
        return true;
    }
}
```

### Response Format

**When rate limit NOT exceeded (allowed):**
```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "success": true,
  "message": "If an account exists with this email, you will receive a password reset link."
}
```

**When rate limit EXCEEDED (blocked):**
```json
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 3600
X-RateLimit-Limit: 3
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1696521600

{
  "success": false,
  "error": "Rate limit exceeded",
  "message": "Too many password reset requests. Please try again later.",
  "retryAfter": 3600
}
```

### Response Headers

| Header | Description | Example |
|--------|-------------|---------|
| `Retry-After` | Seconds until retry allowed | `3600` |
| `X-RateLimit-Limit` | Maximum requests allowed | `3` |
| `X-RateLimit-Remaining` | Requests remaining in window | `0` |
| `X-RateLimit-Reset` | Unix timestamp when limit resets | `1696521600` |

## Frontend Handling

### Countdown Timer

When rate limit is exceeded, the frontend displays a countdown timer:

```typescript
// ForgotPasswordForm.tsx
const [rateLimitCountdown, setRateLimitCountdown] = useState(0);

useEffect(() => {
  if (error?.type === 'rateLimitExceeded') {
    const retryAfter = error.retryAfter || 60;
    setRateLimitCountdown(retryAfter);
  }
}, [error]);

// Countdown every second
useEffect(() => {
  if (rateLimitCountdown > 0) {
    const timer = setTimeout(
      () => setRateLimitCountdown(rateLimitCountdown - 1),
      1000
    );
    return () => clearTimeout(timer);
  }
}, [rateLimitCountdown]);
```

### UI Behavior

**Submit button:**
- Disabled when `rateLimitCountdown > 0`
- Shows "Wait X seconds" text
- Enabled when countdown reaches 0

**Error message:**
- Displays: "Too many requests. Please wait X seconds."
- Updates every second
- Disappears when countdown reaches 0

## Monitoring

### Metrics to Track

**CloudWatch Metrics:**
- `PasswordResetRateLimitViolations` - Count of blocked requests
- `PasswordResetRequests` - Total requests (allowed + blocked)
- `PasswordResetRateLimitRatio` - Percentage of blocked requests

**Custom Metrics:**
```java
// In RateLimitService.java
if (timestamps.size() >= MAX_REQUESTS) {
    metricsService.incrementCounter("password_reset.rate_limit.violations");
    return false;
}
metricsService.incrementCounter("password_reset.requests");
```

### Alerts

**High Rate Limit Violations**
```yaml
Metric: PasswordResetRateLimitViolations
Threshold: > 50 violations per hour
Action: SNS notification to security team
Severity: WARNING
```

**Unusual Spike in Reset Requests**
```yaml
Metric: PasswordResetRequests
Threshold: > 100 requests per minute
Action: SNS notification to DevOps team
Severity: CRITICAL
```

### Logging

All rate limit violations are logged for security auditing:

```
WARN  [PasswordResetService] Rate limit exceeded for password reset: u***@example.com
INFO  [AuditLogger] Password reset attempt: email=u***@example.com, ip=192.168.1.1, status=RATE_LIMIT_EXCEEDED
```

## Adjusting Rate Limits

### Increase Limit (if needed)

**When to increase:**
- Legitimate users hitting limit frequently
- Support tickets about rate limiting
- Business requirement for more resets

**How to increase:**

```java
// In RateLimitService.java
private static final int MAX_REQUESTS = 5;  // Increased from 3
private static final long WINDOW_MS = 60 * 60 * 1000;  // Keep 1 hour
```

**Recommended limits:**
- **Development:** 10 requests per hour (for testing)
- **Staging:** 5 requests per hour
- **Production:** 3 requests per hour (current)

### Decrease Window (faster reset)

```java
// In RateLimitService.java
private static final int MAX_REQUESTS = 3;
private static final long WINDOW_MS = 30 * 60 * 1000;  // 30 minutes instead of 1 hour
```

### Per-IP Rate Limiting (future enhancement)

```java
public boolean allowPasswordReset(String email, String ipAddress) {
    // Rate limit per email (existing)
    boolean emailAllowed = checkEmailRateLimit(email);

    // Rate limit per IP (new)
    boolean ipAllowed = checkIpRateLimit(ipAddress);

    return emailAllowed && ipAllowed;
}
```

**Recommended IP rate limits:**
- 10 requests per IP per hour
- 50 requests per IP per day

## Rate Limit Bypass (Emergency)

### For Legitimate Users

**Option 1: Manual reset (DevOps)**
```bash
# Restart API Gateway to clear in-memory cache
docker-compose restart api-gateway

# Or restart specific pod in Kubernetes
kubectl rollout restart deployment api-gateway
```

**Option 2: Admin API (future)**
```bash
POST /api/v1/admin/rate-limit/reset
Authorization: Bearer {admin-token}

{
  "email": "user@example.com",
  "reason": "Support ticket #12345"
}
```

**Option 3: Support intervention**
- User contacts support
- Support admin resets password manually in Cognito
- User receives new temporary password

### For Testing

**Development environment:**
```java
// In RateLimitService.java (dev profile only)
@Value("${rate-limit.enabled:true}")
private boolean rateLimitEnabled;

public boolean allowPasswordReset(String email) {
    if (!rateLimitEnabled) {
        return true;  // Bypass for testing
    }
    // ... normal logic
}
```

**Configuration:**
```yaml
# application-dev.yml
rate-limit:
  enabled: false  # Disable for local testing
```

## Security Considerations

### Why Rate Limiting?

**Without rate limiting, attackers could:**
1. **Enumerate valid emails** - Send thousands of requests to discover valid accounts
2. **DoS attack** - Flood SES with emails, causing service degradation
3. **Spam users** - Annoy users with constant reset emails
4. **Increase costs** - Each SES email costs money ($0.10 per 1,000)

### Why 3 requests per hour?

**Rationale:**
- **Legitimate use case:** User typos email 2-3 times, or doesn't receive email and resends
- **Too restrictive:** 1-2 requests might block legitimate users
- **Too permissive:** 10+ requests enables abuse

**Industry standards:**
- GitHub: 5 requests per hour
- Google: 3 requests per hour
- Microsoft: 3 requests per hour
- **BATbern: 3 requests per hour** ✅

### Combined Limit Explanation

**Why combined limit for forgot-password + resend?**

Without combined limit, attackers could:
```
- Send 3 forgot-password requests
- Send 3 resend requests
= 6 total emails sent (abuse!)
```

With combined limit:
```
- Send 2 forgot-password requests
- Send 1 resend request
= 3 total emails sent (limit reached)
```

## Troubleshooting

### User Complains About Rate Limit

**Step 1: Verify legitimate user**
- Check CloudWatch logs for their email
- Verify number of requests made
- Check IP address for suspicious activity

**Step 2: Check if abuse**
```bash
# Count requests per email
aws logs filter-log-events \
  --log-group-name /aws/apigateway/batbern-prod \
  --filter-pattern "user@example.com Password reset" \
  --start-time $(date -u -d '1 hour ago' +%s)000 \
  --region eu-central-1
```

**Step 3: Take action**
- **Legitimate:** Provide workaround (wait, or manual reset)
- **Abuse:** Block IP, report to security team

### Rate Limit Not Working

**Check implementation:**
```bash
# Test with curl (should fail on 4th request)
for i in {1..4}; do
  curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com"}'
  echo "Request $i"
  sleep 1
done
```

**Check logs:**
```bash
docker-compose logs -f api-gateway | grep "Rate limit"
```

**Verify service is running:**
```bash
# Check RateLimitService bean
curl http://localhost:8080/actuator/beans | jq '.beans[] | select(.type | contains("RateLimitService"))'
```

## Performance Impact

### Memory Usage

**Estimate:**
- 1 email = ~100 bytes (email string + 3 timestamps)
- 1,000 emails = ~100 KB
- 10,000 emails = ~1 MB
- 100,000 emails = ~10 MB

**Cleanup:**
- Expired timestamps removed on each request
- Full cleanup every 1 hour (scheduled task)

### Latency

Rate limit check adds **< 1ms** overhead:
- HashMap lookup: O(1)
- Filter expired: O(n) where n ≤ 3
- Insert timestamp: O(1)

**Total impact:** Negligible

## Future Enhancements

### 1. Distributed Rate Limiting (Redis)

**Current:** In-memory (single instance)
**Problem:** Doesn't scale across multiple API Gateway instances

**Solution:** Use Redis
```java
@Service
public class RedisRateLimitService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean allowPasswordReset(String email) {
        String key = "rate-limit:password-reset:" + email;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }

        return count <= MAX_REQUESTS;
    }
}
```

### 2. User-based Rate Limiting

**Current:** Email-based (anonymous)
**Enhancement:** Authenticated user rate limiting

```java
// Different limits for authenticated vs anonymous
private static final int AUTH_MAX_REQUESTS = 5;
private static final int ANON_MAX_REQUESTS = 3;
```

### 3. Adaptive Rate Limiting

**Concept:** Adjust limits based on user behavior
- New users: 3 requests/hour
- Trusted users: 10 requests/hour
- Suspicious users: 1 request/hour

### 4. CAPTCHA Integration

**When rate limit hit:**
- Show CAPTCHA challenge
- If passed, allow 1 additional request

## Related Documentation

- [Forgot Password Flow](./forgot-password-flow.md)
- [API Documentation](../api/auth-endpoints.openapi.yml)
- [Troubleshooting Guide](./troubleshooting-forgot-password.md)
- [AWS SES Configuration](./aws-ses-configuration.md)
