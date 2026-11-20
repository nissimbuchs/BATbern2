-- V39: deleting a profile failed with a foreign-key violation (500) when the person had granted a role
-- to someone (role_assignments.granted_by) or had a paired Watch (watch_pairings.username). The grant
-- keeps the role and forgets who granted it; the pairing goes with the profile.
ALTER TABLE role_assignments DROP CONSTRAINT role_assignments_granted_by_fkey;
ALTER TABLE role_assignments ADD CONSTRAINT role_assignments_granted_by_fkey
    FOREIGN KEY (granted_by) REFERENCES user_profiles(id) ON DELETE SET NULL;

ALTER TABLE watch_pairings DROP CONSTRAINT fk_watch_user;
ALTER TABLE watch_pairings ADD CONSTRAINT fk_watch_user
    FOREIGN KEY (username) REFERENCES user_profiles(username) ON DELETE CASCADE;
