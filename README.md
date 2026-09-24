# SIGA Identity Service

Microservicio IAM de SIGA: usuarios, RBAC dinámico, JWT RS256, MFA/TOTP, sesiones refresh rotativas y eventos de auditoría mediante outbox. Java 21, Spring Boot 3.5.16, PostgreSQL 17, Redis 7 y RabbitMQ 4.

## Inicio local

Requisitos: JDK 21+ y Docker con Compose v2. Maven Wrapper está incluido.

```powershell
java scripts/SetupKeys.java
docker compose -p siga-iam up -d --wait postgres redis rabbitmq
./scripts/Start-Local.ps1
```

`SetupKeys.java` genera claves RSA, cifrado MFA y contraseñas aleatorias en `.secrets/` y `.env`; preserva configuraciones existentes. El usuario inicial es `admin.demo`, con contraseña `IAM_BOOTSTRAP_PASSWORD` de `.env`. La contraseña inicial solo sustituye el placeholder del seed; reiniciar no cambia una cuenta ya inicializada.

Para Windows con Docker dentro de Ubuntu/WSL, usar `./scripts/Start-Infrastructure.ps1` en lugar del comando docker. Mantiene WSL activo en segundo plano y espera a que los servicios estén saludables.

API: `http://localhost:8081/api/v1`; salud: `http://localhost:8081/actuator/health`; clave pública: `http://localhost:8081/.well-known/jwks.json`.

### Primer acceso (MFA obligatorio del administrador)

1. POST `/auth/login`: username `admin.demo`, password de `.env`.
2. POST `/auth/mfa/enroll`: el `challengeId` devuelto; registrar `secret` u `otpauthUri` en un autenticador TOTP.
3. POST `/auth/mfa/verify`: `challengeId` y código de 6 dígitos actual. Devuelve accessToken y refreshToken.
4. Enviar `Authorization: Bearer <accessToken>` al gestionar cuentas/roles. En accesos posteriores omitir enrolamiento y verificar directamente el TOTP.

No existe registro público de administradores. Los usuarios se crean por un usuario con USER_MANAGE; asignar roles requiere además ROLE_MANAGE. Toda escritura administrativa requiere MFA reciente (5 min). Repetir login+MFA renueva esa verificación.

## Todo en contenedores

```sh
java scripts/SetupKeys.java
docker compose -p siga-iam --profile app up -d --build --wait
```

## Pruebas

Crear una BD separada, nunca ejecutar tests contra la BD de trabajo:

```sh
docker compose -p siga-iam exec postgres createdb -U siga_iam siga_identity_test
```

En Windows: `./scripts/Test-Local.ps1`. En Linux exportar `.env`, establecer `DB_URL=jdbc:postgresql://localhost:55432/siga_identity_test` y ejecutar `./mvnw verify`. Las pruebas de integración rechazan bases sin sufijo `_test`; usan Redis DB 1 aislada de la DB 0 del servicio. CI crea PostgreSQL, Redis y RabbitMQ reales.

Resultados JUnit: `target/surefire-reports/`; cobertura: `target/site/jacoco/index.html`.

`python scripts/smoke.py` ejecuta un flujo HTTP contra la API local, crea una cuenta de prueba única y la desactiva al terminar. Si el administrador aún no tiene MFA configurado, el script lo enrola y guarda su secreto de demostración en `.local/demo-mfa.json` (ignorado por git); usarlo solo en el entorno local generado. Un administrador ya enrolado exige que dicho secreto local exista; no se restablece MFA automáticamente.

## Thunder Client

Extensión `rangav.vscode-thunder-client`. Colección: `docs/thunder-collection_SIGA.json`; entorno sin secretos: `docs/thunder-environment_local.json`. Importar si se dispone de licencia; alternativamente crear las solicitudes del [guion manual](docs/THUNDER_CLIENT.md). No se debe compartir un entorno con contraseñas/tokens rellenados.

## Documentación

- [OpenAPI](docs/openapi.yaml)
- [Decisiones y discrepancias resueltas](docs/DECISIONES.md)
- [Modelo físico](docs/database/physical_model.sql), [lógico](docs/database/logical_model.md) y [diccionario](docs/database/dictionary.md)
- [Matriz de pruebas](docs/TESTING.md)

Las claves privadas y `.env` permanecen locales. Para un despliegue real, inyectar secretos, usar TLS en el punto de entrada y crear un rol PostgreSQL de ejecución con privilegios limitados; el propietario de BD de Compose corresponde al entorno de desarrollo.
