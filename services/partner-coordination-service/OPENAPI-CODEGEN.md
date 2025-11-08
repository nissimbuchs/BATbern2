# OpenAPI Code Generation - Partner Coordination Service

This service uses the **hybrid contract-first approach** with OpenAPI Generator. For comprehensive documentation on the pattern, implementation examples, and best practices, see:

📖 **[OpenAPI Code Generation Guide](../../docs/guides/openapi-code-generation.md)** - Complete guide with examples

This document provides Partner Coordination Service-specific quick reference.

---

## Quick Reference

### OpenAPI Specification

**Location**: `docs/api/partners-api.openapi.yml`

**Tags (API Interfaces)**:
- `Partners` - Partner relationship management
- `Partner Contacts` - Contact person management
- `Topic Voting` - Topic voting and suggestions
- `Partner Meetings` - Meeting coordination

### Generated API Interfaces

Generated in `ch.batbern.partners.api.generated/`:

- **`PartnersApi`** - Partner CRUD and listing
  - `listPartners()` - List partners with filtering
  - `getPartnerByCompanyName()` - Get partner by company name (ADR-003)
  - `updatePartner()` - Update partnership details

- **`PartnerContactsApi`** - Contact management
  - `getPartnerContacts()` - List contacts for a partner
  - `addPartnerContact()` - Add contact person
  - `removePartnerContact()` - Remove contact person

- **`TopicVotingApi`** - Topic voting and suggestions
  - `getPartnerVotes()` - Get partner's votes
  - `voteOnTopic()` - Cast vote on topic
  - `getPartnerSuggestions()` - Get partner's suggestions
  - `suggestTopic()` - Submit topic suggestion

- **`PartnerMeetingsApi`** - Meeting coordination (future)

### Generated DTOs

Generated in `ch.batbern.partners.dto.generated/`:

**Partner Management**:
- `PartnerResponse` - Partner details with optional includes
- `UpdatePartnerRequest` - Update partnership details
- `PartnershipTier` - Enum: bronze, silver, gold, platinum, strategic
- `PartnershipBenefits` - Benefits per partnership tier

**Contact Management**:
- `PartnerContactResponse` - Contact details (HTTP-enriched with User data)
- `AddPartnerContactRequest` - Add contact request
- `ContactRole` - Enum: primary, billing, technical, marketing

**Topic Voting**:
- `TopicVote` - Vote details
- `CastVoteRequest` - Vote submission
- `TopicSuggestion` - Suggestion details
- `SubmitSuggestionRequest` - Suggestion submission
- `SuggestionStatus` - Enum: submitted, under_review, accepted, rejected

### Shared-Kernel Imports

**NOT generated** - imported from shared-kernel:
- `ErrorResponse` - Standard error responses
- `PaginationMetadata` - Pagination info
- `DomainEventPublisher` - Event publishing
- `BATbernException` hierarchy - Exception handling

---

## ADR-003 Compliance: Meaningful IDs

**CRITICAL**: Partner Coordination Service uses **meaningful identifiers** for cross-service references:

✅ **Correct Usage**:
```java
// Partner stores companyName (meaningful ID), NOT companyId UUID
@Entity
public class Partner {
    @Column(name = "company_name", nullable = false, unique = true, length = 12)
    private String companyName;  // ✅ ADR-003: meaningful ID
}

// PartnerContact stores username (meaningful ID), NOT userId UUID
@Entity
public class PartnerContact {
    @Column(name = "username", nullable = false, length = 100)
    private String username;  // ✅ ADR-003: meaningful ID
}
```

❌ **Wrong Usage**:
```java
// ❌ NEVER store UUID FKs for cross-service references
private UUID companyId;  // ❌ WRONG
private UUID userId;     // ❌ WRONG
```

**Why?** Partners and Contacts reference entities in other services (Company Service, User Service). Per ADR-003, we use meaningful IDs and HTTP enrichment, NOT database foreign keys.

📖 See [Microservices HTTP Clients Guide](../../docs/guides/microservices-http-clients.md) for HTTP enrichment patterns.

---

## Commands

### Generate API Code

```bash
# From project root
./gradlew :services:partner-coordination-service:openApiGenerate

# Or build (includes generation)
./gradlew :services:partner-coordination-service:build
```

### Clean and Regenerate

```bash
./gradlew :services:partner-coordination-service:clean \
          :services:partner-coordination-service:openApiGenerate
```

### Run Tests

