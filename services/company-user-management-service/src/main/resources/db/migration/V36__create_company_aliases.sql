-- V36__create_company_aliases.sql
-- #1251 (owner decision 2026-11-07: alias table yes). When two companies are merged or a company's
-- key is renamed, the old key and the old display name stay findable: company search, the
-- registration typeahead and POST /companies/get-or-create resolve them to the company, so the
-- duplicate is not created again the next day. One alias belongs to one company (alias_key is the
-- primary key; recording it again re-points it). Deleting the company removes its aliases.
CREATE TABLE company_aliases (
    alias_key          VARCHAR(12)  PRIMARY KEY,
    alias_display_name VARCHAR(255),
    company_id         UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    reason             VARCHAR(10)  NOT NULL,
    created_by         VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_aliases_key CHECK (alias_key ~ '^[a-zA-Z0-9]{1,12}$'),
    CONSTRAINT chk_company_aliases_reason CHECK (reason IN ('MERGE', 'RENAME'))
);
CREATE INDEX idx_company_aliases_company ON company_aliases (company_id);
-- No index on the display name: it is matched through batbern_fold (STABLE, not indexable), and the
-- table holds a row per merged or renamed company.
