# Diccionario IAM implementado

Se conservan las siete tablas canónicas de SIGA v1.2. El modelo físico completo de este servicio está en `physical_model.sql`; la ejecución se hace exclusivamente mediante Flyway V1–V4.

| Tabla | Responsabilidad y restricciones |
|---|---|
| user_account | UUID, username/email únicos, hash Argon2id, activo, bloqueo tras 5 fallos por 15 min. Secreto TOTP cifrado AES-256-GCM. |
| role | Código único, nombre y estado. ADMIN es un rol protegido. |
| permission | Catálogo de 9 permisos iniciales, código único. |
| user_role | N:M usuario/rol; PK compuesta y FK solo IAM. |
| role_permission | N:M rol/permiso; PK compuesta y FK solo IAM. |
| refresh_token | Hash SHA-256 de 256 bits aleatorios, jti único, familia, vencimiento, revocación y siguiente jti. |
| outbox_event | Evento versionado, payload, trazas, PENDING/PUBLISHED/FAILED, intentos y próxima ejecución. |

## Extensión V4

| Columna | Tipo | Uso |
|---|---|---|
| user_account.mfa_enrolled | boolean, false, NOT NULL | Diferencia configuración pendiente de un autenticador confirmado. Solo una verificación TOTP válida lo confirma. |
| user_account.last_totp_step | bigint nullable | Último intervalo de 30 s aceptado; rechaza reuso incluso entre desafíos distintos. |
| refresh_token.mfa_authenticated_at | timestamptz nullable | Conserva la antigüedad real del segundo factor durante refresh; refrescar no renueva step-up. |

Redis conserva desafíos de 5 minutos, contadores OTP y rate limit por IP (10/minuto). No almacena contraseñas ni secretos TOTP. La pérdida de Redis invalida los desafíos; no concede acceso. Cada familia de refresh se serializa con bloqueo del usuario y del token en PostgreSQL.
