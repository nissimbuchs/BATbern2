-- V86: Fix stored media URLs that used raw CloudFront distribution domain instead of the CDN alias
--
-- Root cause: event-management-stack.ts passed distributionDomainName (e.g.
--   https://dhndjchovz1zp.cloudfront.net) instead of cdnDomain alias
--   (e.g. https://cdn.staging.batbern.ch / https://cdn.batbern.ch).
--   company-user-management-stack already used the alias — only EMS was affected.
--
-- Affected tables:
--   event_photos.display_url          — s3_key stored separately, used for reconstruction
--   event_teaser_images.image_url     — s3_key stored separately, used for reconstruction
--   events.theme_image_url            — s3_key not stored, use regexp to swap host
--
-- Strategy:
--   1. Infer the correct CDN alias from any existing correct URL on this DB instance.
--   2. Fall back to https://cdn.staging.batbern.ch (the environment where this bug was observed).
--   3. All three tables are updated only where the URL contains '.cloudfront.net'.
--   4. This migration is a no-op when no .cloudfront.net URLs exist (e.g. production with no bad data).

DO $$
DECLARE
    v_cdn_domain TEXT;
BEGIN
    -- Prefer the alias already stored in a correct event_photos URL on this instance
    SELECT SUBSTRING(display_url FROM '^https://cdn\.[^/]+')
    INTO v_cdn_domain
    FROM event_photos
    WHERE display_url LIKE 'https://cdn.%'
      AND display_url NOT LIKE '%cloudfront.net%'
    LIMIT 1;

    -- Try event_teaser_images if no luck above
    IF v_cdn_domain IS NULL THEN
        SELECT SUBSTRING(image_url FROM '^https://cdn\.[^/]+')
        INTO v_cdn_domain
        FROM event_teaser_images
        WHERE image_url LIKE 'https://cdn.%'
          AND image_url NOT LIKE '%cloudfront.net%'
        LIMIT 1;
    END IF;

    -- Try events.theme_image_url
    IF v_cdn_domain IS NULL THEN
        SELECT SUBSTRING(theme_image_url FROM '^https://cdn\.[^/]+')
        INTO v_cdn_domain
        FROM events
        WHERE theme_image_url LIKE 'https://cdn.%'
          AND theme_image_url NOT LIKE '%cloudfront.net%'
        LIMIT 1;
    END IF;

    -- Final fallback: staging alias (where this bug was introduced and observed)
    IF v_cdn_domain IS NULL THEN
        v_cdn_domain := 'https://cdn.staging.batbern.ch';
    END IF;

    -- Fix event_photos: reconstruct display_url from s3_key + correct domain
    UPDATE event_photos
    SET display_url = v_cdn_domain || '/' || s3_key
    WHERE display_url LIKE '%cloudfront.net%';

    -- Fix event_teaser_images: same pattern
    UPDATE event_teaser_images
    SET image_url = v_cdn_domain || '/' || s3_key
    WHERE image_url LIKE '%cloudfront.net%';

    -- Fix events.theme_image_url: no s3_key stored, swap host portion via regexp
    UPDATE events
    SET theme_image_url = REGEXP_REPLACE(
        theme_image_url,
        '^https://[^/]+\.cloudfront\.net/',
        v_cdn_domain || '/'
    )
    WHERE theme_image_url LIKE '%cloudfront.net%';

END $$;
