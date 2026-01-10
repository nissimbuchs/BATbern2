# Story 4.2: Historical Event Archive Browsing - Modern Wireframe

**Story**: Epic 4, Story 4.2
**Screen**: Historical Event Archive Browser
**User Role**: Public Visitor / Attendee
**Related FRs**: FR2 (Archive Browsing), FR4 (Content Discovery)
**Last Updated**: 2026-01-10

---

## Screen Overview

A clean, modern event archive browsing interface matching the BATbern public website dark theme. Displays historical events in a filterable, searchable grid with chronological sorting. Designed for 3 events per year, avoiding unnecessary year-level navigation.

**Design System**: shadcn/ui dark theme with OKLCH color system
**Layout**: Responsive grid with sidebar filters (desktop) / filter sheet (mobile)

---

## Desktop Layout (1024px+)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [NAVIGATION BAR - PublicNavigation component]                                        │
│ BATbern Logo    Home    Archive    About                          [DE] [EN]         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─ HERO SECTION ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Event Archive                                                                   │ │
│  │  Explore 20+ years of BATbern conferences                                       │ │
│  │                                                                                  │ │
│  │  [Search events, speakers, topics...]                        🔍 [Search]        │ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─ FILTER SIDEBAR ────┐  ┌─ EVENT GRID ─────────────────────────────────────────┐ │
│  │                      │  │                                                       │ │
│  │ Filters              │  │  Showing 54 events  [Grid] [List]    Sort: Newest ▼ │ │
│  │ ─────────────        │  │  ───────────────────────────────────────────────     │ │
│  │                      │  │                                                       │ │
│  │ 📅 Time Period       │  │  ┌────────────────────────────────────────────────┐ │ │
│  │ ○ All Events         │  │  │ [Event Image]                                  │ │ │
│  │ ● Last 5 Years       │  │  │                                                │ │ │
│  │ ○ 2020-2024          │  │  │ BATbern Spring 2025                            │ │ │
│  │ ○ 2015-2019          │  │  │ May 15, 2025 • Cloud Native Architecture       │ │ │
│  │ ○ 2010-2014          │  │  │ ───────────────────────────────────────────── │ │ │
│  │ ○ Before 2010        │  │  │                                                │ │ │
│  │                      │  │  │ Sessions:                                      │ │ │
│  │ 🎯 Topics            │  │  │ • Kubernetes in Production                     │ │ │
│  │ ☐ Cloud (23)         │  │  │   Peter Müller, SwissCloud AG                 │ │ │
│  │ ☐ Security (18)      │  │  │                                                │ │ │
│  │ ☐ AI/ML (15)         │  │  │ • Container Security Best Practices           │ │ │
│  │ ☐ DevOps (27)        │  │  │   Sarah Kim, SecureTech GmbH                  │ │ │
│  │ ☐ Kubernetes (12)    │  │  │                                                │ │ │
│  │ + Show more          │  │  │ • Service Mesh with Istio                     │ │ │
│  │                      │  │  │   Thomas Weber, MicroSys                       │ │ │
│  │ [Clear All Filters]  │  │  │                                                │ │ │
│  │                      │  │  │ + 5 more sessions                              │ │ │
│  └──────────────────────┘  │  │                                                │ │ │
│                             │  │ [View Full Schedule]                          │ │ │
│                             │  └────────────────────────────────────────────────┘ │ │
│                             │                                                       │ │
│                             │  ┌────────────────────────────────────────────────┐ │ │
│                             │  │ [Event Image]                                  │ │ │
│                             │  │                                                │ │ │
│                             │  │ BATbern Summer 2024                            │ │ │
│                             │  │ August 20, 2024 • Microservices at Scale       │ │ │
│                             │  │ ───────────────────────────────────────────── │ │ │
│                             │  │                                                │ │ │
│                             │  │ Sessions:                                      │ │ │
│                             │  │ • Event-Driven Architecture                    │ │ │
│                             │  │   Anna Lopez, DataFlow Systems                 │ │ │
│                             │  │                                                │ │ │
│                             │  │ • API Gateway Patterns                         │ │ │
│                             │  │   Marc Baumann, API Solutions                  │ │ │
│                             │  │                                                │ │ │
│                             │  │ • Observability at Scale                       │ │ │
│                             │  │   Lisa Chen, MonitorPro                        │ │ │
│                             │  │                                                │ │ │
│                             │  │ + 4 more sessions                              │ │ │
│                             │  │                                                │ │ │
│                             │  │ [View Full Schedule]                          │ │ │
│                             │  └────────────────────────────────────────────────┘ │ │
│                             │                                                       │ │
│                             │  ┌─ Loading indicator (when scrolling) ──────────┐ │ │
│                             │  │                                                │ │ │
│                             │  │  ⟳  Loading more events...                    │ │ │
│                             │  │                                                │ │ │
│                             │  │  Showing 20 of 54 events                      │ │ │
│                             │  │                                                │ │ │
│                             │  └────────────────────────────────────────────────┘ │ │
│                             └───────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─ FOOTER ────────────────────────────────────────────────────────────────────────┐ │
│  │ © 2025 BATbern  |  About  |  Contact  |  Privacy  |  Newsletter                 │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Mobile Layout (320px-767px)

```
┌───────────────────────────────────────┐
│ ☰  BATbern Archive          [DE][EN] │
├───────────────────────────────────────┤
│                                       │
│  Event Archive                        │
│  Explore 20+ years of BATbern        │
│  conferences                          │
│                                       │
│  [Search...]              🔍          │
│                                       │
│  [🔧 Filters (2)]  [Grid][List] ▼    │
│  ─────────────────────────────────    │
│                                       │
│  Showing 54 events                    │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │ [Event Image]                   │ │
│  │                                 │ │
│  │ BATbern Spring 2025             │ │
│  │ May 15, 2025 • Cloud Native     │ │
│  │ ─────────────────────────────   │ │
│  │                                 │ │
│  │ Sessions:                       │ │
│  │ • Kubernetes in Production      │ │
│  │   P. Müller, SwissCloud AG      │ │
│  │                                 │ │
│  │ • Container Security            │ │
│  │   S. Kim, SecureTech            │ │
│  │                                 │ │
│  │ • Service Mesh with Istio       │ │
│  │   T. Weber, MicroSys            │ │
│  │                                 │ │
│  │ + 5 more sessions               │ │
│  │                                 │ │
│  │ [View Full Schedule]            │ │
│  └─────────────────────────────────┘ │
│                                       │
│  ┌─────────────────────────────────┐ │
│  │ [Event Image]                   │ │
│  │                                 │ │
│  │ BATbern Summer 2024             │ │
│  │ August 20, 2024 • Microservices │ │
│  │ ─────────────────────────────   │ │
│  │                                 │ │
│  │ Sessions:                       │ │
│  │ • Event-Driven Architecture     │ │
│  │   A. Lopez, DataFlow Systems    │ │
│  │                                 │ │
│  │ • API Gateway Patterns          │ │
│  │   M. Baumann, API Solutions     │ │
│  │                                 │ │
│  │ • Observability at Scale        │ │
│  │   L. Chen, MonitorPro           │ │
│  │                                 │ │
│  │ + 4 more sessions               │ │
│  │                                 │ │
│  │ [View Full Schedule]            │ │
│  └─────────────────────────────────┘ │
│                                       │
│  ⟳ Loading more...                   │
│  Showing 20 of 54 events             │
│                                       │
└───────────────────────────────────────┘
```

