# Revisión y validación local — 26 de septiembre de 2026

> Registro de la primera validación, conservado como evidencia histórica. La continuación que cierra protección de pruebas, instalación nueva y probes está en [CIERRE_LOCAL_2026-09-26.md](CIERRE_LOCAL_2026-09-26.md); comandos vigentes en [LOCAL.md](LOCAL.md).

Alcance: configuración/validación local. Sin despliegues, recursos cloud, publicación de imágenes ni operaciones Git de reset/clean/rebase/merge/push. No se cambiaron contratos, ADR, código funcional IAM ni migraciones existentes.

## Estado inicial confirmado

| Repositorio | Rama / HEAD | Estado |
|---|---|---|
| Identity | `main`, `983153c` | Dos cambios previos: `target/classes/application.yml` y `target/classes/application-local.yml`, preservados. Seis archivos de `target/` estaban versionados. |
| Infrastructure | `chore/configuracion-entorno`, `8107172` | Limpio; `compose/.env` ignorado existente, conservado. |
| Documentation | `feature/SIGA-DOCS-initial-upload`, `2e2d09a` | Limpio; reglas en `SIGA_Documentacion_Tecnica_Final_v1.2/AGENTS.md`. |

Se revisaron ramas y seis entradas de reflog en cada repositorio. Identity registra el reset previo a `origin/main` y antes el commit `7cea564` en `chore/configuracion-entorno`; esa rama local y las demás se conservaron. No se hizo fetch: las referencias remotas listadas son las disponibles localmente. La protección de ownership de Git en el sandbox se atendió con `git -c safe.directory=<ruta exacta>` por comando, sin cambiar configuración global.

Docker Desktop estaba detenido. Tras iniciarlo se encontraron cuatro contenedores previos: `siga-local-postgres-1`, `siga_redis`, `siga_rabbitmq`, `siga_minio`. Fueron creados con la combinación del Compose general y el local. Redes: `siga-local_data`, `siga-local_backend` (además de las del motor). Volúmenes: `siga-local_postgres-data`, `siga-local_redis_data`, `siga-local_rabbitmq_data`, `siga-local_minio_data`.

El PostgreSQL existente tenía base `siga`, solo esquema `public` y rol administrador `postgres`, sin IAM. Su volumen fue creado el 03/09/2026. No tenía puerto publicado. Las variables de inicialización no pueden cambiar la contraseña de ese volumen; se preservó la administrativa y se generó una contraseña nueva para `siga_iam`. No se inspeccionaron credenciales del motor PostgreSQL instalado en Windows. No se encontró listener en 5432; se eligió 15432 para mantener independencia de ese motor.

Versiones confirmadas: Java Temurin `21.0.12.1`, Maven Wrapper `3.9.16`, Maven instalado `3.9.10` (no usado en la validación), Spring Boot del POM y ejecución `3.5.16`, Docker Engine/CLI `28.0.1`, Compose `v2.33.1-desktop.1`, PostgreSQL `17.11`, Redis `7.4.11`. RabbitMQ usa imagen `rabbitmq:4-management`; MinIO usa `minio/minio:latest` (etiquetas observadas, no garantía de reproducibilidad binaria). El Dockerfile de Identity usa Maven `3.9.11`/Temurin 21 y JRE 21 sin usuario root. No se construyó esa imagen.

CI Identity (`.github/workflows/ci.yml`) configura Java 21, PostgreSQL 17, Redis 7, RabbitMQ 4, setup de claves y `mvn verify`, con puertos 55432/56379/55672 y artefactos JUnit/JaCoCo. Se inspeccionó, pero no se ejecutó GitHub Actions ni se infirió su estado de un merge. En Infrastructure los workflows son plantillas: `backend-ci.yml` invoca `./mvnw` y un Dockerfile inexistentes en ese repo; `deploy-gcp.yml` es manual/de referencia. Documentation tiene ejemplos en `devops/github-actions/`, no CI activo en `.github/workflows`. Estas plantillas no prueban integración funcional.

## Contraste con v1.2

Prefijo canónico: `../../siga-documentation/SIGA_Documentacion_Tecnica_Final_v1.2/` desde este documento.

