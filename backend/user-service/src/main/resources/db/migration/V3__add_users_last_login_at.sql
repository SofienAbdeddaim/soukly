-- AUTH-03: date of the last successful login. NULL until the first one, which the front end answers with the
-- optional welcome screen (firstLogin in the login response). Nullable, so the previous version still runs.

ALTER TABLE users ADD COLUMN last_login_at timestamptz;
