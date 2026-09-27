# Continuación de validación local — 26/09/2026

Parte de [VALIDACION_LOCAL_2026-09-26.md](VALIDACION_LOCAL_2026-09-26.md), sin repetir inventario general ni reconstruir Identity. Guía vigente: [LOCAL.md](LOCAL.md). Matriz y propuestas para revisión: [PROPUESTAS_CONTRATO_Y_REFRESH.md](PROPUESTAS_CONTRATO_Y_REFRESH.md).

## A. Configuración local terminada

- Instalación reproducible por instancia en `compose.team.yml`, sin nombres globales de contenedor ni volúmenes compartidos con `siga-local`. Los cuatro servicios están fijados por digest, incluido MinIO `RELEASE.2025-09-07T16-13-09Z` (PostgreSQL 17.11, Redis 7.4.11, RabbitMQ 4.3.5).
- Generación de archivos env y claves por integrante, sin credenciales previas ni PostgreSQL instalado. Init no sobrescribe archivos existentes y rechaza datos huérfanos sin env. Puertos parametrizados y publicados únicamente en loopback; MinIO opcional para otros componentes.
- Login operativo `siga_iam`; login de pruebas `siga_iam_test`, propietario de `siga_identity_local_test` y sin CONNECT sobre `siga`. Estos permisos se aplican solo a nuevas instancias, no al volumen anterior. Flyway continúa siendo propietario de las migraciones IAM; V1–V4 no se modificaron.
- Guard global en el classpath de pruebas: inicializador que instala un BeanFactoryPostProcessor, antes de creación de singletons/DataSource/Flyway. Valida nombres de base exactos, URL sin parámetros, login dedicado, Redis DB 1 y rechaza conexiones alternativas de Flyway/pool/JNDI. BeforeEach añade comprobación defensiva del usuario/base reales, pero no es la primera barrera.
- Suite con servicios externos Compose, no Testcontainers. Se eliminaron sus tres dependencias sin uso; CI se ajustó al nombre del login de pruebas. No se ejecutó CI remoto.
- Management solo en perfil local: listener separado en 127.0.0.1, GET de health/liveness/readiness/prometheus desde loopback; resto denegado. Liveness es interno; readiness incluye PostgreSQL/Redis. RabbitMQ sigue respaldado por outbox y no se incluye en readiness; sí aparece en salud agregada.
- Scripts de inicialización, arranque con espera de readiness, pruebas, health y parada por PID verificado. Builds separados por instancia y por uso: `target` para el JAR y `test-build` para pruebas, bajo `.local/<instance>/`.

## Ensayo limpio y evidencia

Instancia `repro-20260926`, proyecto `siga-repro-20260926`, offset 20000. Se crearon desde cero su red y cuatro volúmenes `siga-repro-20260926_{postgres-data,redis-data,rabbitmq-data,minio-data}`. No se montó ningún volumen `siga-local_*` ni se copiaron sus credenciales. Puertos: DB 35432, Redis 26379, AMQP/UI 25672/35672, MinIO/UI 29000/29001, API 28081, management 29081.

