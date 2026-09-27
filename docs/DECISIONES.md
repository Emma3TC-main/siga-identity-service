# Decisiones de implementación

> Este archivo registra decisiones históricas de implementación; no sustituye los ADR y contratos aprobados en `siga-documentation/SIGA_Documentacion_Tecnica_Final_v1.2`. La revisión local del 26/09/2026 identifica discrepancias pendientes en [VALIDACION_LOCAL_2026-09-26.md](VALIDACION_LOCAL_2026-09-26.md). Para los puertos y comandos vigentes del entorno compartido, usar [LOCAL.md](LOCAL.md).

## Alcance y fuentes

Encargo confirmado en el chat: implementar `siga-identity-service` en Spring Boot, login/cuentas/permisos; probar las APIs y levantar PostgreSQL con Docker si es posible. Los demás repositorios SIGA quedan fuera de esta implementación.

Se partió del repositorio de Emma3TC-main y se contrastó con los ZIP proporcionados. Fuentes: Manual Técnico SIGA v1.2, ADR-011, ADR-018, SQL físico IAM, CUS-01/02/03, OpenAPI Identity y secuencias originales. No se usaron los diagramas renderizados para inferir requisitos.

## Contrato

- Se implementan los 12 métodos del contrato original, con sus rutas y respuestas.
- Login responde 200 también cuando requiere MFA, como OpenAPI; SEQ-01 mostraba 202. Esta discrepancia se resuelve explícitamente a favor de la respuesta contractual que consumen los clientes.
- Se añaden `POST /auth/mfa/enroll` para completar el enrolamiento faltante y `GET /permissions` para consultar los UUID del catálogo. JWKS se sirve en `/.well-known/jwks.json`.
- MFA es obligatorio para USER_MANAGE, ROLE_MANAGE, MOVEMENT_AUTHORIZE e INVENTORY_ADJUST. Las escrituras administrativas requieren MFA de los últimos 5 minutos. Un nuevo login+MFA permite step-up sin inventar un endpoint de las operaciones de inventario.
- Crear usuarios con `mfaEnabled=true` deja el enrolamiento pendiente: el usuario inicia sesión, obtiene challenge, solicita enrolamiento, registra la clave en su autenticador y verifica OTP. No se devuelve el secreto en el CRUD de usuarios.
- Se impide autodesactivarse y modificar permisos del rol ADMIN.
- Esta API implementa el transporte JSON de refresh descrito en OpenAPI. La atribución histórica de cookies/CSRF a BFF/Gateway no estaba aprobada: SEQ-15 muestra IAM respondiendo con refresh rotado en cookie y no hay BFF aprobado. La decisión técnica y el contrato web están pendientes; ver [propuesta para revisión](PROPUESTAS_CONTRATO_Y_REFRESH.md). No se implementaron cookies/login Web en esta tarea.

## Persistencia, seguridad y mensajería

- Se preservan V1–V3 para no romper checksums de instalaciones existentes. V4 incorpora tres campos necesarios para enrolamiento y prevención de replay, sin crear tablas adicionales ni FK entre servicios. Ver diccionario y SQL sincronizados.
- Las cuentas y sesiones son autoridad de PostgreSQL. Las claves privadas RSA y AES se generan por instalación; no se versionan ni se incluyen en el ZIP entregable. No se guardan tokens sin hash en la BD.
- Las sesiones se invalidan al desactivar usuarios o cambiar autorizaciones. Los endpoints IAM consultan estado y permisos actuales en cada request. Otros servicios que validen solo JWT deben considerar revocación o aceptar la vigencia máxima de 30 min; JWKS no contiene información de revocación.
- Los errores de autenticación que deben persistir (contador de fallos y revocación por refresh reutilizado) no revierten la transacción. Las demás escrituras y su outbox son atómicas.
- Eventos: UserCreated, UserUpdated, UserDisabled y RoleChanged. Envelope v1 con productor identity-service; exchange `siga.events`, routing `iam.<eventType>`, cola durable `siga.identity.audit`. El equipo de Audit debe enlazar su consumidor con esta topología.
- Publisher con confirmaciones, detección de retornos, mensajes persistentes, `FOR UPDATE SKIP LOCKED` y reintento cada 30 segundos. Entrega al menos una vez: consumidores deduplican por eventId. Los eventos pendientes se conservan aunque el broker falle.
- Las consultas SQL usan parámetros. `presentation` adapta HTTP, `application` coordina transacciones, `infrastructure` implementa SQL, Redis, claves y mensajería, `domain` contiene el modelo de cuenta y errores.

## Configuración operativa

Puertos locales 8081, 55432, 56379, 55672 y 55673. Compose publica solo en 127.0.0.1. No se cambia el PostgreSQL 17 que ya estaba instalado en Windows. Docker se instaló mediante el repositorio Ubuntu en WSL, sin Docker Desktop.

Thunder Client está instalado. Su importación y CLI requieren licencia según [documentación oficial](https://docs.thunderclient.com/cli) y [guía de importación](https://docs.thunderclient.com/features/import). Se proporciona colección y matriz; la validación ejecutada se identifica como JUnit/HTTP, no como ejecución en Thunder Client.
