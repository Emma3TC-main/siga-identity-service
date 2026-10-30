INSERT INTO iam.permission(id, code, description) VALUES
('10000000-0000-0000-0000-000000000012', 'EVIDENCE_READ', 'Consultar evidencias'),
('10000000-0000-0000-0000-000000000013', 'EVIDENCE_WRITE', 'Adjuntar evidencias');

DO $$
BEGIN
    IF (SELECT count(*) FROM iam.role WHERE code IN ('ADMIN', 'ENCARGADO', 'SUPERVISOR')) <> 3 THEN
        RAISE EXCEPTION 'Expected IAM roles ADMIN, ENCARGADO and SUPERVISOR are not all present';
    END IF;
END $$;

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
CROSS JOIN iam.permission p
WHERE r.code IN ('ADMIN', 'ENCARGADO', 'SUPERVISOR')
  AND p.code IN ('EVIDENCE_READ', 'EVIDENCE_WRITE');
