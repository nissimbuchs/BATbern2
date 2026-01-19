# BATbern Data Architecture - Logical Diagram

## Overview

This diagram shows the logical data architecture following ADR-003 (Meaningful Identifiers) and ADR-004 (User Data Centralization).

**Key Principles:**
- **Single PostgreSQL database** (`batbern`) with shared `public` schema
- **Cross-service references** use meaningful IDs (username, eventCode, companyName) - NOT UUIDs
- **Within-service references** use UUID foreign keys
- **No database foreign keys** across service boundaries
- **HTTP-based enrichment** for cross-service data access

---

## Service Ownership Diagram

```mermaid
flowchart TB
    subgraph DB["PostgreSQL Database: batbern"]
        subgraph CUM["Company-User Management Service"]
            companies[("companies<br/>─────────<br/>id: UUID (PK)<br/>name: VARCHAR ★<br/>display_name")]
            users[("user_profiles<br/>─────────<br/>id: UUID (PK)<br/>username: VARCHAR ★<br/>email, firstName, lastName<br/>bio, profilePictureUrl<br/>company_id: company.name")]
            roles[("role_assignments<br/>─────────<br/>user_id: UUID (FK)<br/>role: ENUM")]

            users -->|UUID FK| roles
            users -.->|"company_id = name"| companies
        end

        subgraph EMS["Event Management Service"]
            events[("events<br/>─────────<br/>id: UUID (PK)<br/>event_code: VARCHAR ★<br/>organizer_username<br/>title, description")]
            sessions[("sessions<br/>─────────<br/>id: UUID (PK)<br/>slug: VARCHAR ★<br/>event_id: UUID (FK)<br/>title, description")]
            session_users[("session_users<br/>─────────<br/>id: UUID (PK)<br/>session_id: UUID (FK)<br/>username ★<br/>role: speaker/moderator")]
            speakers_table[("speakers<br/>─────────<br/>id: UUID (PK)<br/>username ★<br/>expertise_areas<br/>speaking_topics")]
            speaker_pool[("speaker_pool<br/>─────────<br/>id: UUID (PK)<br/>event_id: UUID (FK)<br/>username ★ (nullable)<br/>speaker_name, email†<br/>status, notes")]

            sessions -->|UUID FK| events
            session_users -->|UUID FK| sessions
            speaker_pool -->|UUID FK| events
        end

        subgraph PCS["Partner Coordination Service"]
            partners[("partners<br/>─────────<br/>id: UUID (PK)<br/>company_name ★<br/>partnership_level<br/>is_active")]
            partner_contacts[("partner_contacts<br/>─────────<br/>id: UUID (PK)<br/>partner_id: UUID (FK)<br/>username ★<br/>contact_role")]

            partner_contacts -->|UUID FK| partners
        end

        subgraph AES["Attendee Experience Service"]
            registrations[("event_registrations<br/>─────────<br/>id: UUID (PK)<br/>event_code ★<br/>username ★<br/>status, registered_at")]
        end
    end

    %% Cross-service references (meaningful IDs - dashed lines)
    events -.->|"organizer_username"| users
    session_users -.->|"username"| users
    speakers_table -.->|"username"| users
    speaker_pool -.->|"username (optional)"| users
    partners -.->|"company_name"| companies
    partner_contacts -.->|"username"| users
    registrations -.->|"event_code"| events
    registrations -.->|"username"| users

    style companies fill:#e1f5fe
    style users fill:#e1f5fe
    style roles fill:#e1f5fe
    style events fill:#fff3e0
    style sessions fill:#fff3e0
    style session_users fill:#fff3e0
    style speakers_table fill:#fff3e0
    style speaker_pool fill:#fff3e0
    style partners fill:#f3e5f5
    style partner_contacts fill:#f3e5f5
    style registrations fill:#e8f5e9
```

**Legend:**
- ★ = Meaningful ID (exposed in API)
- † = ADR-004 exemption (SpeakerPool brainstorming entity)
- Solid arrows = UUID Foreign Keys (within service)
- Dashed arrows = Meaningful ID references (cross-service, no DB FK)

