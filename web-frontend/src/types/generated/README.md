# Generated API Types

This directory contains TypeScript types auto-generated from OpenAPI specifications.

## Generation

Types are generated using `openapi-typescript` from the OpenAPI spec files in the `docs/api/` directory.

### Generate Types

```bash
npm run generate:api-types
```

This will generate TypeScript types from:

| OpenAPI Spec                                   | Generated File                     |
| ---------------------------------------------- | ---------------------------------- |
| `docs/api/companies-api.openapi.yml`           | `company-api.types.ts`             |
| `docs/api/users-api.openapi.yml`               | `user-api.types.ts`                |
| `docs/api/events-core-api.openapi.yml`         | `events-core-api.types.ts`         |
| `docs/api/event-sessions-api.openapi.yml`      | `event-sessions-api.types.ts`      |
| `docs/api/event-speakers-api.openapi.yml`      | `event-speakers-api.types.ts`      |
| `docs/api/event-registrations-api.openapi.yml` | `event-registrations-api.types.ts` |
| `docs/api/event-newsletter-api.openapi.yml`    | `event-newsletter-api.types.ts`    |
| `docs/api/event-media-api.openapi.yml`         | `event-media-api.types.ts`         |
| `docs/api/event-ai-api.openapi.yml`            | `event-ai-api.types.ts`            |
| `docs/api/event-app-settings-api.openapi.yml`  | `event-app-settings-api.types.ts`  |
| `docs/api/event-analytics-api.openapi.yml`     | `event-analytics-api.types.ts`     |
| `docs/api/event-watch-api.openapi.yml`         | `event-watch-api.types.ts`         |
| `docs/api/speakers-api.openapi.yml`            | `speakers-api.types.ts`            |
| `docs/api/topics-api.openapi.yml`              | `topics-api.types.ts`              |
| `docs/api/partners-api.openapi.yml`            | `partner-api.types.ts`             |
| `docs/api/attendees-api.openapi.yml`           | `attendees-api.types.ts`           |

### Usage

Import the generated types in your code:

```typescript
import type { components, operations } from '@/types/generated/company-api.types';

// Use schema types
type Company = components['schemas']['CompanyResponse'];
type CreateRequest = components['schemas']['CreateCompanyRequest'];

// Use operation types
type ListCompaniesResponse =
  operations['listCompanies']['responses']['200']['content']['application/json'];
```

### Wrapper Type Files

Domain type files in `src/types/` re-export generated types and add UI-specific extensions:

- `company.types.ts` - imports from `generated/company-api.types.ts`
- `event.types.ts` - imports from the per-domain `generated/event*-api.types.ts` files (events-api was split in API consolidation Phase 6)
- `user.types.ts` - imports from `generated/user-api.types.ts`
- `topic.types.ts` - imports from `generated/topics-api.types.ts`
- `speakerPool.types.ts` - imports from `generated/speakers-api.types.ts`
- `speakerOutreach.types.ts` - imports from `generated/speakers-api.types.ts`

### When to Regenerate

Regenerate types whenever the OpenAPI specification changes:

1. After updating any `docs/api/*.openapi.yml` file
2. Before committing API contract changes
3. When adding new endpoints or modifying existing ones

### Type Safety

These generated types ensure compile-time safety between frontend and backend:

- Request/response payloads match backend DTOs
- Parameter types are enforced
- Schema validation aligns with backend validation

## Notes

- **DO NOT** manually edit files in this directory
- Generated files **ARE** committed to version control
- Run `npm run generate:api-types` as part of your development workflow
