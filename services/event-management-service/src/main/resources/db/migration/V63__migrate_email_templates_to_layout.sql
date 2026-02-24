-- V63: Set layout_key = 'batbern-default' for all seeded content templates
-- that were inserted before layout support was added (Story 10.2 migration).
-- Layout templates (is_layout = true) remain without a layout key.

UPDATE email_templates
SET layout_key = 'batbern-default'
WHERE is_layout = false
  AND is_system_template = true
  AND layout_key IS NULL;
