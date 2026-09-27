# Validación de IAM

## Procedimiento vigente

Seguir [LOCAL.md](LOCAL.md). Los tests usan PostgreSQL/Redis/RabbitMQ externos de Compose, no Testcontainers; se retiraron dependencias Testcontainers no utilizadas. Las nuevas instancias crean `siga_iam_test` sin CONNECT a la base operativa. El inicializador global del classpath de tests rechaza URL/usuario/overrides inseguros antes de instanciar DataSource/Flyway, además de la comprobación defensiva de los fixtures.

Pruebas afectadas ejecutadas en esta continuación: 15 casos de `TestDatabaseSafetyTest`, nueve de `IdentityIntegrationTest`, un arranque de integración con URL operativa rechazado antes de conexión y comprobaciones HTTP específicas de management. No se repitieron los 24 checks históricos ni las dos pruebas criptográficas. Evidencia y límites en [CIERRE_LOCAL_2026-09-26.md](CIERRE_LOCAL_2026-09-26.md). La salida vigente es `.local/<instance>/test-build`; los resultados de abajo siguen siendo históricos.

## Registro histórico

> Los resultados siguientes son históricos (24/09/2026). La ejecución nueva del 26/09/2026 y sus límites están en [VALIDACION_LOCAL_2026-09-26.md](VALIDACION_LOCAL_2026-09-26.md); comandos vigentes en [LOCAL.md](LOCAL.md).

Ejecutada localmente el 24 de septiembre de 2026, con Java 21, PostgreSQL 17.11, Redis 7 y RabbitMQ 4 en Docker/WSL. No se usó H2 para sustituir PostgreSQL.

## Resultados

- 11 tests JUnit: 11 correctos, 0 fallos, 0 errores, 0 omitidos.
- 94,0% de cobertura de líneas JaCoCo de la suite local.
- 24 comprobaciones HTTP reales: todas correctas; firma RSA del access token verificada con la clave pública JWKS.
- OpenAPI validado con openapi-spec-validator.
- JAR ejecutable empaquetado correctamente con Java 21.
- Eventos generados por el flujo HTTP: 6 publicados en RabbitMQ.

## Matriz ejecutada

| Prueba | Evidencia cubierta |
|---|---|
| rfc6238Sha1VectorAndReplay | Vector RFC 6238, ventana temporal y rechazo del mismo paso TOTP |
| encryptionAuthenticatesAndUsesRandomNonces | AES-GCM, nonce aleatorio y detección de manipulación |
| userLifecycleValidationRbacAndOutbox | Crear/listar/consultar/actualizar/desactivar, Argon2id, validación, 401/403/404/409, outbox |
| refreshRotationReuseLogoutAndJwtSignature | Rotación, reutilización, revocación de familia, logout, JWT manipulado y JWKS sin clave privada |
| mfaIsSingleUseEncryptedAndBounded | Enrolamiento, secreto cifrado, MFA repetido, rechazo de reenrolamiento y límite OTP |
| loginLockPersistsAcrossFailedTransactionsAndIpRateLimit | 5 fallos, bloqueo persistido, desbloqueo por tiempo y límite IP |
| rolesPermissionsAndAssignmentsAreAtomic | Roles/permisos, rollback por UUID inexistente, asignaciones y MFA obligatorio |
| concurrentRefreshAllowsOneRotationAndRevokesFamilyOnReuse | Dos refresh simultáneos: uno rota y el segundo revoca la familia |
| outboxRetriesBrokerFailureAndPublishesCanonicalEnvelope | Falla simulada del transporte, outbox permanece pendiente, posterior publicación real a RabbitMQ |
| protectedRoleAndSelfDeactivationAreRejected | Rol ADMIN protegido y autodesactivación rechazada |
| refreshCannotRenewStepUpAndExpiredChallengesAreRejected | Refresh conserva antigüedad de MFA; escritura con MFA antiguo recibe 403; challenge expirado recibe 401 |

## Reproducibilidad

`scripts/Test-Local.ps1` y `.github/workflows/ci.yml` ejecutan la suite. La base debe terminar en `_test`; la suite trunca únicamente las tablas IAM de esa base y limpia Redis DB 1. Redis DB 0 y la base `siga` pertenecen al servicio local.

`scripts/smoke.py` recorre todos los métodos del contrato original, además de salud/JWKS/permisos/enrolamiento. Crea un usuario de prueba y un rol sin privilegios administrativos; desactiva el usuario y cierra la sesión al finalizar. El rol queda como referencia de la prueba. El informe HTTP contiene rutas/estados y no incluye credenciales ni tokens.

Thunder Client quedó instalado y se prepararon colección, entorno vacío y guion. La ejecución dentro de Thunder Client no está verificada: importación y CLI son funcionalidades con licencia. Estas pruebas no se etiquetan como ejecuciones de Thunder Client.

El alcance comprobado es el microservicio IAM. La integración de Gateway, clientes web/móvil y consumidor Audit debe verificarse por sus respectivos repositorios; no se afirma una prueba integral de todo SIGA.
