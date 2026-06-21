-- Re-home email templates that EmailTemplateSeedService.deriveCategory() stamped with its
-- default fallback category 'LAYOUT' because their key prefix matched no branch. A content
-- template stamped LAYOUT (is_layout=false) is orphaned in the admin editor: it is neither a
-- layout (the Layout section shows is_layout=true only) nor selectable under any content tab,
-- so it cannot be edited. Seeding is insert-if-absent, so this corrects already-seeded rows.
--
-- - qna-new-questions (Story 15.7): sent to a session's speakers + the event moderator → SPEAKER.
-- - accept-/cancel-/unsubscribe-confirmation (Story 10.17 inbound-email acks): transactional
--   confirmations → REGISTRATION (an admin-EDIT-only category, NOT a blast send-picker like
--   REGISTRANT_NOTICE / NEWSLETTER), so editing them can never make them mass-sendable.

UPDATE email_templates
   SET category = 'SPEAKER'
 WHERE template_key = 'qna-new-questions'
   AND category = 'LAYOUT';

UPDATE email_templates
   SET category = 'REGISTRATION'
 WHERE template_key IN ('accept-confirmation', 'cancel-confirmation', 'unsubscribe-confirmation')
   AND category = 'LAYOUT';
