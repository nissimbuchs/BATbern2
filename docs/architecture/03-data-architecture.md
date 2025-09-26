# Data Architecture

This document outlines the comprehensive data model and database design for the BATbern Event Management Platform, following Domain-Driven Design principles with separate databases per bounded context.

## Data Models

### Company

**Purpose:** Centralized company entity for speakers, partners, and attendees with logo management and partner company recognition.

**Key Attributes:**
- id: UUID - Unique company identifier
- name: string - Official company name
- isPartner: boolean - Whether company is a BATbern partner
- logo: CompanyLogo - Uploaded logo with metadata
- website: string - Company website URL
- industry: string - Industry sector classification
- employeeCount: number - Approximate employee count
- headquarters: Address - Primary company location

#### TypeScript Interface
```typescript
interface Company {
  id: string;
  name: string;
  displayName: string;
  isPartner: boolean;
  logo?: CompanyLogo;
  website?: string;
  industry: string;
  employeeCount?: number;
  headquarters?: Address;
  description?: string;
  socialLinks: SocialLinks;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string; // User ID who created this company
}

interface CompanyLogo {
  fileId: string;
  fileName: string;
  mimeType: string;
  fileSizeBytes: number;
  s3Key: string;
  uploadedAt: Date;
  uploadedBy: string;
}
```

#### Relationships
- **One-to-Many:** Company → Speakers (speakers belong to companies)
- **One-to-One:** Company ↔ Partner (partner companies have additional partner data)
- **One-to-Many:** Company → Attendees (attendees work for companies)

### Partner

**Purpose:** Partnership-specific data for companies that sponsor BATbern events, with consistent participation across all events.

**Key Attributes:**
- id: UUID - Unique partner identifier
- companyId: UUID - Reference to company entity
- partnershipLevel: PartnershipTier - Sponsorship level and benefits
- partnershipStartDate: Date - When partnership began
- analytics: PartnerAnalytics - ROI and engagement metrics across all events
- strategicInput: PartnerInput - Topic voting and suggestions
- contacts: PartnerContact[] - Multiple contact persons

#### TypeScript Interface
```typescript
interface Partner {
  id: string;
  companyId: string;
  partnershipLevel: PartnershipTier;
  partnershipStartDate: Date;
  partnershipEndDate?: Date;
  isActive: boolean;
  contacts: PartnerContact[];
  analytics: PartnerAnalytics;
  strategicInput: PartnerInput;
  benefits: PartnershipBenefits;
  createdAt: Date;
  updatedAt: Date;
}

interface PartnerAnalytics {
  totalEmployeeAttendanceAllEvents: number;
  averageAttendancePerEvent: number;
  contentEngagementScore: number;
  brandExposureMetrics: BrandExposure;
  roiCalculations: ROIMetrics;
  topicInfluenceScore: number;
}

enum PartnershipTier {
  BRONZE = 'bronze',
  SILVER = 'silver',
  GOLD = 'gold',
  PLATINUM = 'platinum',
  STRATEGIC = 'strategic'
}
```

#### Relationships
- **One-to-One:** Partner → Company (partner data extends company)
- **One-to-Many:** Partner → PartnerContacts (multiple contact persons)
- **One-to-Many:** Partner → EmployeeAttendanceRecords (cross-event attendance tracking)

### Speaker

**Purpose:** Individual speakers with company affiliations and session assignments across multiple events.

**Key Attributes:**
- id: UUID - Unique speaker identifier
- companyId: UUID - Reference to company entity
- profile: SpeakerProfile - Bio, expertise, contact information
- speakingHistory: SpeakingEngagement[] - Past session participation
- availability: SpeakerAvailability - Current availability status

