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
