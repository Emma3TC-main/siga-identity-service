# Identity: etapa 0 del refactor

Fecha: 2026-09-26. Estado: **plan propuesto; ningún movimiento ejecutado**.

## Alcance y punto de comparación

La referencia es el árbol de trabajo actual, no una reconstrucción desde HEAD. Rama `main`, HEAD `983153c3cd46a88c27f8991135d876f0c2226233`; índice sin archivos preparados al inspeccionarlo. Esta etapa consulta Java principal/de pruebas, POM, estado Git e instrucciones/decisiones arquitectónicas pertinentes. No abre `.local/`, contenido de `target/`, migraciones ni configuración de infraestructura. No ejecuta Maven, pruebas, HTTP ni Docker.

La documentación canónica continúa en `siga-documentation/SIGA_Documentacion_Tecnica_Final_v1.2`. Se consideran ADR-001/002 (servicios y límites), ADR-004 (propiedad de esquemas), ADR-011 (JWT/MFA), ADR-018 (gobierno de modelos) y `diagramas/uml/UML_07_Paquetes_Backend.puml` (dependencias hacia el núcleo). ADR-015 describe organización de Web/Mobile; no impone feature-first al backend. UML-07 denomina la entrada `interfaces.rest`; conservar `presentation.rest` en esta propuesta aprovecha la estructura existente sin afirmar que ambos nombres literales sean iguales a la fuente canónica.

El movimiento de paquetes no aprueba diferencias funcionales ni corrige automáticamente la arquitectura. Se conservan las mejoras presentes hasta evaluarlas por separado. No se cambia el contrato, las transacciones, las migraciones, los beans, la seguridad ni la infraestructura.

### Estado Git por ruta antes de añadir este documento

` M`: modificación previa sin preparar; `??`: archivo previo no seguido. Las rutas se expresan desde la raíz de Identity. Las demás unidades Java enumeradas abajo están seguidas y sin diferencias frente a HEAD.

```text
 M .env.example
 M .github/workflows/ci.yml
 M README.md
 M docs/DECISIONES.md
 M docs/TESTING.md
 M docs/database/dictionary.md
 M pom.xml
 M scripts/SetupKeys.java
 M scripts/Start-Infrastructure.ps1
 M scripts/Start-Local.ps1
 M scripts/Test-Local.ps1
 M src/main/java/utp/siga/identity/application/Api.java
 M src/main/java/utp/siga/identity/application/AuthService.java
 M src/main/java/utp/siga/identity/infrastructure/IdentityRepository.java
 M src/main/java/utp/siga/identity/infrastructure/OutboxPublisher.java
 M src/main/java/utp/siga/identity/infrastructure/SecurityConfiguration.java
 M src/main/java/utp/siga/identity/infrastructure/TokenService.java
 M src/main/java/utp/siga/identity/presentation/IdentityController.java
 M src/main/resources/application-local.yml
 M src/test/java/utp/siga/identity/IdentityIntegrationTest.java
 M target/classes/application-local.yml
 M target/classes/application.yml
?? AGENTS.md
?? docs/CIERRE_LOCAL_2026-09-26.md
?? docs/LOCAL.md
?? docs/PROPUESTAS_CONTRATO_Y_REFRESH.md
?? docs/VALIDACION_LOCAL_2026-09-26.md
?? scripts/Import-LocalEnvironment.ps1
?? scripts/Initialize-Local.ps1
?? scripts/Stop-Local.ps1
?? scripts/Test-LocalHealth.ps1
?? src/main/java/utp/siga/identity/infrastructure/LocalManagementSecurity.java
?? src/test/java/utp/siga/identity/TestDatabaseSafety.java
?? src/test/java/utp/siga/identity/TestDatabaseSafetyTest.java
?? src/test/resources/META-INF/spring.factories
```

Las siete modificaciones de Java principal seguidas son concurrentes de formato: comparación léxica con HEAD realizada en esta etapa, conservando cadenas y comentarios, dio tokens idénticos en los siete archivos. Esto no equivale a compilar o probar. Se mantiene su formato actual al proponer movimientos; no se restaura HEAD. `LocalManagementSecurity` es una incorporación funcional previa, no formato. Tampoco se descartan el endurecimiento de pruebas ni los cambios del POM.

Los dos cambios de `target/` se identificaron exclusivamente por estado Git. No se leyeron ni restauraron y quedan fuera de cualquier lote. Ninguna futura preparación de cambios debe usar `git add .`: los lotes deben seleccionar rutas concretas y excluir cambios previos, secretos y generados.

