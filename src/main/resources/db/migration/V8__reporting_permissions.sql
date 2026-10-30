DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM iam.permission
        WHERE id = '10000000-0000-0000-0000-000000000014'
          AND code <> 'ANALYTICS_READ'
    ) THEN
        RAISE EXCEPTION 'Permission UUID 10000000-0000-0000-0000-000000000014 is already used by another code';
    END IF;

    IF EXISTS (
        SELECT 1 FROM iam.permission
        WHERE code = 'ANALYTICS_READ'
          AND id <> '10000000-0000-0000-0000-000000000014'
    ) THEN
        RAISE EXCEPTION 'Permission code ANALYTICS_READ is already associated with another UUID';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM iam.permission
        WHERE id = '10000000-0000-0000-0000-000000000007'
          AND code = 'REPORT_READ'
    ) THEN
        RAISE EXCEPTION 'Canonical REPORT_READ permission is missing or has an unexpected UUID';
    END IF;

    IF (SELECT count(*) FROM iam.role WHERE code IN ('ADMIN', 'ENCARGADO', 'SUPERVISOR')) <> 3 THEN
        RAISE EXCEPTION 'Expected IAM roles ADMIN, ENCARGADO and SUPERVISOR are not all present';
    END IF;
END $$;

INSERT INTO iam.permission(id, code, description)
VALUES ('10000000-0000-0000-0000-000000000014', 'ANALYTICS_READ', 'Consumir exportaciones analíticas');

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
CROSS JOIN iam.permission p
WHERE r.code = 'ADMIN'
  AND p.code = 'ANALYTICS_READ';

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
CROSS JOIN iam.permission p
WHERE r.code = 'SUPERVISOR'
  AND p.code = 'REPORT_READ';
