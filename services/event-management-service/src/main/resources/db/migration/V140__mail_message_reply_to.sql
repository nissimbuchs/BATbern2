-- Story 16.6 AC1: an answer goes to the last inbound mail's Reply-To, else its From.
-- 16.2a parsed Reply-To but did not store it.
ALTER TABLE mail_message ADD COLUMN reply_to_address VARCHAR(320);

COMMENT ON COLUMN mail_message.reply_to_address IS
    'Inbound mail: first Reply-To address, lowercased, or null. Story 16.6 answers go here, else to from_address.';