## Inventario dirigido y dependencias

Hay **18 unidades de código** en las raíces de las cuatro capas: 3 en application, 3 en domain, 9 en infrastructure y 3 en presentation. Incluyen un record y una interfaz. Existen las subcarpetas application/{command,dto,query,usecase}, domain/{model,port,service}, infrastructure/{config,messaging,persistence,security} y presentation/{error,rest}, actualmente sin estas clases.

Prefijo de todas las rutas de esta matriz: `src/main/java/utp/siga/identity/`. Los destinos son propuestas, no archivos creados. “Entrantes” identifica consumidores Java directos y consumidores Spring relevantes; no enumera cada llamada HTTP de los tests. Las dependencias salientes agrupan bibliotecas por función.

| Unidad actual | Responsabilidad | Entrantes | Salientes | Destino propuesto | Imports, visibilidad y Spring |
|---|---|---|---|---|---|
| application/Api.java | 15 records de solicitudes/respuestas y validación | AuthService, ManagementService, IdentityStore, IdentityRepository, IdentityController, IdentityIntegrationTest | Java collections/UUID; Jakarta Validation | application/dto/Api.java | Actualizar imports de seis consumidores; los wildcard no incluyen subpaquetes. Mantener records públicos, nombres/campos y restricciones; no es bean. No separar records en este refactor mecánico. |
| application/AuthService.java | Login, MFA/enrolamiento, refresh y logout | IdentityController, ManagementService, SecurityConfiguration, IdentityIntegrationTest | Api, Account, IdentityException, IdentityRepository, Crypto, TokenService, ChallengeStore, PasswordEncoder, JWT y transacciones Spring | application/usecase/AuthService.java | Imports consumidores y Api; mientras ManagementService siga en la raíz, importar AuthService allí. Preservar @Service, métodos públicos y @Transactional/noRollbackFor. |
| application/ManagementService.java | Altas/cambios de usuarios, roles, asignaciones e invalidación de sesiones | IdentityController | Api, AuthService, IdentityException, IdentityRepository, Crypto, PasswordEncoder y transacciones | application/usecase/ManagementService.java | Imports de controlador y Api/AuthService según lote. Mantener API pública y @Service/@Transactional. |
| domain/Account.java | Estado de cuenta y MFA, record | AuthService, IdentityStore, IdentityRepository, TokenService | UUID, Instant | domain/model/Account.java | Imports en los cuatro consumidores; referencias con tipo inferido no necesitan import adicional. Record público; sin Spring. |
| domain/IdentityStore.java | Interfaz de consultas y registro de eventos | IdentityRepository, que la implementa | Account, Api, tipos Java | application/port/IdentityStore.java | Import en repositorio y Account/Api en la interfaz. Pública; sin Spring. El destino en Application elimina la dependencia `domain → application.dto.Api` sin cambiar firmas y sigue UML-07: Application define puertos e Infrastructure los implementa. |
| domain/IdentityException.java | Error con status HTTP, código y detalle | AuthService, ManagementService, ChallengeStore, IdentityRepository, IdentityController, Problems, IdentityIntegrationTest | RuntimeException | domain/exception/IdentityException.java | **Subcarpeta nueva propuesta**; actualizar seis consumidores principales y FQCN del test. Mantener constructor/campos/fábrica públicos. Sin Spring; no rediseñar el error aquí. |
| infrastructure/AccessPolicy.java | Comprobar antigüedad de auth_time, máximo 300 s | IdentityController mediante SpEL `@accessPolicy.recent(authentication)` | Authentication, Jwt, Instant | infrastructure/security/AccessPolicy.java | Solo cambia package/ruta; no hay import Java consumidor. Mantener @Component("accessPolicy") y método público. Scan recursivo suficiente. |
| infrastructure/Bootstrap.java | Inicializar contraseña del usuario semilla cuando corresponde | Spring ApplicationRunner | IdentityRepository, PasswordEncoder, @Value y transacciones | infrastructure/config/Bootstrap.java | Añadir import de repositorio al abandonar paquete compartido. Mantener @Component, ApplicationRunner y @Transactional; sin ampliar visibilidad. |
| infrastructure/ChallengeStore.java | Desafíos MFA y límites de intentos/IP en Redis | AuthService | IdentityException, Crypto.hash, StringRedisTemplate/Lua, propiedades y Java | infrastructure/security/ChallengeStore.java | Import en AuthService y Crypto si los movimientos son separados. Mantener @Component, TTL, claves y métodos públicos. |
| infrastructure/Crypto.java | AES-GCM, hashes, secretos aleatorios y TOTP | AuthService, ManagementService, ChallengeStore, TokenService, CryptoTest, IdentityIntegrationTest | JCA/JCE, Java, @Value | infrastructure/security/Crypto.java | Actualizar seis consumidores, incluidos los que hoy comparten paquete. No cambiar métodos públicos/estáticos ni propiedad de clave. @Component sigue detectable. |
| infrastructure/IdentityRepository.java | Adaptador JDBC IAM, mapeo de filas y outbox | AuthService, ManagementService, Bootstrap, OutboxPublisher, SecurityConfiguration, IdentityController, IdentityIntegrationTest | IdentityStore, Account, IdentityException, Api, CorrelationFilter, Spring JDBC | infrastructure/persistence/IdentityRepository.java | Imports de seis consumidores principales y FQCN del test. Mantener @Repository y jdbc() público; no sustituir JDBC por JPA ni modificar SQL. |
| infrastructure/LocalManagementSecurity.java | Cadena local para health/probes/Prometheus por puerto y loopback | Spring, perfil local | HttpSecurity, SecurityFilterChain, @Value, autorización | infrastructure/config/LocalManagementSecurity.java | Sin consumidores Java. Mantener @Profile("local"), @Order(0), matcher y método @Bean con visibilidad de paquete: Spring lo admite. Es archivo previo no seguido. |
| infrastructure/OutboxPublisher.java | Publicar outbox con confirmación, reintentos y topología AMQP | Spring scheduler; IdentityIntegrationTest lo construye | IdentityRepository, RabbitTemplate, Jackson, JDBC/transacciones | infrastructure/messaging/OutboxPublisher.java | Import de repositorio y dos FQCN de construcción en test. Conservar @ConditionalOnProperty, @Scheduled, @Transactional y nombres de beans; los métodos @Bean de paquete no requieren hacerse públicos. |
| infrastructure/SecurityConfiguration.java | PasswordEncoder, validación JWT, cadena HTTP y autorización basada en estado actual | Spring | AuthService.privileged, TokenService, IdentityRepository, Problems, Spring Security/Argon2 | infrastructure/config/SecurityConfiguration.java | Importar TokenService/IdentityRepository al separar paquetes y ajustar otros imports según lotes. Conservar @EnableMethodSecurity, nombres/visibilidad de métodos @Bean y reglas. |
| infrastructure/TokenService.java | Emisión RS256 y JWKS, carga/validación de claves | AuthService, SecurityConfiguration, IdentityController | Account, Crypto.hash, Nimbus, Spring JWT, JCA/Resource/@Value | infrastructure/security/TokenService.java | Actualizar tres consumidores; importar Crypto si aún está fuera y Account. Conservar campos públicos usados por configuración, claims y @Component. |
| presentation/CorrelationFilter.java | Correlation ID, cabeceras y contexto por hilo | IdentityRepository, Problems; cadena Servlet | Servlet, OncePerRequestFilter, UUID/ThreadLocal | presentation/rest/CorrelationFilter.java | Actualizar import de repositorio y añadirlo a Problems. Mantener current() público estático, override protegido, @Component y @Order(-200). |
| presentation/IdentityController.java | Adaptador HTTP, validación y guards de métodos | Spring MVC; clientes/tests HTTP | Api, AuthService, ManagementService, IdentityRepository, TokenService, IdentityException, AccessPolicy por SpEL; Servlet/Validation/JWT | presentation/rest/IdentityController.java | Ajustar imports conforme a lotes. Mantener @RestController, mappings, firmas, nombres de parámetros usados por SpEL y expresiones. No requiere registro MVC nuevo. |
| presentation/Problems.java | Traducción de excepciones y escritura uniforme de errores HTTP | SecurityConfiguration; Spring MVC advice | IdentityException, CorrelationFilter, MVC/DAO/Redis exceptions, Jackson/Servlet | presentation/error/Problems.java | Import en SecurityConfiguration y de CorrelationFilter. Conservar @RestControllerAdvice global, body/write públicos y handlers de paquete: no necesitan visibilidad pública. |