| Comprobación afectada | Resultado real |
|---|---|
| Initialize + Compose config | Correctos; secretos nuevos ignorados, proyecto distinto. Repetir Init conservó el hash del env y las claves existentes. |
| Creación desde volumen vacío | Bases `siga` y `siga_identity_local_test`; roles distintos; ningún schema IAM antes de arrancar Identity. No requirió contraseña previa. |
| Arranque de dependencias | PostgreSQL/Redis/RabbitMQ healthy. MinIO, activado opcionalmente, `/minio/health/live` HTTP 200. |
| TCP IAM | Conexión autenticada a `siga` con `siga_iam`. Login de pruebas conecta a su base dedicada. |
| Separación SQL | `has_database_privilege('siga_iam_test','siga','CONNECT') = false`; intento TCP real rechazado con permission denied for database siga. |
| Flyway limpio | V1, V2, V3, V4 success; `iam` owner `siga_iam`, siete tablas de dominio más historial. También V1–V4 correctas en la base dedicada de pruebas. |
| Protección previa: unitarias | 15 casos `TestDatabaseSafetyTest`, cero fallos/errores/omitidos. Incluyen URL operativa, otro nombre con sufijo _test, parámetros JDBC, encoding del path, conexión alternativa, usuario incorrecto y Redis operativo; los beans que podrían escribir no llegan a inicializarse. |
| Protección previa: integración negativa | Un método de la suite arrancado con URL `...:35432/siga` fue rechazado: Maven exit 1 esperado, marcador TEST_DATABASE_GUARD; no apareció inicio de Hikari ni migración. La base seguía sin IAM antes del arranque posterior del servicio. |
| Integración afectada | Nueve pruebas `IdentityIntegrationTest` correctas con el nuevo login/configuración, `BUILD SUCCESS`. No se repitieron CryptoTest ni los 24 checks históricos. |
| HTTP management | Health/liveness/readiness UP; sin components/details. Prometheus 200 con métricas JVM; API `/actuator/prometheus` 401; management `/actuator/env` 403. Listeners comprobados en 127.0.0.1:28081/29081. |
| Fallo controlado de Redis aislado | Redis detenido temporalmente: liveness UP, readiness HTTP 503; Redis reanudado y probes de nuevo UP. No se detuvo Redis del entorno original. |
| Parada y arranque de la guía | Stop-Local validó el PID y paró solo el JAR de ensayo; Team-Local Stop/Start conservó volúmenes, credenciales, cuatro migraciones y una cuenta demo en la base operativa nueva. El nuevo arranque esperó readiness y Test-LocalHealth pasó. |
| Conflicto de puertos | Init con puertos del entorno anterior rechazó el puerto ocupado antes de crear archivo env; no creó contenedores ni volúmenes del intento. |
| Preservación original | Mismos IDs de los cuatro contenedores previos, sin recrearlos/reiniciarlos; Identity original siguió respondiendo UP. Env originales y clave privada mantuvieron sus hashes. Sin cambios en migraciones ni fuentes canónicas. |

Evidencia privada (no preparada para commit): `.local/repro-20260926/negative-database.log`, `integration-verify.log`, `final-integration.log`, `safety-verify.log`, logs del JAR y reportes JUnit. La última ubicación de informes es `.local/repro-20260926/test-build/surefire-reports/`. No se publica cobertura conjunta nueva: JaCoCo de ejecuciones selectivas no equivale a una suite completa desde cero.

Limitación de reproducibilidad: se probaron volúmenes/roles/credenciales nuevos y los comandos de Windows/PowerShell, usando las imágenes por digest y dependencias Maven presentes en caché. No se simuló instalar Windows, JDK o Docker Desktop ni descargar todo desde una red vacía. Esos requisitos y la necesidad de acceso a registros/Maven están explícitos en la guía.

Estado final: el ensayo `siga-repro-20260926` y su JAR se dejan **detenidos**, con todos sus volúmenes, credenciales y claves conservados para reanudar mediante la guía. El entorno original `siga-local` y su Identity siguen en ejecución; health original UP. La configuración nueva de management fue comprobada en el ensayo, no aplicada mediante reinicio al proceso original.

### Incidencias detectadas y corregidas

- El primer verify de integración pasó los nueve tests pero falló al renombrar el JAR que usaba el proceso anterior en `.local/target`. Se separó el build por instancia y después el build de tests del JAR activo. Se repitió la verificación afectada en la ubicación separada: empaquetado correcto incluso con la instancia en ejecución. No se detuvo Identity anterior para resolver el bloqueo.
- La política del sandbox bloqueó la ejecución de un script nuevo. Se comprobó la instrucción de la guía `Set-ExecutionPolicy -Scope Process RemoteSigned` en una sesión autorizada, sin modificación permanente; health/config/status pasaron.
- Durante cambios de recursos se observó `target/classes/application-local.yml` igual al nuevo recurso fuente, compatible con regeneración automática del IDE; no se atribuye el mecanismo sin evidencia adicional. Su contenido previo se recuperó de forma exacta mediante comparación con el SHA-256 tomado antes de editar y se conservó copia privada en `.local/preserved-target-application-local.yml`. No hubo reset/restore a ciegas. `application.yml` mantuvo su hash; los dos cambios previos de target siguen fuera del índice.
- La regeneración de ese recurso reapareció durante otra verificación. Se conserva la copia exacta por hash para no perder el trabajo previo, y se repone al terminar las compilaciones únicamente si el archivo actual coincide con el recurso generado. Una compilación automática posterior del IDE puede volver a actualizar target; no se cambió la configuración global del editor.