---

## Archive Event Detail View (Desktop)

When user clicks "View Full Schedule" on an event card, navigate to `/archive/{eventCode}` which shows a content-focused archive detail page:

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ [NAVIGATION BAR - PublicNavigation component]                                        │
│ BATbern Logo    Home    Archive    About                          [DE] [EN]         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  [← Back to Archive]                                                                 │
│                                                                                       │
│  ┌─ EVENT HEADER ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  BATbern Spring 2025                                                            │ │
│  │  May 15, 2025 • Cloud Native Architecture                                       │ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─ EVENT DESCRIPTION ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  A comprehensive deep-dive into cloud native technologies and best practices   │ │
│  │  for building scalable, resilient applications. This event brought together    │ │
│  │  leading experts to share their experiences with Kubernetes, microservices,    │ │
│  │  and modern DevOps practices.                                                   │ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─ SESSIONS & PRESENTATIONS ─────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  8 Sessions                                                                      │ │
│  │  ────────────                                                                    │ │
│  │                                                                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │  Keynote: Kubernetes in Production                                       │  │ │
│  │  │                                                                           │  │ │
│  │  │  👤 Peter Müller • SwissCloud AG                                         │  │ │
│  │  │                                                                           │  │ │
│  │  │  Learn how to deploy and manage Kubernetes clusters at scale in          │  │ │
│  │  │  production environments. This session covers best practices for          │  │ │
│  │  │  high availability, security, monitoring, and cost optimization.          │  │ │
│  │  │                                                                           │  │ │
│  │  │  📄 Presentation Materials:                                               │  │ │
│  │  │  [📥 Download PDF (2.4 MB)] [📊 View Slides Online]                      │  │ │
│  │  │                                                                           │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │  Container Security Best Practices                                       │  │ │
│  │  │                                                                           │  │ │
│  │  │  👤 Sarah Kim • SecureTech GmbH                                          │  │ │
│  │  │                                                                           │  │ │
│  │  │  Comprehensive guide to securing containerized applications. Topics       │  │ │
│  │  │  include image scanning, runtime protection, secrets management,          │  │ │
│  │  │  network policies, and compliance automation.                             │  │ │
│  │  │                                                                           │  │ │
│  │  │  📄 Presentation Materials:                                               │  │ │
│  │  │  [📥 Download PDF (3.1 MB)] [📊 View Slides Online]                      │  │ │
│  │  │                                                                           │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │  Service Mesh with Istio                                                 │  │ │
│  │  │                                                                           │  │ │
│  │  │  👤 Thomas Weber • MicroSys                                              │  │ │
│  │  │                                                                           │  │ │
│  │  │  Deep dive into service mesh architecture using Istio for managing       │  │ │
│  │  │  microservices communication, traffic management, observability,          │  │ │
│  │  │  and security in cloud native applications.                               │  │ │
│  │  │                                                                           │  │ │
│  │  │  📄 Presentation Materials:                                               │  │ │
│  │  │  [📥 Download PDF (1.8 MB)] [📊 View Slides Online]                      │  │ │
│  │  │                                                                           │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │  Event-Driven Architecture Patterns                                      │  │ │
│  │  │                                                                           │  │ │
│  │  │  👤 Anna Lopez • DataFlow Systems                                        │  │ │
│  │  │                                                                           │  │ │
│  │  │  Learn how to design scalable event-driven systems using message queues, │  │ │
│  │  │  event streaming platforms, and CQRS patterns for decoupled services.    │  │ │
│  │  │                                                                           │  │ │
│  │  │  📄 Presentation Materials:                                               │  │ │
│  │  │  [📥 Download PDF (2.9 MB)] [📊 View Slides Online]                      │  │ │
│  │  │                                                                           │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │  API Gateway Strategies                                                  │  │ │
│  │  │                                                                           │  │ │
│  │  │  👤 Marc Baumann • API Solutions                                         │  │ │
│  │  │                                                                           │  │ │
│  │  │  Best practices for API gateway implementation, rate limiting,           │  │ │
│  │  │  authentication, and routing in microservices architectures.             │  │ │
│  │  │                                                                           │  │ │
│  │  │  📄 Presentation Materials:                                               │  │ │
│  │  │  [📥 Download PDF (1.5 MB)]                                               │  │ │
│  │  │                                                                           │  │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                                  │ │
│  │  [... continues with all remaining sessions]                                    │ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─ FOOTER ───────────────────────────────────────────────────────────────────────┐ │
│  │ © 2025 BATbern  |  About  |  Contact  |  Privacy  |  Newsletter                │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

**Route**: `/archive/{eventCode}` (new dedicated archive detail page)
**Component**: `ArchiveEventDetailPage` (new component, not HomePage)

---

## Archive Event Detail Components

### 11. Event Header
**Component**: Custom header section
**Styling**:
```tsx
<div className="mb-8">
  <Link
    to="/archive"
    className="inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-zinc-300 mb-6"
  >
    <ChevronLeft className="h-4 w-4" />
    Back to Archive
  </Link>

  <div className="bg-zinc-900/50 border border-zinc-800 rounded-lg p-8">
    <h1 className="text-4xl font-light text-zinc-100 mb-3">
      {event.title}
    </h1>
    <div className="flex items-center gap-3 text-lg text-zinc-400">
      <time dateTime={event.date}>
        {format(new Date(event.date), 'MMMM d, yyyy')}
      </time>
      {event.topic && (
        <>
          <span>•</span>
          <span>{event.topic}</span>
        </>
      )}
    </div>
  </div>
</div>
```

### 12. Event Description
**Component**: Content section
**Styling**:
```tsx
{event.description && (
  <div className="bg-zinc-900/50 border border-zinc-800 rounded-lg p-8 mb-8">
    <p className="text-zinc-300 leading-relaxed">
      {event.description}
    </p>
  </div>
)}
```

