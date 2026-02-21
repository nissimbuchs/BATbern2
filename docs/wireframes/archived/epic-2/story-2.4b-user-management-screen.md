# Story 2.5: User Management Screen - Wireframe

**Story**: Epic 2, Story 5 - React Frontend CRUD Foundation (User Management)
**Screen**: User Management Screen (Organizer-only)
**User Role**: ORGANIZER
**Status**: ✅ **IMPLEMENTED IN MVP**
**Related FR**: FR23 (User Management interface), FR22 (User role management)

**Roles**: ORGANIZER, SPEAKER, PARTNER, ATTENDEE (no ADMIN role - organizers have admin privileges)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ← Back                  User Management                       [+ Add User]   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─── SEARCH & FILTERS ──────────────────────────────────────────────────┐  │
│  │                                                                         │  │
│  │   🔍 [Search users by name or email...]         [Clear Filters]       │  │
│  │                                                                         │  │
│  │   Role:      [All Roles ▼]  [ORGANIZER] [SPEAKER] [PARTNER] [ATTENDEE]│  │
│  │   Company:   [All Companies ▼]                                         │  │
│  │   Status:    [⚫ Active] [○ Inactive] [○ All]                          │  │
│  │                                                                         │  │
│  │   Showing 47 users  |  20 per page ▼                                   │  │
│  │                                                                         │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│  ┌─── USER LIST ─────────────────────────────────────────────────────────┐  │
│  │                                                                         │  │
│  │  Name ↑         Email               Company        Roles      Actions  │  │
│  │  ─────────────  ──────────────────  ────────────  ─────────  ────────  │  │
│  │                                                                         │  │
│  │  Anna Müller    anna.m@techcorp.ch  TechCorp AG   🎯 🎤 👤  [•••]     │  │
│  │  👤 Photo                                                              │  │
│  │                                                                         │  │
│  │  ─────────────────────────────────────────────────────────────────────  │  │
│  │                                                                         │  │
│  │  Peter Schmidt peter.s@example.com  SwissBank    🎯         [•••]     │  │
│  │  👤 Photo                                                              │  │
│  │                                                                         │  │
│  │  ─────────────────────────────────────────────────────────────────────  │  │
│  │                                                                         │  │
│  │  Maria Garcia   m.garcia@partner.ch PartnerCo    🏢 👤      [•••]     │  │
│  │  👤 Photo                                                              │  │
│  │                                                                         │  │
│  │  ─────────────────────────────────────────────────────────────────────  │  │
│  │                                                                         │  │
│  │  [... 17 more users ...]                                               │  │
│  │                                                                         │  │
│  │  ─────────────────────────────────────────────────────────────────────  │  │
│  │                                                                         │  │
│  │  ◀ Previous    Page 1 of 3    Next ▶                                   │  │
│  │                                                                         │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘

