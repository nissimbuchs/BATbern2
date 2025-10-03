# Company Management Feature - 2-Story Approach Example

This example demonstrates the **2-story approach** for a simple CRUD feature. Use this for simple features, small teams, or when you want less coordination overhead.

## When to Use 2-Story Approach

✅ **Use 2-Story When:**
- Simple CRUD operations
- Small team (1-2 developers)
- Low complexity
- Tight deadline
- Less coordination overhead desired

❌ **Don't Use 2-Story When:**
- Complex business logic
- Large team (3+ developers)
- Need maximum parallelization
- Multiple domain integrations

---

## Overview

**Feature:** Company Management CRUD
**Total Time:** 5 days development (3-5 days calendar depending on team)

### Story Breakdown

```
Story 3.X-1: Company Management Frontend-First (3 days)
  - Define API contract inline
  - Implement complete UI with MSW mocks
  - Ready for demo with mocked data

Story 3.X-2: Company Management Backend-Integration (2 days)
  - Implement backend against inline contract
  - Replace MSW mocks with real APIs
  - Deploy and validate
```

---

## Story 3.X-1: Company Management Frontend-First

**Template:** `story-frontend-first-tmpl.yaml`
**Duration:** 3 days
**Owner:** Frontend Developer (or full-stack developer)

### Story

**As an** organizer,
**I want** to manage company information (create, view, update, delete),
**so that** I can maintain accurate partner company records.

**Frontend-First Focus:** This story defines the API contract inline and implements the complete UI/UX with MSW mocks. Backend implementation follows in Story 3.X-2.

---

### API Contract Definition (Inline)

#### Endpoints

**GET /api/v1/companies**
- Query params: `search` (string), `limit` (number), `offset` (number)
- Response: `{ companies: Company[], pagination: Pagination }`
- Auth: Required (all roles)

**POST /api/v1/companies**
- Request: `{ name: string, address: string, contactEmail: string }`
- Response: `Company`
- Auth: Required (organizer only)
- Validation:
  - `name`: required, min 2, max 255
  - `address`: required, min 5, max 500
  - `contactEmail`: required, valid email format

**GET /api/v1/companies/{id}**
- Response: `Company`
- Auth: Required (all roles)

**PUT /api/v1/companies/{id}**
- Request: `{ name?: string, address?: string, contactEmail?: string }`
- Response: `Company`
- Auth: Required (organizer or company admin)

**DELETE /api/v1/companies/{id}**
- Response: 204 No Content
- Auth: Required (organizer only)
- Error: 409 if company has active events

#### TypeScript Interfaces

```typescript
// Request DTOs
interface CreateCompanyRequest {
  name: string;        // min: 2, max: 255
  address: string;     // min: 5, max: 500
  contactEmail: string; // email format
}

interface UpdateCompanyRequest {
  name?: string;
  address?: string;
  contactEmail?: string;
}

// Response DTOs
interface Company {
  id: string;
  name: string;
  address: string;
  contactEmail: string;
  createdAt: string;
  updatedAt: string;
}

interface CompanyListResponse {
  companies: Company[];
  pagination: {
    total: number;
    limit: number;
    offset: number;
    hasMore: boolean;
  };
}
```

#### Error Codes

- `VALIDATION_ERROR` (400): Invalid input
- `COMPANY_EXISTS` (409): Company name already exists
- `COMPANY_HAS_EVENTS` (409): Cannot delete company with events
- `UNAUTHORIZED` (401): Not authenticated
- `FORBIDDEN` (403): Insufficient permissions
- `NOT_FOUND` (404): Company not found

---

### MSW Mock Configuration

