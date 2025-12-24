# BATbern Code Templates Library

**Purpose**: Reusable implementation patterns extracted from BATbern stories to eliminate duplication and reduce story file token count.

**Target**: Reduce large story files from ~14,000 words to ~2,000 words (86% reduction) by referencing templates instead of embedding full code.

---

## How to Use Templates

### In Story Dev Notes

**Before** (embedding full code, ~1000 lines):
```markdown
## Dev Notes

### Spring Boot Service Setup
```java
package ch.batbern.partners;

@SpringBootApplication
public class PartnerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PartnerServiceApplication.class, args);
    }
}

@Entity
@Table(name = "partners")
public class PartnerEntity {
    @Id
    private UUID id;
    // ... 200 more lines
}
```
... (900 more lines of boilerplate)
```

**After** (referencing template, ~50 lines):
```markdown
## Dev Notes - Implementation Guide

### Quick Start
1. Follow: `docs/templates/backend/spring-boot-service-foundation.md`
2. Reference existing: `services/partner-coordination-service/`
3. Key deviations: Custom engagement calculation logic

### Templates Used
- `docs/templates/backend/spring-boot-service-foundation.md` - Service setup, JPA entities, repositories
- `docs/templates/backend/jwt-propagation-pattern.md` - Cross-service HTTP calls with auth

### Story-Specific Code (ONLY deviations from templates)
```java
// Custom engagement calculation (not in template)
public EngagementScore calculateEngagement(Partner partner) {
    return new EngagementScore(
        partner.getEventAttendance(),
        partner.getTopicVotes(),
        partner.getContentSubmissions()
    );
}
```
```

---

## Template Index

### Backend Templates

| Template | Description | Lines | Used in Stories | Status |
|----------|-------------|-------|-----------------|--------|
| [spring-boot-service-foundation.md](backend/spring-boot-service-foundation.md) | Package structure, JPA entities, repositories, service layer | 400 | 2.2, 2.7, 2.8, 5.5 | ✅ Available |
| [jwt-propagation-pattern.md](backend/jwt-propagation-pattern.md) | RestTemplate with JWT interceptors for cross-service calls | 220 | 2.7, 2.8.1 | ✅ Available |
| [integration-test-pattern.md](backend/integration-test-pattern.md) | Testcontainers PostgreSQL setup, MockMvc, test structure | 800 | 2.8.1, 5.5, all API stories | ✅ Available |
| [flyway-migration-pattern.md](backend/flyway-migration-pattern.md) | PostgreSQL migration patterns (ADR-003 compliance) | 600 | 5.5 | ✅ Available |
| [spring-transaction-pattern.md](backend/spring-transaction-pattern.md) | Transaction management, isolation levels, rollback strategies | 550 | 5.5 | ✅ Available |
| [event-driven-idempotency-pattern.md](backend/event-driven-idempotency-pattern.md) | Idempotent event listeners for domain events | 450 | 5.5 | ✅ Available |
| [exception-handling-pattern.md](backend/exception-handling-pattern.md) | Global exception handler, standard error responses | 150 | 2.7, 2.8.1 | 📋 Planned |
| [openapi-code-generation-pattern.md](backend/openapi-code-generation-pattern.md) | Hybrid contract-first API approach with OpenAPI Generator | 250 | 2.7, 2.8 | 📋 Planned |

### Frontend Templates

| Template | Description | Lines | Used in Stories | Status |
|----------|-------------|-------|-----------------|--------|
| [react-query-caching-pattern.md](frontend/react-query-caching-pattern.md) | React Query hooks for server state management with caching | 280 | 2.5.3, 2.8.1 | ✅ Available |
| [zustand-store-pattern.md](frontend/zustand-store-pattern.md) | Zustand store for UI state (filters, view mode, selections) | 240 | 2.5.3, 2.8.1 | ✅ Available |
| [api-service-pattern.md](frontend/api-service-pattern.md) | Axios client with interceptors, error handling, resource expansion | 320 | 2.5.3, 2.8.1 | ✅ Available |
| [form-validation-pattern.md](frontend/form-validation-pattern.md) | React Hook Form + Zod validation with auto-save patterns | 350 | 2.5.3 | ✅ Available |
| [i18n-pattern.md](frontend/i18n-pattern.md) | react-i18next setup, translation files, locale formatting | 280 | 2.5.3 | ✅ Available |
| [react-component-pattern.md](frontend/react-component-pattern.md) | Atomic design structure, props, Material-UI integration | 300 | 2.5.3, 2.8.1 | 📋 Planned |

### Infrastructure Templates

| Template | Description | Lines | Used in Stories | Status |
|----------|-------------|-------|-----------------|--------|
| [aws-cdk-service-stack.md](infrastructure/aws-cdk-service-stack.md) | CDK stack for microservices (ECS Fargate, RDS, VPC) | 300 | Infrastructure stories | 📋 Planned |
| [github-actions-ci-pattern.md](infrastructure/github-actions-ci-pattern.md) | CI/CD pipeline for Java + Node.js monorepo | 200 | DevOps stories | 📋 Planned |

