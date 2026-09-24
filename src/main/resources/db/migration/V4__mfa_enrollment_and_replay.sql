-- MFA state remains owned by user_account (the canonical seven IAM tables).
ALTER TABLE iam.user_account ADD COLUMN mfa_enrolled boolean NOT NULL DEFAULT false;
ALTER TABLE iam.user_account ADD COLUMN last_totp_step bigint;
ALTER TABLE iam.refresh_token ADD COLUMN mfa_authenticated_at timestamptz;
