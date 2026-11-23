-- The Mail Center conversation opened for a speaker proposal ("I could speak on that"), where
-- organizers answer the person. Set once by the Mail Center after the proposal is committed;
-- null for proposals made before it opened one. A key of this service's mail_conversation, no FK:
-- a conversation can be deleted by retention without touching the proposal.
ALTER TABLE session_proposals ADD COLUMN conversation_key VARCHAR(40);

CREATE INDEX idx_session_proposals_conversation_key ON session_proposals (conversation_key)
    WHERE conversation_key IS NOT NULL;
