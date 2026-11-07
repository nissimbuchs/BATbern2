-- V23__profile_merge_requests.sql
-- #1237: a person who cannot read an earlier profile's mailbox any more (a former employer's
-- address) asks the organizers to merge it into their login profile. Any organizer approves or
-- declines in the Mail Center. Usernames, not ids: the source profile is deleted by the merge, and
-- the request stays as the record of who asked, who decided and why.
CREATE TABLE profile_merge_requests (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_username VARCHAR(100)  NOT NULL,
    source_username    VARCHAR(100)  NOT NULL,
    note               VARCHAR(500),
    status             VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_by         VARCHAR(100),
    decided_at         TIMESTAMPTZ,
    reason             VARCHAR(1000),
    CONSTRAINT chk_profile_merge_requests_status CHECK (status IN ('OPEN', 'APPROVED', 'DECLINED'))
);

-- At most one open request per profile pair.
CREATE UNIQUE INDEX uq_profile_merge_requests_open
    ON profile_merge_requests (requester_username, source_username) WHERE status = 'OPEN';
CREATE INDEX idx_profile_merge_requests_pair
    ON profile_merge_requests (requester_username, source_username, decided_at DESC);
