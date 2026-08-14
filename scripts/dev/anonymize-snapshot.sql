-- =============================================================================
-- anonymize-snapshot.sql
--
-- Rewrites all personally-identifying data in a *scratch copy* of the BATbern
-- production database so the result is safe to use as a local/dev seed.
--
-- MUST be run against a scratch database, never against production.
-- The driver script (create-anonymized-snapshot.sh) enforces that.
--
-- Design notes:
--   * Deterministic. Same input -> same output, every run. This keeps values
--     consistent ACROSS tables without needing a join: e.g. the same original
--     email in user_profiles and registrations maps to the same fake email.
--   * Username rewriting is driven dynamically off information_schema, so a
--     newly added *_username column is picked up automatically instead of
--     silently leaking real names.
--   * CLAUDE.md:906 ("Anonymize production data before using in development")
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- ---------------------------------------------------------------------------
-- Guard: refuse to run anywhere that looks like production.
-- ---------------------------------------------------------------------------
DO $guard$
BEGIN
  IF current_database() NOT LIKE '%scratch%' AND current_database() NOT LIKE '%anon%' THEN
    RAISE EXCEPTION 'Refusing to run: database "%" is not a scratch database. '
                    'Expected a name containing "scratch" or "anon".', current_database();
  END IF;
END
$guard$;

-- ---------------------------------------------------------------------------
-- Helper functions (dropped at the end)
-- ---------------------------------------------------------------------------

-- Stable non-negative integer derived from an arbitrary key.
CREATE OR REPLACE FUNCTION _anon_idx(k text, n int) RETURNS int AS $$
  SELECT ((('x' || substr(md5(coalesce(k, '')), 1, 8))::bit(32)::bigint) & 2147483647)::int % n;
$$ LANGUAGE sql IMMUTABLE;

-- Base fake email, keyed on the LOWERCASED original so the same person resolves
-- to the same address across tables. .invalid is reserved by RFC 2606 and can
-- never be delivered to -- the property that makes a repeat of the 2026-07-01
-- mass-mail incident impossible from this data.
--
-- NOTE: this base is not unique on its own. Production contains accounts whose
-- emails differ only in case (user_profiles_email_key is case-SENSITIVE), which
-- collapse to the same base. _anon_email_map below de-duplicates them.
CREATE OR REPLACE FUNCTION _anon_email_base(t text) RETURNS text AS $$
  SELECT CASE
           WHEN t IS NULL OR t = '' THEN t
           ELSE 'u' || substr(md5(lower(t)), 1, 12)
         END;
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION _anon_first(k text) RETURNS text AS $$
  SELECT (ARRAY[
    'anna','beat','chiara','daniel','elena','fabian','gina','heinz','irene','jonas',
    'karin','lukas','marta','noah','olivia','pascal','quirin','rahel','simon','tanja',
    'urs','vera','werner','xenia','yves','zoe','andrea','bruno','claudia','dominik',
    'eva','felix','gabriela','hans','ines','jan','katja','leo','monika','nico'
  ])[1 + _anon_idx(k || ':first', 40)];
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION _anon_last(k text) RETURNS text AS $$
  SELECT (ARRAY[
    'meier','keller','baumann','frei','graf','huber','iten','jost','kunz','lang',
    'moser','naef','oberli','peter','roth','schmid','tanner','ulrich','vogel','wyss',
    'zimmermann','ammann','brunner','christen','duerr','egger','fischer','gerber','hofer','isler',
    'kaufmann','lehmann','marti','neuenschwander','odermatt','probst','rieder','steiner','thoma','widmer'
  ])[1 + _anon_idx(k || ':last', 40)];
$$ LANGUAGE sql IMMUTABLE;

-- ---------------------------------------------------------------------------
-- 1. Accounts to preserve (so Nissim can still log in locally)
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _anon_keep_email(email text PRIMARY KEY);
INSERT INTO _anon_keep_email(email) VALUES
  ('nissim@buchs.be'),
  ('nissim.buchs@me.com'),
  ('nissim.buchs@gmail.com'),
  ('nissim.buchs@elca.ch');

CREATE TEMP TABLE _anon_keep_username(username text PRIMARY KEY);
INSERT INTO _anon_keep_username(username)
SELECT DISTINCT up.username
FROM user_profiles up
WHERE lower(up.email) IN (SELECT lower(email) FROM _anon_keep_email)
  AND up.username IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. Build the username map from EVERY username-bearing column in the schema.
