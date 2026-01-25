# Filter Modal (Multi-Context)

## Header Information

**Story:** Epic 5, Story 5.1 - Historical Content Search / Multi-Context Filtering
**Screen:** Filter Modal (Responsive Mobile/Tablet Filter Interface)
**User Role:** Attendee, Speaker, Organizer, Partner, Public (context-dependent)
**Related FR:** FR13 (Content discovery with filtering), FR6 (Attendee access with filtering), FR11 (Archive access with search/filter)

---

## Visual Wireframe

### Desktop/Tablet View (Sidebar - Not Modal)

```
┌─── FILTERS ─────────────────────┐
│                                  │
│  Search within results           │
│  ┌────────────────────────────┐ │
│  │ 🔍 Filter by keyword...    │ │
│  └────────────────────────────┘ │
│                                  │
│  Topic Categories (142 total)   │
│  ┌────────────────────────────┐ │
│  │ ☑ Cloud Native      (142)  │ │
│  │ ☐ Security          (89)   │ │
│  │ ☐ AI/ML             (67)   │ │
│  │ ☐ DevOps            (134)  │ │
│  │ ☐ Data Engineering  (45)   │ │
│  │ [Show More ▼]              │ │
│  └────────────────────────────┘ │
│                                  │
│  Time Period                     │
│  ┌────────────────────────────┐ │
│  │ ○ Last Month               │ │
│  │ ○ Last Year                │ │
│  │ ● Last 5 Years             │ │
│  │ ○ All Time (20+ years)     │ │
│  │                            │ │
│  │ Custom Range:              │ │
│  │ From: [2020  ▼] To: [2024▼]│ │
│  └────────────────────────────┘ │
│                                  │
│  Content Type                    │
│  ┌────────────────────────────┐ │
│  │ ☑ Presentations    (523)   │ │
│  │ ☑ Videos           (89)    │ │
│  │ ☐ Code Examples    (34)    │ │
│  │ ☐ Workshop Materials (12)  │ │
│  └────────────────────────────┘ │
│                                  │
│  Speaker                         │
│  ┌────────────────────────────┐ │
│  │ 🔍 Search speakers...      │ │
│  │                            │ │
│  │ ☐ Thomas Weber      (12)   │ │
│  │ ☐ Sara Kim          (8)    │ │
│  │ ☐ Alex Müller       (15)   │ │
│  │ [Show All Speakers ▼]      │ │
│  └────────────────────────────┘ │
│                                  │
│  Company                         │
│  ┌────────────────────────────┐ │
│  │ ☐ UBS               (45)   │ │
│  │ ☐ Credit Suisse     (38)   │ │
│  │ ☐ Swisscom          (42)   │ │
│  │ ☐ Swiss Re          (28)   │ │
│  │ [Show All Companies ▼]     │ │
│  └────────────────────────────┘ │
│                                  │
│  Rating                          │
│  ┌────────────────────────────┐ │
│  │ ○ All Ratings              │ │
│  │ ● 4+ Stars         (187)   │ │
│  │ ○ 3+ Stars         (245)   │ │
│  └────────────────────────────┘ │
│                                  │
│  [Clear All]     [Apply Filters]│
│                                  │
└──────────────────────────────────┘
```

### Mobile View (Full-Screen Modal)