#### TypeScript Interface
```typescript
interface Speaker {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  companyId: string;
  position: string;
  profile: SpeakerProfile;
  availability: SpeakerAvailability;
  communicationPreferences: ContactPreferences;
  speakingHistory: SpeakingEngagement[];
  createdAt: Date;
  updatedAt: Date;
}

interface SpeakerProfile {
  shortBio: string;
  detailedBio: string;
  expertiseAreas: string[];
  profilePhotoUrl?: string;
  linkedInUrl?: string;
  twitterHandle?: string;
  certifications: string[];
  languages: string[];
  speakingTopics: string[];
}

enum SpeakerAvailability {
  AVAILABLE = 'available',
  BUSY = 'busy',
  UNAVAILABLE = 'unavailable',
  INVITED = 'invited',
  CONFIRMED = 'confirmed',
  DECLINED = 'declined'
}
```

#### Relationships
- **Many-to-One:** Speaker → Company (speaker belongs to company)
- **Many-to-Many:** Speaker ↔ Sessions (speakers can present multiple sessions)
- **One-to-Many:** Speaker → SpeakingEngagements (historical session participation)

### Session

**Purpose:** Individual agenda items with multiple speaker support and comprehensive material management.

**Key Attributes:**
- id: UUID - Unique session identifier
- eventId: UUID - Parent event reference
- speakers: SessionSpeaker[] - Multiple speakers with roles
- schedule: SessionSchedule - Timing and location details
- materials: SessionMaterials - Presentation files and resources

#### TypeScript Interface
```typescript
interface Session {
  id: string;
  eventId: string;
  title: string;
  description: string;
  sessionType: SessionType;
  speakers: SessionSpeaker[]; // Multiple speakers
  startTime: Date;
  endTime: Date;
  room?: string;
  capacity?: number;
  materials: SessionMaterials;
  tags: string[];
  prerequisites?: string[];
  targetAudience: ExperienceLevel[];
  language: string;
  createdAt: Date;
  updatedAt: Date;
}

interface SessionSpeaker {
  speakerId: string;
  role: SpeakerRole;
  presentationTitle?: string; // Speaker-specific title if different
  isConfirmed: boolean;
  invitedAt: Date;
  confirmedAt?: Date;
  declinedAt?: Date;
  declineReason?: string;
}

enum SessionType {
  KEYNOTE = 'keynote',
  PRESENTATION = 'presentation',
  WORKSHOP = 'workshop',
  PANEL_DISCUSSION = 'panel_discussion',
  NETWORKING = 'networking',
  BREAK = 'break',
  LUNCH = 'lunch'
}

enum SpeakerRole {
  PRIMARY_SPEAKER = 'primary_speaker',
  CO_SPEAKER = 'co_speaker',
  MODERATOR = 'moderator',
  PANELIST = 'panelist'
}
```

#### Relationships
- **Many-to-One:** Session → Event (session belongs to event)
- **Many-to-Many:** Session ↔ Speakers (multiple speakers per session)
- **One-to-Many:** Session → AttendeeRatings (feedback and ratings)
- **One-to-Many:** Session → Materials (uploaded files and resources)

### Event

**Purpose:** Conference events with attendee registrations and session management, no direct speaker relationships.

#### TypeScript Interface
```typescript
interface Event {
  id: string;
  eventNumber: number;
  title: string;
  description: string;
  eventDate: Date;
  registrationDeadline: Date;
  venue: Venue;
  status: EventStatus;
  organizerId: string;
  capacity: number;
  currentAttendeeCount: number;
  topics: Topic[];
  sessions: Session[]; // Sessions contain speaker relationships
  createdAt: Date;
  updatedAt: Date;
  publishedAt?: Date;
  metadata: EventMetadata;
}

enum EventStatus {
  PLANNING = 'planning',
  TOPIC_DEFINED = 'topic_defined',
  SPEAKERS_INVITED = 'speakers_invited',
  AGENDA_DRAFT = 'agenda_draft',
  PUBLISHED = 'published',
  REGISTRATION_OPEN = 'registration_open',
  REGISTRATION_CLOSED = 'registration_closed',
  IN_PROGRESS = 'in_progress',
  COMPLETED = 'completed',
  ARCHIVED = 'archived'
}
```