--    Driven off information_schema so nothing is missed.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _anon_user_map(
  old_username text PRIMARY KEY,
  new_username text
);

DO $collect$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.column_name ~* 'username'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.data_type IN ('character varying', 'text')
  LOOP
    EXECUTE format(
      -- Only values that match the username format enforced by
      -- user_profiles.chk_username_format count as usernames. Username-ish
      -- columns in production also hold Cognito sub UUIDs, email addresses and
      -- 'manual-import-*' markers; letting those into the map made the
      -- value-based rewrite overwrite unrelated fields -- it replaced a real
      -- cognito_user_id with a generated name, which would break login.
      'INSERT INTO _anon_user_map(old_username)
         SELECT DISTINCT %I FROM public.%I
          WHERE %I IS NOT NULL AND %I <> ''''
            AND %I ~ ''^[a-z]+\.[a-z]+(\.[0-9]+)?$''
       ON CONFLICT (old_username) DO NOTHING',
      r.column_name, r.table_name, r.column_name, r.column_name, r.column_name);
  END LOOP;
END
$collect$;

-- Also harvest usernames that appear ONLY as path segments. S3 keys reference
-- users who may no longer exist in any username column (deleted accounts, or
-- files uploaded under a since-changed name), so collecting from columns alone
-- leaves those names in the paths.
--
-- Filename segments are excluded by extension: '^[a-z]+\.[a-z]+$' happily
-- matches 'image.jpeg', and renaming those would corrupt the keys.
DO $collect_paths$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.data_type IN ('character varying', 'text')
      AND c.column_name ~* '(url|key|path|location|uri)$'
  LOOP
    EXECUTE format(
      'INSERT INTO _anon_user_map(old_username)
         SELECT DISTINCT s
           FROM public.%I, unnest(string_to_array(%I, ''/'')) AS s
          WHERE %I IS NOT NULL
            AND s ~ ''^[a-z]+\.[a-z]+(\.[0-9]+)?$''
            AND s !~ ''\.(jpg|jpeg|png|gif|webp|svg|pdf|zip|pptx|ppt|docx|doc|xlsx|csv|txt|mp4|mov|key|html)$''
       ON CONFLICT (old_username) DO NOTHING',
      r.table_name, r.column_name, r.column_name);
  END LOOP;
END
$collect_paths$;

-- Assign fake usernames. Preserved accounts map to themselves.
-- A numeric suffix guarantees uniqueness when two originals land on the same
-- first.last pair.
WITH gen AS (
  SELECT
    m.old_username,
    _anon_first(m.old_username) AS f,
    _anon_last(m.old_username)  AS l,
    row_number() OVER (
      PARTITION BY _anon_first(m.old_username), _anon_last(m.old_username)
      ORDER BY m.old_username
    ) AS rn
  FROM _anon_user_map m
)
UPDATE _anon_user_map m
SET new_username = CASE
      WHEN m.old_username IN (SELECT username FROM _anon_keep_username)
        THEN m.old_username
      WHEN g.rn = 1 THEN g.f || '.' || g.l
      ELSE g.f || '.' || g.l || '.' || g.rn
    END
FROM gen g
WHERE g.old_username = m.old_username;

-- Force the generated namespace to be DISJOINT from the real one.
--
-- Generated names are drawn from ordinary Swiss name pools, so a fake can land
-- exactly on some other user's REAL username ('beat.lang' is both). That
-- overlap is corrosive: it makes value-based verification report false leaks,
-- and it causes transient UNIQUE violations mid-rewrite. Suffixing the
-- offenders until no generated name equals any original removes the ambiguity
-- entirely -- after this, "value equals an original username" means a genuine
-- leak, full stop.
-- The suffix must satisfy user_profiles.chk_username_format:
--   ^[a-z]+\.[a-z]+(\.[0-9]+)?$
-- so it has to be numeric. Production has ZERO usernames with a 3-or-more digit
-- suffix, which is why numbering from 900 upwards is disjoint by construction
-- as well as format-legal.
DO $disjoint$
DECLARE
  n     bigint;
  guard int := 0;
BEGIN
  LOOP
    WITH bad AS (
      SELECT m.old_username,
             row_number() OVER (ORDER BY m.old_username) AS k
      FROM _anon_user_map m
      WHERE m.old_username <> m.new_username        -- never touch preserved accounts
        AND EXISTS (SELECT 1 FROM _anon_user_map o WHERE o.old_username = m.new_username)
    )
    UPDATE _anon_user_map m
    SET new_username = regexp_replace(m.new_username, '\.[0-9]+$', '')
                       || '.' || (900 + guard * 1000 + b.k)
    FROM bad b
    WHERE b.old_username = m.old_username;

    GET DIAGNOSTICS n = ROW_COUNT;
    EXIT WHEN n = 0;

    guard := guard + 1;
    IF guard > 10 THEN
      RAISE EXCEPTION 'could not make the username namespace disjoint after 10 passes';
    END IF;
  END LOOP;
  RAISE NOTICE 'username namespace is disjoint from the original set (% extra pass(es))', guard;
END
$disjoint$;

CREATE UNIQUE INDEX _anon_user_map_new_uniq ON _anon_user_map(new_username);

-- ---------------------------------------------------------------------------
-- 2b. Build the email map from EVERY email-bearing text column in the schema.
--     Guarantees global uniqueness even where two originals differ only in case.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _anon_email_map(
  old_email text PRIMARY KEY,
  new_email text
);

DO $collect_email$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.column_name ~* '(^|_)(email|email_address)$'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.data_type IN ('character varying', 'text')
  LOOP
    EXECUTE format(
      'INSERT INTO _anon_email_map(old_email)
         SELECT DISTINCT %I FROM public.%I
          WHERE %I IS NOT NULL AND %I <> ''''
       ON CONFLICT (old_email) DO NOTHING',
      r.column_name, r.table_name, r.column_name, r.column_name);
  END LOOP;
