-- Users created by an Admin sign in with a temporary password and must replace it.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
