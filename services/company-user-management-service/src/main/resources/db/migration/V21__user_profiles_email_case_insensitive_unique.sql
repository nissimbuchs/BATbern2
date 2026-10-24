-- V21__user_profiles_email_case_insensitive_unique.sql
-- One user per email address regardless of capitalisation (#1099). Case-sensitive matching in
-- get-or-create had created 5 duplicate people in production; those pairs were merged by hand on
-- 2026-10-18, and the User entity now stores the address trimmed + lower-cased. This index makes
-- the rule hold at the database level, including for writes that bypass the entity (Lambdas).
-- Fails (and keeps the old image running) if case-duplicates exist again; check with:
--   SELECT lower(email), count(*) FROM user_profiles GROUP BY 1 HAVING count(*) > 1;
CREATE UNIQUE INDEX uq_user_profiles_email_lower ON user_profiles (lower(email));
