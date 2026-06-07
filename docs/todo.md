# BATbern Project TODO


Make staging production: progress


SES confirmed: Production access enabled, 50k/day send quota. DNS records documented to /tmp/prod-dns-records.json.
                                                                                                                           
  Key findings from the DNS records:                                                                                       
  - batbern.ch + www.batbern.ch → CloudFront (production frontend)
  - api.batbern.ch → API Gateway (production)                                                                              
  - cdn.batbern.ch → CloudFront (production CDN)                                                                         
  - project.batbern.ch → CloudFront (some project page — need to preserve this)
  - staging.batbern.ch → NS delegation to staging account
  - Several ACM validation CNAMEs
