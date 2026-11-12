-- V153: the uploads table of event-management (Story 17.11, ADR-002 decision 2026-11-07).
--
-- One upload contract for every service: shared-kernel ch.batbern.shared.upload runs the mechanics
-- (PENDING -> CONFIRMED -> ASSOCIATED, expiry), each service keeps its uploads in its own table. All
-- services share one database, so the table name carries the service.
--
-- Starts empty (owner 2026-11-12): no upload or logo request was in flight, so nothing is copied from
-- the shared logos table. logos stays until its drop gets the owner's go.

CREATE TABLE event_management_uploads (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    upload_id       VARCHAR(100)  NOT NULL UNIQUE,
    purpose         VARCHAR(50)   NOT NULL,
    s3_key          VARCHAR(500)  NOT NULL,
    file_url        VARCHAR(1000),
    file_extension  VARCHAR(10)   NOT NULL,
    file_size       BIGINT        NOT NULL,
    content_type    VARCHAR(100)  NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    target_type     VARCHAR(50),
    target_id       VARCHAR(255),
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT event_management_uploads_status_check CHECK (status IN ('pending', 'confirmed', 'associated')),
    CONSTRAINT event_management_uploads_associated_check CHECK (
        (status = 'associated') = (target_id IS NOT NULL AND file_url IS NOT NULL AND expires_at IS NULL))
);

-- The expiry job reads PENDING and CONFIRMED rows by expiry.
CREATE INDEX idx_event_management_uploads_expiry ON event_management_uploads (expires_at) WHERE status IN ('pending', 'confirmed');
CREATE INDEX idx_event_management_uploads_target ON event_management_uploads (target_type, target_id);