### 13. Session Card (Archive Detail)
**Component**: Detailed session card with materials
**Note**: All sessions (4-8) shown at once, no pagination needed
**Styling**:
```tsx
// Render all sessions
<div className="space-y-6">
  {event.sessions?.map((session) => (
    <Card key={session.sessionSlug} className="bg-zinc-900/50 border-zinc-800">
      <CardHeader>
        <CardTitle className="text-2xl font-light text-zinc-100">
          {session.title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Speaker(s) */}
        {session.speakers && session.speakers.length > 0 && (
          <div className="flex flex-wrap gap-4">
            {session.speakers.map((speaker) => (
              <div key={speaker.username} className="flex items-center gap-3">
                {speaker.profilePictureUrl ? (
                  <img
                    src={speaker.profilePictureUrl}
                    alt={`${speaker.firstName} ${speaker.lastName}`}
                    className="w-12 h-12 rounded-full object-cover"
                  />
                ) : (
                  <div className="w-12 h-12 rounded-full bg-zinc-800 flex items-center justify-center">
                    <User className="h-6 w-6 text-zinc-600" />
                  </div>
                )}
                <div>
                  <div className="text-zinc-200 font-medium">
                    {speaker.firstName} {speaker.lastName}
                  </div>
                  {speaker.company && (
                    <div className="text-sm text-zinc-500">{speaker.company}</div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Description */}
        {session.description && (
          <p className="text-zinc-400 leading-relaxed">
            {session.description}
          </p>
        )}

        {/* Materials */}
        {session.materials && session.materials.length > 0 && (
          <div className="border-t border-zinc-800 pt-6">
            <h4 className="text-sm font-medium text-zinc-400 mb-3">
              📄 Presentation Materials:
            </h4>
            <div className="flex flex-wrap gap-3">
              {session.materials.map((material) => (
                <Button
                  key={material.id}
                  variant="outline"
                  className="border-zinc-800 hover:bg-zinc-800"
                  asChild
                >
                  <a
                    href={material.downloadUrl}
                    download
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FileDown className="mr-2 h-4 w-4" />
                    Download PDF
                    {material.fileSize && (
                      <span className="ml-2 text-xs text-zinc-500">
                        ({formatFileSize(material.fileSize)})
                      </span>
                    )}
                  </a>
                </Button>
              ))}
              {session.slidesUrl && (
                <Button
                  variant="outline"
                  className="border-zinc-800 hover:bg-zinc-800"
                  asChild
                >
                  <a
                    href={session.slidesUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <Presentation className="mr-2 h-4 w-4" />
                    View Slides Online
                  </a>
                </Button>
              )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  ))}
</div>

// Helper function
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
};
```

### 14. Speaker Grid (Archive Detail)
**Component**: Simplified speaker grid
**Styling**:
```tsx
<div className="bg-zinc-900/50 border border-zinc-800 rounded-lg p-8">
  <h3 className="text-xl font-light text-zinc-100 mb-6">All Speakers</h3>
  <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
    {speakers.map((speaker) => (
      <div key={speaker.username} className="text-center">
        {speaker.profilePictureUrl ? (
          <img
            src={speaker.profilePictureUrl}
            alt={`${speaker.firstName} ${speaker.lastName}`}
            className="w-24 h-24 rounded-full mx-auto mb-3 object-cover"
          />
        ) : (
          <div className="w-24 h-24 rounded-full bg-zinc-800 flex items-center justify-center mx-auto mb-3">
            <User className="h-12 w-12 text-zinc-600" />
          </div>
        )}
        <div className="text-sm text-zinc-200 font-medium">
          {speaker.firstName} {speaker.lastName}
        </div>
        {speaker.company && (
          <div className="text-xs text-zinc-500 mt-1">{speaker.company}</div>
        )}
      </div>
    ))}
  </div>
</div>
```

---

## Component Specifications

### 1. Hero Section
**Component**: Custom hero banner
**Styling**:
- Background: `bg-zinc-950` with subtle gradient
- Title: `text-4xl md:text-5xl font-light text-zinc-100`
- Subtitle: `text-lg text-zinc-400`
- Padding: `py-12 md:py-16`

### 2. Search Bar
**Component**: shadcn Input with Search icon
**Features**:
- Full-text search across event titles, topics, speakers
- Debounced input (300ms)
- Clear button when text entered
- Placeholder: "Search events, speakers, topics..."

**Styling**:
```tsx
<Input
  className="bg-zinc-900 border-zinc-800 text-zinc-100 placeholder:text-zinc-500"
  icon={<Search className="h-5 w-5 text-zinc-400" />}
/>
```

### 3. Filter Sidebar (Desktop)
**Component**: Custom sidebar (240px width)
**Styling**:
- Background: `bg-zinc-900/50 border-r border-zinc-800`
- Sticky positioning: `sticky top-20`
- Max height: `max-h-[calc(100vh-5rem)] overflow-y-auto`

**Filter Sections**:
```tsx
// Time Period Radio Group
<div className="space-y-2">
  <label className="text-sm font-medium text-zinc-300">📅 Time Period</label>
  <RadioGroup defaultValue="last-5-years">
    <RadioGroupItem value="all" label="All Events" />
    <RadioGroupItem value="last-5-years" label="Last 5 Years" />
    <RadioGroupItem value="2020-2024" label="2020-2024" />
    <RadioGroupItem value="2015-2019" label="2015-2019" />
    <RadioGroupItem value="2010-2014" label="2010-2014" />
    <RadioGroupItem value="before-2010" label="Before 2010" />
  </RadioGroup>
</div>

// Topics Checkboxes
<div className="space-y-2">
  <label className="text-sm font-medium text-zinc-300">🎯 Topics</label>
  <Checkbox label="Cloud (23)" />
  <Checkbox label="Security (18)" />
  <Checkbox label="AI/ML (15)" />
  <Checkbox label="DevOps (27)" />
  <Checkbox label="Kubernetes (12)" />
  <Button variant="link" className="text-blue-400 hover:text-blue-300">
    + Show more
  </Button>
</div>
```

### 4. Filter Sheet (Mobile)
**Component**: shadcn Sheet (slide from bottom)
**Trigger**: "🔧 Filters (3)" button showing active filter count
**Content**: Same filter sections as sidebar
**Styling**:
```tsx
<Sheet>
  <SheetTrigger asChild>
    <Button variant="outline" className="border-zinc-800">
      🔧 Filters {activeFilters > 0 && `(${activeFilters})`}
    </Button>
  </SheetTrigger>
  <SheetContent side="bottom" className="bg-zinc-900 border-zinc-800">
    <SheetHeader>
      <SheetTitle className="text-zinc-100">Filter Events</SheetTitle>
    </SheetHeader>
    {/* Filter sections */}
    <SheetFooter>
      <Button variant="outline" onClick={clearFilters}>Clear All</Button>
      <Button onClick={applyFilters}>Apply Filters</Button>
    </SheetFooter>
  </SheetContent>
</Sheet>
```

### 5. View Controls
**Component**: Button group with toggle icons
**Options**: Grid view, List view
**Styling**:
```tsx
<div className="flex gap-2">
  <Button
    variant={viewMode === 'grid' ? 'default' : 'outline'}
    size="icon"
    onClick={() => setViewMode('grid')}
    className="border-zinc-800"
  >
    <LayoutGrid className="h-4 w-4" />
  </Button>
  <Button
    variant={viewMode === 'list' ? 'default' : 'outline'}
    size="icon"
    onClick={() => setViewMode('list')}
    className="border-zinc-800"
  >
    <List className="h-4 w-4" />
  </Button>
</div>
```

