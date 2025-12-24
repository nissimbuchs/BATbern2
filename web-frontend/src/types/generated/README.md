# Generated API Types

This directory contains TypeScript types auto-generated from OpenAPI specifications.

## Generation

Types are generated using `openapi-typescript` from the OpenAPI spec files in the `docs/api/` directory.

### Generate Types

```bash
npm run generate:api-types
```

This will generate TypeScript types from:

| OpenAPI Spec | Generated File |
|-------------|----------------|
| `docs/api/companies-api.openapi.yml` | `company-api.types.ts` |
| `docs/api/users-api.openapi.yml` | `user-api.types.ts` |
| `docs/api/events-api.openapi.yml` | `events-api.types.ts` |
| `docs/api/speakers-api.openapi.yml` | `speakers-api.types.ts` |
| `docs/api/topics-api.openapi.yml` | `topics-api.types.ts` |
| `docs/api/partners-api.openapi.yml` | `partner-api.types.ts` |
| `docs/api/attendees-api.openapi.yml` | `attendees-api.types.ts` |

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
- `event.types.ts` - imports from `generated/events-api.types.ts`
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