LEGEND:
🎯 = Organizer  🎤 = Speaker  🏢 = Partner  👤 = Attendee
[•••] = Actions menu (View, Edit Roles, Delete)
```

---

## Key Interactive Elements

### Search & Autocomplete
- **Search Bar**: Real-time autocomplete using `GET /api/v1/users/search`
- **Debouncing**: 300ms delay before API call
- **Performance**: <100ms response with Caffeine cache
- **Clear Button**: Resets all filters and search

### Filter Controls
- **Role Filter**: Multi-select checkboxes (ORGANIZER, SPEAKER, PARTNER, ATTENDEE)
- **Company Filter**: Dropdown populated from company list
- **Status Filter**: Radio buttons (Active, Inactive, All)
- **URL Sync**: Filters persist in URL query parameters

### User Table
- **Sortable Columns**: Click column header to sort (Name, Email, Company)
- **Row Click**: Opens User Detail Modal
- **Photo**: 40×40px profile picture thumbnail
- **Role Badges**: Visual indicators (🎯 🎤 🏢 👤)
- **Actions Menu**: Three-dot menu with View, Edit Roles, Delete options

### Actions
- **[+ Add User]**: Opens User Create Modal
- **[View]**: Opens User Detail Modal (read-only)
- **[Edit Roles]**: Opens Role Manager Modal
- **[Delete]**: Confirmation dialog → GDPR cascade deletion
- **Minimum Organizers Rule**: Prevents deletion if last organizer

### Pagination
- **Items per Page**: 20 (default), 50, 100 options
- **Navigation**: Previous/Next buttons + page numbers
- **Total Count**: "Showing 47 users"

---

## Functional Requirements Met

- **FR23**: User Management interface with CRUD operations
  - ✅ Display all platform users with comprehensive filtering
  - ✅ Search functionality with autocomplete
  - ✅ View detailed user information
  - ✅ Manage role assignments
  - ✅ Intuitive administrative interface

- **FR22**: User role management
  - ✅ Promote/demote users via Role Manager Modal
  - ✅ Enforce minimum 2 organizers rule
  - ✅ Complete audit trails

---

## User Interactions

### Searching Users
1. User types in search bar
2. After 300ms debounce, API call to `GET /api/v1/users/search?query={input}`
3. Autocomplete dropdown shows matching users
4. User selects from dropdown or presses Enter
5. Table updates with filtered results

### Filtering Users
1. User selects role filter (e.g., SPEAKER)
2. API call: `GET /api/v1/users?filter={"role":"SPEAKER"}&include=company,roles`
3. Table updates with filtered users (<150ms)
4. Filter badges appear above table showing active filters

### Viewing User Details
1. User clicks on table row
2. User Detail Modal opens showing full user information
3. Modal displays: Profile, Roles, Company, Activity History
4. [Edit] button available for organizers
5. [Close] or click outside modal to dismiss

### Managing Roles
1. User clicks [Edit Roles] from actions menu
2. Role Manager Modal opens
3. Current roles displayed with checkboxes
4. User toggles roles (ORGANIZER, SPEAKER, PARTNER, ATTENDEE)
5. System validates minimum 2 organizers rule
6. User clicks [Save Changes]
7. API call: `PUT /api/v1/users/{id}/roles`
8. Success toast notification, table refreshes

### Creating User
1. User clicks [+ Add User] button
2. User Create Modal opens with form fields:
   - First Name, Last Name, Email (required)
   - Company (dropdown)
   - Initial Roles (checkboxes)
3. User fills form and clicks [Create User]
4. API call: `POST /api/v1/users`
5. Success toast, table refreshes, new user appears

### Deleting User (GDPR)
1. User clicks [Delete] from actions menu
2. Confirmation dialog appears:
   - "Are you sure you want to delete [User Name]?"
   - "This will permanently delete all user data (GDPR compliance)."
   - [Cancel] [Delete User]
3. If minimum organizers rule violated, error message shown
4. User confirms deletion
5. API call: `DELETE /api/v1/users/{id}`
6. Success toast, table refreshes, user removed

---

## API Requirements

### User List API
```http
GET /api/v1/users?filter={"role":"SPEAKER"}&include=company,roles&page=1&limit=20
Authorization: Bearer {token}
```

**Response:**
```json
{
  "data": [
    {
      "id": "uuid",
      "firstName": "Anna",
      "lastName": "Müller",
      "email": "anna.m@techcorp.ch",
      "roles": ["ORGANIZER", "SPEAKER", "ATTENDEE"],
      "isActive": true,
      "profilePictureUrl": "https://cdn.batbern.ch/...",
      "company": {
        "id": "uuid",
        "name": "TechCorp AG"
      }
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "totalItems": 47,
    "totalPages": 3,
    "hasNext": true,
    "hasPrev": false
  }
}
```

### User Search API
```http
GET /api/v1/users/search?query=Anna&limit=10
Authorization: Bearer {token}
```

**Response:**
```json
[
  {
    "id": "uuid",
    "firstName": "Anna",
    "lastName": "Müller",
    "email": "anna.m@techcorp.ch",
    "roles": ["ORGANIZER", "SPEAKER"],
    "profilePictureUrl": "https://..."
  }
]
```

### Update Roles API
```http
PUT /api/v1/users/{id}/roles
Authorization: Bearer {token}
Content-Type: application/json

{
  "roles": ["ORGANIZER", "SPEAKER"]
}
```

---

## Technical Notes

- **State Management**: Zustand store for user list, filters, pagination state
- **API Client**: React Query for server state, caching, and optimistic updates
- **Performance**: Virtual scrolling for large user lists (>100 users)
- **Caching**: React Query cache with 5-minute stale time
- **Real-time Updates**: WebSocket events for user role changes (optional)
- **Responsive Design**: Table collapses to card view on mobile (<768px)
- **Authorization**: Server-side verification of ORGANIZER role required
- **Internationalization (i18n)**:
  - **Library**: i18next + react-i18next
  - **Namespace**: `userManagement`
  - **Supported Languages**: German (de-CH) primary, English (en-US) secondary
  - **All UI text must be fully translatable**: Buttons, labels, messages, errors, tooltips
  - **Translation hook**: `const { t } = useTranslation('userManagement')`
  - **Language switcher**: Global language switcher in main navigation
  - **User preference**: Language persisted in user profile and localStorage

---

## Internationalization (i18n) Requirements

### Translation Namespace
**Namespace**: `userManagement`

**File Paths**:
- `web-frontend/src/i18n/de/userManagement.json` (German - Primary)
- `web-frontend/src/i18n/en/userManagement.json` (English - Secondary)

### Required Translation Keys

**Screen Titles & Actions**:
```json
{
  "title": "Benutzerverwaltung" / "User Management",
  "addUser": "Benutzer hinzufügen" / "Add User",
  "back": "Zurück" / "Back"
}
```

**Search & Filters**:
```json
{
  "search.placeholder": "Nach Name oder E-Mail suchen..." / "Search users by name or email...",
  "filters.clearAll": "Filter löschen" / "Clear Filters",
  "filters.role.label": "Rolle" / "Role",
  "filters.role.all": "Alle Rollen" / "All Roles",
  "filters.role.organizer": "Organisator" / "Organizer",
  "filters.role.speaker": "Referent" / "Speaker",
  "filters.role.partner": "Partner" / "Partner",
  "filters.role.attendee": "Teilnehmer" / "Attendee",
  "filters.company.label": "Firma" / "Company",
  "filters.company.all": "Alle Firmen" / "All Companies",
  "filters.status.label": "Status" / "Status",
  "filters.status.active": "Aktiv" / "Active",
  "filters.status.inactive": "Inaktiv" / "Inactive",
  "filters.status.all": "Alle" / "All"
}
```

**Table Headers**:
```json
{
  "table.name": "Name" / "Name",
  "table.email": "E-Mail" / "Email",
  "table.company": "Firma" / "Company",
  "table.roles": "Rollen" / "Roles",
  "table.actions": "Aktionen" / "Actions",
  "table.showing": "{{count}} Benutzer anzeigen" / "Showing {{count}} users",
  "table.perPage": "pro Seite" / "per page",
  "table.empty": "Keine Benutzer gefunden" / "No users found",
  "table.emptySearch": "Keine Benutzer entsprechen Ihrer Suche" / "No users match your search"
}
```

**Pagination**:
```json
{
  "pagination.previous": "Vorherige" / "Previous",
  "pagination.next": "Nächste" / "Next",
  "pagination.page": "Seite {{current}} von {{total}}" / "Page {{current}} of {{total}}"
}
```

**Actions Menu**:
```json
{
  "actions.view": "Anzeigen" / "View",
  "actions.editRoles": "Rollen bearbeiten" / "Edit Roles",
  "actions.delete": "Löschen" / "Delete"
}
```

**Modals**:
```json
{
  "modal.userDetail.title": "Benutzerdetails" / "User Details",
  "modal.userCreate.title": "Neuen Benutzer erstellen" / "Create New User",
  "modal.roleManager.title": "Rollen verwalten" / "Manage Roles",
  "modal.delete.title": "Benutzer löschen" / "Delete User",
  "modal.delete.message": "Möchten Sie {{name}} wirklich löschen? Dies wird alle Benutzerdaten dauerhaft löschen (DSGVO-konform)." / "Are you sure you want to delete {{name}}? This will permanently delete all user data (GDPR compliance).",
  "modal.delete.confirm": "Benutzer löschen" / "Delete User",
  "modal.delete.cancel": "Abbrechen" / "Cancel"
}
```

**Form Fields**:
```json
{
  "form.firstName": "Vorname" / "First Name",
  "form.lastName": "Nachname" / "Last Name",
  "form.email": "E-Mail" / "Email",
  "form.company": "Firma" / "Company",
  "form.roles": "Rollen" / "Roles",
  "form.required": "Erforderlich" / "Required",
  "form.optional": "Optional" / "Optional"
}
```

**Validation Errors**:
```json
{
  "error.required": "Dieses Feld ist erforderlich" / "This field is required",
  "error.email.invalid": "Ungültige E-Mail-Adresse" / "Invalid email address",
  "error.email.exists": "E-Mail-Adresse existiert bereits" / "Email address already exists",
  "error.minOrganizers": "Es müssen mindestens 2 Organisatoren im System vorhanden sein" / "System must have at least 2 organizers",
  "error.deleteLastOrganizer": "Sie können den letzten Organisator nicht löschen" / "Cannot delete the last organizer"
}
```

**Success Messages**:
```json
{
  "success.userCreated": "Benutzer erfolgreich erstellt" / "User created successfully",
  "success.userUpdated": "Benutzer erfolgreich aktualisiert" / "User updated successfully",
  "success.userDeleted": "Benutzer erfolgreich gelöscht" / "User deleted successfully",
  "success.rolesUpdated": "Rollen erfolgreich aktualisiert" / "Roles updated successfully"
}
```

**Loading States**:
```json
{
  "loading.users": "Benutzer werden geladen..." / "Loading users...",
  "loading.search": "Suchen..." / "Searching...",
  "loading.delete": "Löschen..." / "Deleting..."
}
```

### Implementation Example

```typescript
// UserList.tsx
import { useTranslation } from 'react-i18next';

const UserList: React.FC = () => {
  const { t } = useTranslation('userManagement');

  return (
    <Box>
      <Typography variant="h4">
        {t('title')}
      </Typography>

      <Button onClick={handleAddUser}>
        {t('addUser')}
      </Button>

      <TextField
        placeholder={t('search.placeholder')}
        // ...
      />

      <Table>
        <TableHead>
          <TableRow>
            <TableCell>{t('table.name')}</TableCell>
            <TableCell>{t('table.email')}</TableCell>
            <TableCell>{t('table.company')}</TableCell>
            <TableCell>{t('table.roles')}</TableCell>
            <TableCell>{t('table.actions')}</TableCell>
          </TableRow>
        </TableHead>
        {/* ... */}
      </Table>
    </Box>
  );
};
```

### Translation Validation

**Pre-commit Hook**: All translation keys must exist in both `de` and `en` files
**CI/CD**: Build fails if translation keys are missing or out of sync
**Testing**: Component tests verify translations render correctly in both languages

---

## Navigation Map

### Primary Actions
1. **← Back** → Navigate to Organizer Dashboard
2. **[+ Add User]** → Open User Create Modal
3. **Click row** → Open User Detail Modal
4. **[•••] Actions** → Dropdown menu (View, Edit Roles, Delete)

### Modal Navigation
1. **User Detail Modal** → View user information, option to edit
2. **User Create Modal** → Form to create new user
3. **Role Manager Modal** → Manage user role assignments
4. **Delete Confirmation Dialog** → Confirm GDPR deletion

---

## Accessibility Notes

- **ARIA Labels**: All interactive elements labeled
- **Keyboard Navigation**: Full keyboard support (Tab, Enter, Esc)
- **Screen Reader**: Table headers, row count, filter states announced
- **Focus Management**: Modal focus trap, return focus on close
- **Color Contrast**: WCAG 2.1 AA compliant
- **Role Badges**: Alt text for screen readers

---

## State Management

### Local Component State
- `searchQuery: string` - Current search input
- `filters: UserFilters` - Active filters (role, company, status)
- `selectedUser: User | null` - User for detail modal
- `page: number` - Current page number
- `limit: number` - Items per page

### Server State (React Query)
- Query Key: `['users', { filter, page, limit }]`
- Stale Time: 5 minutes
- Cache Time: 30 minutes
- Refetch on window focus: true

---

## Form Validation Rules

### User Create Form
- **First Name**: Required, 2-100 characters, letters and spaces only
- **Last Name**: Required, 2-100 characters, letters and spaces only
- **Email**: Required, valid email format, unique (server-side check)
- **Company**: Optional, must be valid company UUID if provided
- **Roles**: At least one role required

### Role Management
- **Minimum Organizers**: System must have at least 2 ORGANIZER users
- **Role Validation**: Cannot remove ORGANIZER role if it would violate minimum rule
- **Audit Trail**: All role changes logged with timestamp, user, and reason

---

## Edge Cases & Error Handling

- **Empty State - No Users**: Show "No users found" with friendly icon and message
- **Empty State - No Search Results**: Show "No users match your search" with suggestion to clear filters
- **Loading State - User List**: Display skeleton loaders for table rows during data fetch
- **Loading State - Search**: Show spinner in search bar during autocomplete
- **Error State - List Load Failed**: Show error message with [Retry] button
- **Error State - Delete Failed**: Show inline error message with specific reason
- **Permission Denied - Edit User**: If user tries to edit another organizer without permission
- **Network Offline**: Show offline indicator, cache data for offline viewing
- **Slow Connection**: Progressive loading - show user names first, then details
- **Concurrent Edits**: Detect if user was modified during edit session, warn before overwriting
- **Session Expired During Edit**: Detect 401 response, prompt to re-authenticate without losing form data
- **Minimum Organizers Violation**: Show clear error message when attempting to delete/demote last organizer

---

## Responsive Design Considerations

### Mobile Layout Changes (<768px)

- **Table to Card View**: User list transforms to card layout
- **Compact Filters**: Filters collapse into expandable panel
- **Search Bar**: Full-width search with larger touch target
- **Actions Menu**: Bottom sheet instead of dropdown
- **Pagination**: Simplified controls (Previous/Next only)
- **Profile Photos**: Larger 60×60px on mobile cards

### Tablet Layout (768px - 1024px)

- **Table Layout Maintained**: Compact column widths
- **Filter Panel**: Side panel or collapsed by default
- **Touch Targets**: All buttons minimum 44×44px
- **Modal Sizing**: Modals take 80% screen width

### Desktop Layout (>1024px)

- **Full Table**: All columns visible
- **Filter Sidebar**: Persistent filter panel on left
- **Hover States**: Row hover effects, tooltip on truncated text
- **Multi-select**: Bulk actions for multiple users (optional)

---

## Performance Optimization

### Initial Load
- **Target**: <150ms P95 for user list with 20 users
- **Optimization**: Database indexes on email, company_id, roles
- **Caching**: React Query cache with 5-minute stale time

### Search
- **Target**: <100ms P95 with Caffeine cache hit
- **Debouncing**: 300ms delay to reduce API calls
- **Cache**: Caffeine in-memory cache on backend (10-minute TTL)

### Filtering
- **Target**: <150ms P95 with filters and includes
- **Optimization**: JSON filter syntax allows single API call
- **Resource Expansion**: `?include=company,roles` reduces requests from 3 to 1

### Pagination
- **Strategy**: Server-side pagination (20/50/100 per page)
- **Prefetching**: Prefetch next page in background
- **Total Count**: Cached for 5 minutes to avoid repeated COUNT queries

---

## Security Considerations

### Authorization
- **Role Check**: ORGANIZER role required for all operations
- **Server-side Validation**: All role checks enforced on backend
- **Audit Logging**: All user management actions logged with user ID, timestamp, IP

### Data Protection
- **Email Privacy**: Email visible only to organizers
- **Password**: Never displayed or transmitted (managed by Cognito)
- **Profile Pictures**: Served via CloudFront CDN with presigned URLs

### Business Rules
- **Minimum Organizers**: Enforced on both frontend and backend
- **Cascade Deletion**: GDPR-compliant deletion across all domain services
- **Audit Trail**: Complete history of role changes and user modifications

---

## Change Log

| Date       | Version | Description                                        | Author      |
|------------|---------|----------------------------------------------------|-------------|
| 2025-10-19 | 1.0     | Initial wireframe creation for User Management screen | John (PM) |

---

## Review Notes

### Stakeholder Feedback
- Awaiting initial review from frontend team and UX designer

### Design Iterations
- v1.0: Initial design based on FR23, FR22 requirements and company-management-screen.md pattern

### Open Questions
- **Q1**: Should we add bulk actions (e.g., bulk role assignment, bulk delete)?
- **Q2**: Should we display last login time in the user list?
- **Q3**: Do we need export functionality (CSV/Excel) for user list?
- **Q4**: Should activity history be visible in the User Detail Modal?
