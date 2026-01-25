# BATbern Project TODO

## Errors:
- ask ux expert to reorganize better the user-guide.
- clean up all tasks and notifications when an event is archived

## Architecture & Development
- [ ] topics have UUID in api, against ADR-003
- [ ] event has dto and generated dto in backend, should only use generated dto's.
- [ ] TODO: story-1.16-workflow-visualisation wirframe was never implemented. shows up when you press on the progress bar

Accessing the PostgeSQL through the tunnel:
PGPASSWORD from /Users/nissim/dev/bat/BATbern-feature/.env.native
export PGPASSWORD="***" && psql -h localhost -p 5432 -U postgres -d batbern -c "\d user_profiles"
