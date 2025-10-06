# BATbern Conference Website - Product Requirements Document

## Executive Summary

BATbern (Berner Architekten-Treffen) is a conference website for Swiss IT architecture professionals. The Angular-based single-page application serves as the digital presence for a long-running technical meetup series focused on enterprise architecture, software development, and emerging technologies.

## Product Overview

### Product Vision
To provide a comprehensive digital platform that showcases the BATbern conference series, facilitates event discovery, and serves as an archive for technical presentations and community content.

### Product Goals
- **Information Hub**: Central source for current and upcoming BAT events
- **Historical Archive**: Comprehensive repository of past conferences and materials
- **Community Engagement**: Platform for participant interaction and content sharing
- **Event Promotion**: Effective marketing and registration facilitation

## Market Context

### Target Audience
- **Primary**: Swiss IT architects, senior developers, and technical leads
- **Secondary**: Software engineering professionals interested in enterprise architecture
- **Tertiary**: Technology decision-makers and consultants

### Business Context
- Long-established technical meetup (50+ events since 2005)
- Regular schedule with thematic focus areas
- Professional networking and knowledge sharing community
- Partnership with Zentrum Paul Klee venue in Bern

## Core Features & Functionality

### 1. Current Event Information (Landing Page)
**Purpose**: Display next scheduled BAT event details

**Features**:
- Hero section with conference branding
- Current event details (BAT number, date, topic, venue)
- Event type classification (Abend-BAT, Ganztages-BAT, etc.)
- Registration integration
- Venue information and directions

**Data Model**: Topic entity with `next: true` flag

### 2. Event Archive System
**Purpose**: Historical repository of all BAT events

**Features**:
- Chronological listing of all BAT events (BAT 1-54+)
- Event details including dates, topics, and types
- Sortable by BAT number (reverse chronological)
- Direct navigation to detailed event pages
- Integration with external presentation platform (Slideshare)

**Data Model**: Topic collection with full historical data

### 3. Event Detail Pages
**Purpose**: Comprehensive information about specific BAT events

**Features**:
- Session listings with speaker information
- Presentation materials and PDF access
- Speaker profiles with bio, company, and portraits
- Photo galleries from events
- Abstract and content descriptions
- External link management for presentations

**Data Models**:
- Session entity with speaker details
- Picture entity for event photography
- Referent (speaker) information

### 4. Planned Events Preview
**Purpose**: Future event visibility and planning

**Features**:
- Upcoming BAT event cards
- Event type and date information
- Placeholder topics for community input
- Contact information for topic suggestions

**Data Model**: Topic entities with `planned: true` flag

### 5. Registration System Integration
**Purpose**: Event sign-up functionality

**Current State**: Component structure exists but registration details to be determined
**Future Enhancement**: Full registration workflow implementation

### 6. Organization Information
**Purpose**: Background about BAT community and organizers

**Current State**: Component exists for association/organization details
**Content**: Details about the organizing body and community

### 7. Contact & Communication
**Purpose**: Community engagement and inquiry handling

**Features**:
- Contact information display
- Email integration for topic suggestions
- Community communication channels

## Technical Architecture

### Frontend Technology Stack
- **Framework**: Angular 14.x
- **UI Library**: Clarity Design System (@clr/angular)
- **Additional UI**: Bootstrap 5.2, ng-bootstrap
- **Styling**: SCSS/CSS with responsive design
- **Icons**: Clarity Design System icons

### Data Architecture
- **Data Source**: Static JSON files (`topics.json`, `sessions.json`, `pictures.json`)
- **Services**: Angular service layer with HTTP client
- **State Management**: Component-based state with RxJS observables
- **Data Processing**: Client-side filtering, sorting, and mapping

### Content Management
- **Presentations**: External integration with Slideshare
- **Images**: Local asset management with structured naming
- **PDFs**: Mixed local and external hosting strategy

## Data Models

### Topic Entity
```typescript
interface ITopic {
  bat: number;           // Event sequence number
  topic: string;         // Event theme/title
  datum: string;         // Date information
  next?: boolean;        // Current event flag
  planned?: boolean;     // Future event flag
  eventType?: string;    // Event format classification
}
```

### Session Entity
```typescript
interface ISession {
  bat: number;           // Associated event number
  pdf: string;           // Presentation link/path
  author: string;        // Presenter name
  title: string;         // Presentation title
  abstract: string;      // Content description
  referenten: IReferent[]; // Speaker details
}
```

### Speaker/Referent Entity
```typescript
interface IReferent {
  name: string;          // Speaker name
  bio: string;           // Professional background
  portrait: string;      // Photo reference
  company: string;       // Organization affiliation
}
```

### Picture Entity
```typescript
interface IPicture {
  bat: number;           // Associated event number
  image: string;         // Image filename
}
```

## User Experience Requirements

### Navigation & Information Architecture
- **Single-page application** with section-based navigation
- **Current event focus** on landing page
- **Archive accessibility** through dedicated event listings
- **Detail page** drill-down for comprehensive information

### Responsive Design
- Mobile-first approach with adaptive layouts
- Image gallery optimizations for different screen sizes
- Touch-friendly interaction patterns

### Performance Requirements
- Fast initial page load with lazy loading for images
- Efficient data filtering and sorting for large event archives
- Smooth scrolling and navigation transitions

## Content Strategy

### Historical Content
- **Complete archive** of 50+ BAT events dating to 2005
- **Presentation materials** with external platform integration
- **Speaker profiles** and expertise documentation
- **Event photography** for community engagement

### Current Content Management
- **Event information** updates for each BAT cycle
- **Speaker coordination** for profile and presentation materials
- **Community communication** through integrated contact systems

## Success Metrics

### User Engagement
- Page views and session duration
- Event detail page engagement
- Archive exploration patterns
- Registration conversion rates

### Community Value
- Topic suggestion submissions
- External presentation platform integration usage
- Return visitor patterns for regular attendees

## Technical Considerations

### Scalability
- Static content approach supports reliable hosting
- JSON-based data management for easy content updates
- External platform integration reduces local storage requirements

### Maintenance
- Component-based architecture for feature updates
- Service layer abstraction for potential backend integration
- Consistent data modeling for content expansion

## Future Enhancements

### Short-term Improvements
- Complete registration system implementation
- Enhanced mobile experience optimization
- Content management workflow improvements

### Long-term Vision
- Backend content management system
- User account and community features
- Advanced search and filtering capabilities
- Real-time event updates and notifications

## Conclusion

The BATbern website serves as a critical digital touchpoint for a well-established technical community. The current Angular implementation provides a solid foundation for event information dissemination and community engagement, with clear pathways for enhancement and growth as the conference series continues to evolve.