# BATbern Project TODO

## Documentation & Website
- [ ] Think on a migration strategy, to smoothly transition from the current website to the new one

## Architecture & Development
- [ ] topics have UUID in api, against ADR-003
- [ ] not all ids are translated in topics backlog
- [ ] event has dto and generated dto in backend, should only use generated dto's.
- [x] event has status and workflowState. redundant? ✅ COMPLETED: status field removed in V17 migration (2025-12-15)
- [ ] event has metadata in api payload for theme and eventType that are both obsolete
- [ ] history diagram not shown but implemented


Accessing the PostgeSQL through the tunnel:
PGPASSWORD from /Users/nissim/dev/bat/BATbern-feature/.env.native
export PGPASSWORD="***" && psql -h localhost -p 5432 -U postgres -d batbern -c "\d user_profiles"
