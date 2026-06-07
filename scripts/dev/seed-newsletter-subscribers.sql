-- Seed 3000 dummy newsletter subscribers for local development
-- Enables testing the async send job, progress bar, and service-kill/resume scenarios.
--
-- Run with:
--   docker exec batbern-dev-postgres psql -U postgres -d batbern_development \
--       -f /path/to/seed-newsletter-subscribers.sql
-- Or via make target (if defined):
--   make dev-seed-newsletter
--
-- Safe to re-run: uses INSERT ... ON CONFLICT DO NOTHING on the unique email constraint.
-- Generates:
--   • 3000 active subscribers  (email: subscriber.0001@local.dev … subscriber.3000@local.dev)
--   • ~150 already-unsubscribed rows (every 20th subscriber)
--   • Mix: 2/3 German locale, 1/3 English; sources: registration / explicit / import
--   • 100 subscribers linked to known usernames (e.g. test.user.0001)

DO $$
DECLARE
    i          INTEGER;
    lang       VARCHAR(5);
    src        VARCHAR(50);
    unsub_at   TIMESTAMPTZ;
    uname      VARCHAR(100);
BEGIN
    FOR i IN 1..3000 LOOP
        -- Language: de for first two thirds, en for last third
        lang    := CASE WHEN i % 3 = 0 THEN 'en' ELSE 'de' END;

        -- Source: cycle through registration / explicit / import
        src     := CASE (i % 3)
                       WHEN 1 THEN 'registration'
                       WHEN 2 THEN 'explicit'
                       ELSE        'import'
                   END;

        -- Unsubscribed: every 20th subscriber opted out
        unsub_at := CASE WHEN i % 20 = 0
                         THEN NOW() - (random() * INTERVAL '180 days')
                         ELSE NULL
                    END;

        -- Username: first 100 get a linked username, rest are anonymous
        uname := CASE WHEN i <= 100
                      THEN 'test.user.' || LPAD(i::text, 4, '0')
                      ELSE NULL
                 END;

        INSERT INTO newsletter_subscribers (
            id,
            email,
            first_name,
            language,
            source,
            username,
            unsubscribe_token,
            subscribed_at,
            unsubscribed_at,
            created_at,
            updated_at
        ) VALUES (
            gen_random_uuid(),
            'subscriber.' || LPAD(i::text, 4, '0') || '@local.dev',
            'Subscriber ' || i,
            lang,
            src,
            uname,
            gen_random_uuid()::text,
            NOW() - (random() * INTERVAL '365 days'),
            unsub_at,
            NOW(),
            NOW()
        )
        ON CONFLICT (email) DO NOTHING;
    END LOOP;

    RAISE NOTICE '✅ Newsletter subscriber seed complete.';
    RAISE NOTICE '   Active:      ~%  (every 20th is unsubscribed)',
        (SELECT COUNT(*) FROM newsletter_subscribers WHERE unsubscribed_at IS NULL
         AND email LIKE 'subscriber.%@local.dev');
    RAISE NOTICE '   Unsubscribed: % ',
        (SELECT COUNT(*) FROM newsletter_subscribers WHERE unsubscribed_at IS NOT NULL
         AND email LIKE 'subscriber.%@local.dev');
    RAISE NOTICE '   Total seeded: % ',
        (SELECT COUNT(*) FROM newsletter_subscribers WHERE email LIKE 'subscriber.%@local.dev');
END $$;

-- Quick summary view
SELECT
    COUNT(*)                                                         AS total,
    COUNT(*) FILTER (WHERE unsubscribed_at IS NULL)                  AS active,
    COUNT(*) FILTER (WHERE unsubscribed_at IS NOT NULL)              AS unsubscribed,
    COUNT(*) FILTER (WHERE language = 'de' AND unsubscribed_at IS NULL) AS active_de,
    COUNT(*) FILTER (WHERE language = 'en' AND unsubscribed_at IS NULL) AS active_en,
    COUNT(*) FILTER (WHERE username IS NOT NULL)                     AS with_username
FROM newsletter_subscribers
WHERE email LIKE 'subscriber.%@local.dev';