`IdentityApplication.java` permanece en `utp.siga.identity`: @SpringBootApplication mantiene el escaneo recursivo de los destinos y @EnableScheduling. No aparecen entidades JPA que deban reconfigurarse por estos movimientos. El POM no configura paquetes concretos para descubrimiento; no necesita cambios por renombrar estos paquetes. La compilación y el arranque de un lote futuro deberán confirmar esta conclusión estática.

### Dependencias arquitectónicas que no se solucionan moviendo archivos

- `IdentityStore → application.dto.Api`: la dependencia existía antes de mover Api y actualmente invierte `APP → DOM`. El lote 5 corregirá la dirección moviendo el puerto a Application sin cambiar firmas; separar después repositorio de dominio, consultas y eventos exigiría otro diseño.
- AuthService y ManagementService dependen de adaptadores concretos y usan JDBC a través del repositorio; IdentityController también consulta directamente el repositorio. Ponerlos en usecase/rest no invierte esas dependencias.
- `IdentityRepository → presentation.CorrelationFilter`: el adaptador de persistencia obtiene contexto de una clase HTTP. Una abstracción de contexto requeriría otra etapa.
- `IdentityException` transporta status HTTP dentro de domain, en tensión con UML-07. La carpeta exception solo organiza el tipo existente; no resuelve esa tensión.
- `SecurityConfiguration → AuthService.privileged` sigue la dirección SEC → APP representada por UML-07, pero comparte una política estática que conviene evaluar aparte. No extraerla durante movimientos.

