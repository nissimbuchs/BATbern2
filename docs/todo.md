# BATbern Project TODO

## Errors:
- cannot see the results of 5.4
- all buttons below session speaker tables fail
- addind speakers to the pool directly adds it to the session
- organizer dropdown in add to pool not selectable
- organizer dropdown when not in select topic page, shows user.nissim, but not real organizer users
- adding a speaker to the speaker pool from the speaker pool page, does not create a session it seams, or its a caching problem

## Documentation & Website
- [ ] Think on a migration strategy, to smoothly transition from the current website to the new one

## Architecture & Development
- [ ] topics have UUID in api, against ADR-003
- [ ] event has dto and generated dto in backend, should only use generated dto's.
- [ ] event has metadata in api payload for theme and eventType that are both obsolete
- [ ] TODO: story-1.16-workflow-visualisation wirframe was never implemented. shows up when you press on the progress bar
- [x] event dashboard status filter has wrong stati. change year filter to be a textbox that allows any year

Accessing the PostgeSQL through the tunnel:
PGPASSWORD from /Users/nissim/dev/bat/BATbern-feature/.env.native
export PGPASSWORD="***" && psql -h localhost -p 5432 -U postgres -d batbern -c "\d user_profiles"