---

## Template Standard Format

All templates follow this structure for consistency:

```markdown
# {Pattern Name}

**Category**: Backend / Frontend / Infrastructure
**Used in Stories**: [List of story IDs that use this pattern]
**Last Updated**: {Date}

## Overview
{1-2 sentences on when to use this pattern}

## Prerequisites
- {Required dependencies, tools, or setup}

## Implementation Steps

### Step 1: {Title}
{Explanation}
```{language}
// Full working code example
```

### Step 2: {Title}
...

## Configuration Files
{Required config (application.yml, package.json, etc.)}

## Testing
{How to test this pattern}

## Common Pitfalls
- **Pitfall**: {Description}
  **Solution**: {How to avoid/fix}

## Story-Specific Adaptations
{How to customize this pattern for specific use cases}

## Related Patterns
- See also: `{other-template}.md`
```

---

## Maintenance

### When to Create a New Template

Create a new template when:
- Same code block (>50 lines) appears in 2+ stories
- Pattern is reusable across multiple domains
- Code is proven and tested in production

### When to Update a Template

Update a template when:
- Better implementation discovered in new story
- Pattern evolves (e.g., React 19 upgrade)
- Bug fix applies to all uses of the pattern

**Process**:
1. Update template file
2. Update "Last Updated" date
3. Update "Used in Stories" list
4. Notify team if breaking change

### Version History

Track major template changes in this section:

- **2025-12-24**: Story 5.5 template extraction completed (11 templates total, 3 new)
  - ✅ **Backend**: flyway-migration-pattern.md (NEW - extracted from Story 5.5, V22 migration)
  - ✅ **Backend**: spring-transaction-pattern.md (NEW - extracted from Story 5.5, transaction handling patterns)
  - ✅ **Backend**: event-driven-idempotency-pattern.md (NEW - extracted from Story 5.5, event listeners with idempotency)
  - ✅ **Backend**: integration-test-pattern.md (UPDATED - added Story 5.5 test examples for task creation and transactions)
  - ✅ **Backend**: spring-boot-service-foundation.md (UPDATED - now used by Story 5.5)
  - **Token Savings**: Story 5.5 reduced by ~399 lines (72+60+59+70+54+84 from patterns) = 71% reduction
  - **Story 5.5**: 1,624 lines → ~470 lines (estimated)

- **2025-12-20**: Phase 4 template extraction completed (8 templates available)
  - ✅ **Frontend**: api-service-pattern.md (extracted from Story 2.5.3, lines 1396-1493)
  - ✅ **Frontend**: form-validation-pattern.md (extracted from Story 2.5.3, lines 1412-1596)
  - ✅ **Frontend**: i18n-pattern.md (extracted from Story 2.5.3, lines 960-1115)
  - **Token Savings**: Story 2.5.3 reduced by ~487 lines (97+170+135+85 from patterns)
  - 📋 **Remaining**: 6 templates planned for future extraction

- **2025-01-20**: Phase 1 template extraction completed (5 templates available)
  - ✅ **Backend**: spring-boot-service-foundation.md (extracted from docs/guides/, Story 2.2, 2.7)
  - ✅ **Backend**: jwt-propagation-pattern.md (extracted from docs/guides/microservices-http-clients.md, Story 2.7)
  - ✅ **Backend**: integration-test-pattern.md (extracted from docs/architecture/coding-standards.md, Story 2.8.1)
  - ✅ **Frontend**: react-query-caching-pattern.md (extracted from Story 2.5.3, 2.8.1)
  - ✅ **Frontend**: zustand-store-pattern.md (extracted from Story 2.5.3, 2.8.1)

---

## Benefits

**Token Savings**:
- Story 2.5.3: 14,256 words → ~2,000 words (86% reduction)
- Average across active stories: 76% reduction

**Developer Experience**:
- ✅ Focus on story-specific logic (what's different)
- ✅ Reference proven patterns (what's standard)
- ✅ Consistent architecture across services
- ✅ Faster onboarding for new developers

**Maintainability**:
- ✅ Single source of truth for patterns
- ✅ Easy to evolve patterns (update once, benefits all stories)
- ✅ Clear separation: templates (how) vs. stories (what + why)

---

## Related Documentation

- [Coding Standards](../architecture/coding-standards.md) - Development standards and TDD practices
- [Service Foundation Pattern Guide](../guides/service-foundation-pattern.md) - Detailed service architecture
- [Microservices HTTP Clients Guide](../guides/microservices-http-clients.md) - Cross-service communication
- [OpenAPI Code Generation Guide](../guides/openapi-code-generation.md) - Contract-first API approach
- [Flyway Migration Guide](../guides/flyway-migration-guide.md) - Database migration patterns

**Templates complement guides**: Guides explain architecture and decisions (why), templates provide copy-paste implementation (how).