### 6. Sort Dropdown
**Component**: shadcn Select
**Options**: Newest first, Oldest first, Most attended, Most sessions
**Styling**:
```tsx
<Select defaultValue="newest">
  <SelectTrigger className="w-40 bg-zinc-900 border-zinc-800 text-zinc-100">
    <SelectValue />
  </SelectTrigger>
  <SelectContent className="bg-zinc-900 border-zinc-800">
    <SelectItem value="newest">Newest First</SelectItem>
    <SelectItem value="oldest">Oldest First</SelectItem>
    <SelectItem value="attended">Most Attended</SelectItem>
    <SelectItem value="sessions">Most Sessions</SelectItem>
  </SelectContent>
</Select>
```

### 7. Event Card (Grid View)
**Component**: shadcn Card with session list
**Dimensions**:
- Desktop: `w-full` (2 columns in grid)
- Tablet: `w-full` (1 column)
- Mobile: `w-full` (1 column)

**Styling**:
```tsx
<Card className="group bg-zinc-900/50 border-zinc-800 hover:border-zinc-700 transition-all overflow-hidden">
  <CardContent className="p-0">
    {/* Event Image */}
    <div className="aspect-video bg-zinc-800 overflow-hidden">
      {event.themeImageUrl ? (
        <img
          src={event.themeImageUrl}
          alt={event.title}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
        />
      ) : (
        <div className="w-full h-full flex items-center justify-center">
          <Calendar className="h-16 w-16 text-zinc-700" />
        </div>
      )}
    </div>

    {/* Event Content */}
    <div className="p-6 space-y-4">
      {/* Title & Date */}
      <div>
        <h3 className="text-xl font-light text-zinc-100 group-hover:text-blue-400 transition-colors">
          {event.title}
        </h3>
        <div className="flex items-center gap-3 text-sm text-zinc-400 mt-2">
          <time dateTime={event.date}>
            {format(new Date(event.date), 'MMMM d, yyyy')}
          </time>
          {event.topic && (
            <>
              <span>•</span>
              <span>{event.topic}</span>
            </>
          )}
        </div>
      </div>

      <div className="h-px bg-zinc-800" />

      {/* Sessions List (first 3) */}
      <div className="space-y-3">
        <h4 className="text-sm font-medium text-zinc-400">Sessions:</h4>
        {event.sessions?.slice(0, 3).map((session) => (
          <div key={session.sessionSlug} className="text-sm">
            <div className="text-zinc-200">{session.title}</div>
            {session.speakers?.[0] && (
              <div className="text-zinc-500 text-xs mt-0.5">
                {session.speakers[0].firstName} {session.speakers[0].lastName}
                {session.speakers[0].company && `, ${session.speakers[0].company}`}
              </div>
            )}
          </div>
        ))}

        {event.sessions && event.sessions.length > 3 && (
          <div className="text-xs text-zinc-500">
            + {event.sessions.length - 3} more sessions
          </div>
        )}
      </div>

      {/* Action Button */}
      <Button
        variant="outline"
        className="w-full border-zinc-800 hover:bg-zinc-800"
        onClick={() => navigate(`/events/${event.eventCode}`)}
      >
        View Full Schedule
      </Button>
    </div>
  </CardContent>
</Card>
```

### 8. Event Card (List View)
**Component**: shadcn Card with horizontal layout
**Styling**:
```tsx
<Card className="group bg-zinc-900/50 border-zinc-800 hover:border-zinc-700 transition-all">
  <CardContent className="p-6">
    <div className="flex gap-6">
      {/* Thumbnail */}
      <div className="flex-shrink-0 w-32 h-32 bg-zinc-800 rounded-lg overflow-hidden">
        {event.themeImageUrl ? (
          <img
            src={event.themeImageUrl}
            alt={event.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Calendar className="h-8 w-8 text-zinc-700" />
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 space-y-3">
        <div>
          <h3 className="text-xl font-light text-zinc-100 group-hover:text-blue-400 transition-colors">
            {event.title}
          </h3>
          <div className="flex items-center gap-3 text-sm text-zinc-400 mt-1">
            <time>{format(new Date(event.date), 'MMMM d, yyyy')}</time>
            {event.topic && (
              <>
                <span>•</span>
                <span>{event.topic}</span>
              </>
            )}
          </div>
        </div>

        {/* Sessions (compact list) */}
        <div className="space-y-1.5 text-sm">
          {event.sessions?.slice(0, 2).map((session) => (
            <div key={session.sessionSlug} className="flex items-baseline gap-2">
              <span className="text-zinc-200">{session.title}</span>
              {session.speakers?.[0] && (
                <span className="text-zinc-500 text-xs">
                  {session.speakers[0].firstName} {session.speakers[0].lastName}
                </span>
              )}
            </div>
          ))}
          {event.sessions && event.sessions.length > 2 && (
            <div className="text-xs text-zinc-500">
              + {event.sessions.length - 2} more sessions
            </div>
          )}
        </div>
      </div>

      {/* Action */}
      <div className="flex-shrink-0 flex items-center">
        <Button
          variant="outline"
          className="border-zinc-800 hover:bg-zinc-800"
          onClick={() => navigate(`/events/${event.eventCode}`)}
        >
          View Full Schedule
        </Button>
      </div>
    </div>
  </CardContent>
</Card>
```

### 9. Infinite Scroll Loader
**Component**: Intersection Observer trigger with loading indicator
**Behavior**: Automatically loads more events when user scrolls near bottom
**Implementation**:
```tsx
import { useInView } from 'react-intersection-observer';

// Intersection observer hook
const { ref: loadMoreRef, inView } = useInView({
  threshold: 0,
  rootMargin: '400px', // Start loading 400px before reaching the trigger
});

// Auto-load when scrolling into view
useEffect(() => {
  if (inView && hasMore && !isLoadingMore) {
    loadMore();
  }
}, [inView, hasMore, isLoadingMore]);

// Render component
<div className="py-8">
  {/* Progress indicator */}
  <div className="text-center text-sm text-zinc-400 mb-4">
    Showing {displayedEvents.length} of {totalEvents} events
  </div>

  {/* Loading indicator (shows when fetching more) */}
  {hasMore && (
    <div ref={loadMoreRef} className="flex flex-col items-center gap-4">
      {isLoadingMore && (
        <div className="flex items-center gap-2 text-zinc-400">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading more events...</span>
        </div>
      )}
    </div>
  )}

  {/* End of results */}
  {!hasMore && displayedEvents.length > 0 && (
    <div className="text-center text-sm text-zinc-500">
      You've reached the end of the archive
    </div>
  )}
</div>
```

**NPM Package**: `react-intersection-observer`
```bash
npm install react-intersection-observer
```

