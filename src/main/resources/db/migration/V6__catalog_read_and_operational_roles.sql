INSERT INTO iam.permission(id, code, description)
VALUES ('10000000-0000-0000-0000-000000000011', 'CATALOG_READ', 'Consultar catálogo');

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
JOIN iam.permission p ON p.code IN ('INVENTORY_READ', 'MOVEMENT_CREATE', 'CATALOG_READ')
WHERE r.code = 'ENCARGADO';

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
JOIN iam.permission p ON p.code IN ('INVENTORY_READ', 'MOVEMENT_AUTHORIZE', 'CATALOG_READ')
WHERE r.code = 'SUPERVISOR';

INSERT INTO iam.role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM iam.role r
JOIN iam.permission p ON p.code = 'CATALOG_READ'
WHERE r.code = 'ADMIN';

INSERT INTO iam.user_account(id, username, email, password_hash, mfa_enabled)
VALUES (
  '12000000-0000-0000-0000-000000000002',
  'encargado.demo',
  'encargado.demo@siga.local',
  '$argon2id$ENCARGADO_DEMO_REPLACE_WITH_REAL_HASH',
  false
);

INSERT INTO iam.user_role(user_id, role_id)
SELECT '12000000-0000-0000-0000-000000000002'::uuid, id
FROM iam.role
WHERE code = 'ENCARGADO';