#### Relationships
- **One-to-Many:** Event → Sessions (event contains multiple sessions)
- **Many-to-Many:** Event ↔ Attendees (through EventRegistration)
- **No direct relationship to Speakers** (speakers connected via sessions)
- **No direct relationship to Partners** (partners participate in all events)

### Attendee

**Purpose:** Conference participants with company affiliations and multi-event participation tracking.

#### TypeScript Interface
```typescript
interface Attendee {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  companyId?: string; // Optional company affiliation
  position?: string;
  profile: AttendeeProfile;
  preferences: ContentPreferences;
  engagementHistory: AttendeeEngagement;
  eventRegistrations: EventRegistration[]; // Many-to-many with events
  newsletterSubscription: boolean;
  gdprConsent: GDPRConsent;
  createdAt: Date;
  updatedAt: Date;
  lastLoginAt?: Date;
}

interface EventRegistration {
  eventId: string;
  registrationDate: Date;
  status: RegistrationStatus;
  sessionPreferences: string[];
  specialRequests?: string;
  attendanceConfirmed?: boolean;
  actualAttendance: boolean;
}
```

## Database Schema

### Event Management Service Database Schema

```sql
-- Events table (aggregate root)
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_number INTEGER UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_date TIMESTAMP WITH TIME ZONE NOT NULL,
    registration_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_name VARCHAR(255) NOT NULL,
    venue_address TEXT NOT NULL,
    venue_capacity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'planning', 'topic_defined', 'speakers_invited', 'agenda_draft',
        'published', 'registration_open', 'registration_closed',
        'in_progress', 'completed', 'archived'
    )),
    organizer_id UUID NOT NULL,
    current_attendee_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

-- Sessions table
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    session_type VARCHAR(50) NOT NULL CHECK (session_type IN (
        'keynote', 'presentation', 'workshop', 'panel_discussion',
        'networking', 'break', 'lunch'
    )),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    room VARCHAR(100),
    capacity INTEGER,
    language VARCHAR(10) DEFAULT 'de',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_event_date ON events(event_date);
CREATE INDEX idx_sessions_event_id ON sessions(event_id);
```

### Speaker Coordination Service Database Schema

```sql
-- Speakers table
CREATE TABLE speakers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company_id UUID NOT NULL, -- References company service
    position VARCHAR(255),
    short_bio TEXT,
    detailed_bio TEXT,
    expertise_areas TEXT[] DEFAULT '{}',
    availability VARCHAR(50) NOT NULL CHECK (availability IN (
        'available', 'busy', 'unavailable', 'invited', 'confirmed', 'declined'
    )) DEFAULT 'available',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Session speaker assignments (many-to-many with roles)
CREATE TABLE session_speakers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL, -- References event service
    speaker_id UUID NOT NULL REFERENCES speakers(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL CHECK (role IN (
        'primary_speaker', 'co_speaker', 'moderator', 'panelist'
    )),
    presentation_title VARCHAR(255),
    is_confirmed BOOLEAN DEFAULT FALSE,
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    declined_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(session_id, speaker_id)
);

-- Indexes
CREATE INDEX idx_speakers_email ON speakers(email);
CREATE INDEX idx_speakers_company_id ON speakers(company_id);
CREATE INDEX idx_session_speakers_session_id ON session_speakers(session_id);
```

### Company Management Service Database Schema

```sql
-- Companies table
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    is_partner BOOLEAN DEFAULT FALSE,
    website VARCHAR(500),
    industry VARCHAR(100),
    employee_count INTEGER,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- Company logos (simplified)
CREATE TABLE company_logos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    uploaded_by UUID NOT NULL,
    is_current BOOLEAN DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_companies_name ON companies(name);
CREATE INDEX idx_companies_is_partner ON companies(is_partner);
CREATE UNIQUE INDEX idx_company_current_logo ON company_logos(company_id) WHERE is_current = TRUE;
```

