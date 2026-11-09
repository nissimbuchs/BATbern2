-- Owner decision 2026-11-09 (Epic 17.0 deferred work, option A): every company key
-- (companies.name, the company's meaningful ID) has the shape ^[a-zA-Z0-9]{1,12}$, the slug that
-- get-or-create has always stored. POST /companies stored the typed name unchanged until then, so
-- some keys are longer or contain spaces (local snapshot: 45 of 712, none referenced by a user).
--
-- The block between the markers is shared-kernel db/company/company_key_changes.sql byte for byte
-- (CompanyKeyChangesContract). It records old key -> new key once in the shared table
-- company_key_changes: the new key is the old key slugged by the same rule as
-- SlugGenerationService.generateCompanyName (umlauts spelled out, accents dropped, lower case,
-- letters and digits only, at most 12), unique by the rule of ensureUniqueCompanyName (digit
-- appended within 12 characters), in the order of the old keys; 'company' when nothing is left.
-- Event management and partner coordination run the same block in their own migration and
-- rewrite their own references from that table, whichever service migrates first.
-- Idempotent: a compliant key is never touched, a mapped key is never mapped again.

-- >>> canonical company re-key block
SELECT pg_advisory_xact_lock(hashtext('company_key_changes'));

CREATE TABLE IF NOT EXISTS company_key_changes (
    old_key    VARCHAR(255) PRIMARY KEY,
    new_key    VARCHAR(12)  NOT NULL UNIQUE,
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

DO $company_key_changes$
DECLARE
    v_old_key   text;
    v_base      text;
    v_candidate text;
    v_suffix    integer;
BEGIN
    IF to_regclass('public.companies') IS NULL THEN
        RETURN;
    END IF;
    FOR v_old_key IN
        SELECT c.name FROM companies c
        WHERE c.name !~ '^[a-zA-Z0-9]{1,12}$'
          AND NOT EXISTS (SELECT 1 FROM company_key_changes k WHERE k.old_key = c.name)
        ORDER BY c.name COLLATE "C"
    LOOP
        v_base := left(regexp_replace(lower(normalize(
                      replace(replace(replace(replace(replace(replace(replace(v_old_key,
                          'ä', 'ae'), 'ö', 'oe'), 'ü', 'ue'), 'Ä', 'ae'), 'Ö', 'oe'), 'Ü', 'ue'), 'ß', 'ss'),
                      NFD)), '[^a-z0-9]', '', 'g'), 12);
        IF v_base = '' THEN
            v_base := 'company';
        END IF;
        v_candidate := v_base;
        v_suffix := 1;
        WHILE EXISTS (SELECT 1 FROM companies WHERE name = v_candidate)
           OR EXISTS (SELECT 1 FROM company_key_changes WHERE new_key = v_candidate) LOOP
            v_suffix := v_suffix + 1;
            v_candidate := left(v_base, 12 - length(v_suffix::text)) || v_suffix::text;
        END LOOP;
        INSERT INTO company_key_changes (old_key, new_key) VALUES (v_old_key, v_candidate);
    END LOOP;
END
$company_key_changes$;

-- <<< canonical company re-key block

-- Company-user-management rewrites its own tables. S3 object keys (companies.logo_s3_key,
-- logos.s3_key) keep the old name in their path: they locate a file, they are no reference.
UPDATE companies c
SET name = k.new_key, updated_at = now()
FROM company_key_changes k
WHERE c.name = k.old_key;

-- Empty by construction (chk_company_id_format), kept so no reference can be missed.
UPDATE user_profiles u
SET company_id = k.new_key
FROM company_key_changes k
WHERE u.company_id = k.old_key;

UPDATE logos l
SET associated_entity_id = k.new_key
FROM company_key_changes k
WHERE l.associated_entity_type = 'COMPANY' AND l.associated_entity_id = k.old_key;

UPDATE company_logo_requests r
SET company_name = k.new_key
FROM company_key_changes k
WHERE r.company_name = k.old_key;

UPDATE activity_history a
SET entity_id = k.new_key
FROM company_key_changes k
WHERE a.entity_type = 'COMPANY' AND a.entity_id = k.old_key;
