-- Story 16.0 (#1162) stopped sending cancel-confirmation: a reply "cancel" now sends the
-- deregistration-link mail and the link flow confirms the cancellation. The classpath files are
-- deleted in the same change, so the seeder no longer recreates the rows; this removes the
-- already-seeded ones, which would otherwise stay editable in the admin editor without effect.

DELETE FROM email_templates
 WHERE template_key = 'cancel-confirmation';