END
$collect_email$;

-- Preserved addresses map to themselves; everything else gets the deterministic
-- base, suffixed with a counter when several originals share one base.
WITH gen AS (
  SELECT
    m.old_email,
    _anon_email_base(m.old_email) AS base,
    row_number() OVER (
      PARTITION BY _anon_email_base(m.old_email)
      ORDER BY m.old_email
    ) AS rn
  FROM _anon_email_map m
)
UPDATE _anon_email_map m
SET new_email = CASE
      WHEN lower(m.old_email) IN (SELECT lower(email) FROM _anon_keep_email)
        THEN m.old_email
      WHEN g.rn = 1 THEN g.base || '@example.invalid'
      ELSE g.base || '.' || g.rn || '@example.invalid'
    END
FROM gen g
WHERE g.old_email = m.old_email;

CREATE UNIQUE INDEX _anon_email_map_new_uniq ON _anon_email_map(new_email);

-- ---------------------------------------------------------------------------
-- 3. Drop notification history outright.
--    notifications.body holds fully-rendered email bodies (names + addresses
--    inline); it is also ~34 MB of the ~53 MB database and dev never reads it.
-- ---------------------------------------------------------------------------
TRUNCATE TABLE notifications;

-- ---------------------------------------------------------------------------
-- 4. Structured PII columns.
--    Emails are always derived from the ORIGINAL email, so the same person
--    resolves to the same fake address in every table without a join.
-- ---------------------------------------------------------------------------

-- Names first: these key off the ORIGINAL username/email values, so they must
-- run before the username rewrite in step 5 and before emails are remapped.

-- user_profiles ------------------------------------------------------------
UPDATE user_profiles up SET
  first_name       = _anon_first(up.username),
  last_name        = _anon_last(up.username),
  bio              = NULL,
  cognito_user_id  = gen_random_uuid()::text
WHERE lower(up.email) NOT IN (SELECT lower(email) FROM _anon_keep_email);

-- registrations ------------------------------------------------------------
UPDATE registrations SET
  attendee_first_name  = _anon_first(coalesce(attendee_username, attendee_email)),
  attendee_last_name   = _anon_last(coalesce(attendee_username, attendee_email)),
  deregistration_token = gen_random_uuid()   -- uuid column, not a hex string
WHERE lower(coalesce(attendee_email, '')) NOT IN (SELECT lower(email) FROM _anon_keep_email);

-- newsletter_subscribers ---------------------------------------------------
UPDATE newsletter_subscribers SET
  first_name        = _anon_first(coalesce(username, email)),
  unsubscribe_token = replace(gen_random_uuid()::text, '-', '')
WHERE lower(coalesce(email, '')) NOT IN (SELECT lower(email) FROM _anon_keep_email);