### 10. Empty State
**Component**: Custom empty state card
**Shown When**: No events match filters
**Styling**:
```tsx
<div className="flex flex-col items-center justify-center py-24 px-4">
  <Calendar className="h-16 w-16 text-zinc-700 mb-4" />
  <h3 className="text-xl font-light text-zinc-300 mb-2">
    No events found
  </h3>
  <p className="text-zinc-400 text-center mb-6">
    Try adjusting your filters or search query
  </p>
  <Button
    variant="outline"
    onClick={clearFilters}
    className="border-zinc-800 hover:bg-zinc-800"
  >
    Clear All Filters
  </Button>
</div>
```

---

## API Requirements

### Initial Page Load

**1. GET /api/v1/events**
```http
GET /api/v1/events?page=1&limit=20&filter={"workflowState":{"$in":["UPCOMING","COMPLETED","ARCHIVED"]}}&include=topics,sessions,speakers&sort=-date
```

**Purpose**: Fetch initial 20 events with sessions and speakers for event cards

**Response** (EventListResponse):
```typescript
{
  data: [
    {
      eventId: "uuid",
      eventCode: "BATbern56",
      eventNumber: 56,
      title: "BATbern Spring 2025",
      date: "2025-05-15",
      topic: "Cloud Native Architecture",
      workflowState: "UPCOMING",
      venueName: "Bern Convention Center",
      venueCapacity: 300,
      themeImageUrl: "https://cdn.batbern.ch/events/bat56/hero.jpg",
      // From topics expansion
      topics: [
        { id: "uuid", name: "Cloud Native", code: "cloud-native" }
      ],
      // From sessions expansion (with nested speakers)
      sessions: [
        {
          sessionSlug: "kubernetes-production",
          title: "Kubernetes in Production",
          sessionType: "Keynote",
          description: "Learn how to deploy and manage Kubernetes...",
          room: "Main Hall",
          capacity: 300,
          startTime: "2025-05-15T09:15:00Z",
          endTime: "2025-05-15T10:00:00Z",
          // From speakers expansion (nested in session)
          speakers: [
            {
              username: "peter.mueller",
              firstName: "Peter",
              lastName: "Müller",
              email: "peter@swisscloud.ch",
              company: "SwissCloud AG",
              profilePictureUrl: "https://cdn.batbern.ch/speakers/peter-mueller.jpg"
            }
          ]
        },
        // ... 7 more sessions
      ]
    },
    // ... 19 more events
  ],
  pagination: {
    page: 1,
    limit: 20,
    total: 54,
    pages: 3
  }
}
```

**Caching**: 15-minute Caffeine cache on backend
**Performance**: <500ms P95 uncached, <50ms cached
**Note**: Sessions are limited to 8 per event max, so this expansion is efficient

**2. GET /api/v1/topics**
```http
GET /api/v1/topics?status=ACTIVE&sort=name
```

**Purpose**: Populate topic filter checkboxes with counts

**Response**:
```typescript
{
  data: [
    { id: "uuid", name: "Cloud", code: "cloud", eventCount: 23 },
    { id: "uuid", name: "Security", code: "security", eventCount: 18 },
    { id: "uuid", name: "AI/ML", code: "ai-ml", eventCount: 15 },
    // ...
  ]
}
```

**Caching**: 30-minute cache (topics rarely change)

---

### User Actions

**3. Search Events**
```http
GET /api/v1/events?page=1&limit=20&filter={"$or":[{"title":{"$contains":"kubernetes"}},{"topic":{"$contains":"kubernetes"}}]}&include=analytics&sort=-date
```

**Trigger**: User types in search box (debounced 300ms)
**Behavior**: Full-text search across title, topic, description

**4. Apply Filters**
```http
GET /api/v1/events?page=1&limit=20&filter={"workflowState":{"$in":["COMPLETED"]},"year":{"$gte":2020,"$lte":2024},"topics":{"$in":["cloud","security"]}}&include=analytics&sort=-date
```

**Trigger**: User selects filter options
**Behavior**: Combine all active filters with AND logic

**5. Change Sort Order**
```http
GET /api/v1/events?page=1&limit=20&filter={...}&include=analytics&sort=-attendeeCount
```

**Options**:
- Newest first: `sort=-date`
- Oldest first: `sort=date`
- Most attended: `sort=-attendeeCount`
- Most sessions: `sort=-sessionCount`

**6. Pagination (Infinite Scroll)**
```http
GET /api/v1/events?page=2&limit=20&filter={...}&include=topics,sessions,speakers&sort=-date
```

**Trigger**: User scrolls within 400px of bottom (intersection observer)
**Behavior**: Automatically append to existing results
**Optimization**: Debounce scroll events, prevent duplicate requests

**7. View Archive Event Details**
```http
GET /api/v1/events/{eventCode}?include=topics,sessions,speakers
```

**Purpose**: Fetch complete event data for archive detail page

**Response**:
```typescript
{
  eventId: "uuid",
  eventCode: "BATbern56",
  title: "BATbern Spring 2025",
  date: "2025-05-15",
  topic: "Cloud Native Architecture",
  description: "A comprehensive deep-dive into cloud native technologies...",
  topics: [
    { id: "uuid", name: "Cloud Native", code: "cloud-native" }
  ],
  sessions: [
    {
      sessionSlug: "kubernetes-production",
      title: "Kubernetes in Production",
      sessionType: "Keynote",
      description: "Learn how to deploy and manage Kubernetes clusters...",
      speakers: [
        {
          username: "peter.mueller",
          firstName: "Peter",
          lastName: "Müller",
          company: "SwissCloud AG",
          profilePictureUrl: "https://cdn.batbern.ch/speakers/peter-mueller.jpg"
        }
      ],
      // Materials/attachments for this session
      materials: [
        {
          id: "uuid",
          fileName: "kubernetes-production.pdf",
          fileSize: 2458624, // bytes
          downloadUrl: "https://cdn.batbern.ch/presentations/bat56/kubernetes-production.pdf",
          uploadedAt: "2025-05-15T10:30:00Z"
        }
      ],
      slidesUrl: "https://slides.batbern.ch/bat56/kubernetes-production" // Optional
    },
    // ... 7 more sessions
  ]
}
```

**Trigger**: User clicks "View Full Schedule" button on event card
**Navigation**: `/archive/{eventCode}` (ArchiveEventDetailPage component)
**Note**: Excludes venue, logistics, timetable - only content that matters for archive

---

## Navigation Map

### Entry Points (Navigate TO Archive Page)

1. **PublicNavigation** → "Archive" link
   - Route: `/archive`
   - Available on all public pages

2. **HomePage (Current Event)** → Footer "Explore Archive" link
   - Route: `/archive`
   - Context: Users viewing current event want to see past events

3. **Direct URL** → `/archive`
   - SEO-optimized page
   - Shareable filtered URLs: `/archive?year=2024&topic=cloud`

### Exit Points (Navigate FROM Archive Page)

4. **Event Card / "View Full Schedule" button** → Archive Event Detail Page
   - Route: `/archive/{eventCode}`
   - Shows event description, sessions, speakers, presentation materials
   - Content-focused view (no venue, logistics, timetables)

