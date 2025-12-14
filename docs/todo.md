# BATbern Project TODO

## Documentation & Website
- [ ] Think on a migration strategy, to smoothly transition from the current website to the new one

## Architecture & Development
- [ ] topics have UUID in api, against ADR-003
- [ ] not all ids are translated in topics backlog
- [ ] event has dto and generated dto in backend, should only use generated dto's
- [ ] event has status and workflow state redundant?
- [ ] event has metadate for thema and event_type that are both obsolete

Accessing the PostgeSQL through the tunnel:
PGPASSWORD from /Users/nissim/dev/bat/BATbern-feature/.env.native
export PGPASSWORD="***" && psql -h localhost -p 5432 -U postgres -d batbern -c "\d user_profiles"