```bash
# Run all tests (includes schema validation)
./gradlew :services:partner-coordination-service:test

# Run specific test
./gradlew :services:partner-coordination-service:test \
  --tests PartnerControllerIntegrationTest
```

---

## Generated Code Location

```
services/partner-coordination-service/
└── build/
    └── generated/
        └── src/main/java/
            └── ch/batbern/partners/
                ├── api/generated/          # API Interfaces
                │   ├── PartnersApi.java
                │   ├── PartnerContactsApi.java
                │   ├── TopicVotingApi.java
                │   └── PartnerMeetingsApi.java
                └── dto/generated/          # DTOs
                    ├── PartnerResponse.java
                    ├── UpdatePartnerRequest.java
                    ├── PartnerContactResponse.java
                    ├── AddPartnerContactRequest.java
                    ├── PartnershipTier.java
                    ├── ContactRole.java
                    ├── TopicVote.java
                    └── ... (and more)
```

**Important**:
- Generated code is in `build/generated/`
- Created during compilation
- **NOT committed to Git** (gitignored)
- Regenerated on every build

---

## Implementation Pattern

### Controller Example

```java
@RestController
@RequiredArgsConstructor
public class PartnerController implements PartnersApi {

    private final PartnerService partnerService;
    private final IncludeParser includeParser;

    @Override
    public ResponseEntity<PartnerResponse> getPartnerByCompanyName(
            String companyName,
            Optional<String> include) {

        Set<String> includes = includeParser.parse(include.orElse(null));
        PartnerResponse partner = partnerService.getPartner(companyName, includes);

        return ResponseEntity.ok(partner);
    }

    @Override
    public ResponseEntity<PartnerResponse> updatePartner(
            String companyName,
            UpdatePartnerRequest request) {

        PartnerResponse updated = partnerService.updatePartner(companyName, request);
        return ResponseEntity.ok(updated);
    }
}
```

### Service Example with HTTP Enrichment

```java
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final CompanyServiceClient companyServiceClient;  // HTTP client
    private final UserServiceClient userServiceClient;        // HTTP client

    public PartnerResponse getPartner(String companyName, Set<String> includes) {
        Partner partner = partnerRepository.findByCompanyName(companyName)
            .orElseThrow(() -> new NotFoundException("Partner not found: " + companyName));

        PartnerResponse response = mapToResponse(partner);

        // HTTP enrichment per ADR-004
        if (includes.contains("company")) {
            CompanyResponse company = companyServiceClient.getCompany(companyName);
            response.setCompany(company);  // Enrich response
        }

        return response;
    }
}
```

📖 **For complete implementation patterns**, see:
- [Service Foundation Pattern Guide](../../docs/guides/service-foundation-pattern.md) - Service structure
- [Microservices HTTP Clients Guide](../../docs/guides/microservices-http-clients.md) - HTTP enrichment
- [OpenAPI Code Generation Guide](../../docs/guides/openapi-code-generation.md) - Full patterns

---

## What NOT to Do

❌ **Don't modify generated code** - it will be overwritten on next build
❌ **Don't commit generated code** - it's in `build/` directory (gitignored)
❌ **Don't create DTOs manually** - use the generated ones
❌ **Don't store cross-service UUID FKs** - use meaningful IDs (ADR-003)
❌ **Don't bypass shared-kernel** - reuse common infrastructure

---

## Updating the API

1. **Update** `docs/api/partners-api.openapi.yml`
2. **Rebuild** the service: `./gradlew :services:partner-coordination-service:build`
3. **Fix** any compilation errors in controllers (type safety!)
4. **Write tests** (TDD: tests first, then implementation)
5. **Test** your implementation

---

## Related Documentation

- 📖 [OpenAPI Code Generation Guide](../../docs/guides/openapi-code-generation.md) - Complete pattern documentation
- 📖 [Service Foundation Pattern](../../docs/guides/service-foundation-pattern.md) - Service structure
- 📖 [Microservices HTTP Clients](../../docs/guides/microservices-http-clients.md) - HTTP enrichment
- 📖 [Flyway Migration Guide](../../docs/guides/flyway-migration-guide.md) - Database schema
- 📖 [ADR-003: Meaningful Identifiers](../../docs/architecture/ADR-003-meaningful-identifiers-public-apis.md) - Microservices isolation
- 📖 [ADR-004: Factor User Fields](../../docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md) - HTTP enrichment pattern

---

**Last Updated**: 2025-01-08
**Status**: Ready for implementation