### Partner Analytics Service Database Schema

```sql
-- Partners table
CREATE TABLE partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL UNIQUE, -- References company service
    partnership_level VARCHAR(50) NOT NULL CHECK (partnership_level IN (
        'bronze', 'silver', 'gold', 'platinum', 'strategic'
    )),
    partnership_start_date DATE NOT NULL,
    partnership_end_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Partner analytics aggregations
CREATE TABLE partner_analytics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    event_id UUID, -- NULL for cross-event analytics
    total_employee_attendance INTEGER DEFAULT 0,
    content_engagement_score DECIMAL(5,2) DEFAULT 0.00,
    brand_exposure_score DECIMAL(5,2) DEFAULT 0.00,
    roi_score DECIMAL(10,2) DEFAULT 0.00,
    topic_influence_score DECIMAL(5,2) DEFAULT 0.00,
    calculation_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(partner_id, event_id)
);

-- Indexes
CREATE INDEX idx_partners_company_id ON partners(company_id);
CREATE INDEX idx_partners_active ON partners(is_active);
CREATE INDEX idx_partner_analytics_partner_id ON partner_analytics(partner_id);
```

### Attendee Experience Service Database Schema

```sql
-- Attendees table
CREATE TABLE attendees (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company_id UUID, -- References company service (optional)
    position VARCHAR(255),
    newsletter_subscription BOOLEAN DEFAULT FALSE,
    gdpr_consent_given BOOLEAN DEFAULT FALSE,
    gdpr_consent_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE
);

-- Event registrations (many-to-many)
CREATE TABLE event_registrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL, -- References event service
    attendee_id UUID NOT NULL REFERENCES attendees(id) ON DELETE CASCADE,
    registration_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'registered', 'waitlisted', 'confirmed', 'cancelled', 'attended'
    )) DEFAULT 'registered',
    special_requests TEXT,
    attendance_confirmed BOOLEAN DEFAULT FALSE,
    actual_attendance BOOLEAN DEFAULT FALSE,
    UNIQUE(event_id, attendee_id)
);

-- Content engagement tracking
CREATE TABLE content_engagement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attendee_id UUID NOT NULL REFERENCES attendees(id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL, -- 'session', 'document', 'video', etc.
    content_id UUID NOT NULL,
    engagement_type VARCHAR(50) NOT NULL, -- 'view', 'download', 'rating', etc.
    engagement_value DECIMAL(5,2), -- rating score or duration
    engaged_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_attendees_email ON attendees(email);
CREATE INDEX idx_attendees_company_id ON attendees(company_id);
CREATE INDEX idx_event_registrations_event_id ON event_registrations(event_id);
CREATE INDEX idx_event_registrations_attendee_id ON event_registrations(attendee_id);
CREATE INDEX idx_content_engagement_attendee_id ON content_engagement(attendee_id);
```

## Data Consistency and Cross-Service Communication

### Domain Events

```typescript
// Shared domain events for data consistency
interface DomainEvent {
  id: string;
  aggregateId: string;
  aggregateType: string;
  eventType: string;
  eventVersion: number;
  occurredAt: Date;
  data: Record<string, any>;
}

// Example events
interface CompanyCreatedEvent extends DomainEvent {
  eventType: 'CompanyCreated';
  data: {
    companyId: string;
    name: string;
    isPartner: boolean;
  };
}

interface SpeakerInvitedEvent extends DomainEvent {
  eventType: 'SpeakerInvited';
  data: {
    speakerId: string;
    sessionId: string;
    eventId: string;
    invitedAt: Date;
  };
}
```

### Data Synchronization Strategy

1. **Eventual Consistency:** Services maintain local copies of essential data from other domains
2. **Event-Driven Updates:** Domain events propagate changes across service boundaries
3. **Saga Pattern:** Complex cross-service transactions use choreographed sagas
4. **Read Replicas:** Frequently accessed cross-domain data is cached locally