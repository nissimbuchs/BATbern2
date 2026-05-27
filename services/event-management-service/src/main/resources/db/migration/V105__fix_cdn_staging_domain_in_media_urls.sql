-- V105: Rewrite any stored media URLs still using the retired cdn.staging.batbern.ch
--       host to the live cdn.batbern.ch alias.
--
-- Context / why this exists as a NEW migration:
--   V86 (V86__fix_cloudfront_domain_in_media_urls.sql) originally fell back to
--   'https://cdn.staging.batbern.ch' when it could not infer a CDN alias, so on the
--   staging/production account it may have written that (now-retired) host into media
--   URLs. PR #669's AWS-consolidation cleanup did a bulk substitution that ALSO edited
--   V86 in place (cdn.staging.batbern.ch -> cdn.batbern.ch). Editing an already-applied
--   migration changed its checksum and broke Flyway validation on EMS startup
--   ("Migration checksum mismatch for migration version 86"), which kept EMS stuck on a
--   stale image (the ECS deployment circuit-breaker rolled every new task back).
--
--   The correct fix is to (a) revert V86 to its original content so its checksum matches
--   what is recorded in flyway_schema_history, and (b) apply the intended cdn.staging ->
--   cdn alias data fix here, as a proper forward migration.
--
-- Affected columns (same set V86 touched):
--   event_photos.display_url
--   event_teaser_images.image_url
--   events.theme_image_url
--
-- Idempotent: only rows whose value contains the retired host are touched; a no-op
-- everywhere the dead host never landed (e.g. a clean DB or production with good data).

UPDATE event_photos
SET display_url = REPLACE(display_url, 'https://cdn.staging.batbern.ch', 'https://cdn.batbern.ch')
WHERE display_url LIKE '%cdn.staging.batbern.ch%';

UPDATE event_teaser_images
SET image_url = REPLACE(image_url, 'https://cdn.staging.batbern.ch', 'https://cdn.batbern.ch')
WHERE image_url LIKE '%cdn.staging.batbern.ch%';

UPDATE events
SET theme_image_url = REPLACE(theme_image_url, 'https://cdn.staging.batbern.ch', 'https://cdn.batbern.ch')
WHERE theme_image_url LIKE '%cdn.staging.batbern.ch%';