-- Every email column, rewritten through the map (dynamic — nothing missed).
DO $emails$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.column_name ~* '(^|_)(email|email_address)$'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.data_type IN ('character varying', 'text')
  LOOP
    EXECUTE format(
      'UPDATE public.%I x SET %I = m.new_email
         FROM _anon_email_map m
        WHERE x.%I = m.old_email AND x.%I <> m.new_email',
      r.table_name, r.column_name, r.column_name, r.column_name);
  END LOOP;
END
$emails$;

-- speaker_pool.speaker_name ------------------------------------------------
-- A single varchar holding the speaker's FULL name ("Markus Roder"). It matches
-- none of the first_name/last_name/email patterns, so earlier column-pattern
-- passes left it completely untouched.
UPDATE speaker_pool
SET speaker_name = initcap(_anon_first(speaker_name)) || ' ' || initcap(_anon_last(speaker_name))
WHERE speaker_name IS NOT NULL AND speaker_name <> '';

-- watch_pairings.device_name -----------------------------------------------
-- Device names routinely embed the owner's name ("Nissim's iPhone").
UPDATE watch_pairings SET device_name = 'Dev Device'
WHERE device_name IS NOT NULL AND device_name <> '';

-- watch_pairings token -----------------------------------------------------
-- session_users ------------------------------------------------------------
UPDATE session_users su SET
  speaker_first_name = _anon_first(su.username),
  speaker_last_name  = _anon_last(su.username)
WHERE su.username IS NULL
   OR su.username NOT IN (SELECT username FROM _anon_keep_username);

-- watch_pairings -----------------------------------------------------------
UPDATE watch_pairings SET pairing_token = replace(gen_random_uuid()::text, '-', '');

-- ---------------------------------------------------------------------------
-- 4b. Scrub email addresses out of FREE TEXT and JSONB.
--
--     Anonymizing only the email *columns* is not enough: organiser notes carry
--     real addresses inline. Found in production in speaker_pool.notes,
--     speaker_outreach_history.notes, event_tasks.notes and app_settings.
--     A column-scoped pass can never catch these, so this sweeps every text and
--     jsonb column in the schema.
--
--     The (?!example\.invalid) lookahead means already-anonymized values are
--     left alone, so this is safe to run over every column including the email
--     ones.
--
--     Names inside free text are handled separately by step 4c.
-- ---------------------------------------------------------------------------
DO $freetext$
DECLARE
  r   record;
  rx  text := '[A-Za-z0-9._%+-]+@(?!example\.invalid)[A-Za-z0-9.-]+\.[A-Za-z]{2,}';
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name, c.data_type
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.is_generated = 'NEVER'
      -- Email columns proper are handled by _anon_email_map in step 4. Sweeping
      -- them here too would rewrite every preserved address to the SAME
      -- 'redacted@' literal and violate uq_newsletter_email.
      AND c.column_name !~* '(^|_)(email|email_address)$'
      AND (c.data_type IN ('text', 'jsonb', 'json')
           OR (c.data_type = 'character varying'
               AND coalesce(c.character_maximum_length, 9999) >= 64))
  LOOP
    IF r.data_type IN ('jsonb', 'json') THEN
      EXECUTE format(
        'UPDATE public.%I SET %I = regexp_replace(%I::text, %L, %L, ''g'')::%s
          WHERE %I::text ~ %L',
        r.table_name, r.column_name, r.column_name, rx, 'redacted@example.invalid',
        r.data_type, r.column_name, rx);
    ELSE
      EXECUTE format(
        'UPDATE public.%I SET %I = regexp_replace(%I, %L, %L, ''g'')
          WHERE %I ~ %L',
        r.table_name, r.column_name, r.column_name, rx, 'redacted@example.invalid',
        r.column_name, rx);
    END IF;
  END LOOP;
END
$freetext$;