5. **"← Back to Archive" link** (on detail page) → Archive Browse Page
   - Route: `/archive`
   - Returns to filtered/searched state (via URL params)

6. **Download PDF button** (on detail page) → Direct download
   - Triggers presentation file download
   - Opens in new tab or downloads file

7. **View Slides Online button** (on detail page) → External slides viewer
   - Opens presentation in external viewer (e.g., SlideShare, Google Slides)
   - New tab/window

8. **Topic Badge (future)** → Topic Discovery Page (Story 4.3)
   - Route: `/topics/{topicCode}`
   - Shows all events and content for topic

9. **Search Results (future)** → Content Search Page (Story 4.3)
   - Route: `/search?q={query}`
   - Full-text content search across presentations

---

## State Management

### URL State (React Router `useSearchParams`)
```typescript
// All filters persist in URL for shareable links
const [searchParams, setSearchParams] = useSearchParams();

// Read from URL
const search = searchParams.get('q') || '';
const timePeriod = searchParams.get('period') || 'last-5-years';
const topics = searchParams.get('topics')?.split(',') || [];
const viewMode = searchParams.get('view') || 'grid';
const sortBy = searchParams.get('sort') || 'newest';

// Update URL
const updateFilters = (newFilters) => {
  const params = new URLSearchParams();
  if (newFilters.search) params.set('q', newFilters.search);
  if (newFilters.period) params.set('period', newFilters.period);
  if (newFilters.topics.length) params.set('topics', newFilters.topics.join(','));
  // ...
  setSearchParams(params);
};
```

### Component State
```typescript
// View preferences (localStorage)
const [viewMode, setViewMode] = useState<'grid' | 'list'>(
  localStorage.getItem('archive-view-mode') || 'grid'
);

// Loading states
const [isLoading, setIsLoading] = useState(false);
const [isLoadingMore, setIsLoadingMore] = useState(false);

// Data
const [events, setEvents] = useState<EventDetail[]>([]);
const [topics, setTopics] = useState<Topic[]>([]);
const [pagination, setPagination] = useState({
  page: 1,
  limit: 20,
  total: 0,
  pages: 0
});

// Filter state
const [activeFilters, setActiveFilters] = useState({
  search: '',
  timePeriod: 'last-5-years',
  topics: []
});
```

### React Query Integration (Infinite Scroll)
```typescript
import { useInfiniteQuery } from '@tanstack/react-query';

// Infinite events query
const {
  data,
  fetchNextPage,
  hasNextPage,
  isFetchingNextPage,
  isLoading,
  error
} = useInfiniteQuery({
  queryKey: ['events', 'archive', activeFilters, sortBy],
  queryFn: ({ pageParam = 1 }) => eventApiClient.getEvents(
    { page: pageParam, limit: 20 },
    {
      year: getYearRangeFromPeriod(activeFilters.timePeriod),
      search: activeFilters.search,
      topics: activeFilters.topics
    },
    { expand: ['topics', 'sessions', 'speakers'] }
  ),
  getNextPageParam: (lastPage) => {
    const { page, pages } = lastPage.pagination;
    return page < pages ? page + 1 : undefined;
  },
  staleTime: 5 * 60 * 1000, // 5 minutes
  keepPreviousData: true // Smooth transitions
});

// Flatten all pages into single events array
const events = data?.pages.flatMap(page => page.data) ?? [];
const totalEvents = data?.pages[0]?.pagination.total ?? 0;

// Auto-load more with intersection observer
const { ref: loadMoreRef, inView } = useInView({
  threshold: 0,
  rootMargin: '400px'
});

useEffect(() => {
  if (inView && hasNextPage && !isFetchingNextPage) {
    fetchNextPage();
  }
}, [inView, hasNextPage, isFetchingNextPage, fetchNextPage]);

// Topics query
const topicsQuery = useQuery({
  queryKey: ['topics'],
  queryFn: () => topicService.getTopics({ status: 'ACTIVE' }),
  staleTime: 30 * 60 * 1000 // 30 minutes
});
```

---

## Responsive Behavior

### Breakpoints
- **Mobile**: 320px - 767px
- **Tablet**: 768px - 1023px
- **Desktop**: 1024px+

### Layout Changes

**Mobile (320-767px)**:
- Hero: Full width, smaller text
- Search: Full width
- Filters: Bottom sheet (Sheet component)
- View controls: Horizontal scroll if needed
- Sort: Full width select
- Event grid: 1 column, full width cards
- Load more: Full width button

**Tablet (768-1023px)**:
- Hero: Full width
- Search: Full width
- Filters: Bottom sheet or collapsible sidebar
- Event grid: 2 columns
- Layout similar to desktop but compressed

**Desktop (1024px+)**:
- Hero: Full width
- Search: Centered, max-width
- Filters: Fixed sidebar (240px)
- Event grid: 2 columns in main area
- Full desktop layout as shown in wireframe

### Component Responsive Classes
```tsx
// Hero
<section className="py-8 md:py-12 lg:py-16">
  <h1 className="text-3xl md:text-4xl lg:text-5xl">Event Archive</h1>
</section>

// Grid container
<div className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6">
  {/* Event cards */}
</div>

// Sidebar (desktop only)
<aside className="hidden lg:block lg:w-60 lg:flex-shrink-0">
  {/* Filters */}
</aside>

// Filter button (mobile/tablet only)
<Button className="lg:hidden">
  🔧 Filters
</Button>
```

---

## Accessibility Requirements

### WCAG 2.1 Level AA Compliance

**1. Keyboard Navigation**
- All interactive elements focusable via Tab
- Focus visible with high-contrast outline
- Focus order matches visual layout
- Skip links for main content

**2. Screen Reader Support**
```tsx
// Event card
<Card role="article" aria-labelledby={`event-title-${event.eventId}`}>
  <h3 id={`event-title-${event.eventId}`}>{event.title}</h3>
  <time dateTime={event.date} aria-label={formatDate(event.date)}>
    {displayDate}
  </time>
  <Button aria-label={`View details for ${event.title}`}>
    View Details
  </Button>
</Card>

// Filter section
<section aria-label="Event filters">
  <fieldset>
    <legend className="sr-only">Time period filter</legend>
    <RadioGroup aria-label="Select time period">
      <RadioGroupItem value="all" aria-label="Show all events" />
    </RadioGroup>
  </fieldset>
</section>

// Load more button
<Button
  aria-live="polite"
  aria-busy={isLoading}
  aria-label={`Load more events. Currently showing ${count} of ${total}`}
>
  {isLoading ? 'Loading...' : 'Load More'}
</Button>
```

**3. Color Contrast**
- Text on background: Minimum 4.5:1 ratio
- Primary text (zinc-100 on zinc-950): ~16:1 ✓
- Secondary text (zinc-400 on zinc-950): ~7:1 ✓
- Interactive elements: Minimum 3:1 ratio
- Focus indicators: High contrast blue-400

**4. Form Labels**
- All form controls have associated labels
- Placeholder text not used as sole label
- Error messages associated with inputs via aria-describedby

