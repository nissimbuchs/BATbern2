# BATbern Project TODO

## Errors:
- as ux epert to help with eventdetail and eventdetaildit pages and where to put all the session pages. use tabs?
- update prd with new flow
- ask ux expert to reorganize better the user-guide.
- ask user expert to use playwright on localhost to get screenshots of all the major screens and pages
- company logos on list does not show, but on detail shows. again the origin problem?

## Documentation & Website
- [ ] Think on a migration strategy, to smoothly transition from the current website to the new one

## Architecture & Development
- [on branch claude/review-branches-BROGI] topics have UUID in api, against ADR-003
- [on branch claude/review-branches-BROGI] event has dto and generated dto in backend, should only use generated dto's.
- [] event has metadata in api payload for theme and eventType that are both obsolete
- [ ] TODO: story-1.16-workflow-visualisation wirframe was never implemented. shows up when you press on the progress bar

Accessing the PostgeSQL through the tunnel:
PGPASSWORD from /Users/nissim/dev/bat/BATbern-feature/.env.native
export PGPASSWORD="***" && psql -h localhost -p 5432 -U postgres -d batbern -c "\d user_profiles"
