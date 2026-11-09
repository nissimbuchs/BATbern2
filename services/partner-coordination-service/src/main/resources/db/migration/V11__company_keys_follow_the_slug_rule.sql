-- Owner decision 2026-11-09 (Epic 17.0 deferred work, option A): every company key has the shape
-- ^[a-zA-Z0-9]{1,12}$. Company-user-management re-keys the companies whose key does not
-- (CUMS V28, which explains the rule). This service keeps company keys of its own, so it runs the
-- same canonical block (shared-kernel db/company/company_key_changes.sql byte for byte,
-- CompanyKeyChangesContract): whichever service migrates first computes the mapping into the shared
-- table company_key_changes from the companies table, the others find it there. Then this
-- service rewrites only its own references. Without a companies table (this service's own test
-- database) the block maps nothing. Idempotent.

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

-- Partner coordination's company references: the partner itself and its topic suggestions and votes.
UPDATE partners p
SET company_name = k.new_key
FROM company_key_changes k
WHERE p.company_name = k.old_key;

UPDATE topic_suggestions t
SET company_name = k.new_key
FROM company_key_changes k
WHERE t.company_name = k.old_key;

UPDATE topic_votes v
SET company_name = k.new_key
FROM company_key_changes k
WHERE v.company_name = k.old_key;
