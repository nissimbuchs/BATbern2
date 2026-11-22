-- V41: agentic interaction, epic agent identity, story 4. The organizer agent's switch, allowlist
-- and delegate, kept in CUMS because CUMS issues the agent grants and tokens and cannot call EMS
-- (epic decision 2026-11-21; EMS app_settings cannot serve).
--
-- agent_settings is a singleton (id = 1). It starts switched off with no delegate; no username is
-- seeded (C3: the allowlist and the delegate are organizers chosen at run time). The delegate and
-- every allowlisted username are profiles: a deleted profile drops out of the allowlist and clears
-- the delegate, a renamed one follows (as watch_pairings, V39).
CREATE TABLE agent_settings (
    id                INTEGER      PRIMARY KEY,
    enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    delegate_username VARCHAR(100),
    updated_at        TIMESTAMPTZ,
    updated_by        VARCHAR(255),
    CONSTRAINT chk_agent_settings_singleton CHECK (id = 1),
    CONSTRAINT fk_agent_settings_delegate FOREIGN KEY (delegate_username)
        REFERENCES user_profiles (username) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE agent_allowlist (
    username VARCHAR(100) PRIMARY KEY,
    added_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    added_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_agent_allowlist_user FOREIGN KEY (username)
        REFERENCES user_profiles (username) ON DELETE CASCADE ON UPDATE CASCADE
);

INSERT INTO agent_settings (id, enabled) VALUES (1, false);
