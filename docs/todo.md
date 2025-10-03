# BATbern Project TODO

## Documentation & Website
- [x] Create a docs website, where the whole BATbern project is publicly available as nice html pages (prd, architecture, wireframes, stories, ...)
- [ ] Think on a migration strategy, to smoothly transition from the current website to the new one
- [ ] Ensure the website looks great also for non-logged-in attendees

## Integrations & Tooling
- [x] Use mcp to connect to jira and track epics, stories, tasks, releases there
- [x] Update the story template to always read the relevant parts of the architecture and ux screens
- [ ] Enhance the DoD in stories to work with jira

## Architecture & Development
- [x] Try to separate the project into scs (self-contained-services), or really separate projects, in order to be able to work with a smaller context
- [x] Shard the prd and include references to the architecture and uc wireframes
- [x] Ask sm to use a test-driven approach
- [ ] Define human readable business IDs for important entities like events, sessions, users (all roles), companies and others

## Infrastructure
- [x] Create an aws account for BATbern. create a user for claude

## More detailed Stories, with wireframes and api's
- [x] Wireframes fertig ergänzen
- [x] Sitemap pro Rolle zeichnen
- [ ] Wireframes durch pm reviewen und nicht benötigtes löschen 
- [ ] Fehlende Wireframes designen
- [ ] API Architektur aktualisieren
- [ ] Datenmodelle aktualisieren 
- [ ] Stories mit wireframes verknüpfen 
- [ ] Stories in substories aufteilen. Wie noch unklar. Pro task, pro Screen oder pro screencomponent, …

## Needed PRD updates:
- [x] FR04: remove this requirement
- [x] FR09: remove this requirement
- [x] FR13: remove this requirement ✅ Completed 2025-10-01 (Sprint Change Proposal)
- [x] FR16: remove this requirement ✅ Completed 2025-10-01 (Sprint Change Proposal)
- [x] FR18: Update PRD to explicitly require: Visual heat map representation, ML similarity scoring with duplicate avoidance staleness detection with recommended wait periods
- [x] New requirement: Organizers are like administrators, and can promote other users to speakers or organizers
- [x] EXPAND PRD Add new section: “4.1 Authentication & Authorization Architecture”:
        Detailed AWS Cognito integration requirements
        Role-permission matrix for all features
        User lifecycle management workflows
- [x] ENHANCE PRD Add requirements:
        Email template management system (FR20)
        User notification preference system (FR14)
        Notification escalation rules (FR20)
        AWS SES integration specifications
- [x] EXPAND PRD Add new section: "4.2 Content Management & Storage Architecture"
        AWS S3 storage strategy
        CloudFront CDN configuration
        File size and format constraints
        Storage quota policies per role
        Backup and disaster recovery for content
- [ ] ENHANCE PRD Add requirement: Organizers can schedule partner meetings. invites are automatically sent to partners as calendar invites.
- [x] FR17: remove state waitlist. this is a separate list of ready, but declined speakers
- [x] FR17: remove state informed. this is not a state
- [x] FR19: remove lessons learned
- [ ] 
- [ ] 
- [ ] 
- [ ] 


## Missing wireframes
in the docs/wireframe folder, update the wireframes as follows:\
- [ ] add Public Profile Preview Screen
- [ ] add Invitation Management Screen
- [ ] add content library/repository screen
- [ ] add content detail/edit screen
- [ ] add Partner Directory/List Screen
- [ ] add Partner Detail Screen
- [ ] add Partner Settings Screen
- [ ] add User Profile Screen
- [x] add User Settings Screen
- [ ] add Company Management Screen
- [ ] add Moderator Review Queue Screen
- [ ] add System Settings/Configuration Screen
- [ ] add Resource Viewer Screen
- [ ] add Announcement Details Screen
- [ ] add Event Details Page (Attendee View)
- [ ] add Content Viewer Page
- [ ] add Session Details Modal
- [ ] add Attendee List Modal
- [ ] 
- [ ] 
- [ ] 
- [ ] 
- [ ] 
- [ ] 
