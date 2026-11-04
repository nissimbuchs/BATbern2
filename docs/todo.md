# BATbern Project TODO

> **HISTORICAL, DONE (marked 2026-11-04, doc audit #1210).** These are working notes from the
> staging-to-production consolidation (last changed 2026-03-11). The consolidation is done: the
> account 188701360969 serves production (www.batbern.ch, api.batbern.ch, cdn.batbern.ch), see
> `CLAUDE.md` → Deployment. Open work is tracked in
> `_bmad-output/implementation-artifacts/sprint-status.yaml` and GitHub issues, not here.


Make staging production: progress


SES confirmed: Production access enabled, 50k/day send quota. DNS records documented to /tmp/prod-dns-records.json.
                                                                                                                           
  Key findings from the DNS records:                                                                                       
  - batbern.ch + www.batbern.ch → CloudFront (production frontend)
  - api.batbern.ch → API Gateway (production)                                                                              
  - cdn.batbern.ch → CloudFront (production CDN)                                                                         
  - project.batbern.ch → CloudFront (some project page — need to preserve this)
  - staging.batbern.ch → NS delegation to staging account
  - Several ACM validation CNAMEs