**5. Landmarks**
```tsx
<main aria-label="Event archive">
  <header aria-label="Page header">
    <h1>Event Archive</h1>
  </header>
  <nav aria-label="Filter controls">
    {/* Filters */}
  </nav>
  <section aria-label="Event results">
    {/* Event grid */}
  </section>
</main>
```

---

## Performance Requirements

### Load Time
- **Initial page load**: <2.5s LCP (Largest Contentful Paint)
- **Search/filter update**: <500ms
- **Image loading**: Lazy loading with intersection observer
- **Pagination**: <300ms

### Optimization Strategies

**1. Image Optimization**
```tsx
// Use CDN with automatic optimization
<img
  src={`${event.themeImageUrl}?w=400&h=225&q=80&fm=webp`}
  loading="lazy"
  decoding="async"
  alt={event.title}
/>

// Blur placeholder while loading
{!imageLoaded && (
  <div className="absolute inset-0 bg-zinc-800 animate-pulse" />
)}
```

**2. Code Splitting**
```typescript
// Lazy load filter components
const FilterSidebar = lazy(() => import('./FilterSidebar'));
const FilterSheet = lazy(() => import('./FilterSheet'));

// Lazy load event cards below fold
const EventCard = lazy(() => import('./EventCard'));
```

**3. Intersection Observer** (for infinite scroll)
```typescript
import { useInView } from 'react-intersection-observer';

const { ref: loadMoreRef, inView } = useInView({
  threshold: 0,
  rootMargin: '400px', // Start loading 400px before bottom
  triggerOnce: false // Re-trigger when scrolling back up
});

// Prevent duplicate requests with ref tracking
const loadingRef = useRef(false);

useEffect(() => {
  if (inView && hasNextPage && !isFetchingNextPage && !loadingRef.current) {
    loadingRef.current = true;
    fetchNextPage().finally(() => {
      loadingRef.current = false;
    });
  }
}, [inView, hasNextPage, isFetchingNextPage]);
```

**4. Debounced Search**
```typescript
const debouncedSearch = useMemo(
  () => debounce((query: string) => {
    updateFilters({ search: query });
  }, 300),
  []
);
```

**5. Memoization**
```typescript
// Memoize filtered/sorted results
const displayedEvents = useMemo(() => {
  // Client-side filtering/sorting if needed
  return events;
}, [events, activeFilters, sortBy]);

// Memoize expensive computations
const topicCounts = useMemo(() => {
  return calculateTopicCounts(events);
}, [events]);
```

---

## Error States

### Network Error
```tsx
{error && (
  <div className="bg-red-900/20 border border-red-800 rounded-lg p-6 text-center">
    <AlertCircle className="h-12 w-12 text-red-400 mx-auto mb-4" />
    <h3 className="text-lg font-medium text-red-300 mb-2">
      Failed to load events
    </h3>
    <p className="text-sm text-zinc-400 mb-4">
      {error.message}
    </p>
    <Button
      variant="outline"
      onClick={() => refetch()}
      className="border-red-800 hover:bg-red-900/30"
    >
      Try Again
    </Button>
  </div>
)}
```

### No Results
```tsx
{!isLoading && events.length === 0 && (
  <div className="flex flex-col items-center justify-center py-24">
    <Search className="h-16 w-16 text-zinc-700 mb-4" />
    <h3 className="text-xl font-light text-zinc-300 mb-2">
      No events found
    </h3>
    <p className="text-zinc-400 text-center mb-6">
      Try adjusting your filters or search query
    </p>
    <Button
      variant="outline"
      onClick={clearAllFilters}
      className="border-zinc-800 hover:bg-zinc-800"
    >
      Clear All Filters
    </Button>
  </div>
)}
```

### Loading State
```tsx
{isLoading && (
  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
    {[...Array(6)].map((_, i) => (
      <Card key={i} className="bg-zinc-900/50 border-zinc-800">
        <CardContent className="p-0">
          <div className="aspect-video bg-zinc-800 animate-pulse" />
          <div className="p-6 space-y-4">
            <div className="h-6 bg-zinc-800 rounded animate-pulse" />
            <div className="h-4 bg-zinc-800 rounded w-3/4 animate-pulse" />
            <div className="flex gap-4">
              <div className="h-4 bg-zinc-800 rounded w-20 animate-pulse" />
              <div className="h-4 bg-zinc-800 rounded w-20 animate-pulse" />
            </div>
          </div>
        </CardContent>
      </Card>
    ))}
  </div>
)}
```

---

## SEO Optimization

### Meta Tags
```tsx
<Helmet>
  <title>BATbern Event Archive | 20+ Years of Conference Knowledge</title>
  <meta
    name="description"
    content="Explore 20+ years of BATbern conferences. Access presentations, speakers, and sessions from 50+ events covering Cloud, Security, AI/ML, and more."
  />
  <meta
    name="keywords"
    content="BATbern, conference archive, tech events, cloud computing, security, AI/ML, speakers"
  />

  {/* Open Graph */}
  <meta property="og:title" content="BATbern Event Archive" />
  <meta property="og:description" content="Explore 20+ years of BATbern conferences" />
  <meta property="og:type" content="website" />
  <meta property="og:url" content="https://batbern.ch/archive" />
  <meta property="og:image" content="https://batbern.ch/og-archive.jpg" />

  {/* Twitter Card */}
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="BATbern Event Archive" />
  <meta name="twitter:description" content="Explore 20+ years of conferences" />

  {/* Canonical */}
  <link rel="canonical" href="https://batbern.ch/archive" />
</Helmet>
```

### Structured Data (JSON-LD)
```tsx
<script type="application/ld+json">
{JSON.stringify({
  "@context": "https://schema.org",
  "@type": "CollectionPage",
  "name": "BATbern Event Archive",
  "description": "Archive of BATbern conferences from 2000 to present",
  "url": "https://batbern.ch/archive",
  "publisher": {
    "@type": "Organization",
    "name": "BATbern",
    "url": "https://batbern.ch"
  },
  "numberOfItems": totalEvents,
  "itemListElement": events.map((event, index) => ({
    "@type": "Event",
    "name": event.title,
    "startDate": event.date,
    "location": {
      "@type": "Place",
      "name": event.venueName
    },
    "url": `https://batbern.ch/events/${event.eventCode}`
  }))
})}
</script>
```

---

## React Router Configuration

```tsx
// Add to web-frontend/src/App.tsx or routing config

import { ArchivePage } from '@/pages/public/ArchivePage';
import { ArchiveEventDetailPage } from '@/pages/public/ArchiveEventDetailPage';

// Routes
<Routes>
  {/* Public routes */}
  <Route path="/" element={<HomePage />} />
  <Route path="/archive" element={<ArchivePage />} />
  <Route path="/archive/:eventCode" element={<ArchiveEventDetailPage />} />
  <Route path="/events/:eventCode" element={<HomePage />} /> {/* Upcoming events */}
  {/* ... other routes */}