-- ---------------------------------------------------------------------------
-- 4c. Blank organiser COMMENTARY columns.
--
--     The email sweep in 4b cannot remove personal NAMES, and these columns are
--     free-text opinion written *about* identifiable people ("X declined
--     because...", outreach status, decline reasons). A verified snapshot still
--     contained real surnames here (Waber, Paonessa, Verschinin, Pletz) after
--     every email was scrubbed.
--
--     Deliberately NOT included: sessions.title/description, events.description,
--     topics.* -- those are published on batbern.ch, so the speaker names in
--     them are already public, and dev genuinely needs that content to render
--     a realistic site.
--
--     Cost of blanking: ~65 rows in total across these columns.
-- ---------------------------------------------------------------------------
DO $commentary$
DECLARE
  r    record;
  cols text[] := ARRAY[
    'event_tasks.notes',
    'partner_meetings.notes',
    'partner_notes.content',
    'session_timing_history.change_reason',
    'session_timing_history.notes',
    'session_users.decline_reason',
    'speaker_outreach_history.notes',
    'speaker_pool.decline_reason',
    'speaker_pool.notes',
    'speaker_pool.preference_comments',
    'speaker_status_history.change_reason'
  ];
  entry text;
  tbl   text;
  col   text;
BEGIN
  FOREACH entry IN ARRAY cols LOOP
    tbl := split_part(entry, '.', 1);
    col := split_part(entry, '.', 2);

    -- Skip silently if the column has since been renamed or dropped.
    PERFORM 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = tbl AND column_name = col;
    IF NOT FOUND THEN
      RAISE NOTICE 'commentary scrub: %.% not present, skipping', tbl, col;
      CONTINUE;
    END IF;

    -- Skip columns guarded by a CHECK constraint: those are categorical
    -- (e.g. session_timing_history.change_reason is an enum-like set, not
    -- free text), and a redaction literal would violate the constraint.
    PERFORM 1
      FROM pg_constraint c
      JOIN unnest(c.conkey) k ON true
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k
     WHERE c.contype = 'c'
       AND c.conrelid = format('public.%I', tbl)::regclass
       AND a.attname = col;
    IF FOUND THEN
      RAISE NOTICE 'commentary scrub: %.% is CHECK-constrained (categorical), skipping', tbl, col;
      CONTINUE;
    END IF;

    EXECUTE format(
      'UPDATE public.%I SET %I = ''[redacted for dev snapshot]''
        WHERE %I IS NOT NULL AND %I <> ''''',
      tbl, col, col, col);
  END LOOP;
END
$commentary$;

-- ---------------------------------------------------------------------------
-- 5. Rewrite EVERY username column via the map (dynamic — nothing missed).
--
--    Usernames are referenced by at least one FOREIGN KEY
--    (watch_pairings.username -> user_profiles.username), which is neither
--    DEFERRABLE nor ON UPDATE CASCADE. That makes BOTH update orders illegal:
--    child-first orphans the new value, parent-first orphans the old one.
--    So the username FKs are dropped and rebuilt inside this transaction.
--    Rebuilding revalidates, which doubles as a referential-integrity check.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _anon_dropped_fks(conname text, tbl text, def text);

INSERT INTO _anon_dropped_fks(conname, tbl, def)
SELECT DISTINCT c.conname, c.conrelid::regclass::text, pg_get_constraintdef(c.oid)
FROM pg_constraint c
JOIN unnest(c.conkey) k ON true
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k
WHERE c.contype = 'f' AND a.attname ~* 'username';

DO $dropfk$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _anon_dropped_fks LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
  END LOOP;
END
$dropfk$;

-- UNIQUE constraints on username must go too. The map guarantees the FINAL set
-- of usernames is unique, but PostgreSQL checks uniqueness row-by-row, and a
-- generated name (e.g. 'yves.peter') can transiently collide with a REAL user
-- of that name who has not been rewritten yet. Dropping and rebuilding sidesteps
-- the intermediate state; the rebuild still proves final uniqueness.
CREATE TEMP TABLE _anon_dropped_uniques(conname text, tbl text, def text);

INSERT INTO _anon_dropped_uniques(conname, tbl, def)
SELECT DISTINCT c.conname, c.conrelid::regclass::text, pg_get_constraintdef(c.oid)
FROM pg_constraint c
JOIN unnest(c.conkey) k ON true
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k
WHERE c.contype = 'u' AND a.attname ~* 'username';

DO $dropuq$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _anon_dropped_uniques LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
  END LOOP;
END
$dropuq$;

-- ...and standalone UNIQUE INDEXes on username (e.g. idx_users_username), which
-- live in pg_index rather than pg_constraint and so are missed by the block above.
CREATE TEMP TABLE _anon_dropped_uidx(idxname text, def text);

INSERT INTO _anon_dropped_uidx(idxname, def)
SELECT i.relname, pg_get_indexdef(ix.indexrelid)
FROM pg_index ix
JOIN pg_class i ON i.oid = ix.indexrelid
JOIN pg_class t ON t.oid = ix.indrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'public'
  AND ix.indisunique
  AND NOT ix.indisprimary
  AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = ix.indexrelid)
  AND EXISTS (
    SELECT 1 FROM unnest(ix.indkey) k
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k
    WHERE a.attname ~* 'username');

DO $dropuidx$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _anon_dropped_uidx LOOP
    EXECUTE format('DROP INDEX public.%I', r.idxname);
  END LOOP;
END
$dropuidx$;

-- Matched by VALUE, not by column name. Name-matching kept missing columns:
-- speaker_pool.assigned_organizer_id holds a username, as do created_by,
-- updated_by, uploaded_by, changed_by, reviewed_by, triggered_by and
-- suggested_by. Every text column is offered to the map instead; columns that
-- hold no usernames simply update zero rows.
DO $rewrite$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.table_name NOT LIKE 'flyway\_%'
      AND c.table_name <> 'shedlock'
      AND c.is_generated = 'NEVER'
      AND c.column_name !~* '(^|_)(email|email_address)$'
      AND c.data_type IN ('character varying', 'text')
  LOOP
    EXECUTE format(
      'UPDATE public.%I x SET %I = m.new_username
         FROM _anon_user_map m
        WHERE x.%I = m.old_username AND x.%I <> m.new_username',
      r.table_name, r.column_name, r.column_name, r.column_name);
  END LOOP;
END
$rewrite$;

-- Usernames also appear as PATH SEGMENTS inside S3 keys and URLs, e.g.
--   profile-pictures/2025/baltisar.oswald/profile-<uuid>.jpeg
-- Whole-value matching cannot see those, so replace them segment-wise.
DO $paths$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.is_generated = 'NEVER'
      AND c.data_type IN ('character varying', 'text')
      AND c.column_name ~* '(url|key|path|location|uri)$'
  LOOP
    EXECUTE format(
      'UPDATE public.%I x
          SET %I = replace(x.%I, ''/'' || m.old_username || ''/'', ''/'' || m.new_username || ''/'')
         FROM _anon_user_map m
        WHERE x.%I LIKE ''%%/'' || m.old_username || ''/%%''
          AND m.old_username <> m.new_username',
      r.table_name, r.column_name, r.column_name, r.column_name);
  END LOOP;
END
$paths$;

-- Rebuild unique indexes, then unique constraints, then FKs — in that order,
-- since each later one may depend on the earlier. Any failure here means the
-- rewrite produced duplicates or broke referential integrity.
DO $adduidx$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _anon_dropped_uidx LOOP
    EXECUTE r.def;
  END LOOP;
END
$adduidx$;

DO $adduq$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _anon_dropped_uniques LOOP
    EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', r.tbl, r.conname, r.def);
  END LOOP;
END
$adduq$;

-- Rebuild the username FKs. This revalidates every row, so a failure here means
-- the rewrite broke referential integrity — exactly what we want it to catch.
DO $addfk$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _anon_dropped_fks LOOP
    EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', r.tbl, r.conname, r.def);
  END LOOP;
END
$addfk$;

-- ---------------------------------------------------------------------------
-- 6. Regenerate name-derived search vectors (skip GENERATED columns).
-- ---------------------------------------------------------------------------
DO $vectors$
DECLARE
  gen text;
BEGIN
  SELECT is_generated INTO gen
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'session_users'
    AND column_name = 'speaker_name_vector';

  IF gen IS NOT NULL AND gen = 'NEVER' THEN
    UPDATE session_users
    SET speaker_name_vector = to_tsvector('simple',
          coalesce(speaker_first_name, '') || ' ' || coalesce(speaker_last_name, ''));
  END IF;
END
$vectors$;

-- ---------------------------------------------------------------------------
-- 7. Clean up helpers. _anon_user_map is dropped so the mapping between real
--    and fake identities is NOT shipped inside the snapshot -- keeping it
--    would make the whole exercise reversible.
-- ---------------------------------------------------------------------------
DROP FUNCTION _anon_first(text);
DROP FUNCTION _anon_last(text);
DROP FUNCTION _anon_email_base(text);
DROP FUNCTION _anon_idx(text, int);


-- ---------------------------------------------------------------------------
-- 8. Verification, INSIDE the transaction: any surviving real address aborts
--    the whole run. Dynamic over every email column, so a table added later is
--    checked automatically. Preserved accounts are exempt EVERYWHERE they
--    appear -- they show up in registrations and newsletter_recipients too, not
--    just in user_profiles.
-- ---------------------------------------------------------------------------
DO $verify$
DECLARE
  r      record;
  n      bigint;
  sample text;
  total  bigint := 0;
  rx     text := '[A-Za-z0-9._%+-]+@(?!example\.invalid)[A-Za-z0-9.-]+\.[A-Za-z]{2,}';
BEGIN
  -- Scans EVERY text-ish column in the schema, not just the ones step 4 chose
  -- to anonymize. That distinction matters: the first version of this script
  -- verified only the email columns it had itself rewritten, so it happily
  -- passed while 15 real addresses sat in speaker_pool.notes and friends. A
  -- check that shares its assumptions with the code it checks proves nothing.
  FOR r IN
    SELECT c.table_name, c.column_name, c.data_type
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.data_type IN ('text', 'jsonb', 'json', 'character varying')
  LOOP
    EXECUTE format(
      $q$SELECT count(*), coalesce(min(substring(%I::text from %L)), '')
           FROM public.%I
          WHERE %I::text ~ %L
            AND lower(%I::text) NOT IN (SELECT lower(email) FROM _anon_keep_email)$q$,
      r.column_name, rx, r.table_name, r.column_name, rx, r.column_name)
    INTO n, sample;

    -- The preserve-list addresses are legitimate wherever they appear.
    IF n > 0 AND sample <> '' AND lower(sample) NOT IN (
         SELECT lower(email) FROM _anon_keep_email) THEN
      RAISE WARNING 'LEAK: % row(s) in %.% contain a real address -- e.g. %',
        n, r.table_name, r.column_name, sample;
      total := total + n;
    END IF;
  END LOOP;

  -- Second, value-based sweep: does ANY text column still hold an ORIGINAL
  -- username? This is the check that does not depend on guessing column names,
  -- and it is what would have caught speaker_pool.assigned_organizer_id and the
  -- *_by family on the first run instead of the fourth.
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.table_name NOT LIKE 'flyway\_%'
      AND c.table_name <> 'shedlock'
      AND c.data_type IN ('character varying', 'text')
  LOOP
    EXECUTE format(
      $q$SELECT count(*), coalesce(min(x.%I), '')
           FROM public.%I x
           JOIN _anon_user_map m ON x.%I = m.old_username
          WHERE m.old_username <> m.new_username$q$,
      r.column_name, r.table_name, r.column_name)
    INTO n, sample;

    IF n > 0 THEN
      RAISE WARNING 'LEAK: % row(s) in %.% still hold a real username -- e.g. %',
        n, r.table_name, r.column_name, sample;
      total := total + n;
    END IF;
  END LOOP;

  -- Third sweep: usernames embedded as PATH SEGMENTS (S3 keys, URLs).
  FOR r IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE '\_anon\_%'
      AND c.data_type IN ('character varying', 'text')
  LOOP
    EXECUTE format(
      $q$SELECT count(*), coalesce(min(x.%I), '')
           FROM public.%I x
           JOIN _anon_user_map m
             ON x.%I LIKE '%%/' || m.old_username || '/%%'
          WHERE m.old_username <> m.new_username$q$,
      r.column_name, r.table_name, r.column_name)
    INTO n, sample;

    IF n > 0 THEN
      RAISE WARNING 'LEAK: % row(s) in %.% embed a real username in a path -- e.g. %',
        n, r.table_name, r.column_name, sample;
      total := total + n;
    END IF;
  END LOOP;

  IF total > 0 THEN
    RAISE EXCEPTION '% row(s) still contain real personal data -- rolling back', total;
  END IF;

  RAISE NOTICE 'Verification passed: no real email addresses or usernames anywhere in the schema';
END
$verify$;

COMMIT;

\echo ''
\echo '=== POST-ANONYMIZATION SUMMARY ==='
SELECT 'user_profiles'          AS tbl, count(*) AS rows FROM user_profiles
UNION ALL SELECT 'registrations',          count(*) FROM registrations
UNION ALL SELECT 'newsletter_subscribers', count(*) FROM newsletter_subscribers
UNION ALL SELECT 'sessions',               count(*) FROM sessions
UNION ALL SELECT 'companies',              count(*) FROM companies
UNION ALL SELECT 'events',                 count(*) FROM events
UNION ALL SELECT 'notifications (truncated)', count(*) FROM notifications;
