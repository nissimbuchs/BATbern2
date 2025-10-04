# Company Management Screen - Wireframe

**Story**: Epic 1, Story 1.14 - Company Management Service Foundation
**Screen**: Company Management Screen
**User Role**: Organizer (primary), Speaker (limited view/create)
**Related FR**: FR3 (Core functionality), FR12 (Partner coordination)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard        Company Management           [+ Add New Company]          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── SEARCH & FILTERS ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  [🔍 Search companies...]                    [Partner Only ☐] [Verified Only ☐] │ │
│  │                                                                                   │ │
│  │  Industry: [All ▾]  Employees: [All ▾]  Sort By: [Name A-Z ▾]                   │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── COMPANY LIST ──────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Showing 23 companies                                            [Grid ▣] [List ☰]│ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [Logo]  TechCorp AG                                    ⭐ PARTNER ✅ VERIFIED│  │ │
│  │  │         Zurich, Switzerland                                                 │  │ │
│  │  │         Cloud Computing & DevOps • 142 Employees                           │  │ │
│  │  │         🔗 techcorp.ch                                                      │  │ │
│  │  │         [View Details] [Edit] [12 Associated Speakers →]                   │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [Logo]  SwissBank Ltd                                              ✅ VERIFIED│  │ │
│  │  │         Basel, Switzerland                                                  │  │ │
│  │  │         Financial Services • 523 Employees                                  │  │ │
│  │  │         🔗 swissbank.ch                                                     │  │ │
│  │  │         [View Details] [Edit] [5 Associated Speakers →]                    │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [📷]    Digital Solutions GmbH                                 ⏳ PENDING    │  │ │
│  │  │         Bern, Switzerland                                                   │  │ │
│  │  │         Software Development • 45 Employees                                 │  │ │
│  │  │         🔗 digitalsol.ch                                                    │  │ │
│  │  │         [View Details] [Edit] [2 Associated Speakers →]                    │  │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [Load More...] (20 more companies)                                              │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Create/Edit Company Modal

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [X] Create New Company                                                                │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── COMPANY DETAILS ───────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Company Name *                                                                   │ │
│  │  [_______________________________________________________________________]        │ │
│  │  💡 Start typing to see suggestions and check for duplicates                     │ │
│  │                                                                                   │ │
│  │  Display Name (if different from legal name)                                     │ │
│  │  [_______________________________________________________________________]        │ │
│  │                                                                                   │ │
│  │  Swiss UID (Optional - for automatic verification)                               │ │
│  │  [CHE-___.___.___]  [🔍 Verify]                                                  │ │
│  │  ℹ️ We'll validate this with the Swiss UID register                              │ │
│  │                                                                                   │ │
│  │  Website                                                                          │ │
│  │  [https://_______________________________________________________________]        │ │
│  │                                                                                   │ │
│  │  Industry *                                                                       │ │
│  │  [Select Industry ▾]                                                             │ │
│  │  [Cloud Computing] [DevOps] [Financial Services] [Healthcare] [...]              │ │
│  │                                                                                   │ │
│  │  Sector (Optional)                                                                │ │
│  │  [Select Sector ▾]                                                               │ │
│  │  [Public] [Private] [Non-profit] [Government]                                    │ │
│  │                                                                                   │ │
│  │  Number of Employees                                                              │ │
│  │  [1-10 ▾] [10-50] [50-200] [200-1000] [1000+]                                   │ │
│  │                                                                                   │ │
│  │  Location                                                                         │ │
│  │  City: [________________] Canton: [Select Canton ▾] Country: [Switzerland ▾]     │ │
│  │                                                                                   │ │
│  │  Description                                                                      │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Brief description of the company's activities and expertise...            │  │ │
│  │  │                                                                            │  │ │
│  │  │                                                                            │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │  0/500 characters                                                                │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── LOGO UPLOAD ───────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Company Logo                                                                     │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │                                                                            │  │ │
│  │  │         📁  Drag & drop logo here or click to browse                      │  │ │
│  │  │                                                                            │  │ │
│  │  │         Accepted: PNG, JPEG, SVG • Max size: 5 MB                         │  │ │
│  │  │         Recommended: 500x500px square, transparent background             │  │ │
│  │  │                                                                            │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  Preview:                                                                         │ │
│  │  ┌─────────┐                                                                     │ │
│  │  │ [Logo]  │ ✅ logo-techcorp.png (245 KB) [Remove]                              │ │
│  │  └─────────┘                                                                     │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PARTNER STATUS (Organizer Only) ──────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  ⭐ Partner Status                                                                │ │
│  │  [☐ This company is a BATbern partner]                                           │ │
│  │                                                                                   │ │
│  │  Partner Benefits (when enabled):                                                │ │
│  │  • Priority speaker invitations                                                  │ │
│  │  • Enhanced analytics dashboard access                                           │ │
│  │  • Topic voting privileges                                                       │ │
│  │  • Logo display on event pages                                                   │ │
│  │                                                                                   │ │
│  │  Partner Level: [Bronze ▾] [Silver] [Gold] [Platinum]                           │ │
│  │  Partnership Start Date: [DD/MM/YYYY]                                            │ │
│  │  Partnership End Date: [DD/MM/YYYY]                                              │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── VERIFICATION ──────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Verification Status: ⏳ PENDING                                                  │ │
│  │                                                                                   │ │
│  │  [☐ Manually verify this company] (Organizer only)                              │ │
│  │                                                                                   │ │
│  │  Verification Method:                                                             │ │
│  │  • Swiss UID Register: ⏳ Pending validation                                      │ │
│  │  • Website Domain: ✅ Verified (techcorp.ch)                                      │ │
│  │  • Manual Review: ⏳ Pending organizer approval                                   │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│                                                   [Cancel] [Save Draft] [Save & Create]│
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Company Detail View

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Companies        TechCorp AG                    [Edit] [⚙ Settings]        │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── COMPANY PROFILE ───────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  ┌────────┐  TechCorp AG                                    ⭐ PARTNER ✅ VERIFIED│ │
│  │  │        │  Cloud Computing & DevOps                                            │ │
│  │  │  Logo  │  Zurich, Switzerland                                                 │ │
│  │  │        │  🔗 techcorp.ch                                                      │ │
│  │  └────────┘  Swiss UID: CHE-123.456.789                                          │ │
│  │             Employees: 142 • Sector: Private • Founded: 2010                     │ │
│  │                                                                                   │ │
│  │  ┌─── DESCRIPTION ───────────────────────────────────────────────────────────┐  │ │
│  │  │ Leading provider of cloud-native solutions and DevOps consulting services │  │ │
│  │  │ in Switzerland. Specializing in Kubernetes orchestration, infrastructure  │  │ │
│  │  │ automation, and cloud migration strategies for enterprise clients.        │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ASSOCIATED SPEAKERS & EMPLOYEES ───────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  12 Associated Speakers                                       [+ Link Speaker]    │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [Photo] Dr. Peter Muller • Principal Cloud Architect                      │  │ │
│  │  │         8 Talks • 4.7★ Average Rating                                      │  │ │
│  │  │         [View Profile →]                                                   │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ [Photo] Anna Schmidt • Senior DevOps Engineer                             │  │ │
│  │  │         5 Talks • 4.9★ Average Rating                                      │  │ │
│  │  │         [View Profile →]                                                   │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  [View All Speakers (12) →]                                                       │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PARTNER INFORMATION (If Partner) ─────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Partnership Level: Gold Partner                                                 │ │
│  │  Partnership Period: Jan 2023 - Dec 2025                                         │ │
│  │  Primary Contact: Maria Weber (maria.weber@techcorp.ch)                          │ │
│  │                                                                                   │ │
│  │  Partnership Benefits Active:                                                     │ │
│  │  ✅ Priority speaker invitations                                                  │ │
│  │  ✅ Enhanced analytics dashboard                                                  │ │
│  │  ✅ Topic voting (3 votes per event)                                              │ │
│  │  ✅ Logo on event pages and marketing materials                                   │ │
│  │                                                                                   │ │
│  │  [📊 View Partner Analytics →]                                                    │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── STATISTICS ────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Event Participation                                                              │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ Total Events with Speakers:     18 events                                 │  │ │
│  │  │ Total Presentations:            42 presentations                          │  │ │
│  │  │ Total Attendees from Company:   156 attendees                            │  │ │
│  │  │ First Event:                    Spring 2015                               │  │ │
│  │  │ Most Recent Event:              Autumn 2024                               │  │ │
│  │  └───────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                   │ │
│  │  Topic Expertise (from speaker presentations)                                     │ │
│  │  [Kubernetes: 12] [Cloud Architecture: 8] [DevOps: 15] [CI/CD: 7] [...]          │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ACTIVITY HISTORY ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Recent Activity                                                                  │ │
│  │  • 2024-03-10: Dr. Peter Muller invited to speak at Spring 2024 Conference       │ │
│  │  • 2024-02-15: Company verified via Swiss UID register                            │ │
│  │  • 2024-01-20: Partnership renewed until Dec 2025                                 │ │
│  │  • 2023-11-05: Anna Schmidt confirmed for Autumn 2023 workshop                    │ │
│  │                                                                                   │ │
│  │  [View Full History →]                                                            │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Company List View
- **Search Bar**: Real-time autocomplete search with Redis-backed suggestions
- **Filter Controls**: Partner status, verification status, industry, employee count
- **View Toggle**: Switch between grid and list view
- **Sort Dropdown**: Sort by name, industry, number of speakers, partnership status
- **Company Cards**: Clickable cards showing key company information
- **Quick Actions**: View, Edit, View Speakers buttons
- **Load More**: Pagination or infinite scroll for large company lists

### Create/Edit Company Form
- **Company Name Field**: Real-time duplicate detection and name suggestions
- **UID Verification**: Swiss UID register integration for automatic verification
- **Logo Upload**: Drag-and-drop file upload with preview and validation
- **Industry Tags**: Multi-select industry/topic tags
- **Partner Toggle**: Enable/disable partner status with privilege configuration
- **Verification Controls**: Manual verification checkbox (organizer-only)
- **Save Options**: Save draft, save and continue, save and close

### Company Detail View
- **Profile Section**: Complete company information display
- **Speaker List**: Associated speakers with quick links to profiles
- **Partner Info**: Partnership details and benefits (if applicable)
- **Statistics Dashboard**: Event participation and engagement metrics
- **Activity Timeline**: Recent company-related activities
- **Action Buttons**: Edit company, manage speakers, view analytics

---

## Functional Requirements Met

- **FR3**: Core company management with CRUD operations, Swiss UID validation, and employee relationships
- **FR12**: Partner company management with special status indicators, partnership levels, and benefit tracking
- **Logo Management**: S3-based logo upload with CDN integration for fast delivery
- **Search & Discovery**: Redis-backed autocomplete search for efficient company lookup
- **Data Integrity**: Duplicate detection, UID verification, and data validation
- **Relationship Management**: Employee-company associations visible and manageable

---

## User Interactions

### For Organizers (Full Access)
1. **Create Company**: Click [+ Add New Company] → Fill form → Upload logo → Set partner status → Save
2. **Search Companies**: Type in search bar → Filter by partner/verified status → Sort results
3. **Edit Company**: Click company card → Click [Edit] → Modify fields → Save changes
4. **Verify Company**: Open company → Check verification → Click [Manually verify] (if needed)
5. **Manage Partner Status**: Edit company → Toggle partner status → Set partnership level → Save
6. **Link Speakers**: Company detail → Click [+ Link Speaker] → Search and select speaker
7. **View Statistics**: Company detail → Review participation metrics and topic expertise
8. **View Analytics**: Click [📊 View Partner Analytics] (for partner companies)

### For Speakers (Limited Access)
9. **Create Own Company**: Click [+ Add New Company] → Fill basic details → Submit for verification
10. **View Company**: Search and view company details (read-only for others' companies)
11. **Update Own Company**: Edit company they created or are affiliated with
12. **See Associated Speakers**: View list of colleagues from same company

### Common Interactions
13. **Autocomplete Search**: Start typing company name → Select from suggestions
14. **Filter Results**: Apply filters → Results update automatically
15. **Toggle View**: Switch between grid and list view for companies
16. **Upload Logo**: Drag file to upload area OR click to browse → Preview → Confirm
17. **UID Verification**: Enter Swiss UID → Click [🔍 Verify] → Automatic validation

---

## Technical Notes

### Component Structure
- **CompanyManagementScreen.tsx**: Main screen component with search and list
- **CompanyList.tsx**: Paginated company list with grid/list views
- **CompanyCard.tsx**: Individual company card component
- **CompanyForm.tsx**: Create/edit form with validation
- **CompanyDetailView.tsx**: Detailed company profile display
- **LogoUpload.tsx**: Drag-and-drop logo upload component
- **CompanySearch.tsx**: Autocomplete search with Redis backend
- **PartnerManagementPanel.tsx**: Partner status and benefits configuration

### State Management
- **Local State**: Form data, upload progress, UI toggles (grid/list)
- **Zustand Store**: Company search cache, filter preferences
- **React Query**: Server state for companies, speakers, statistics
  - `companies` query: Cached for 5 minutes
  - `companyDetail` query: Cached for 10 minutes
  - `companySuggestions` query: Cached for 15 minutes

### API Integration
- **Search**: `GET /api/v1/companies?query={text}&isPartner={bool}` (autocomplete)
- **Create**: `POST /api/v1/companies` (multipart/form-data for logo)
- **Logo Upload**: Presigned S3 URL workflow
  - Request: `POST /api/v1/files/presigned-upload-url`
  - Upload: Direct to S3
  - Confirm: `POST /api/v1/files/{fileId}/confirm`
- **Update**: `PUT /api/v1/companies/{companyId}`
- **Delete**: `DELETE /api/v1/companies/{companyId}`
- **Get Detail**: `GET /api/v1/companies/{companyId}`
- **Link Speaker**: `POST /api/v1/companies/{companyId}/speakers/{speakerId}`

### File Upload Workflow
1. User selects logo file (drag-and-drop or browse)
2. Client validates file (type: PNG/JPEG/SVG, size: max 5MB)
3. Client requests presigned URL: `POST /api/v1/files/presigned-upload-url`
4. Client uploads directly to S3 using presigned URL
5. Client confirms upload: `POST /api/v1/files/{fileId}/confirm`
6. Server returns CDN URL for logo display
7. Client updates company with logo CDN URL

### Validation Rules
- **Company Name**: Required, 2-200 characters, duplicate check
- **UID Format**: CHE-XXX.XXX.XXX (Swiss format), optional
- **Logo**: PNG/JPEG/SVG, max 5MB, recommended 500x500px
- **Website**: Valid URL format, https preferred
- **Industry**: Required, select from predefined list
- **Description**: Max 500 characters

### Caching Strategy
- **Redis Autocomplete**: Company name suggestions cached with 15-minute TTL
- **React Query**: Company details cached for 10 minutes
- **CDN**: Logo files cached with CloudFront (1-year max-age)
- **Search Results**: Cached for 5 minutes, invalidated on company updates

### Security Considerations
- **Role-Based Access**: Organizers have full access, speakers limited to create/edit own
- **Logo Upload**: Presigned URLs valid for 15 minutes, MIME type validation
- **UID Verification**: Swiss UID register API integration with rate limiting
- **Duplicate Prevention**: Server-side duplicate detection before creation
- **Audit Trail**: All company changes logged with user ID and timestamp

---

## API Requirements

### Initial Page Load APIs

When the Company Management Screen loads, the following APIs are called:

1. **GET /api/v1/companies**
   - Query params: `limit=20, offset=0, sortBy=name, order=asc`
   - Returns: Paginated list of companies with basic info
   - Used for: Initial company list display
   - Response includes: id, name, displayName, isPartner, logo CDN URL, industry, employeeCount, verificationStatus

2. **GET /api/v1/companies/stats**
   - Returns: Overall statistics
   - Used for: Display total companies, partners, verified count
   - Response: `{ totalCompanies: 23, partnerCount: 5, verifiedCount: 18 }`

3. **GET /api/v1/companies/industries**
   - Returns: List of available industries for filtering
   - Used for: Populate industry filter dropdown
   - Response: `{ industries: ["Cloud Computing", "Financial Services", ...] }`

### Action APIs

APIs called by user interactions:

#### Search & Filter

4. **GET /api/v1/companies**
   - Triggered by: Search input (debounced), filter changes, sort changes
   - Query params: `query={searchText}&isPartner={bool}&industry={industry}&limit=20&offset=0`
   - Returns: Filtered and paginated company list
   - Used for: Real-time search and filtering

5. **GET /api/v1/companies/suggestions**
   - Triggered by: Search input (autocomplete)
   - Query params: `q={searchText}&limit=10`
   - Returns: Autocomplete suggestions from Redis cache
   - Response: `{ suggestions: [{ id, name, displayName, logo }] }`
   - Used for: Autocomplete dropdown

#### CRUD Operations

6. **POST /api/v1/companies**
   - Triggered by: [Save & Create] button in create form
   - Content-Type: `multipart/form-data`
   - Payload: Company data + logo file (if provided)
   - Returns: Created company with ID and CDN URLs
   - Side effects: Creates company, uploads logo to S3, publishes CompanyCreatedEvent

7. **PUT /api/v1/companies/{companyId}**
   - Triggered by: [Save] button in edit form
   - Payload: Updated company data
   - Returns: Updated company object
   - Side effects: Updates company, invalidates cache, publishes CompanyUpdatedEvent

8. **DELETE /api/v1/companies/{companyId}**
   - Triggered by: [Delete] button confirmation
   - Returns: `{ success: true }`
   - Side effects: Soft delete company, removes from search index, publishes CompanyDeletedEvent

9. **GET /api/v1/companies/{companyId}**
   - Triggered by: Click company card, navigate to detail view
   - Returns: Complete company details
   - Used for: Detail view display

#### Logo Management (Presigned URL Workflow)

10. **POST /api/v1/files/presigned-upload-url**
    - Triggered by: Logo file selected in upload component
    - Payload: `{ filename, contentType: "logo", fileSizeBytes, mimeType }`
    - Returns: `{ uploadUrl, fileId, expiresIn: 900, requiredHeaders }`
    - Used for: Generate S3 presigned upload URL

11. **POST /api/v1/files/{fileId}/confirm**
    - Triggered by: After successful S3 upload
    - Payload: `{ checksum: "SHA-256 hash" }`
    - Returns: `{ fileId, status: "completed", cdnUrl }`
    - Used for: Confirm upload and get CDN URL
    - Side effects: Activates file, returns CloudFront URL

12. **DELETE /api/v1/files/{fileId}**
    - Triggered by: [Remove] logo button
    - Returns: `{ success: true }`
    - Used for: Delete logo from S3
    - Side effects: Soft delete file, update storage quota

#### Verification & Partner Management

13. **POST /api/v1/companies/{companyId}/verify**
    - Triggered by: [🔍 Verify] button for UID verification
    - Payload: `{ uid: "CHE-XXX.XXX.XXX" }`
    - Returns: `{ verified: true, source: "uid_register", verifiedAt }`
    - Used for: Automatic Swiss UID verification
    - Side effects: Updates verification status

14. **POST /api/v1/companies/{companyId}/partner-status**
    - Triggered by: Partner status toggle (organizer-only)
    - Payload: `{ isPartner: true, partnerLevel: "gold", startDate, endDate }`
    - Returns: Updated company with partner status
    - Side effects: Publishes PartnerStatusChangedEvent

#### Speaker Association

15. **GET /api/v1/companies/{companyId}/speakers**
    - Triggered by: Company detail view load
    - Query params: `limit=10, offset=0`
    - Returns: Paginated list of associated speakers
    - Used for: Display speaker list in detail view

16. **POST /api/v1/companies/{companyId}/speakers/{speakerId}**
    - Triggered by: [+ Link Speaker] button
    - Returns: `{ success: true, relationship }`
    - Used for: Link speaker to company
    - Side effects: Creates employee-company relationship

17. **DELETE /api/v1/companies/{companyId}/speakers/{speakerId}**
    - Triggered by: [Unlink] speaker button
    - Returns: `{ success: true }`
    - Used for: Remove speaker-company association

#### Statistics & Analytics

18. **GET /api/v1/companies/{companyId}/statistics**
    - Triggered by: Company detail view load
    - Returns: Participation statistics
    - Response: `{ totalEvents, totalPresentations, totalAttendees, firstEvent, mostRecentEvent, topicExpertise }`
    - Used for: Statistics section in detail view

19. **GET /api/v1/companies/{companyId}/activity**
    - Triggered by: Company detail view load
    - Query params: `limit=5, offset=0`
    - Returns: Recent activity timeline
    - Used for: Activity history section

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard** → Navigate to Event Management Dashboard
   - Type: Full page navigation
   - Target: [story-1.16-event-management-dashboard.md](story-1.16-event-management-dashboard.md)
   - Context: Returns to main organizer portal

2. **[+ Add New Company] button** → Opens create company modal
   - Type: Modal overlay
   - Opens: Create Company Form (inline modal)
   - Role-based access: Organizer (full), Speaker (basic create only)
   - Closes on: Save, Cancel, or ESC key

3. **Company Card click** → Navigate to company detail view
   - Type: Full page navigation
   - Target: Company Detail View (same file, different section)
   - Context: Company ID passed as route parameter

4. **[Edit] button** → Opens edit company modal
   - Type: Modal overlay
   - Opens: Edit Company Form (inline modal)
   - Pre-fills: Current company data
   - Role-based access: Organizer (all companies), Speaker (own company only)

### Secondary Navigation (Data Interactions)

5. **[View Details] button** → Navigate to company detail view
   - Type: Full page navigation
   - Same as company card click

6. **[12 Associated Speakers →] link** → Navigate to filtered speaker list
   - Type: Full page navigation
   - Target: Speaker Matching Interface [story-3.1-speaker-matching-interface.md](story-3.1-speaker-matching-interface.md)
   - Context: Pre-filtered by company ID

7. **Search input (autocomplete)** → Show suggestions dropdown
   - Type: In-place dropdown
   - No navigation, displays suggestions below search bar
   - Click suggestion → Filters company list

8. **Filter controls** → Update company list
   - Type: In-place content update
   - No navigation, updates company list with filtered results
   - Visual feedback: Loading spinner during API call

9. **[Grid/List] toggle** → Switch view mode
   - Type: In-place view change
   - No navigation, re-renders company list in different layout

10. **[Load More] button** → Load additional companies
    - Type: In-place content expansion
    - API call: Increments offset, appends results
    - Visual feedback: Loading spinner, then additional cards appear

### Company Detail View Navigation

11. **[View Profile →] link (Speaker card)** → Navigate to speaker profile
    - Type: Full page navigation
    - Target: Speaker Profile Detail View [story-7.1-speaker-profile-detail-view.md](story-7.1-speaker-profile-detail-view.md)
    - Context: Speaker ID passed as route parameter

12. **[+ Link Speaker] button** → Opens speaker search modal
    - Type: Modal overlay
    - Opens: Speaker search and selection modal
    - API call: `POST /api/v1/companies/{companyId}/speakers/{speakerId}` on selection

13. **[📊 View Partner Analytics →] button** → Navigate to partner analytics
    - Type: Full page navigation
    - Target: Partner Analytics Dashboard [story-6.1-partner-analytics.md](story-6.1-partner-analytics.md)
    - Context: Company ID and partner data passed
    - Role-based access: Organizer and Partner users only

14. **[⚙ Settings] button** → Opens company settings modal
    - Type: Modal overlay
    - Opens: Company settings (advanced configuration)
    - Includes: Notifications, visibility, data export options

15. **[View All Speakers (12) →] link** → Navigate to speaker list
    - Type: Full page navigation
    - Target: Speaker Matching Interface [story-3.1-speaker-matching-interface.md](story-3.1-speaker-matching-interface.md)
    - Context: Filtered by company

16. **[View Full History →] link** → Opens activity history modal or page
    - Type: Modal or full page (depending on data volume)
    - Shows: Complete activity timeline for company
    - API call: `GET /api/v1/companies/{companyId}/activity` with full pagination

### Logo Upload Workflow Navigation

17. **Logo upload area click** → Opens file browser
    - Type: OS file dialog
    - No navigation, returns to form with selected file

18. **Drag & drop logo** → Upload and preview
    - Type: In-place file upload
    - Shows: Upload progress bar, then preview
    - API calls:
      - `POST /api/v1/files/presigned-upload-url`
      - Direct S3 upload via presigned URL
      - `POST /api/v1/files/{fileId}/confirm`

19. **[Remove] logo button** → Delete logo and clear preview
    - Type: In-place action
    - API call: `DELETE /api/v1/files/{fileId}` (if already uploaded)
    - Visual feedback: Preview clears, upload area resets

### Error States & Redirects

20. **Company not found** → Navigate to company list with error
    - Type: Full page navigation with error message
    - Target: Company Management Screen (list view)
    - Message: "Company not found or has been deleted"

21. **Unauthorized edit attempt** → Show permission error
    - Type: Inline error message
    - Message: "You don't have permission to edit this company"
    - No automatic navigation, edit button disabled

22. **API error during save** → Show error state
    - Type: In-page error message above form
    - Message: "Unable to save company. Please try again."
    - Action: [Retry] button to resubmit

23. **Network error** → Show offline state
    - Type: Banner notification
    - Message: "You are offline. Changes will be saved when connection is restored."
    - Shows: Last cached data

24. **Upload quota exceeded** → Show quota error
    - Type: Modal dialog
    - Message: "Storage quota exceeded. Please remove old files or contact support."
    - Action: [Manage Files] button → User file management screen

### Form Submission Flows

25. **[Save Draft] button** → Save without validation
    - Type: API call with partial data
    - No navigation, shows success toast: "Draft saved"
    - Allows: Incomplete data for later completion

26. **[Save & Create] button** → Validate and save
    - Type: API call with full validation
    - On success: Close modal, refresh company list, show toast: "Company created successfully"
    - On validation error: Show inline errors, keep modal open

27. **[Cancel] button** → Discard changes
    - Type: Close modal
    - If unsaved changes: Show confirmation dialog
    - Confirmation: "Discard unsaved changes?"
    - Actions: [Discard] [Keep Editing]

---

## Responsive Design Considerations

### Mobile Layout Changes

- **Search Bar**: Full-width search with filters in collapsible panel
- **Company Cards**: Full-width cards, stacked vertically
- **Logo Upload**: Simplified upload button (no drag-and-drop on mobile)
- **Form Fields**: Full-width fields, stacked layout
- **Partner Status**: Collapse benefits into expandable accordion
- **Statistics**: Swipeable cards for different stat categories

### Tablet Layout Changes

- **Company Cards**: Two-column grid layout
- **Search & Filters**: Side-by-side layout with search and filters
- **Form Layout**: Two-column form for better space utilization
- **Detail View**: Side-by-side profile and statistics sections

### Mobile-Specific Interactions

- **Swipe to Delete**: Swipe left on company card to reveal delete option
- **Pull to Refresh**: Refresh company list by pulling down
- **Bottom Sheet**: Open filters and actions in bottom sheet instead of dropdown
- **FAB Button**: Floating action button for [+ Add New Company]
- **Sticky Header**: Company name sticky at top when scrolling detail view

---

## Accessibility Notes

- **Keyboard Navigation**: All interactive elements accessible via Tab key
- **ARIA Labels**:
  - `aria-label="Search companies"` on search input
  - `aria-label="Upload company logo"` on upload area
  - `aria-label="Mark as partner"` on partner checkbox
  - `aria-label="Verify company via Swiss UID"` on verify button
- **Focus Indicators**: 2px solid border on focused elements
- **Screen Reader Support**:
  - Form fields have associated labels
  - Error messages announced with `aria-live="polite"`
  - Loading states announced: "Loading companies..."
  - Success/error toasts announced automatically
- **Color Contrast**: All text meets WCAG 2.1 AA standards (4.5:1 minimum)
- **Alt Text**: Company logos include descriptive alt text: "Company name logo"
- **Skip Links**: "Skip to company list" link for keyboard users
- **ARIA Live Regions**: Search results and filters update announced to screen readers

---

## State Management

### Local Component State

- `searchQuery`: string - Current search text
- `filters`: object - Active filters (isPartner, industry, employeeCount)
- `viewMode`: 'grid' | 'list' - Current view mode
- `sortBy`: string - Current sort field
- `isModalOpen`: boolean - Create/edit modal state
- `selectedCompany`: Company | null - Currently selected company for editing
- `uploadProgress`: number - Logo upload progress percentage

### Global State (Zustand Store)

- `companies.searchCache`: Map - Cached search results
- `companies.filterPreferences`: object - User's saved filter preferences
- `auth.currentUser`: User - Current authenticated user
- `auth.currentRole`: UserRole - Current user role (organizer, speaker)
- `ui.notifications`: Notification[] - Toast notifications

### Server State (React Query)

- `companies`: Company list (cached for 5 minutes)
- `companyDetail`: Individual company (cached for 10 minutes)
- `companySuggestions`: Autocomplete suggestions (cached for 15 minutes)
- `companyStatistics`: Company stats (cached for 5 minutes)
- `associatedSpeakers`: Company speakers (cached for 10 minutes)

### Real-Time Updates

- **WebSocket**: Real-time company updates when other users make changes
- **Company Created**: Toast notification when new company added
- **Partner Status Changed**: Live update of partner badge and benefits
- **Verification Status**: Real-time verification updates from Swiss UID register

---

## Edge Cases & Error Handling

- **Empty Company List**: Show "No companies found" with [+ Add New Company] suggestion
- **No Search Results**: Show "No companies match your search" with suggestion to clear filters
- **Duplicate Company Name**: Show warning: "A company with this name already exists. Continue anyway?"
- **Missing Logo**: Display default company icon placeholder
- **Failed Logo Upload**: Show error with [Retry Upload] button
- **UID Verification Timeout**: Show "Verification taking longer than expected. We'll notify you when complete."
- **Loading State**: Display skeleton screens while data loads
- **API Error**: Show error message with [Retry] button
- **Permission Denied**: Hide edit/delete buttons for unauthorized users
- **Offline Mode**: Show cached data with banner "Viewing offline version"
- **Slow Network**: Show progress indicators for >2 seconds
- **Upload Too Large**: Prevent upload, show "File too large (max 5MB)"
- **Invalid File Type**: Reject upload, show "Please upload PNG, JPEG, or SVG files only"
- **Quota Exceeded**: Block upload, show quota usage and [Manage Files] link
- **Concurrent Edit Conflict**: Show "This company was modified by another user. Refresh to see latest?"

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Company Management Screen | Sally (UX Expert) |

---

## Review Notes

### Open Questions

1. **Company Ownership**: Should speakers be allowed to claim/verify their company affiliation?
2. **Auto-Verification**: Should we auto-verify companies based on email domain matching?
3. **Company Merging**: What workflow for merging duplicate companies discovered later?
4. **Historical Data**: How to handle company data from 20+ years of historical events?
5. **Multi-Company Affiliation**: Can speakers be associated with multiple companies over time?
6. **Partner Self-Service**: Should partner companies have self-service access to update their info?

### Design Iterations

- **v1.0**: Initial comprehensive design with UID verification and partner management
- Consider adding: Company relationship graph showing connections between companies and speakers
- Consider adding: Company timeline showing participation history across all events
- Consider adding: Bulk import feature for migrating historical company data

### Stakeholder Feedback

- Pending review from organizers for partner management workflow validation
- Need to validate Swiss UID verification integration with actual register API
- Confirm storage quota limits and CDN configuration with infrastructure team
- Validate duplicate detection logic with historical data patterns