```
┌───────────────────────────────────────────────────────────────────────────┐
│ ✕ Close                    Filters                      [Clear All]       │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Active Filters (3)                                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │  Cloud Native ✕    Last 5 Years ✕    4+ Stars ✕                     │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  247 results found                                                         │
│                                                                            │
│  ┌─── TOPIC CATEGORIES ───────────────────────────────────────────────▼─┐ │
│  │                                                                        │ │
│  │  Search topics...                                                     │ │
│  │  ┌──────────────────────────────────────────────────────────────┐   │ │
│  │  │ 🔍 Filter by topic...                                         │   │ │
│  │  └──────────────────────────────────────────────────────────────┘   │ │
│  │                                                                        │ │
│  │  ☑ Cloud Native                                         142           │ │
│  │  ☐ Security                                              89           │ │
│  │  ☐ AI/ML                                                 67           │ │
│  │  ☐ DevOps                                               134           │ │
│  │  ☐ Data Engineering                                      45           │ │
│  │  ☐ Frontend Development                                  78           │ │
│  │  ☐ Backend Architecture                                  92           │ │
│  │  ☐ Database Systems                                      56           │ │
│  │  ☐ Testing & QA                                          34           │ │
│  │  ☐ Agile & Project Management                            28           │ │
│  │                                                                        │ │
│  │  [Load More Topics...]                                                │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─── TIME PERIOD ────────────────────────────────────────────────────▼─┐ │
│  │                                                                        │ │
│  │  ○ Last Month                                                         │ │
│  │  ○ Last Year                                                          │ │
│  │  ● Last 5 Years                                                       │ │
│  │  ○ All Time (20+ years)                                               │ │
│  │                                                                        │ │
│  │  ☐ Custom Date Range                                                  │ │
│  │  ┌──────────────────────┬──────────────────────┐                     │ │
│  │  │ From: [2020    ▼]    │ To: [2024      ▼]    │                     │ │
│  │  └──────────────────────┴──────────────────────┘                     │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─── CONTENT TYPE ───────────────────────────────────────────────────▲─┐ │
│  │                                                                        │ │
│  │  ☑ Presentations                                        523           │ │
│  │  ☑ Videos                                                89           │ │
│  │  ☐ Code Examples                                         34           │ │
│  │  ☐ Workshop Materials                                    12           │ │
│  │  ☐ Handouts & Documents                                  67           │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─── SPEAKER ────────────────────────────────────────────────────────▼─┐ │
│  │                                                                        │ │
│  │  Search speakers...                                                   │ │
│  │  ┌──────────────────────────────────────────────────────────────┐   │ │
│  │  │ 🔍 Find speaker by name...                                    │   │ │
│  │  └──────────────────────────────────────────────────────────────┘   │ │
│  │                                                                        │ │
│  │  Recent & Popular Speakers:                                           │ │
│  │  ☐ Thomas Weber (UBS)                                    12           │ │
│  │  ☐ Sara Kim (Swisscom)                                    8           │ │
│  │  ☐ Alex Müller (Credit Suisse)                           15           │ │
│  │  ☐ Lisa Wang (Swiss Re)                                   6           │ │
│  │                                                                        │ │
│  │  [Browse All Speakers →]                                              │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─── COMPANY ────────────────────────────────────────────────────────▼─┐ │
│  │                                                                        │ │
│  │  ☐ UBS                                                   45           │ │
│  │  ☐ Credit Suisse                                         38           │ │
│  │  ☐ Swisscom                                              42           │ │
│  │  ☐ Swiss Re                                              28           │ │
│  │  ☐ Zurich Insurance                                      21           │ │
│  │  ☐ PostFinance                                           18           │ │
│  │                                                                        │ │
│  │  [Show All Companies...]                                              │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─── RATING ─────────────────────────────────────────────────────────▼─┐ │
│  │                                                                        │ │
│  │  ○ All Ratings                                          247           │ │
│  │  ● 4+ Stars (⭐⭐⭐⭐)                                    187           │ │
│  │  ○ 3+ Stars (⭐⭐⭐)                                     245           │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─── FILTER PRESETS ─────────────────────────────────────────────────▼─┐ │
│  │                                                                        │ │
│  │  💾 My Saved Filters:                                                 │ │
│  │                                                                        │ │
│  │  📌 Recent Security Topics                        [Load] [Delete]     │ │
│  │     Security (4+ stars, Last year)                                    │ │
│  │                                                                        │ │
│  │  📌 DevOps Resources                               [Load] [Delete]     │ │
│  │     DevOps, Cloud Native (All time)                                   │ │
│  │                                                                        │ │
│  │  [💾 Save Current Filters]                                            │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│                                                                            │
│  ┌─── ACTIONS ──────────────────────────────────────────────────────────┐ │
│  │                                                                        │ │
│  │                 [Clear All Filters]    [Apply Filters (247)]          │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Collapsible Sections
- **Accordion Headers**: Each filter category (Topic, Time, Content Type, etc.) is collapsible
  - Tap/click header to expand/collapse section
  - Arrow indicator (▼/▲) shows expanded/collapsed state
  - Default: First 3 sections expanded, rest collapsed
  - State persists within session (remembers what user expanded)

### Active Filters Bar
- **Filter Pills**: Selected filters displayed as dismissible pills at top
  - Each pill shows filter name with ✕ close button
  - Click ✕ to remove individual filter
  - Pills wrap to multiple rows if needed
  - Color-coded by category (blue for topics, green for time, etc.)

### Search Within Filters
- **Filter Search Boxes**: Search within long lists (topics, speakers, companies)
  - Real-time filtering as user types (debounced 300ms)
  - Highlights matching text in results
  - Shows "No results found" if no matches
  - Clear button (✕) appears when text entered

### Checkbox Lists
- **Multi-Select Checkboxes**: Allow multiple selections per category
  - Result count shown next to each option (e.g., "Cloud Native (142)")
  - Count updates in real-time as filters applied
  - Grayed out with count (0) if no results for that option
  - "Select All" / "Deselect All" for categories with many options

### Radio Button Groups
- **Single-Select Options**: Time period, rating level (only one choice)
  - Visual indication of selected option (filled circle)
  - Tap anywhere on row to select (not just radio button)

### Date Range Picker
- **Custom Range**: Dropdown selectors for start/end year
  - Start year must be before end year (validation)
  - Defaults to current year for end date
  - Goes back to 2000 (20+ years of content)

### Result Counter
- **Live Result Count**: Shows number of results matching current filters
  - Updates in real-time as filters changed
  - Format: "247 results found" or "No results" if 0
  - Helps users understand impact of each filter

### Action Buttons
- **[Apply Filters]**: Sticky button at bottom (always visible)
  - Shows result count on button: "Apply Filters (247)"
  - Primary action color (blue)
  - Disabled if no changes made
- **[Clear All]**: Remove all filters at once
  - Secondary action (gray/outline)
  - Confirmation if >5 filters active
- **[Save Current Filters]**: Save filter preset for future use
  - Opens modal to name the preset
  - Saves to user profile

---

## Functional Requirements Met

- **FR13 (Content Discovery)**: Advanced filtering for content search with faceted navigation, multi-dimensional filtering
- **FR6 (Attendee Access)**: Filter event listings, session schedules, speaker directories
- **FR11 (Archive Access)**: Filter historical content by year, topic, speaker across 20+ years
- **NFR1 (Responsive Design)**: Mobile-first filter interface with touch-optimized controls, collapsible sections

---

## User Interactions

### Opening Filter Modal (Mobile)
1. User clicks **[Filters]** button on Content Discovery or Event Listing screen
2. Filter modal slides up from bottom (smooth animation)
3. Modal takes full screen on mobile, semi-transparent backdrop
4. Current active filters displayed at top
5. First 3 filter sections expanded by default
6. Result count shows current total
7. Focus moves to first interactive element (search box or first checkbox)

### Applying Filters
1. User selects/deselects checkboxes or radio buttons
2. Active filters bar updates immediately (adds/removes pills)
3. Result count updates in real-time via debounced API call (500ms)
4. **[Apply Filters]** button updates count: "Apply Filters (142)"
5. User scrolls to bottom (or taps sticky button)
6. User clicks **[Apply Filters (142)]**
7. Modal closes with smooth animation
8. Parent screen (Content Discovery) refreshes with filtered results
9. Filter button badge shows active filter count (e.g., "Filters (3)")

### Real-Time Filter Updates
1. As user checks/unchecks options:
   - Immediate visual feedback (checkbox state changes)
   - Active filter pill appears/disappears at top
   - Result count API called after 500ms debounce
   - Counts next to other options update (e.g., "DevOps (134)" → "DevOps (45)")
2. Options with 0 results become grayed out but remain selectable
3. User can see impact of each filter before applying

### Searching Within Filters
1. User clicks search box in "Topic Categories" section
2. Keyboard appears (mobile) or cursor active (desktop)
3. User types "secur"
4. After 300ms debounce, list filters to show only matching topics:
   - "Security (89)"
   - "Security Audit Logging (12)"
   - "DevSecOps (23)"
5. Non-matching topics hidden
6. User can click [✕] in search box to clear, showing all topics again

### Using Filter Presets
1. User has previously saved filter combinations
2. User expands "Filter Presets" section (collapsed by default)
3. Sees list of saved presets with descriptions:
   - "Recent Security Topics" (Security, 4+ stars, Last year)
   - "DevOps Resources" (DevOps, Cloud Native, All time)
4. User clicks **[Load]** on "Recent Security Topics"
5. All filters reset to preset values:
   - Topics: Security ☑
   - Rating: 4+ Stars ●
   - Time: Last Year ●
6. Result count updates
7. Active filter pills update
8. User can modify loaded preset and save as new

### Saving New Preset
1. User configures desired filters (e.g., Cloud Native, AI/ML, 4+ stars)
2. User clicks **[💾 Save Current Filters]**
3. Modal overlay appears: "Save Filter Preset"
   ```
   ┌─────────────────────────────────────────┐
   │  Save Filter Preset                     │
   ├─────────────────────────────────────────┤
   │                                         │
   │  Preset Name                            │
   │  ┌───────────────────────────────────┐ │
   │  │ AI & Cloud Resources              │ │
   │  └───────────────────────────────────┘ │
   │                                         │
   │  Current Filters:                       │
   │  • Cloud Native                         │
   │  • AI/ML                                │
   │  • 4+ Stars                             │
   │                                         │
   │  [Cancel]              [Save Preset]    │
   │                                         │
   └─────────────────────────────────────────┘
   ```
4. User enters name, clicks **[Save Preset]**
5. Success toast: "Filter preset saved"
6. Preset appears in "Filter Presets" section

### Clearing Filters
1. User clicks **[Clear All]** button (top-right or bottom)
2. If 5+ filters active: Confirmation dialog
   ```
   Clear all filters?

   This will remove all 7 active filters.

   [Cancel]  [Clear All Filters]
   ```
3. On confirm:
   - All checkboxes unchecked
   - Radio buttons reset to defaults
   - Active filter pills disappear
   - Result count returns to total (e.g., "247 results")
   - Success toast: "All filters cleared"

### Removing Individual Filters
1. User clicks ✕ on active filter pill (e.g., "Cloud Native ✕")
2. Filter immediately removed:
   - Checkbox unchecks in filter list
   - Pill disappears from active filters bar
   - Result count updates
   - Other filter counts recalculate
3. No confirmation needed for single filter removal

### Closing Without Applying
1. User clicks **[✕ Close]** button (top-left)
2. If changes made but not applied: Confirmation dialog
   ```
   Discard filter changes?

   You have unsaved filter changes that will be lost.

   [Keep Editing]  [Discard Changes]
   ```
3. On [Discard Changes]:
   - Modal closes
   - Filters revert to last applied state
   - No changes to search results
4. On [Keep Editing]:
   - Dialog closes
   - Modal remains open

---

## Technical Notes

### Frontend Implementation
- **Framework**: React 18.2 with TypeScript, Material-UI Drawer (mobile) / Sidebar (desktop)
- **State Management**: Zustand for filter state (persists across navigation), React Hook Form for form state
- **Responsive Breakpoints**:
  - Mobile (<768px): Full-screen modal with collapsible sections
  - Tablet (768-1024px): Slide-in drawer (60% width)
  - Desktop (>1024px): Permanent sidebar (25% width)
- **Animations**: Framer Motion for smooth modal transitions, accordion expand/collapse

### Real-Time Filter Counts
- **Debounced API**: 500ms debounce on filter changes to reduce API calls
- **Optimistic Updates**: UI updates immediately, API call in background
- **Caching**: Filter count results cached (5 min TTL) per unique filter combination
- **Request Deduplication**: Multiple rapid filter changes coalesced into single API call

### Filter State Persistence
- **Session Storage**: Current filter state saved to sessionStorage (survives page refresh)
- **URL Parameters**: Filters encoded in URL query params for shareability
  - Example: `?topics=cloud-native,security&rating=4&year=2020-2024`
  - Deep linking: Shared URL loads with filters pre-applied
- **User Presets**: Saved to user profile (database), synced across devices

### Performance Optimization
- **Virtualized Lists**: Long lists (topics, speakers, companies) use react-window for performance
  - Only renders visible items (viewport + buffer)
  - Smooth scrolling with thousands of items
- **Lazy Loading**: Filter options loaded on-demand (expand section → fetch options)
- **Search Indexing**: Client-side fuzzy search using Fuse.js for filter-within-filter

### Accessibility Features
- **Keyboard Navigation**: Full keyboard support (Tab, Space, Enter, Arrow keys)
- **Screen Reader**: ARIA labels on all controls, live regions announce result count changes
- **Focus Trapping**: Modal traps focus (Tab cycles within modal)
- **Escape Key**: Closes modal (with confirmation if changes made)

---

## API Requirements

> **API Consolidation Note (Stories 1.19, 1.20, 1.21, 1.22)**: This wireframe has been updated to use consolidated APIs from Stories 1.19 (Speakers), 1.20 (Content), 1.21 (Topics), and 1.22 (Companies). These consolidations use standardized `?filter={}` patterns and faceted search capabilities, reducing API calls while providing rich filtering options.

### Initial Page Load APIs

1. **GET /api/v1/content?facets=topics,speakers,companies,contentTypes,years** *(Story 1.20)*
   - **Consolidation**: Uses faceted search pattern to get filter options with counts in single request
   - **Authorization**: Optional (some filters require auth for personalization)
   - **Query Params**:
     - `facets=topics,speakers,companies,contentTypes,years` (request filter facets)
     - `context` (optional): "content" | "events" | "speakers" | "sessions"
   - **Returns**: Available filter options with counts
     ```json
     {
       "context": "content",
       "topics": [
         {
           "id": "topic-cloud-native",
           "name": "Cloud Native",
           "slug": "cloud-native",
           "count": 142,
           "subcategories": [
             {
               "id": "topic-kubernetes",
               "name": "Kubernetes",
               "count": 78
             },
             {
               "id": "topic-docker",
               "name": "Docker",
               "count": 56
             }
           ]
         },
         {
           "id": "topic-security",
           "name": "Security",
           "slug": "security",
           "count": 89
         }
         // ... more topics
       ],
       "contentTypes": [
         {
           "id": "presentation",
           "name": "Presentations",
           "count": 523
         },
         {
           "id": "video",
           "name": "Videos",
           "count": 89
         }
       ],
       "speakers": [
         {
           "id": "speaker-123",
           "name": "Thomas Weber",
           "company": "UBS",
           "count": 12,
           "avatar": "https://..."
         }
         // ... more speakers
       ],
       "companies": [
         {
           "id": "company-456",
           "name": "UBS",
           "count": 45
         }
         // ... more companies
       ],
       "yearRange": {
         "min": 2000,
         "max": 2024,
         "eventYears": [2024, 2023, 2022, ...]
       },
       "presets": [
         {
           "id": "preset-789",
           "name": "Recent Security Topics",
           "description": "Security content, 4+ stars, Last year",
           "filters": {
             "topics": ["topic-security"],
             "rating": 4,
             "timePeriod": "last-year"
           },
           "createdAt": "2024-03-15T10:00:00Z"
         }
       ],
       "presets": [...]
     }
     ```
   - **Used for**: Populate filter options, show counts with facet data
   - **Performance**: <500ms (P95) with facets
   - **Consolidation Benefit**: Single request provides all filter options using faceted search (eliminates need for separate filter-options endpoint)

2. **GET /api/v1/users/{userId}/preferences?include=filterPresets** *(Story 1.23)*
   - **Consolidation**: Uses unified preferences endpoint to fetch filter presets
   - **Authorization**: Requires authenticated user
   - **Path Params**: `userId` (UUID)
   - **Query Params**:
     - `include=filterPresets` (expand filter presets from preferences)
   - **Returns**: User's saved filter presets in preferences
     ```json
     {
       "presets": [
         {
           "id": "preset-789",
           "name": "Recent Security Topics",
           "description": "Security (4+ stars, Last year)",
           "context": "content",
           "filters": {
             "topics": ["topic-security"],
             "rating": 4,
             "timePeriod": "last-year"
           },
           "useCount": 15,
           "lastUsed": "2024-04-01T14:30:00Z",
           "createdAt": "2024-03-15T10:00:00Z"
         },
         {
           "id": "preset-790",
           "name": "DevOps Resources",
           "description": "DevOps, Cloud Native (All time)",
           "context": "content",
           "filters": {
             "topics": ["topic-devops", "topic-cloud-native"],
             "timePeriod": "all-time"
           },
           "useCount": 8,
           "lastUsed": "2024-03-28T09:15:00Z",
           "createdAt": "2024-03-10T16:45:00Z"
         }
       ]
     }
     ```
   - **Used for**: Load user's saved filter presets from preferences
   - **Performance**: <150ms (P95)
   - **Consolidation Benefit**: Filter presets integrated into user preferences

---

### User Action APIs

1. **GET /api/v1/content?filter={}&facets=topics,speakers,companies** *(Story 1.20)*
   - **Consolidation**: Real-time filter count uses same content endpoint with filter parameters
   - **Triggered by**: Filter changes (debounced 500ms), real-time result count updates
   - **Authorization**: Optional
   - **Query Params**:
     - `filter={}` (current filter state as JSON)
     - `facets=topics,speakers,companies` (get updated facet counts)
   - **Payload**: N/A (GET request with query params)
     ```json
     {
       "context": "content",
       "filters": {
         "topics": ["topic-cloud-native"],
         "contentTypes": ["presentation", "video"],
         "timePeriod": "last-5-years",
         "rating": 4,
         "speakers": [],
         "companies": []
       }
     }
     ```
   - **Response**: Result count and updated facet counts
     ```json
     {
       "totalResults": 247,
       "facetCounts": {
         "topics": {
           "topic-cloud-native": 142,
           "topic-security": 45,
           "topic-devops": 67,
           "topic-aiml": 23
         },
         "contentTypes": {
           "presentation": 198,
           "video": 32,
           "code": 12,
           "workshop": 5
         },
         "speakers": {
           "speaker-123": 8,
           "speaker-456": 5
         },
         "companies": {
           "company-456": 25,
           "company-789": 18
         },
         "ratings": {
           "all": 247,
           "4plus": 187,
           "3plus": 245
         }
       },
       "executionTimeMs": 45
     }
     ```
   - **Used for**: Update result count display, update counts next to each filter option
   - **Performance**: <500ms (P95)
   - **Consolidation Benefit**: Single content endpoint handles both search and facet counting (eliminates separate /filters/count endpoint)

2. **PUT /api/v1/users/{userId}/preferences** *(Story 1.23)*
   - **Consolidation**: Save filter presets through unified preferences endpoint
   - **Triggered by**: **[Save Preset]** button in save preset modal
   - **Authorization**: Requires authenticated user
   - **Path Params**: `userId` (UUID)
   - **Payload**: Partial update with new filter preset
     ```json
     {
       "name": "AI & Cloud Resources",
       "description": "Cloud Native, AI/ML, 4+ stars",
       "context": "content",
       "filters": {
         "topics": ["topic-cloud-native", "topic-aiml"],
         "rating": 4,
         "timePeriod": "all-time"
       },
       "isPublic": false
     }
     ```
   - **Validation**:
     - Name: Required, 3-50 characters, unique per user+context
     - Filters: At least one filter must be set
   - **Response**:
     ```json
     {
       "presetId": "preset-791",
       "name": "AI & Cloud Resources",
       "filters": { ... },
       "createdAt": "2024-04-01T15:00:00Z",
       "success": true
     }
     ```
   - **Used for**: Save current filter state as reusable preset in user preferences
   - **Performance**: <150ms (P95)
   - **Consolidation Benefit**: Filter presets managed through user preferences (no separate preset endpoints)

3. **GET /api/v1/topics?filter={"query":"{searchTerm}"}&limit=10** *(Story 1.21)*
   - **Consolidation**: Topic autocomplete uses consolidated Topics API
   - **Triggered by**: Typing in topic filter search box
   - **Authorization**: Optional
   - **Query Params**:
     - `filter={"query":"secur"}` (search filter)
     - `limit=10` (max autocomplete results)
   - **Returns**: Topic autocomplete suggestions
     ```json
     {
       "topics": [
         {
           "id": "topic-security",
           "name": "Security",
           "count": 89
         },
         {
           "id": "topic-security-audit",
           "name": "Security Audit Logging",
           "count": 12
         }
       ],
       "totalMatches": 3
     }
     ```
   - **Performance**: <200ms (P95)

4. **GET /api/v1/speakers?filter={"query":"{searchTerm}"}&limit=10** *(Story 1.19)*
   - **Consolidation**: Speaker autocomplete uses consolidated Speakers API
   - **Triggered by**: Typing in speaker filter search box
   - **Authorization**: Optional
   - **Query Params**:
     - `filter={"query":"weber"}` (search filter)
     - `limit=10` (max autocomplete results)
   - **Returns**: Speaker autocomplete suggestions
   - **Performance**: <300ms (P95)

5. **GET /api/v1/companies/search?query={searchTerm}&limit=10** *(Story 1.22)*
   - **Consolidation**: Company autocomplete uses dedicated search endpoint with Caffeine in-memory caching
   - **Triggered by**: Typing in company filter search box
   - **Authorization**: Optional
   - **Query Params**:
     - `query={searchTerm}` (search query)
     - `limit=10` (max autocomplete results)
   - **Returns**: Company autocomplete suggestions
     ```json
     {
       "query": "secur",
       "type": "topic",
       "suggestions": [
         {
           "id": "topic-security",
           "name": "Security",
           "match": "Security",
           "count": 89,
           "highlightedName": "<mark>Secur</mark>ity"
         },
         {
           "id": "topic-security-audit",
           "name": "Security Audit Logging",
           "match": "Security Audit Logging",
           "count": 12,
           "highlightedName": "<mark>Secur</mark>ity Audit Logging"
         },
         {
           "id": "topic-devsecops",
           "name": "DevSecOps",
           "match": "DevSecOps",
           "count": 23,
           "highlightedName": "Dev<mark>Sec</mark>Ops"
         }
       ],
       "totalMatches": 3
     }
     ```
   - **Performance**: <100ms (P95) with Caffeine in-memory caching
   - **Consolidation Benefit**: Dedicated search endpoints replace generic autocomplete API, optimized per resource type

---

## Navigation Map

### Primary Navigation Actions

1. **✕ Close button** → Close modal, return to parent screen
   - **Behavior**:
     - If no changes: Close immediately
     - If changes made: Show confirmation dialog "Discard filter changes?"
     - On discard: Reset filters to last applied state, close modal
     - On keep editing: Dialog closes, modal remains open
   - **Target**: Parent screen (Content Discovery, Event Listing, Speaker Directory)
   - **No Changes to Results**: Filters not applied, results unchanged

2. **[Apply Filters (247)]** → Apply filters, close modal, refresh results
   - **Behavior**:
     - Modal closes with smooth transition
     - Parent screen shows loading state
     - Results refresh with filtered data
     - URL updates with filter params
     - Filter button badge shows count (e.g., "Filters (3)")
   - **Target**: Parent screen with filtered results
   - **Analytics**: Track applied filters, popular filter combinations

3. **[Clear All]** → Remove all filters
   - **Behavior**:
     - If 5+ filters: Confirmation dialog
     - All checkboxes unchecked, radio buttons reset
     - Active filter pills disappear
     - Result count returns to total
     - Success toast: "All filters cleared"
   - **No Navigation**: Remains in filter modal

---

### Secondary Navigation (Data Interactions)

1. **Active filter pill ✕ click** → Remove individual filter
   - **Behavior**:
     - Pill disappears from active filters bar
     - Corresponding checkbox/radio unchecks
     - Result count updates (debounced API call)
     - Other filter counts recalculate
   - **No Navigation**: Remains in filter modal

2. **[Load] button on saved preset** → Load filter preset
   - **Behavior**:
     - All filters reset to preset values
     - Checkboxes/radios update to match preset
     - Active filter pills update
     - Result count updates
     - Success toast: "Filter preset loaded: {preset name}"
   - **No Navigation**: Remains in filter modal with loaded filters

3. **[Delete] button on saved preset** → Delete filter preset
   - **Behavior**:
     - Confirmation dialog: "Delete filter preset '{name}'?"
     - On confirm: API call to delete
     - Preset removed from list
     - Success toast: "Filter preset deleted"
   - **No Navigation**: Remains in filter modal

4. **[💾 Save Current Filters] button** → Open save preset modal
   - **Target**: Modal overlay for preset naming
   - **Behavior**:
     - Save preset modal appears
     - User enters name and description
     - On save: Preset added to list
     - On cancel: Modal closes, no changes
   - **No Navigation**: Remains in filter modal after save

5. **[Browse All Speakers →] link** → Navigate to full speaker list
   - **Target**: Speaker Directory page (if exists)
   - **Context**: Pre-filters based on current topic/company filters
   - **Behavior**: Opens speaker directory in new context, filter modal may close or remain

6. **[Show More ▼] / [Show Less ▲] in filter lists** → Expand/collapse list
   - **Behavior**:
     - Toggles between showing 5 items vs. all items
     - Arrow indicator flips (▼ ↔ ▲)
     - Smooth height transition animation
   - **No Navigation**: Remains in filter modal

7. **Accordion section header click** → Expand/collapse section
   - **Behavior**:
     - Section content slides down/up
     - Arrow rotates (▼ → ▲)
     - Other sections remain unchanged (multi-expand allowed)
   - **No Navigation**: Remains in filter modal

---

### Event-Driven Navigation

1. **Filter change** → Update result count
   - **Trigger**: Checkbox toggle, radio selection, date change
   - **Behavior**:
     - Immediate UI update (check/uncheck)
     - Active filter pill appears/disappears
     - After 500ms debounce: API call for new count
     - Result count updates: "247 results" → "142 results"
     - Filter option counts recalculate
   - **No Navigation**: Remains in filter modal

2. **Search within filters** → Filter the filter list
   - **Trigger**: Typing in filter search box (300ms debounce)
   - **Behavior**:
     - Non-matching items hidden
     - Matching items highlighted
     - "No results found" if no matches
     - Clear button (✕) appears in search box
   - **No Navigation**: Remains in filter modal

3. **Zero results warning** → Show warning message
   - **Trigger**: Result count reaches 0
   - **Behavior**:
     - Warning banner appears: "⚠️ No results found with current filters. Try removing some filters."
     - **[Clear All]** button emphasized
     - **[Apply Filters]** button disabled (grayed out)
   - **No Navigation**: Remains in filter modal

4. **Preset loaded** → Update all filter controls
   - **Trigger**: Click **[Load]** on saved preset
   - **Behavior**:
     - All filters reset to preset state
     - Checkboxes/radios animated state change
     - Active filter pills update
     - Result count updates
     - Success toast notification
   - **No Navigation**: Remains in filter modal

---

### Error States & Redirects

1. **Filter options load failed (500)** → Show error state
   - **Condition**: API error loading filter options
   - **Display**: Error message in modal
     ```
     ⚠️ Unable to load filters

     There was an error loading filter options.
     Please try again.

     [Retry]  [Close]
     ```
   - **Actions**:
     - **[Retry]**: Reload filter options API
     - **[Close]**: Close modal, return to parent screen

2. **Result count timeout** → Show stale count warning
   - **Condition**: Count API takes >5 seconds
   - **Display**: Warning next to count: "Result count may be outdated. [Refresh]"
   - **Action**: **[Refresh]** button retries count API
   - **No Navigation**: Remains in filter modal

3. **Save preset failed (400/500)** → Show error message
   - **Condition**: Preset save API error
   - **Display**: Error toast with reason
     - 400: "Preset name already exists. Please choose a different name."
     - 500: "Unable to save preset. Please try again."
   - **No Navigation**: Save modal remains open for retry

4. **Session timeout during filtering** → Save state, redirect to login
   - **Condition**: JWT token expired while modal open
   - **Behavior**:
     - Filter state saved to sessionStorage
     - Login modal appears: "Session expired. Please log in to continue."
     - After login: Return to same screen, restore filter state
     - Toast: "Your filters have been restored."

---

## Responsive Design Considerations

### Mobile Layout (< 768px)

- **Full-Screen Modal**: Takes entire viewport, slides up from bottom
- **Sticky Header**: "Filters" title with close and clear buttons always visible
- **Collapsible Sections**: All filter categories start collapsed except first 3
- **Active Filters Bar**: Sticky at top (below header), always visible while scrolling
- **Accordion Animations**: Smooth expand/collapse with spring physics
- **Sticky Footer**: **[Apply Filters (247)]** button always visible at bottom
- **Touch Targets**: Minimum 44x44px for all interactive elements
- **Swipe Gestures**:
  - Swipe down from top → Close modal (with confirmation if changes)
  - Swipe on checkbox rows → No action (prevent accidental swipes)

### Tablet Layout (768px - 1024px)

- **Slide-In Drawer**: 60% width, slides in from right
- **Semi-Transparent Backdrop**: Click outside drawer to close (with confirmation)
- **Scrollable Content**: Drawer content scrolls independently
- **Two-Column Checkboxes**: Some sections show 2 columns of checkboxes to save vertical space
- **Sticky Footer**: Apply/Clear buttons sticky at drawer bottom

### Desktop Layout (> 1024px)

- **Permanent Sidebar**: Not a modal, always visible on left (25% width)
- **No Close Button**: Sidebar always open, not dismissible
- **Apply on Change**: Option to auto-apply filters as they change (no Apply button needed)
  - Setting in user preferences: "Apply filters automatically"
  - If enabled: Results update in real-time (debounced)
  - If disabled: **[Apply Filters]** button required
- **Hover Interactions**: Show tooltips on hover for truncated text

### Mobile-Specific Interactions

- **Tap Section Headers**: Expand/collapse sections (large tap target)
- **Pull-to-Refresh**: Not applicable in modal (conflicting gesture)
- **Virtual Keyboard**: Modal resizes when keyboard appears, focused input remains visible
- **Native Date Pickers**: Use native iOS/Android date pickers for date range

---

## Accessibility Notes

- **Keyboard Navigation**:
  - Tab: Move between interactive elements (checkboxes, radios, buttons)
  - Space: Toggle checkbox, select radio button, expand/collapse section
  - Enter: Activate button, expand/collapse section
  - Escape: Close modal (with confirmation if changes)
  - Arrow Up/Down: Navigate within checkbox/radio lists

- **Focus Management**:
  - On modal open: Focus moves to first checkbox or search box
  - On modal close: Focus returns to "Filters" button that opened modal
  - Focus trap: Tab cycles within modal (doesn't escape to background)
  - Focus visible: High-contrast outline on focused elements

- **Screen Reader Support**:
  - ARIA labels: "Filter modal", "Topic category filters", "Content type filters"
  - ARIA live regions: Result count changes announced ("247 results found" → "142 results found")
  - ARIA expanded: Accordion sections announce expanded/collapsed state
  - ARIA checked: Checkbox states announced
  - ARIA selected: Radio button states announced
  - Section headers: Proper heading hierarchy (h2 for main sections, h3 for subsections)

- **Visual Indicators**:
  - Color + Icon: Active filters use both color (blue) and checkmark icon
  - High Contrast: All text meets WCAG 2.1 AA (4.5:1 for normal text)
  - Focus Indicators: 3px solid outline, high contrast ratio
  - Disabled State: Grayed out with lower opacity, not just color

- **Error Messaging**:
  - Errors announced via ARIA live region
  - Error text visible, not just color indication
  - Clear instructions on how to fix (e.g., "Please select at least one filter")

---

## State Management

### Local Component State

- `expandedSections`: Array of section IDs that are currently expanded
- `filterValues`: Object containing current filter selections
  ```javascript
  {
    topics: ["topic-cloud-native", "topic-security"],
    contentTypes: ["presentation", "video"],
    timePeriod: "last-5-years",
    customRange: { from: 2020, to: 2024 },
    speakers: [],
    companies: [],
    rating: 4
  }
  ```
- `appliedFilters`: Last applied filter state (for reverting on cancel)
- `resultCount`: Current result count from API
- `isLoading`: Boolean for loading state during count API calls
- `searchTerms`: Object mapping section ID to search term (filter-within-filter)

### Global State (Zustand Store)

- `activeFilters`: Currently active/applied filters (persisted)
- `filterPresets`: User's saved filter presets
- `filterHistory`: Recent filter combinations (for suggestions)

### Server State (React Query)

- **Query Keys**:
  - `['filters', 'options', context]`: Filter options with counts (5 min cache)
  - `['filters', 'count', filterState]`: Result count for filter combination (2 min cache)
  - `['user', userId, 'filter-presets', context]`: User's saved presets (10 min cache)

- **Mutations**:
  - `updateFilterCount`: Invalidates count query, triggers recalculation
  - `savePreset`: Invalidates presets query, adds new preset to list
  - `deletePreset`: Invalidates presets query, removes preset from list

### Persistence

- **Session Storage**: Current filter state (survives page refresh within session)
- **URL Parameters**: Applied filters encoded in URL for sharing
  - Example: `?f=topics:cloud-native,security|rating:4|period:last-5-years`
- **Local Storage**: Expanded sections preference (remembers which sections user keeps open)
- **User Profile**: Saved filter presets (synced to database)

---

## Form Validation Rules

### Filter Preset Saving

- **Preset Name**:
  - Required: Cannot be empty
  - Min Length: 3 characters
  - Max Length: 50 characters
  - Unique: Must be unique within user's presets for same context
  - Pattern: Alphanumeric, spaces, hyphens allowed

- **Preset Description**:
  - Optional: Can be empty
  - Max Length: 200 characters

- **Filter Values**:
  - At least one filter must be selected to save preset
  - Error: "Please select at least one filter before saving preset"

### Date Range

- **From Year**:
  - Required if custom range selected
  - Range: 2000 to current year
  - Must be ≤ To Year

- **To Year**:
  - Required if custom range selected
  - Range: From Year to current year
  - Must be ≥ From Year

- **Validation**:
  - If From > To: Error "Start year must be before end year"

---

## Edge Cases & Error Handling

### Empty States

- **No Filter Options**: If API returns empty arrays
  - Display: "No filters available for this content type"
  - Hide empty sections entirely

- **No Saved Presets**: If user has no saved presets
  - Display: "You haven't saved any filter presets yet. Configure filters and click [Save Current Filters] to create one."

- **No Search Results**: If filter search returns no matches
  - Display: "No matching {topics/speakers/companies} found. Try a different search term."

### Loading States

- **Initial Load**: Skeleton screens for filter sections while loading options
- **Result Count Update**: Spinner next to count, previous count shown until new arrives
- **Preset Load**: Loading indicator on **[Load]** button during API call
- **Save Preset**: Spinner on **[Save Preset]** button, disabled during save

### Error States

- **API Timeout**: If filter options API takes >10 seconds
  - Error: "Loading filters is taking longer than expected. [Retry] [Continue Without Filters]"

- **Zero Results**: If all filters result in 0 results
  - Warning banner: "⚠️ No results match your filters. Try removing some filters or [Clear All]"
  - **[Apply Filters]** button disabled

- **Preset Name Conflict**: If user tries to save preset with existing name
  - Error: "A preset with this name already exists. Please choose a different name or delete the existing preset."

- **Network Error During Apply**: If results API fails after applying filters
  - Error toast: "Unable to load filtered results. Please try again."
  - **[Retry]** button in parent screen

### Concurrent Actions

- **Multiple Filter Changes**: Debouncing ensures only last change triggers API
- **Apply While Count Loading**: If user clicks **[Apply]** before count loads
  - Use last known count, proceed with apply
  - Results screen shows loading state until actual results arrive

### Performance Edge Cases

- **1000+ Filter Options**: Virtualized lists prevent DOM bloat
- **Slow Network**: Optimistic UI updates, show immediate feedback
- **Rapid Toggling**: Debounced API calls (500ms) prevent request spam

---

## Change Log

| Date       | Version | Description                          | Author       |
|------------|---------|--------------------------------------|--------------|
| 2025-04-01 | 1.0     | Initial wireframe creation          | Sally (UX)   |

---

## Review Notes

### Stakeholder Feedback

*To be added after stakeholder review*

### Design Iterations

*To be documented as design evolves*

### Open Questions

1. **Auto-Apply vs. Manual Apply**: Should filters auto-apply as users select them (real-time results), or require explicit **[Apply]** button?
   - **Current**: Manual apply (better for mobile, prevents excessive API calls)
   - **Desktop Alternative**: Auto-apply with debounce (500ms) for faster UX
   - **Recommendation**: User preference setting, default to manual apply

2. **Filter Combination Logic**: How should multiple filters within same category combine?
   - **Current**: OR logic within category (Cloud Native OR Security), AND across categories (Topics AND Rating)
   - **Alternative**: Allow user to switch between AND/OR per category
   - **Recommendation**: Keep simple OR/AND for MVP, add advanced logic post-launch

3. **Shareable Filters**: Should users be able to share filter presets with other users or make them public?
   - **Current**: Private presets only
   - **Enhancement**: "Share preset" generates link, "Public presets" gallery
   - **Use Case**: Team collaboration, curated filter lists
   - **Recommendation**: Add post-MVP if user demand exists

4. **Filter Analytics**: Should we track which filters are most popular to optimize UI?
   - **Data Collected**: Filter usage frequency, popular combinations, abandoned filters
   - **Use Cases**: Reorder filters by popularity, suggest common combinations
   - **Privacy**: Aggregate data only, no individual tracking
   - **Recommendation**: Implement analytics, use for continuous UI improvement

5. **Advanced Filters**: Should there be "advanced mode" with more granular controls?
   - **Examples**: Exact date ranges, exclude certain topics, min/max view counts
   - **Current**: Basic filters cover 95% of use cases
   - **Recommendation**: Add "Advanced Filters" toggle post-MVP for power users
