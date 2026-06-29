-- Auth hardening schema changes.
-- Existing plaintext PIN values are migrated lazily on the next successful login
-- or explicit PIN reset/change because bcrypt hashes cannot be derived in SQL.

ALTER TABLE teachers
  MODIFY pin VARCHAR(6) NULL,
  ADD COLUMN pin_hash VARCHAR(100) NULL,
  ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
  ADD COLUMN locked_until DATETIME NULL,
  ADD COLUMN last_login_at DATETIME NULL;

ALTER TABLE students
  MODIFY pin VARCHAR(4) NULL,
  ADD COLUMN pin_hash VARCHAR(100) NULL,
  ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
  ADD COLUMN locked_until DATETIME NULL,
  ADD COLUMN last_login_at DATETIME NULL;

CREATE TABLE remember_me_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  selector_hash VARCHAR(64) NOT NULL,
  validator_hash VARCHAR(64) NOT NULL,
  user_role VARCHAR(20) NOT NULL,
  user_id BIGINT NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  last_used_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_remember_me_selector (selector_hash),
  KEY idx_remember_me_user (user_role, user_id),
  KEY idx_remember_me_expires (expires_at)
);
