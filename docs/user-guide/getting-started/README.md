# Getting Started

> Quick start guide for BATbern organizers

## Welcome to BATbern

BATbern is a comprehensive event management platform designed specifically for Berner Architekten Treffen (BATbern) conferences. As an **Organizer**, you have access to powerful tools for managing every aspect of conference planning and execution.

## What You Can Do

As an organizer, you can:

- ✅ **Manage Entities**: Create and edit companies, users, events, partners, and speakers
- ✅ **Plan Events**: Define event types, select topics, and coordinate speakers
- ✅ **Track Workflows**: Follow the 16-step workflow from concept to execution
- ✅ **Coordinate Partners**: Manage partner relationships and meeting schedules
- ✅ **Analyze Data**: Visualize historical trends with topic heat maps
- ✅ **Generate Reports**: Track event metrics and speaker engagement

## Platform Architecture

BATbern follows a **microservices architecture** with:

- **API Gateway**: Unified entry point at `http://localhost:8080` (development)
- **Domain Services**: Specialized services for companies, events, partners, speakers
- **React Frontend**: Modern single-page application at `http://localhost:3000` (development)
- **AWS Infrastructure**: Scalable cloud deployment with PostgreSQL, S3, CloudFront

## User Roles

BATbern supports 4 distinct roles:

| Role | Access Level | Typical Responsibilities |
|------|--------------|-------------------------|
| **Admin** | Full system access | System configuration, user management, all operations |
| **Organizer** | Event planning & coordination | Plan events, manage speakers, coordinate partners |
| **Speaker** | Speaker profile management | Update profile, submit content, manage session assignments |
| **Attendee** | Public registration | Register for events, view schedules, access materials |

**You are an Organizer** - you have elevated permissions for event planning and coordination.

## Quick Start Checklist

<div class="step" data-step="1">

### Log In to BATbern
Access the platform using your AWS Cognito credentials. See [Login & Authentication](login.md) for detailed instructions.
</div>

<div class="step" data-step="2">

### Explore the Dashboard
Familiarize yourself with the organizer dashboard and navigation patterns. See [Dashboard Navigation](dashboard.md) for a guided tour.
</div>

<div class="step" data-step="3">

### Review Entity Management
Understand how to create and manage companies, users, events, and partners. See [Entity Management](../entity-management/README.md).
</div>

<div class="step" data-step="4">

### Learn the 16-Step Workflow
Master the complete event lifecycle from topic selection to post-event archival. See [16-Step Workflow](../workflow/README.md).
</div>

## Platform Environments

BATbern runs in multiple environments:

| Environment | URL | Purpose | Authentication |
|-------------|-----|---------|----------------|
| **Local** | http://localhost:3000 | Development | Staging Cognito |
| **Development** | https://dev.batbern.ch | Testing | Dev Cognito |
| **Staging** | https://staging.batbern.ch | Pre-production | Staging Cognito |
| **Production** | https://www.batbern.ch | Live conferences | Production Cognito |

**Note**: Local development uses staging Cognito for authentication, avoiding AWS infrastructure costs.

## Key Concepts

### Events
A BATbern event represents a single conference occurrence (e.g., "BATbern 2025"). Events have:
- **Event Type**: Full-day, afternoon, or evening format
- **Timeline**: Date, duration, registration windows
- **Sessions**: Individual presentations or activities
- **Speakers**: Presenters assigned to sessions

### Topics
Topics are subject areas for conference sessions (e.g., "Sustainable Building Materials", "Digital Transformation"). Topics are:
- Selected by organizers using historical heat map data
- Assigned to speakers during outreach
- Used to structure the event agenda

### Workflow States
The platform tracks event and speaker progress through defined states:
- **Event States**: CREATED → TOPIC_SELECTED → SPEAKERS_IDENTIFIED → ... → ARCHIVED
- **Speaker States**: IDENTIFIED → CONTACTED → INTERESTED → ... → CONFIRMED

### Partners
Organizations that collaborate with BATbern:
- **Tier Classification**: Diamond, Platinum, Gold, Silver, Bronze
- **Meeting Coordination**: Schedule partner meetings during events
- **Directory**: Public-facing partner showcase

## What's Next?

- [Login & Authentication →](login.md) - Access your organizer account
- [Dashboard Navigation →](dashboard.md) - Navigate the organizer interface
- [UI Conventions →](navigation.md) - Learn common patterns and shortcuts

## Need Help?

- 📚 Search this documentation (search box in sidebar)
- 🐛 Report issues: [GitHub Issues](https://github.com/nissimbuchs/BATbern2/issues)
- 📧 Contact support: info@berner-architekten-treffen.ch
