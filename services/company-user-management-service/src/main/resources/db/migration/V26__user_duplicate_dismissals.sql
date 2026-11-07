-- V26__user_duplicate_dismissals.sql
-- Organizers work through the list of probable duplicate profiles (GET /users/duplicate-candidates)
-- and mark namesakes as "not a duplicate", so the list shrinks. One row per pair, the smaller id
-- first. A merge deletes the source profile, which removes its rows.
CREATE TABLE user_duplicate_dismissals (
    user_a_id    UUID         NOT NULL REFERENCES user_profiles (id) ON DELETE CASCADE,
    user_b_id    UUID         NOT NULL REFERENCES user_profiles (id) ON DELETE CASCADE,
    dismissed_by VARCHAR(100) NOT NULL,
    dismissed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_a_id, user_b_id),
    CONSTRAINT chk_user_duplicate_dismissals_order CHECK (user_a_id < user_b_id)
);
CREATE INDEX idx_user_duplicate_dismissals_b ON user_duplicate_dismissals (user_b_id);