| Área | Evidencia y conclusión |
|---|---|
| Ownership | `adr/ADR-004_Schemas_PostgreSQL_por_servicio.md`, `adr/ADR-018_Modelo_datos_canonico_coherencia.md`, `database/logical_model.md` §3.1: Identity posee IAM. `src/main/resources/db/migration/V1__create_schema.sql` y V2 solo crean IAM. V2 coincide con el segmento IAM de `database/physical_model.sql` normalizando espacios. |
| Migraciones | V1–V4 permanecen intactas. `application.yml` configura `default-schema` y `schemas` como `iam`; Hibernate `ddl-auto: none`. No se aplicó el SQL consolidado ni se crearon otros schemas de negocio. |
| JDBC/local | El perfil local estaba sin ajustes y el base apuntaba a 55432/56379/55672. Los scripts levantaban otro stack `siga-iam`. Corregido hacia Infrastructure, 15432/6379/5672 y rol dedicado; se conserva la configuración autónoma anterior. |
| Seguridad | `adr/ADR-011_JWT_RS256_MFA.md`, Manual Técnico §7, `diagramas/c4/C4_05_Componente_Identity.puml`, `diagramas/uml/UML_02_Clases_IAM.puml`: RS256, Argon2id, TOTP, refresh 7 días/access 30 minutos. Implementación y pruebas ejercitan estos flujos; claves privadas fuera de Git y JWKS sin parámetros privados. HTTP local ahora solo loopback. |
| Mensajería | Manual §11.3, `especificaciones/CUS_Detallados.md` CUS-02/03 y `trazabilidad/requirements.md` RF-02/03 contemplan eventos IAM. `OutboxPublisher.java` y AMQP ya existían: se reutiliza RabbitMQ, sin añadir integración MinIO. |
| Salud | `SecurityConfiguration.java` permite `/actuator/health`; observado UP. Las rutas `/actuator/health/liveness`, `/actuator/health/readiness` y `/actuator/prometheus` responden 401 sin autenticación; `anyRequest().denyAll()` bloquea endpoints operativos no autorizados explícitamente. No se declara resuelto el acceso de observabilidad descrito por Manual §15. |
| Pruebas | `IdentityIntegrationTest.java` contiene nueve tests reales con PostgreSQL/Redis/RabbitMQ; `CryptoTest.java`, dos. Testcontainers está declarado, pero estos tests usan servicios externos, no contenedores administrados por JUnit. La comprobación `_test` ocurre en BeforeEach, después del arranque/Flyway: no es protección suficiente si alguien ejecuta Maven con una URL incorrecta. El script fija una base dedicada. |

No se usaron `diagramas_render/` ni `out/` para inferir implementación.

## Discrepancias pendientes de decisión aprobada

1. `V4__mfa_enrollment_and_replay.sql` agrega `user_account.mfa_enrolled`, `user_account.last_totp_step` y `refresh_token.mfa_authenticated_at`. Están en las copias `docs/database/` de Identity, pero no en `database/physical_model.sql`, `database/dictionary.md` ni `diagramas/datos/DER_04_Fisico_IAM.puml` canónicos. Requiere revisión conforme a ADR-018; no se modificó V4 ni se asumió aprobada por el historial del servicio.
2. `IdentityController.java`/`docs/openapi.yaml` añaden `/auth/mfa/enroll` y `/permissions`, ausentes de `api/identity-openapi.yaml`. CUS-03 sí menciona `/permissions` y `PATCH /roles`, mientras OpenAPI no declara ese PATCH. Registrar y aprobar el contrato definitivo antes de integrar clientes.
3. `diagramas/secuencia/SEQ_01_Login_MFA.puml` dice 202 para desafío MFA; OpenAPI y código devuelven 200. `docs/DECISIONES.md` eligió 200 a nivel implementación, pero no resuelve por sí solo la contradicción de fuentes canónicas.
4. Manual §7.6 exige refresh web en cookie HttpOnly/Secure/SameSite; OpenAPI transporta refresh en JSON y `docs/DECISIONES.md` delega cookies/CSRF a BFF/Gateway. Confirmar esa responsabilidad mediante la fuente aprobada antes de integrar Web; no se implementó BFF en esta tarea.
5. La política de acceso a métricas y probes debe acordarse antes de observabilidad/Gateway. La salud agregada está verificada; no hay evidencia de readiness/liveness accesibles según lo descrito en Manual §15.

## Archivos cambiados

- Infrastructure: `compose/compose.local.yml`, `compose/.env.example`, `postgres/10-identity-role.sql`, `scripts/Setup-Local.ps1`, `README.md`, `AGENTS.md`. Archivo local ignorado nuevo: `compose/.env.local`; el `.env` previo está intacto.
- Identity: `src/main/resources/application-local.yml`, `pom.xml` (directorio de build configurable), `scripts/Import-LocalEnvironment.ps1`, `scripts/Start-Infrastructure.ps1`, `scripts/Start-Local.ps1`, `scripts/Test-Local.ps1`, `README.md`, `AGENTS.md`, `docs/LOCAL.md`, este informe y avisos históricos en `docs/DECISIONES.md`/`docs/TESTING.md`. Nuevos archivos privados ignorados: `.env`, `.secrets/`, `.local/` y logs.
- Documentation: `AGENTS.md` raíz; se mantienen las reglas existentes del paquete v1.2 y todas sus fuentes aprobadas.