```typescript
// src/mocks/handlers/companyHandlers.ts
import { rest } from 'msw';

const mockCompanies: Company[] = [
  {
    id: '1',
    name: 'Acme Corporation',
    address: '123 Main St, Bern, Switzerland',
    contactEmail: 'contact@acme.com',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z'
  },
  {
    id: '2',
    name: 'Tech Innovations GmbH',
    address: '456 Tech Ave, Zurich, Switzerland',
    contactEmail: 'info@techinnovations.ch',
    createdAt: '2024-02-20T14:30:00Z',
    updatedAt: '2024-02-20T14:30:00Z'
  }
];

export const companyHandlers = [
  // List companies
  rest.get('/api/v1/companies', (req, res, ctx) => {
    const search = req.url.searchParams.get('search');
    let filtered = mockCompanies;

    if (search) {
      filtered = filtered.filter(c =>
        c.name.toLowerCase().includes(search.toLowerCase())
      );
    }

    return res(
      ctx.delay(100),
      ctx.status(200),
      ctx.json({
        companies: filtered,
        pagination: { total: filtered.length, limit: 20, offset: 0, hasMore: false }
      })
    );
  }),

  // Create company
  rest.post('/api/v1/companies', async (req, res, ctx) => {
    const body = await req.json();

    // Simulate duplicate check
    if (mockCompanies.some(c => c.name === body.name)) {
      return res(
        ctx.status(409),
        ctx.json({
          error: {
            code: 'COMPANY_EXISTS',
            message: 'Company name already exists'
          }
        })
      );
    }

    const newCompany = {
      id: crypto.randomUUID(),
      ...body,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    mockCompanies.push(newCompany);

    return res(ctx.status(201), ctx.json(newCompany));
  }),

  // Get company
  rest.get('/api/v1/companies/:id', (req, res, ctx) => {
    const company = mockCompanies.find(c => c.id === req.params.id);
    if (!company) {
      return res(
        ctx.status(404),
        ctx.json({ error: { code: 'NOT_FOUND', message: 'Company not found' } })
      );
    }
    return res(ctx.status(200), ctx.json(company));
  }),

  // Update company
  rest.put('/api/v1/companies/:id', async (req, res, ctx) => {
    const body = await req.json();
    const index = mockCompanies.findIndex(c => c.id === req.params.id);

    if (index === -1) {
      return res(
        ctx.status(404),
        ctx.json({ error: { code: 'NOT_FOUND', message: 'Company not found' } })
      );
    }

    mockCompanies[index] = {
      ...mockCompanies[index],
      ...body,
      updatedAt: new Date().toISOString()
    };

    return res(ctx.status(200), ctx.json(mockCompanies[index]));
  }),

  // Delete company
  rest.delete('/api/v1/companies/:id', (req, res, ctx) => {
    const index = mockCompanies.findIndex(c => c.id === req.params.id);
    if (index === -1) {
      return res(
        ctx.status(404),
        ctx.json({ error: { code: 'NOT_FOUND', message: 'Company not found' } })
      );
    }
    mockCompanies.splice(index, 1);
    return res(ctx.status(204));
  })
];
```

---

### React Components

**Components Created:**
- `CompanyList` - Displays paginated company list
- `CompanyCard` - Individual company display
- `CompanyForm` - Create/edit form with validation
- `CompanyDeleteDialog` - Delete confirmation
- `CompanySearchBar` - Search and filter

**Hooks Created:**
- `useCompanies()` - Query for company list
- `useCreateCompany()` - Mutation for creating
- `useUpdateCompany()` - Mutation for updating
- `useDeleteCompany()` - Mutation for deleting

---

### Acceptance Criteria

1. ✅ Company list displays with search
2. ✅ Create company form validates all fields
3. ✅ Duplicate name shows error message
4. ✅ Edit company updates information
5. ✅ Delete company shows confirmation
6. ✅ Loading states display during operations
7. ✅ Error states handled gracefully
8. ✅ Mobile responsive design
9. ✅ Works completely with MSW mocks
10. ✅ API contract documented for backend team

**Status:** ✅ Done (ready for Story 3.X-2)

---

## Story 3.X-2: Company Management Backend-Integration

**Template:** `story-backend-integration-tmpl.yaml`
**Duration:** 2 days
**Owner:** Backend Developer (or full-stack developer)
**Dependency:** 🔴 Blocked until Story 3.X-1 is Done

### Story

**As an** organizer,
**I want** the company management feature to work with real data persistence,
**so that** company information is permanently stored and available.

**Backend-Integration Focus:** This story implements the backend against the API contract from Story 3.X-1 and replaces MSW mocks with real APIs.

---

### Backend Implementation

#### Database Schema

```sql
-- V10__create_companies_table.sql
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    address VARCHAR(500) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT name_min_length CHECK (LENGTH(name) >= 2),
    CONSTRAINT address_min_length CHECK (LENGTH(address) >= 5)
);

CREATE INDEX idx_companies_name ON companies(name);
CREATE INDEX idx_companies_email ON companies(contact_email);
```

#### Domain Model

```java
@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "contact_email", nullable = false)
    @Email
    private String contactEmail;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Getters, setters, business methods
}
```

#### Service Layer

