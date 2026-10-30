DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM iam.permission
        WHERE id = '10000000-0000-0000-0000-000000000015'
          AND code <> 'INVENTORY_COUNT'
    ) THEN
        RAISE EXCEPTION 'Permission UUID 10000000-0000-0000-0000-000000000015 is already used by another code';
    END IF;

    IF EXISTS (
        SELECT 1 FROM iam.permission
        WHERE code = 'INVENTORY_COUNT'
          AND id <> '10000000-0000-0000-0000-000000000015'
    ) THEN
        RAISE EXCEPTION 'Permission code INVENTORY_COUNT is already associated with another UUID';
    END IF;

    IF (SELECT count(*) FROM iam.role WHERE code IN ('ADMIN', 'ENCARGADO', 'SUPERVISOR')) <> 3 THEN
        RAISE EXCEPTION 'Expected IAM roles ADMIN, ENCARGADO and SUPERVISOR are not all present';
    END IF;
END $$;

INSERT INTO iam.permission(id, code, description)
VALUES ('10000000-0000-0000-0000-000000000015', 'INVENTORY_COUNT', 'Gestionar conteos físicos');

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
CROSS JOIN iam.permission p
WHERE r.code IN ('ADMIN', 'ENCARGADO', 'SUPERVISOR')
  AND p.code = 'INVENTORY_COUNT';
