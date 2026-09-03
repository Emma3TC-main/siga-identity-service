-- TODO: Pegar aquí los INSERT iniciales del esquema 'iam'.

INSERT INTO iam.permission(id,code,description) VALUES
('10000000-0000-0000-0000-000000000001','INVENTORY_READ','Consultar inventario'),
('10000000-0000-0000-0000-000000000002','MOVEMENT_CREATE','Crear movimientos'),
('10000000-0000-0000-0000-000000000003','MOVEMENT_AUTHORIZE','Autorizar movimientos sensibles'),
('10000000-0000-0000-0000-000000000004','INVENTORY_ADJUST','Registrar ajustes'),
('10000000-0000-0000-0000-000000000005','PRODUCT_WRITE','Gestionar catálogo'),
('10000000-0000-0000-0000-000000000006','AUDIT_READ','Consultar auditoría'),
('10000000-0000-0000-0000-000000000007','REPORT_READ','Consultar reportes'),
('10000000-0000-0000-0000-000000000008','USER_MANAGE','Gestionar usuarios'),
('10000000-0000-0000-0000-000000000009','ROLE_MANAGE','Gestionar roles');

INSERT INTO iam.role(id,code,name) VALUES
('11000000-0000-0000-0000-000000000001','ADMIN','Administrador'),
('11000000-0000-0000-0000-000000000002','ENCARGADO','Encargado de almacén'),
('11000000-0000-0000-0000-000000000003','SUPERVISOR','Supervisor');

INSERT INTO iam.role_permission(role_id,permission_id)
SELECT '11000000-0000-0000-0000-000000000001'::uuid,id FROM iam.permission;

INSERT INTO iam.user_account(id,username,email,password_hash,mfa_enabled)
VALUES ('12000000-0000-0000-0000-000000000001','admin.demo','admin.demo@siga.local',
        '$argon2id$DEMO_REPLACE_WITH_REAL_HASH',false);

INSERT INTO iam.user_role(user_id,role_id)
VALUES ('12000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001');
