# BATbern Project TODO

## Documentation & Website
- [ ] Create a docs website, where the whole BATbern project is publicly available as nice html pages (prd, architecture, wireframes, stories, ...)
- [ ] Upgrade the current website on the main branch to latest versions of npm, angular and pull it to the feature branch
- [ ] Move the current BATbern website to aws s3, so we can seamlessly migrate to the new BATbern event site on the feature branch
- [ ] Think on a migration strategy, to smoothly transition from the current website to the new one
- [ ] Ensure the website looks great also for non-logged-in attendees

## Integrations & Tooling
- [ ] Use mcp to connect to figma and create high fidelity screens
- [ ] Use mcp to connect to jira and track epics, stories, tasks, releases there
- [ ] Update the story template to always read the relevant parts of the architecture and ux screens
- [ ] Enhance the DoD in stories to work with jira

## Architecture & Development
- [ ] Try to separate the project into scs (self-contained-services), or really separate projects, in order to be able to work with a smaller context
- [ ] Shard the prd and include references to the architecture and uc wireframes
- [ ] Ask sm to use a test-driven approach

## Infrastructure
- [x] Create an aws account for BATbern. create a user for claude