No se rellenan command/query/domain.service con clases artificiales, no se introduce CQRS ni se divide Api para justificar carpetas vacías.

## Baseline funcional que deben preservar los lotes

### Pruebas disponibles y evidencia histórica

POM actual: Java 21, Spring Boot 3.5.16, JaCoCo 0.8.12; dependencias de pruebas Spring Boot/Security, **sin Testcontainers**. El directorio de build es parametrizable mediante `siga.build.directory` y su valor por defecto es target. Las integraciones dependen de PostgreSQL, Redis y RabbitMQ externos; no arrancan contenedores desde JUnit.

| Unidad de pruebas actual | Casos disponibles por inspección | Dependencias/condiciones |
|---|---:|---|
| CryptoTest | 2 | Vectores TOTP/replay y cifrado/autenticación con nonces; sin servicios externos |
| IdentityIntegrationTest | 9 | Contexto Spring + MockMvc, PostgreSQL dedicado, Redis DB 1, RabbitMQ; reset con comprobaciones antes de TRUNCATE/flush. Outbox periódico desactivado; publicación probada explícitamente |
| TestDatabaseSafetyTest | 15 invocaciones: 2 simples + 8 URLs + 5 conexiones alternativas | Contexto/mocks locales; valida rechazo antes de instanciar un bean que podría migrar/escribir |
| TestDatabaseSafety | Inicializador, no clase de casos JUnit | Guard previo a singleton/Flyway; conservar ubicación y registro existente |

Las nueve integraciones son: `userLifecycleValidationRbacAndOutbox`, `refreshRotationReuseLogoutAndJwtSignature`, `mfaIsSingleUseEncryptedAndBounded`, `loginLockPersistsAcrossFailedTransactionsAndIpRateLimit`, `rolesPermissionsAndAssignmentsAreAtomic`, `concurrentRefreshAllowsOneRotationAndRevokesFamilyOnReuse`, `outboxRetriesBrokerFailureAndPublishesCanonicalEnvelope`, `protectedRoleAndSelfDeactivationAreRejected`, `refreshCannotRenewStepUpAndExpiredChallengesAreRejected`.

Los cuatro archivos de tests permanecen en su paquete raíz. TestDatabaseSafetyTest usa validate() con visibilidad de paquete: mover solo uno rompería ese acceso. Mover el inicializador requeriría además cambiar su registro de recursos, fuera de este plan. Los tests consumidores solo cambiarían imports/FQCN cuando corresponda al lote.

**Evidencia histórica del contexto ya revisado**, sin releer ni ejecutar esos procedimientos en esta etapa:

