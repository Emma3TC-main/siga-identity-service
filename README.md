# SIGA Identity Service

Microservicio IAM de SIGA: usuarios, RBAC dinámico, JWT RS256, MFA/TOTP, sesiones refresh rotativas y eventos de auditoría mediante outbox. Java 21, Spring Boot 3.5.16, PostgreSQL 17, Redis 7 y RabbitMQ 4.

## Entorno local del equipo

Seguir la [guía única Windows/PowerShell](docs/LOCAL.md): instalación nueva por instancia, secretos generados, PostgreSQL/Redis/RabbitMQ compartidos y MinIO opcional. No se necesita PostgreSQL instalado ni GCP. El entorno anterior `siga-local` se conserva separado.

[Resultados de esta continuación](docs/CIERRE_LOCAL_2026-09-26.md): protección de pruebas antes de Flyway, instalación aislada comprobada y probes/métricas en un listener local separado. Las pruebas usan servicios externos Compose, no Testcontainers. El directorio de pruebas es distinto del JAR activo y de `target/` versionado.

El usuario demo inicial es `admin.demo`; su contraseña está en `IAM_BOOTSTRAP_PASSWORD` del archivo privado de la instancia. No imprimirla. El servicio ya implementa MFA, roles/permisos y refresh JSON; los contratos y campos adicionales pendientes de aprobación están en la [matriz de propuestas](docs/PROPUESTAS_CONTRATO_Y_REFRESH.md). La guía de solicitudes sigue en [THUNDER_CLIENT.md](docs/THUNDER_CLIENT.md).

## Thunder Client

Extensión `rangav.vscode-thunder-client`. Colección: `docs/thunder-collection_SIGA.json`; entorno sin secretos: `docs/thunder-environment_local.json`. Importar si se dispone de licencia; alternativamente crear las solicitudes del [guion manual](docs/THUNDER_CLIENT.md). No se debe compartir un entorno con contraseñas/tokens rellenados.

## Documentación

- [OpenAPI](docs/openapi.yaml)
- [Decisiones y discrepancias resueltas](docs/DECISIONES.md)
- [Modelo físico](docs/database/physical_model.sql), [lógico](docs/database/logical_model.md) y [diccionario](docs/database/dictionary.md)
- [Matriz de pruebas](docs/TESTING.md)

Las claves privadas y `.env` permanecen locales. Para un despliegue real, inyectar secretos, usar TLS en el punto de entrada y crear un rol PostgreSQL de ejecución con privilegios limitados; el propietario de BD de Compose corresponde al entorno de desarrollo.
