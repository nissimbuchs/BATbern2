# Getting Started with Bruno Tests

## ⚠️ Important: Token Issue Resolved

Your unauthorized error was caused by using a token from the **development** Cognito pool instead of the **staging** pool.

### Current Situation
- ❌ Old token from: `eu-central-1_camJHQhZ8` (development)
- ✅ Need token from: `eu-central-1_FtgfxgQRF` (staging)
- ✅ Your user exists: `nissim@buchs.be`

## Option 1: Get Token via Script (Recommended)

Run this script to get a fresh token:

```bash
cd bruno-tests
./get-staging-token.sh nissim@buchs.be YOUR_PASSWORD
```

The script will:
1. Authenticate with staging Cognito
2. Display your new JWT token
3. Show when it expires (tokens last 1 hour)

Copy the token and update `bruno-tests/event-management-service/environments/staging.bru`

## Option 2: Get Token via AWS CLI (Manual)

```bash
AWS_PROFILE=batbern-staging aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id 1b53lci6qpqsmdn0u3e8s4knvv \
  --auth-parameters USERNAME=nissim@buchs.be,PASSWORD=YOUR_PASSWORD \
  --region eu-central-1 \
  --query 'AuthenticationResult.IdToken' \
  --output text
```

## Option 3: Get Token via Web App

1. Login to staging web frontend
2. Open Browser DevTools (F12)
3. Go to: Application > Local Storage
4. Find and copy the JWT token
5. Paste into Bruno environment

## Update Bruno Environment

1. Open: `bruno-tests/event-management-service/environments/staging.bru`
2. Replace the `authToken` line:

```
vars {
  baseUrl: https://api.staging.batbern.ch/api/v1
  organizerId: c334a852-10c1-70d2-f403-136e0a60acf7
  authToken: <PASTE_NEW_TOKEN_HERE>
}
```

3. Save the file

## Test the Connection

After updating the token, test with curl:

```bash
# Set your new token
TOKEN="your-new-token-here"

# Test the events endpoint
curl -H "Authorization: Bearer $TOKEN" \
  https://api.staging.batbern.ch/api/v1/events
```

You should see a successful response with event data!

## Run Bruno Tests

1. Open Bruno
2. Load collection: `bruno-tests/event-management-service`
3. Select environment: **staging**
4. Run test: `01-list-events.bru`
5. Should return 200 OK! ✅

## Token Expiry

- JWT tokens expire after **1 hour**
- If you get 401 errors again, get a fresh token
- Consider saving the token generation command for easy refresh

## Staging Environment Details

- **API URL**: https://api.staging.batbern.ch/api/v1
- **Cognito Pool**: eu-central-1_FtgfxgQRF
- **Client ID**: 1b53lci6qpqsmdn0u3e8s4knvv
- **Region**: eu-central-1
- **Your User ID**: c334a852-10c1-70d2-f403-136e0a60acf7

## Troubleshooting

### Still Getting 401?
1. Verify token is from staging pool (not dev)
2. Check token hasn't expired (< 1 hour old)
3. Ensure token is correctly pasted (no extra spaces/newlines)

### Service Not Responding?
```bash
# Check service status
AWS_PROFILE=batbern-staging aws ecs describe-services \
  --cluster batbern-staging \
  --services BATbern-staging-EventManagement-Service9571FDD8-IiP2qfjukm4s \
  --query 'services[0].{Status:status,Running:runningCount}'
```

### Check Logs
```bash
# View recent logs
AWS_PROFILE=batbern-staging aws logs tail \
  /aws/ecs/event-management-service \
  --follow
```

## Next Steps

Once you have a valid token and tests pass:
1. Review the [README.md](event-management-service/README.md) for full test documentation
2. Run all 20 tests in sequence
3. Start testing your API endpoints!

## Need Help?

Issues to check:
- [ ] Token is from staging Cognito pool (`eu-central-1_FtgfxgQRF`)
- [ ] Token is less than 1 hour old
- [ ] Environment file is saved with new token
- [ ] Bruno is using "staging" environment

If still stuck, check CloudWatch logs for the event-management-service.