- [VALIDACION_LOCAL_2026-09-26.md](VALIDACION_LOCAL_2026-09-26.md): 11 pruebas (2 Crypto + 9 integración) y 24 checks HTTP satisfactorios en la validación inicial.
- [CIERRE_LOCAL_2026-09-26.md](CIERRE_LOCAL_2026-09-26.md): 15 casos del guard y 9 integraciones satisfactorios; arranque negativo con URL operativa rechazado antes de Hikari/Flyway; instalación aislada V1–V4, health/probes/métricas y liveness/readiness ante caída de Redis verificados. Los 2 Crypto y 24 checks iniciales no se repitieron en ese cierre.
- **Ejecutado ahora para etapa 0: cero pruebas y cero comprobaciones HTTP.** Los 26 casos disponibles no se presentan como una ejecución conjunta actual. La coincidencia de fecha de los informes no convierte su resultado en una verificación de este plan.

### Contratos HTTP actuales, por lectura de controlador y DTO

Prefijo `/api/v1` salvo JWKS. Códigos de éxito; las validaciones y errores se conservan.

| Método/ruta | HTTP y representación |
|---|---|
| POST /auth/login | 200 LoginResult; también 200 cuando requiere MFA |
| POST /auth/mfa/enroll | 200 EnrollmentResult (secret, otpauthUri) |
| POST /auth/mfa/verify | 200 Tokens |
| POST /auth/refresh | 200 Tokens; refreshToken entra en JSON |
| POST /auth/logout | 204 |
| GET /.well-known/jwks.json (sin prefijo) | 200 JWKS |
| GET /users | 200 Page; page=0, size=20; tamaño admitido 1–100 |
| GET /users/{id} | 200 User |
| POST /users | 201 User |
| PATCH /users/{id} | 200 User |
| PUT /users/{id}/roles | 200 User |
| GET /roles | 200 lista Role |
| POST /roles | 201 Role |
| PUT /roles/{id}/permissions | 200 Role |
| GET /permissions | 200 lista id/code/description |

No existe PATCH /roles. Api mantiene sus 15 records, nombres JSON y restricciones; Tokens contiene accessToken, refreshToken, expiresIn y tokenType. Problems conserva type/title/status/code/detail/instance/correlationId. El filtro conserva X-Correlation-ID y Cache-Control: no-store.

Las diferencias ya identificadas en [PROPUESTAS_CONTRATO_Y_REFRESH.md](PROPUESTAS_CONTRATO_Y_REFRESH.md) siguen **pendientes**, no aprobadas: enrolamiento y listado de permisos frente a OpenAPI, PATCH de roles, respuesta del desafío MFA (implementación 200 frente a secuencia 202), campos MFA de V4 y cookie/CSRF Web. Este refactor no las elimina ni las legitima. No se implementan cookie Web ni BFF.

### Base de datos y seguridad

Baseline histórico de migraciones, no inspeccionado ni recalculado aquí: V1 esquema IAM; V2 tablas IAM; V3 semillas; V4 estado adicional MFA (`mfa_enrolled`, `last_totp_step`, `mfa_authenticated_at`). V1–V4 se aplicaron en la validación aislada anterior. No hay autorización para alterar sus archivos/checksums ni declarar aprobado el desajuste canónico de V4.

Comportamiento observado en el Java actual que debe mantenerse:

- Argon2 para contraseñas, JWT RS256 con issuer/audience/expiración; claves RSA validadas al cargar. Autorización reconsulta cuenta, permisos y familia de sesión vigente.
- MFA con secreto cifrado AES-GCM, TOTP de un uso, desafíos Redis acotados por tiempo/intentos y límite por IP. Persisten bloqueos tras fallo de login.
- Refresh rotativo con hash, bloqueo transaccional y revocación de familia ante reutilización; no renueva el instante original de MFA. Logout revoca familia. Conservar noRollbackFor donde asegura persistencia de medidas de seguridad.
- Métodos administrativos exigen permiso y step-up reciente de 300 s; no permitir autodesactivación ni alterar protecciones ADMIN mediante un movimiento.
- API stateless, CSRF desactivado en el modelo JSON actual. Esto no resuelve CSRF de una futura cookie Web.
- LocalManagementSecurity conserva perfil local, prioridad 0, selección por puerto de gestión y GET de health/probes/Prometheus limitado a loopback. Seguridad principal permite health básico y deniega las demás rutas fuera de API/allowlist. No se cambia política de otros entornos.
- TestDatabaseSafety exige bases de prueba permitidas, usuario dedicado y Redis DB 1; rechaza URLs/opciones alternativas antes de singletons. La protección adicional de permisos PostgreSQL pertenece a la validación histórica, no a una comprobación nueva de infraestructura.

## Plan propuesto de lotes