---

## Cross-Service Communication Pattern

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant GW as API Gateway
    participant EMS as Event Mgmt Service
    participant CUM as Company-User Service
    participant Cache as Caffeine Cache

    FE->>GW: GET /api/v1/sessions/{slug}/speakers
    GW->>EMS: Forward request + JWT

    EMS->>EMS: Query session_users by slug
    Note over EMS: Returns: [{username: "john.doe", role: "speaker"}]

    EMS->>Cache: Check cache for "john.doe"
    alt Cache Hit
        Cache-->>EMS: Cached UserResponse
    else Cache Miss
        EMS->>CUM: GET /api/v1/users/john.doe (JWT propagated)
        CUM-->>EMS: UserResponse (email, name, bio, photo)
        EMS->>Cache: Store with 15min TTL
    end

    EMS->>EMS: Combine Speaker + User data
    EMS-->>GW: SessionSpeakerResponse[]
    GW-->>FE: JSON Response
```

---

## Identifier Strategy

```mermaid
flowchart LR
    subgraph Internal["Database Layer (Internal)"]
        UUID_PK["UUID Primary Keys<br/>• Immutable<br/>• B-tree efficient<br/>• Distributed generation"]
        UUID_FK["UUID Foreign Keys<br/>• Within-service only<br/>• Referential integrity<br/>• Cascade deletes"]
    end

    subgraph External["API Layer (Public)"]
        MID["Meaningful IDs<br/>• eventCode: BATbern56<br/>• username: john.doe<br/>• companyName: GoogleZH"]
    end

    subgraph CrossService["Cross-Service References"]
        NoFK["No Database FK<br/>• Store meaningful ID<br/>• HTTP enrichment<br/>• Microservice isolation"]
    end

    UUID_PK --> UUID_FK
    UUID_PK --> MID
    MID --> NoFK
```

---

## Entity Relationship Summary

| Service | Entity | PK | Meaningful ID | Cross-Service Refs |
|---------|--------|----|--------------|--------------------|
| Company-User | Company | UUID | `name` | - |
| Company-User | User | UUID | `username` | `company_id` → Company.name |
| Event Mgmt | Event | UUID | `event_code` | `organizer_username` → User |
| Event Mgmt | Session | UUID | `slug` | - |
| Event Mgmt | SessionUser | UUID | - | `username` → User |
| Event Mgmt | Speaker | UUID | - | `username` → User |
| Event Mgmt | SpeakerPool | UUID | - | `username` → User (optional†) |
| Partner Coord | Partner | UUID | - | `company_name` → Company |
| Partner Coord | PartnerContact | UUID | - | `username` → User |
| Attendee Exp | Registration | UUID | - | `event_code` → Event, `username` → User |

† SpeakerPool has ADR-004 exemption for brainstorming phase (tracks potential speakers without accounts)

---

## ADR Compliance Summary

### ADR-003: Meaningful Identifiers
- ✅ All public APIs use meaningful IDs (eventCode, username, companyName)
- ✅ Cross-service references store meaningful IDs, not UUIDs
- ✅ No database foreign keys across service boundaries
- ✅ HTTP-based enrichment for cross-service data

### ADR-004: User Data Centralization
- ✅ User profile fields (email, name, bio, photo) only in User Service
- ✅ Domain entities reference User by username
- ✅ 15-minute Caffeine cache for HTTP enrichment
- ⚠️ SpeakerPool exemption documented (brainstorming entity)

---

## Service Boundary Rules

```
┌─────────────────────────────────────────────────────────────┐
│  RULE: Reference Decision Tree                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Is the referenced entity in THIS service's database?        │
│  │                                                           │
│  ├─ YES → Use UUID Foreign Key ✅                            │
│  │        Example: session_id UUID REFERENCES sessions(id)   │
│  │                                                           │
│  └─ NO  → Use Meaningful ID (String) ✅                      │
│           Example: username VARCHAR(100)                     │
│           NO database FK constraint                          │
│           Enrich via HTTP call                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```
