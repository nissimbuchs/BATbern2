# Generated API Types

This directory contains TypeScript types auto-generated from OpenAPI specifications.

## Generation

Types are generated using `openapi-typescript` from the OpenAPI spec files in the `docs/api/` directory.

### Generate Types

```bash
npm run generate:api-types
```

This will generate TypeScript types from:
- `docs/api/companies-api.openapi.yml` → `src/types/generated/company-api.types.ts`

### Usage

Import the generated types in your code:

```typescript
import type { components, operations } from '@/types/generated/company-api.types';

// Use schema types
type Company = components['schemas']['CompanyResponse'];
type CreateRequest = components['schemas']['CreateCompanyRequest'];

// Use operation types
type ListCompaniesResponse = operations['listCompanies']['responses']['200']['content']['application/json'];
```

### When to Regenerate

Regenerate types whenever the OpenAPI specification changes:
1. After updating `docs/api/companies-api.openapi.yml`
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
