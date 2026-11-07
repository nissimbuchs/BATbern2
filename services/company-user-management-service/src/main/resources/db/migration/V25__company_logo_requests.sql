-- V25__company_logo_requests.sql
-- #1246: a participant asks the organizers for a new logo of their company; any organizer accepts
-- or declines in the Mail Center. The old logo URL is a snapshot, so the decision and the record
-- show what the company had when the person asked. The new logo is an upload associated with the
-- request (logos.associated_entity_type = 'LOGO_REQUEST'), out of reach of the logo cleanup.
CREATE TABLE company_logo_requests (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_username VARCHAR(100)  NOT NULL,
    company_name       VARCHAR(255)  NOT NULL,
    old_logo_url       VARCHAR(1000),
    new_logo_upload_id VARCHAR(100)  NOT NULL,
    new_logo_url       VARCHAR(1000) NOT NULL,
    note               VARCHAR(500),
    status             VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_by         VARCHAR(100),
    decided_at         TIMESTAMPTZ,
    reason             VARCHAR(1000),
    CONSTRAINT chk_company_logo_requests_status CHECK (status IN ('OPEN', 'APPROVED', 'DECLINED'))
);

-- At most one open request per person and company.
CREATE UNIQUE INDEX uq_company_logo_requests_open
    ON company_logo_requests (requester_username, company_name) WHERE status = 'OPEN';
