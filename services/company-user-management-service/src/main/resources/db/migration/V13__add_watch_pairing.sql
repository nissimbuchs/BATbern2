-- V13: Add watch_pairings table for Apple Watch pairing code management
-- Story W2.1: Pairing Code Backend & Web Frontend
-- Epic 2: Watch Pairing & Organizer Access

CREATE TABLE watch_pairings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username                VARCHAR(100) NOT NULL,
    pairing_code            VARCHAR(6),
    pairing_code_expires_at TIMESTAMP,
    pairing_token           VARCHAR(256) UNIQUE,
    device_name             VARCHAR(100),
    paired_at               TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_watch_user FOREIGN KEY (username) REFERENCES user_profiles(username)
);

-- Index for looking up pairings by user
CREATE INDEX idx_watch_pairings_username ON watch_pairings(username);

-- Index for code lookup during Watch pairing flow (partial: only rows with active codes)
CREATE INDEX idx_watch_pairings_code ON watch_pairings(pairing_code) WHERE pairing_code IS NOT NULL;

-- Enforce max 2 paired watches per organizer (AC2, NFR19)
-- Only counts rows where pairing is complete (paired_at NOT NULL)
CREATE UNIQUE INDEX idx_watch_pairings_limit
    ON watch_pairings(username, paired_at)
    WHERE paired_at IS NOT NULL;