## B. Decisiones documentales que requieren revisión

La [matriz](PROPUESTAS_CONTRATO_Y_REFRESH.md) contrasta cada fuente con la implementación y propone:

1. Alinear los tres campos MFA de V4 en modelo físico/diccionario/DER/UML y semántica, sin alterar la migración ya aplicada.
2. Aprobar o revisar enrolamiento y GET permissions; resolver la mención ambigua de PATCH roles, sin inventar un endpoint.
3. Mantener HTTP 200 contractual para challenge y proponer corregir SEQ-01, sin aplicarlo a la fuente canónica todavía.
4. Proponer Identity como emisor/gestor de cookie web, Gateway como transporte/CORS; definir CSRF y distinción Web/Mobile antes de modificar el contrato JSON. No hay BFF aprobado.
5. Definir política de acceso de observabilidad para otros entornos y para un scraper en contenedor; no extender automáticamente la excepción loopback local.

Estado de todas: **propuestas, no aprobadas ni aplicadas al paquete canónico**. Solo se corrigió en documentación de implementación la atribución infundada a BFF/Gateway y se explicitó la procedencia del diccionario local.

## C. Integraciones todavía no probadas

Gateway, Web/Mobile, login de navegador con cookies/CSRF/HTTPS, consumidor Audit, Evidence/MinIO, Prometheus/Grafana en contenedores, CI remoto y despliegue de imágenes. No hubo GCP, publicación, commits ni pushes. El ensayo no equivale a integración completa de SIGA.

## Archivos de esta continuación

**Identity:**

- `AGENTS.md`, `.env.example`, `.github/workflows/ci.yml`, `pom.xml`, `README.md`.
- `scripts/Initialize-Local.ps1` (nuevo), `Import-LocalEnvironment.ps1`, `SetupKeys.java`, `Start-Infrastructure.ps1`, `Start-Local.ps1`, `Stop-Local.ps1` (nuevo), `Test-Local.ps1`, `Test-LocalHealth.ps1` (nuevo).
- `src/main/resources/application-local.yml`; `src/main/java/utp/siga/identity/infrastructure/LocalManagementSecurity.java` (nuevo).
- `src/test/java/utp/siga/identity/IdentityIntegrationTest.java`, `TestDatabaseSafety.java` y `TestDatabaseSafetyTest.java` (nuevos); `src/test/resources/META-INF/spring.factories` (nuevo).
- `docs/LOCAL.md`, `docs/PROPUESTAS_CONTRATO_Y_REFRESH.md` (nuevo), este informe, `docs/DECISIONES.md`, `docs/TESTING.md`, `docs/database/dictionary.md` y aviso histórico en `docs/VALIDACION_LOCAL_2026-09-26.md`.

**Infrastructure:** `AGENTS.md`, `.gitignore`, `README.md`; nuevos `compose/compose.team.yml`, `compose/.env.team.example`, `scripts/Team-Local.ps1`, `postgres/05-local-access.sql`, `postgres/20-test-database.sql`. El SQL previo `10-identity-role.sql` se reutiliza intacto.

**Documentation:** sin modificaciones adicionales en esta continuación; se conserva el AGENTS raíz no versionado de la tarea anterior. Ningún ADR, modelo, contrato ni diagrama aprobado fue editado.

Además aparecieron cambios concurrentes de formato en siete fuentes no editadas por esta tarea: `application/Api.java`, `application/AuthService.java`, `infrastructure/IdentityRepository.java`, `infrastructure/OutboxPublisher.java`, `infrastructure/SecurityConfiguration.java`, `infrastructure/TokenService.java` y `presentation/IdentityController.java` (bajo `src/main/java/utp/siga/identity/`). Se comprobó que conservan exactamente la secuencia de tokens, incluidos literales, respecto al HEAD; se dejaron intactos, sin atribuirlos a cambios funcionales de esta tarea.

Los archivos nuevos privados están ignorados. No se ejecutó git add: el índice de los tres repositorios está vacío. `target/` contiene archivos ya versionados y sus cambios previos: no incluirlos con un futuro git add indiscriminado. La guía y esta lista distinguen fuentes de los artefactos generados.
