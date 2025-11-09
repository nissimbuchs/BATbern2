# Partners API - Bruno E2E Tests

**Story**: 2.7 Partner Coordination Service Foundation
**API Spec**: `docs/api/partners-api.openapi.yml`
**Status**: Tests created, awaiting deployment + seed data

---

## Test Coverage

### Partner CRUD (Tests 1-4)
- ✅ `01-list-partners.bru` - List all partners with pagination
- ✅ `02-get-partner-by-company.bru` - Get partner by company name (ADR-003)
- ✅ `03-get-partner-not-found.bru` - 404 handling
- ✅ `04-update-partner.bru` - Update partnership details

### Partner Contacts (Tests 5-8)
- ✅ `05-list-partner-contacts.bru` - List contacts with User enrichment (ADR-004)
- ✅ `06-add-partner-contact.bru` - Add contact by username
- ✅ `07-add-contact-duplicate.bru` - 409 duplicate handling
- ✅ `08-delete-partner-contact.bru` - Remove contact

### Topic Voting (Tests 9-11, 14)
- ✅ `09-list-partner-votes.bru` - List partner votes
- ✅ `10-cast-topic-vote.bru` - Cast vote with weighted influence
- ✅ `11-cast-vote-duplicate.bru` - 409 duplicate vote handling
- ✅ `14-cast-vote-invalid-value.bru` - 400 validation (vote must be 1-5)

### Topic Suggestions (Tests 12-13, 15)
- ✅ `12-list-partner-suggestions.bru` - List partner suggestions
- ✅ `13-submit-suggestion.bru` - Submit topic suggestion
- ✅ `15-submit-suggestion-too-long.bru` - 400 validation (title max 500 chars)

### Authentication & Error Handling (Test 16)
- ✅ `16-unauthorized.bru` - 401 no JWT token

**Total Tests**: 16 test files covering all Partner API endpoints

---

## Prerequisites (Test Data Setup)

⚠️ **CRITICAL**: These tests require deployed staging environment + seed data

### 1. Deploy Partner Coordination Service to Staging

```bash
cd infrastructure
npm run deploy:staging
```

### 2. Create Test Data

**Environment Variables Required** (add to `bruno-tests/environments/staging.bru`):

```bru
vars {
  baseUrl: https://api.staging.batbern.ch/api/v1
  authToken: {{process.env.AUTH_TOKEN}}

  # Partner API test data
  testPartnerCompanyName: GoogleZH
  testContactUsername: jane.doe
  testTopicId: <UUID from Event Service>
}
```

**Test Data Creation** (run once before tests):

```bash
# 1. Create test company (if not exists)
# Via Company Service API or seed script from Story 2.1
curl -X POST https://api.staging.batbern.ch/api/v1/companies \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "GoogleZH",
    "displayName": "Google Zurich",
    "legalName": "Google Switzerland GmbH"
  }'

# 2. Create test users (if not exist)
# Via User Service API or seed script from Story 2.1b
curl -X POST https://api.staging.batbern.ch/api/v1/users \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "jane.doe",
    "email": "jane.doe@google.com",
    "firstName": "Jane",
    "lastName": "Doe"
  }'

# 3. Create test partner for GoogleZH
curl -X POST https://api.staging.batbern.ch/api/v1/partners \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "GoogleZH",
    "partnershipLevel": "gold",
    "partnershipStartDate": "2024-01-01"
  }'

# 4. Create test topic (via Event Service) for voting tests
# Use existing topic or create new one, then set testTopicId in environment
```

**Alternative**: Use seed scripts from Stories 2.1, 2.1b, 2.2 if available.

---

## Running Tests

### Run All Partners API Tests

```bash
# From project root
./scripts/ci/run-bruno-tests.sh partners-api
```

### Run Individual Tests

```bash
# Install Bruno CLI if not already installed
npm install -g @usebruno/cli

# Run specific test
bruno run bruno-tests/partners-api/01-list-partners.bru \
  --env staging \
  --env-var AUTH_TOKEN="$AUTH_TOKEN"

# Run all tests in partners-api collection
bruno run bruno-tests/partners-api \
  --env staging \
  --env-var AUTH_TOKEN="$AUTH_TOKEN"
```

### Get AUTH_TOKEN

```bash
# Use authentication helper script
./scripts/auth/get-token.sh

# Or manually via AWS Cognito
# (see infrastructure configuration for Cognito details)
```

---

## Test Architecture Patterns

### ADR-003: Meaningful Identifiers

Tests verify meaningful IDs in API URLs:

```javascript
// ✅ Correct - using meaningful ID (companyName)
GET /api/v1/partners/GoogleZH

// ❌ Wrong - using UUID
GET /api/v1/partners/550e8400-e29b-41d4-a716-446655440000
```

### ADR-004: HTTP Enrichment

Tests verify User data enrichment via HTTP calls:

```javascript
// Partner Contact Response (enriched)
{
  "id": "...",
  "username": "jane.doe",         // From PartnerContact entity
  "contactRole": "primary",        // From PartnerContact entity
  "isPrimary": true,               // From PartnerContact entity
  "email": "jane.doe@google.com",  // From User Service HTTP call
  "firstName": "Jane",             // From User Service HTTP call
  "lastName": "Doe",               // From User Service HTTP call
  "profilePictureUrl": "..."       // From User Service HTTP call
}
```

### Shared-Kernel ErrorResponse

All error tests verify ErrorResponse schema from shared-kernel:

```javascript
test("should return ErrorResponse from shared-kernel", function() {
  const body = res.getBody();
  expect(body.message).to.exist;
  expect(body.path).to.exist;
  expect(body.correlationId).to.exist;
});
```

---

## Test Execution Checklist

- [ ] Partner Coordination Service deployed to staging
- [ ] Test company created: `GoogleZH`
- [ ] Test user created: `jane.doe`
- [ ] Test partner created for GoogleZH (partnership level: gold)
- [ ] Test topic created (via Event Service), UUID set in env vars
- [ ] AUTH_TOKEN obtained and configured in environment
- [ ] All 16 tests passing against staging environment
- [ ] Tests verified via CI script: `./scripts/ci/run-bruno-tests.sh partners-api`

---

## Related Documentation

- 📖 [Story 2.7: Partner Coordination Service Foundation](../../docs/stories/2.7.partner-coordination-service-foundation.md)
- 📖 [Partners API OpenAPI Spec](../../docs/api/partners-api.openapi.yml)
- 📖 [ADR-003: Meaningful Identifiers](../../docs/architecture/ADR-003-meaningful-identifiers-public-apis.md)
- 📖 [ADR-004: Factor User Fields](../../docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md)

---

**Created**: 2025-01-08
**Status**: Tests ready, awaiting deployment + seed data