</Routes>
```

**Route Distinction**:
- `/archive` - Archive browse page (Story 4.2)
- `/archive/{eventCode}` - Archive event detail (Story 4.2) - content-focused, no logistics
- `/events/{eventCode}` - Current event detail (Story 4.1) - full page with registration, venue, map

---

## Implementation Notes

### File Structure
```
web-frontend/src/
├── pages/
│   └── public/
│       ├── ArchivePage.tsx              # Archive browse page
│       └── ArchiveEventDetailPage.tsx   # Archive event detail page (NEW)
├── components/
│   └── public/
│       ├── Archive/
│       │   ├── ArchiveHero.tsx          # Hero section with search
│       │   ├── FilterSidebar.tsx        # Desktop filter sidebar
│       │   ├── FilterSheet.tsx          # Mobile filter sheet
│       │   ├── EventCard.tsx            # Event card (grid/list)
│       │   ├── EventGrid.tsx            # Grid layout container
│       │   ├── ViewControls.tsx         # Grid/List toggle + Sort
│       │   ├── EmptyState.tsx           # No results state
│       │   ├── EventDetailHeader.tsx    # Event header (detail page) (NEW)
│       │   ├── SessionDetailCard.tsx    # Session with materials (NEW)
│       │   └── SpeakerGrid.tsx          # Simple speaker grid (NEW)
│       └── ui/
│           └── (shadcn components)
├── hooks/
│   └── useArchiveFilters.ts             # Filter state management
├── services/
│   └── eventApiClient.ts                # API client (already exists)
└── types/
    ├── event.types.ts                   # Event types (already exists)
    └── material.types.ts                # Material/attachment types (NEW)
```

### Type Definitions
```typescript
// Add to web-frontend/src/types/event.types.ts

export interface ArchiveFilters {
  search: string;
  timePeriod: 'all' | 'last-5-years' | '2020-2024' | '2015-2019' | '2010-2014' | 'before-2010';
  topics: string[]; // Topic codes
}

export interface ArchiveViewState {
  viewMode: 'grid' | 'list';
  sortBy: 'newest' | 'oldest' | 'attended' | 'sessions';
  filters: ArchiveFilters;
}

export interface EventCardData extends EventDetail {
  // Topics (from ?include=topics)
  topics?: Array<{
    id: string;
    name: string;
    code: string;
  }>;

  // Sessions (from ?include=sessions with nested speakers)
  sessions?: Array<{
    sessionSlug: string;
    title: string;
    sessionType: string;
    description?: string;
    speakers?: Speaker[];
    materials?: SessionMaterial[];
    slidesUrl?: string;
  }>;
}

// Add to web-frontend/src/types/material.types.ts (NEW FILE)

export interface SessionMaterial {
  id: string;
  fileName: string;
  fileSize: number; // bytes
  downloadUrl: string;
  uploadedAt: string;
  contentType?: string; // e.g., "application/pdf"
}

export interface Speaker {
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  company?: string;
  profilePictureUrl?: string;
  bio?: string;
}
```

### React Query Keys
```typescript
// Centralized query key factory
export const archiveKeys = {
  all: ['events', 'archive'] as const,
  lists: () => [...archiveKeys.all, 'list'] as const,
  list: (filters: ArchiveFilters, sort: string, page: number) =>
    [...archiveKeys.lists(), filters, sort, page] as const,
  details: () => [...archiveKeys.all, 'detail'] as const,
  detail: (eventCode: string) =>
    [...archiveKeys.details(), eventCode] as const,
};
```

### Testing Scenarios

**Unit Tests**:
- Filter logic (combining multiple filters)
- Sort logic (different sort orders)
- Search query parsing
- URL state synchronization
- Event card rendering with different data states

**Integration Tests** (Playwright):
```typescript
test('Archive browsing flow with infinite scroll', async ({ page }) => {
  // Navigate to archive
  await page.goto('/archive');

  // Verify initial load
  await expect(page.getByText(/Showing \d+ of \d+ events/)).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(20);

  // Apply topic filter
  await page.getByLabel('Cloud').check();
  await page.waitForTimeout(500); // Wait for debounce
  await expect(page.getByRole('article')).toHaveCount(12);

  // Search
  await page.getByPlaceholder('Search events...').fill('Kubernetes');
  await page.waitForTimeout(500); // Wait for debounce
  const initialCount = await page.getByRole('article').count();
  expect(initialCount).toBeGreaterThan(0);

  // Test infinite scroll
  const initialEventCount = await page.getByRole('article').count();
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.waitForTimeout(1000); // Wait for load
  const newEventCount = await page.getByRole('article').count();
  expect(newEventCount).toBeGreaterThan(initialEventCount);

  // Switch to list view
  await page.getByLabel('List view').click();
  await expect(page.locator('.event-card-list')).toBeVisible();

  // View event details
  await page.getByRole('button', { name: /View Full Schedule/i }).first().click();
  await expect(page).toHaveURL(/\/events\/[A-Za-z0-9-]+/);
});
```

**Accessibility Tests**:
- Keyboard navigation through filters and events
- Screen reader announcements
- Focus management
- ARIA labels and roles
- Color contrast verification

---

## Success Criteria

**Functional**:
- ✅ Users can browse all 50+ historical events
- ✅ Search works across titles, topics, speakers
- ✅ Filters combine correctly (AND logic)
- ✅ Sorting updates results immediately
- ✅ Infinite scroll automatically loads more events
- ✅ Event cards show session titles with speakers and companies
- ✅ Grid/List view toggle persists
- ✅ URLs are shareable with filters
- ✅ "View Full Schedule" navigates to archive detail page
- ✅ Archive detail shows sessions with descriptions and materials
- ✅ Download PDF buttons work for presentation materials
- ✅ Clean content-focused view (no venue/logistics for past events)

**Performance**:
- ✅ Initial page load <2.5s LCP
- ✅ Filter/search updates <500ms
- ✅ Smooth scrolling and interactions
- ✅ Images lazy load properly

**Accessibility**:
- ✅ WCAG 2.1 Level AA compliant
- ✅ Keyboard navigation works
- ✅ Screen reader compatible
- ✅ Color contrast meets standards

**SEO**:
- ✅ Meta tags optimized
- ✅ Structured data included
- ✅ Canonical URLs set
- ✅ Archive page indexed by search engines

---

## Future Enhancements (Post-MVP)

**Story 4.3 Integration**:
- Link to content search from archive
- Show presentation counts per event
- Filter by presentation rating

**Analytics**:
- Track popular filter combinations
- Most viewed events
- Search queries

**Social Features**:
- Share events to social media
- Bookmark favorite events
- Event recommendations

**Advanced Filters**:
- Speaker filter (search/select speakers)
- Date range picker (precise dates)
- Venue filter
- Rating filter (when ratings available)

---

**Document Version**: 1.0
**Created**: 2026-01-10
**Implementation Ready**: Yes
**Approved**: Pending
