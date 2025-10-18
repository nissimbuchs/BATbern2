# Event Management Service - Bruno API Tests

This Bruno collection contains comprehensive API tests for the Event Management Service deployed to staging.

## Setup

### 1. Install Bruno

Download and install Bruno from [https://www.usebruno.com/](https://www.usebruno.com/)

### 2. Open Collection

1. Open Bruno
2. Click "Open Collection"
3. Navigate to `bruno-tests/event-management-service`
4. Select the folder

### 3. Configure Environment

1. In Bruno, select the **staging** environment from the environment dropdown
2. Update the `authToken` variable with your JWT token from AWS Cognito:
   - Go to your Cognito User Pool
   - Create/login with a test user
   - Copy the JWT token
   - Paste into `environments/staging.bru`

Example:
```
vars {
  baseUrl: https://dev.api.batbern.ch/api/v1
  organizerId: 550e8400-e29b-41d4-a716-446655440001
  authToken: eyJraWQiOiJ...your-actual-jwt-token-here
}
```

### 4. Update Organizer ID (if needed)

If you need to use a different organizer ID:
1. Open `environments/staging.bru`
2. Update the `organizerId` value
3. Save the file

## Test Collection Structure

The collection contains 20 tests organized sequentially:

### Event CRUD Operations (Tests 1-7)
- **01-list-events.bru** - List all events with pagination
- **02-list-events-filtered.bru** - Filter events by status and sort
- **03-create-event.bru** - Create a new test event
- **04-get-event.bru** - Get event details
- **05-get-event-expanded.bru** - Get event with expanded resources
- **06-update-event-patch.bru** - Partial update using PATCH
- **07-update-event-put.bru** - Full replacement using PUT

### Session Management (Tests 8-11)
- **08-list-sessions.bru** - List event sessions
- **09-create-session.bru** - Create a session
- **10-update-session.bru** - Update session details
- **11-delete-session.bru** - Delete a session

### Registration Management (Tests 12-15)
- **12-list-registrations.bru** - List event registrations
- **13-create-registration.bru** - Create a registration
- **14-update-registration.bru** - Update registration status
- **15-delete-registration.bru** - Delete a registration

### Workflows & Analytics (Tests 16-19)
- **16-publish-event.bru** - Publish event workflow action
- **17-advance-workflow.bru** - Advance event workflow state
- **18-get-analytics.bru** - Get event analytics
- **19-batch-update.bru** - Batch update multiple events

### Cleanup (Test 20)
- **20-delete-event.bru** - Delete test event (cleanup)

## Running Tests

### Run All Tests in Sequence

1. Select the collection root folder
2. Click the "Run" button (▶️) in Bruno
3. All tests will run sequentially, with each test using data from previous tests

### Run Individual Tests

1. Click on any test file
2. Click "Send" or press `Ctrl/Cmd + Enter`

### Run a Subset

1. Select multiple tests using `Ctrl/Cmd + Click`
2. Right-click and choose "Run"

## Test Features

### Variable Chaining
Tests use Bruno's variable system to chain data:
- Test 03 creates an event and saves `createdEventId`
- Tests 04-20 use `{{createdEventId}}` to reference the created event
- Test 09 saves `createdSessionId` for session tests
- Test 13 saves `createdRegistrationId` for registration tests

### Assertions
Each test includes:
- HTTP status code assertions
- Response body structure validation
- Business logic validation
- Data type checking

### Performance Testing
Tests validate cache headers and response times match OpenAPI specs:
- Event list: <100ms (P95)
- Event detail (basic): <150ms (P95)
- Event detail (all includes): <500ms (P95)
- Cached response: <50ms (P95)

## Expected Test Results

All tests should pass when:
- The event-management-service is deployed and healthy
- Your JWT token is valid
- The database is accessible
- All API endpoints are functioning correctly

## Troubleshooting

### 401 Unauthorized
- Check your `authToken` in the environment file
- Ensure the JWT token hasn't expired
- Verify the token has the correct permissions

### 404 Not Found on Event Operations
- Make sure test 03 (create-event) runs successfully first
- Check that `createdEventId` variable is set
- Verify the staging database has the test event

### 500 Internal Server Error
- Check CloudWatch logs for the event-management-service
- Verify database connectivity
- Check if database migrations are current

### Connection Errors
- Verify the staging environment is deployed
- Check the `baseUrl` in your environment file
- Ensure your network can reach the staging API

## Environment Files

### staging.bru
Points to the staging deployment at `https://dev.api.batbern.ch/api/v1`

### local.bru
Points to local development at `http://localhost:8080/api/v1`

Switch between environments using the dropdown in Bruno.

## API Documentation

For detailed API specifications, see:
- [docs/api/events-api.openapi.yml](../../docs/api/events-api.openapi.yml)

## Test Data

Tests create temporary data with identifiable names:
- Events: "Bruno Test Event {timestamp}"
- Event Number: 999
- Venue: "Test Venue"
- Sessions: "Opening Keynote"
- Registrations: "test.attendee@example.com"

The final test (20-delete-event) cleans up the test event and all associated data.

## CI/CD Integration

These tests can be run in CI/CD pipelines using Bruno CLI:

```bash
# Install Bruno CLI
npm install -g @usebruno/cli

# Run tests
bru run bruno-tests/event-management-service --env staging

# Run with specific environment
bru run bruno-tests/event-management-service --env local
```

## Support

For issues or questions:
1. Check CloudWatch logs for API errors
2. Review the OpenAPI specification
3. Contact the platform team at platform@batbern.ch
