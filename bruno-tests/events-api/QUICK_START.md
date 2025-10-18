# Quick Start Guide

## 5-Minute Setup

### Step 1: Get Your JWT Token

You need a valid AWS Cognito JWT token. Here's how to get one:

**Option A: Using AWS CLI**
```bash
aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id 5h9421vo002bi7udjdu5orp7u3 \
  --auth-parameters USERNAME=your-email@example.com,PASSWORD=your-password \
  --region eu-central-1 \
  --query 'AuthenticationResult.IdToken' \
  --output text
```

**Option B: Using the Web Frontend**
1. Login to the staging web app
2. Open browser DevTools (F12)
3. Go to Application/Storage > Local Storage
4. Find the JWT token in storage
5. Copy the token value

### Step 2: Configure Environment

1. Open `bruno-tests/event-management-service/environments/staging.bru`
2. Replace `YOUR_JWT_TOKEN_HERE` with your actual token:

```
vars {
  baseUrl: https://dev.api.batbern.ch/api/v1
  organizerId: 550e8400-e29b-41d4-a716-446655440001
  authToken: eyJraWQiOiJ...PASTE_YOUR_TOKEN_HERE
}
```

3. Save the file

### Step 3: Open in Bruno

1. Launch Bruno
2. Click "Open Collection"
3. Select `bruno-tests/event-management-service`
4. Select "staging" environment from dropdown

### Step 4: Run Tests

**Run all tests in sequence:**
- Click the root folder
- Click "Run" (▶️)

**Or run tests individually:**
- Click any `.bru` file
- Click "Send"

## Test Flow

Tests run in this order and share data:

1. **List existing events** → See what's already there
2. **Create test event** → Saves `createdEventId`
3. **Get/Update event** → Uses `createdEventId`
4. **Create session** → Saves `createdSessionId`
5. **Update/Delete session** → Uses `createdSessionId`
6. **Create registration** → Saves `createdRegistrationId`
7. **Update/Delete registration** → Uses `createdRegistrationId`
8. **Test workflows** → Publish, advance workflow
9. **Get analytics** → View metrics
10. **Cleanup** → Delete test event

## Expected Results

✅ **All tests should pass** if staging is healthy

⚠️ **Some tests may fail gracefully**:
- Test 16 (Publish) may fail if event doesn't meet publish requirements
- Test 17 (Advance Workflow) may fail if workflow is at final state

## Common Commands

### Get Staging Service Health
```bash
curl https://dev.api.batbern.ch/actuator/health
```

### Check Recent Logs
```bash
AWS_PROFILE=batbern-staging aws logs tail /aws/ecs/event-management-service --follow
```

### Verify Database Connection
```bash
# Connect to staging RDS
psql -h batbern-development-postgres.c10iyqgumkwu.eu-central-1.rds.amazonaws.com \
     -U postgres -d batbern
```

## Troubleshooting

| Error | Solution |
|-------|----------|
| 401 Unauthorized | Token expired - get new token (Step 1) |
| 404 Not Found | Run test 03 first to create event |
| 500 Server Error | Check CloudWatch logs |
| Connection refused | Verify staging is deployed |

## Pro Tips

💡 **Tip 1:** Token expires in 1 hour - save your token generation command

💡 **Tip 2:** Run tests in order first time, then run individually for debugging

💡 **Tip 3:** Check `X-Cache-Status` header to verify caching works

💡 **Tip 4:** Use test 20 to cleanup if you create multiple test events

💡 **Tip 5:** Duplicate a test to create custom scenarios

## Next Steps

Once tests pass:
1. Review [README.md](README.md) for detailed documentation
2. Check [docs/api/events-api.openapi.yml](../../docs/api/events-api.openapi.yml) for API specs
3. Add custom tests for your specific scenarios
4. Integrate into CI/CD pipeline

## Need Help?

- **API Issues:** Check CloudWatch Logs
- **Auth Issues:** Verify Cognito configuration
- **Database Issues:** Check RDS connectivity
- **Questions:** Contact platform@batbern.ch