Cada fila mueve **una** unidad principal y actualiza únicamente los imports/FQCN afectados de la matriz. El orden permite comparar cada diff con el árbol actual. No reformatear archivos completos, cambiar nombres de beans, añadir nuevas abstracciones ni mezclar correcciones funcionales. Si aparece un requisito de visibilidad/configuración no previsto, detener ese lote para revisar su alcance.

| Lote | Unidad a mover | Riesgo principal / verificación selectiva futura |
|---:|---|---|
| 1 | AccessPolicy → infrastructure/security | Menor alcance: sin imports consumidores, bean nombrado. Compilar y ejecutar `IdentityIntegrationTest#refreshCannotRenewStepUpAndExpiredChallengesAreRejected` para comprobar resolución SpEL/step-up |
| 2 | Bootstrap → infrastructure/config | Runner transaccional; comprobar contexto y arranque con base de pruebas, nunca resembrar operativa |
| 3 | Api → application/dto | Compilación de sus consumidores y tests; caso de ciclo de usuarios para serialización/validación |
| 4 | Account → domain/model | Compilación y caso login/refresh para mapeo de cuenta |
| 5 | IdentityStore → application/port | Eliminar `domain → application.dto.Api` según UML-07; compilar implementación y consumidores sin alterar firmas |
| 6 | IdentityException → domain/exception | FQCN del test concurrente y manejo de errores; caso de bloqueo de login y de validación |
| 7 | Crypto → infrastructure/security | CryptoTest y compilación de todos los consumidores |
| 8 | ChallengeStore → infrastructure/security | Caso MFA y caso de límites/bloqueo con Redis de pruebas |
| 9 | TokenService → infrastructure/security | Caso refresh/logout/firma JWT; conservar JWKS y claims |
| 10 | IdentityRepository → infrastructure/persistence | Inyección, SQL/mapeos, transacciones y FQCN test; ciclo de usuarios y asignaciones atómicas |
| 11 | OutboxPublisher → infrastructure/messaging | Beans AMQP y FQCN test; caso outboxRetriesBrokerFailureAndPublishesCanonicalEnvelope |
| 12 | AuthService → application/usecase | Proxies transaccionales, permisos estáticos, consumidor test; casos refresh concurrente y bloqueo persistente |
| 13 | ManagementService → application/usecase | Proxies y asignaciones; caso rolesPermissionsAndAssignmentsAreAtomic |
| 14 | IdentityController → presentation/rest | MVC/method security; ciclo de usuarios y protecciones de roles/autodesactivación |
| 15 | CorrelationFilter → presentation/rest | Registro del filtro, orden y consumidor repositorio; comprobar cabecera/correlación en HTTP/outbox |
| 16 | Problems → presentation/error | Advice global y escritor de seguridad; comprobar errores 400/401/403 y correlación |
| 17 | SecurityConfiguration → infrastructure/config | Descubrimiento y nombres de beans; casos de JWT/RBAC/step-up; no retocar políticas |
| 18 | LocalManagementSecurity → infrastructure/config | Archivo previo no seguido; comprobar perfil local, puerto, loopback y endpoints permitidos/denegados en entorno aislado |

Estas verificaciones son propuestas para la ejecución futura de cada lote, no comandos ejecutados o resultados actuales. Usar el procedimiento local ya validado, base/rol exclusivos de tests y build aislado; no lanzar Maven con el target por defecto sobre este árbol. Antes de cada lote comprobar cambios concurrentes e índice; comparar el diff después. Al terminar los lotes autorizados, decidir una verificación conjunta según los componentes realmente afectados. Ningún lote incluye POM, migraciones, recursos, infraestructura, canonical ni cambios de lógica.

## Primer lote: archivos exactos

Solo un movimiento con cambio de declaración package:

1. Origen: `src/main/java/utp/siga/identity/infrastructure/AccessPolicy.java`.
2. Destino: `src/main/java/utp/siga/identity/infrastructure/security/AccessPolicy.java`.

El package pasaría de `utp.siga.identity.infrastructure` a `utp.siga.identity.infrastructure.security`. `@Component("accessPolicy")`, `recent(Authentication)`, la comparación de 300 segundos y todos los imports existentes permanecerían iguales. **IdentityController.java, SecurityConfiguration.java, tests y pom.xml no necesitan edición en este primer lote.** No se ha ejecutado el movimiento. La etapa 0 termina con este documento.