```java
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final EventRepository eventRepository;

    public Page<Company> findCompanies(String search, Pageable pageable) {
        if (search != null && !search.isEmpty()) {
            return companyRepository.findByNameContaining(search, pageable);
        }
        return companyRepository.findAll(pageable);
    }

    public Company createCompany(CreateCompanyRequest request) {
        if (companyRepository.existsByName(request.getName())) {
            throw new CompanyAlreadyExistsException(request.getName());
        }

        Company company = new Company();
        company.setName(request.getName());
        company.setAddress(request.getAddress());
        company.setContactEmail(request.getContactEmail());

        return companyRepository.save(company);
    }

    public Company updateCompany(UUID id, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new CompanyNotFoundException(id));

        if (request.getName() != null) {
            if (companyRepository.existsByNameAndIdNot(request.getName(), id)) {
                throw new CompanyAlreadyExistsException(request.getName());
            }
            company.setName(request.getName());
        }

        if (request.getAddress() != null) {
            company.setAddress(request.getAddress());
        }

        if (request.getContactEmail() != null) {
            company.setContactEmail(request.getContactEmail());
        }

        return companyRepository.save(company);
    }

    public void deleteCompany(UUID id) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new CompanyNotFoundException(id));

        // Check if company has events
        long eventCount = eventRepository.countByCompanyId(id);
        if (eventCount > 0) {
            throw new CompanyHasEventsException(id, eventCount);
        }

        companyRepository.delete(company);
    }
}
```

#### Controller

```java
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<CompanyListResponse> getCompanies(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<Company> page = companyService.findCompanies(search, pageable);

        return ResponseEntity.ok(new CompanyListResponse(
            page.getContent().stream().map(CompanyMapper::toResponse).toList(),
            new Pagination(
                page.getTotalElements(),
                limit,
                offset,
                page.hasNext()
            )
        ));
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(
        @Valid @RequestBody CreateCompanyRequest request
    ) {
        Company company = companyService.createCompany(request);
        return ResponseEntity
            .created(URI.create("/api/v1/companies/" + company.getId()))
            .body(CompanyMapper.toResponse(company));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable UUID id) {
        Company company = companyService.findById(id);
        return ResponseEntity.ok(CompanyMapper.toResponse(company));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompanyResponse> updateCompany(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCompanyRequest request
    ) {
        Company company = companyService.updateCompany(id, request);
        return ResponseEntity.ok(CompanyMapper.toResponse(company));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable UUID id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Frontend Integration

**Changes Needed:**

```typescript
// .env.production
REACT_APP_USE_MOCKS=false
REACT_APP_API_BASE_URL=https://api.batbern.ch

// No code changes needed in components!
// MSW automatically disabled in production
// React Query hooks work with real API
```

---

### Acceptance Criteria

1. ✅ All API endpoints match contract from Story 3.X-1
2. ✅ Database schema created and migrations applied
3. ✅ Company CRUD operations work with real data
4. ✅ Duplicate name validation prevents conflicts
5. ✅ Cannot delete company with events
6. ✅ MSW mocks disabled in production
7. ✅ Frontend successfully calls real backend
8. ✅ E2E tests passing
9. ✅ Deployed to dev and staging
10. ✅ Ready for production

**Status:** ✅ Done (Feature 3.X Complete!)

---

## Comparison: 2-Story vs 4-Story vs Monolithic

| Aspect | Monolithic | 2-Story Approach | 4-Story Approach |
|--------|------------|------------------|------------------|
| **Stories** | 1 | 2 | 4 |
| **Dev Time** | 5 days | 5 days (3+2) | 5 days (1+2+1+1) |
| **Calendar Time** | 5 days sequential | 3-5 days | 3 days (with 2 devs) |
| **Developers** | 1 full-stack | 1-2 (can be sequential) | 2-3 (parallel) |
| **Coordination** | None (one person) | Low | Medium |
| **Parallelization** | None | Limited | Maximum |
| **Story Size** | Large (5 days) | Medium (2-3 days each) | Small (1-2 days each) |
| **Best For** | Solo dev, prototype | Small team, simple CRUD | Large team, complex features |

---

## Summary

### 2-Story Approach Benefits

✅ **Simpler than 4-story split** - Less overhead, fewer stories to manage
✅ **Frontend-first** - Can demo UI before backend is ready
✅ **Flexible team size** - Works with 1 or 2 developers
✅ **Faster setup** - API contract inline (not separate story)
✅ **Still enables some parallel work** - If you have 2 developers

### When to Use

**Use 2-Story Approach for:**
- Simple CRUD features
- Small teams (1-2 developers)
- Straightforward business logic
- When coordination overhead outweighs parallelization benefits

**Use 4-Story Approach for:**
- Complex features with intricate business logic
- Large teams (3+ developers)
- When maximum parallelization is needed
- Features spanning multiple domains

---

**Total Time for Company Management:**
- Story 3.X-1: 3 days (Frontend-First)
- Story 3.X-2: 2 days (Backend-Integration)
- **Total: 5 days** (or 3 days if working sequentially on both)

Feature is now complete and deployed! 🎉