## Verificaciones ejecutadas

| Verificación | Resultado / evidencia |
|---|---|
| Sintaxis PowerShell | Scripts locales analizados sin errores. |
| `docker compose ... config --quiet` | Correcto; no se imprimió configuración expandida con secretos. |
| Arranque y repetición | PostgreSQL healthy; segunda ejecución preservó `.env.local`, contenedores, volumen y rol. Redis/RabbitMQ/MinIO se reutilizaron con `--no-recreate`. |
| Red local | PostgreSQL escucha solo en 127.0.0.1:15432; Identity en 127.0.0.1:8081. |
| PostgreSQL TCP | Autenticación real con `siga_iam` y su contraseña nueva, base `siga`, PostgreSQL 17.11. |
| Ownership/Flyway | `iam` pertenece a `siga_iam`, siete tablas de dominio + `flyway_schema_history`; V1–V4 success, cero FK cross-schema. Rol sin superusuario/CREATEDB/CREATEROLE. |
| Maven/Java | `Test-Local.ps1`: BUILD SUCCESS, Java 21, Maven 3.9.16, 11 tests, cero fallos/errores/omitidos, JAR empaquetado. Reportes `.local/target/surefire-reports/`. |
| Cobertura | JaCoCo: 548 líneas cubiertas, 35 no cubiertas (94,0%); `.local/target/site/jacoco/`. |
| HTTP real | `python scripts/smoke.py`: 24 comprobaciones correctas; firma RS256 verificada, MFA, usuarios/roles/permisos, refresh/reutilización, 400/401/403/409, logout. `.local/http-results.json`. |
| Salud Identity | `/actuator/health`: HTTP 200, `status: UP`; JWKS RSA sin clave privada; `/api/v1/users` sin token: 401. |
| Dependencias | Redis PONG; RabbitMQ diagnostics ping correcto; MinIO `/minio/health/live`: HTTP 200. Los contenedores Redis/RabbitMQ existentes se preservaron sin recrearlos para aplicar sus nuevos healthchecks; se comprobó su salud con comandos directos. |
| Outbox | Seis eventos PUBLISHED en `siga.iam.outbox_event`; seis mensajes en la cola existente `siga.identity.audit`. No hay consumidor Audit ejecutándose en esta validación. |
| Secretos/Git | `.env`, `.secrets/private.pem`, `.local/target` y `compose/.env.local` ignorados. Sin secretos nuevos versionados ni cambios de ramas/remotos. |

Incidencias verificadas: Docker no accesible inicialmente (motor detenido y restricciones del sandbox), resuelto iniciando Desktop con autorización. Wrapper falló dentro del sandbox por acceso a su entorno; fuera confirmó versión correcta. La primera invocación redirigida de Maven devolvió NativeCommandError por advertencias de Mockito bajo PowerShell 5.1 y `ErrorActionPreference=Stop`, aunque los informes de tests eran correctos. Se ajustó el script para usar el exit code nativo y se repitió `verify`: éxito confirmado. La advertencia de auto-attach de Mockito permanece; no se cambió el framework.

No ejecutado: CI remoto, build Docker del servicio, despliegue integral del Compose general, validación OpenAPI con herramienta externa, clientes Web/Mobile, Gateway, consumidor Audit o despliegue cloud. No se atribuye a esta ejecución la validación histórica del 24/09/2026.

## Operación y pendientes

Comandos PowerShell exactos de arranque, pruebas y parada sin borrar datos: [LOCAL.md](LOCAL.md). El entorno se deja en ejecución, incluido el JAR de Identity (PID guardado en `.local/identity.pid`). El smoke deja una cuenta desactivada, un rol demo y enrolamiento MFA demo conservado en el archivo privado `.local/demo-mfa.json`.

La primera integración local de Identity con PostgreSQL, Redis y RabbitMQ está comprobada. Pendientes: aprobación de las discrepancias anteriores; conexión de Gateway/clientes y consumidor Audit; validar CI remoto por separado; acordar acceso de observabilidad. La plantilla CI de Infrastructure y los archivos `target/` versionados requieren mantenimiento posterior, sin eliminar trabajo previo en esta tarea.
