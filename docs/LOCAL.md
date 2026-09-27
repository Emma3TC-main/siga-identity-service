# Identity local — guía única para el equipo

Procedimiento para **instalaciones nuevas en Windows/PowerShell**, sin PostgreSQL instalado y sin GCP. El entorno anterior `siga-local` se conserva: no se migra ni se recrea al ejecutar esta guía.

## 1. Requisitos y carpetas

- JDK 21 en PATH y, si está definido, en JAVA_HOME (verificado: Temurin 21.0.12.1); Maven Wrapper incluido (3.9.16). No hace falta instalar Maven.
- Docker Desktop iniciado, motor Linux y Compose v2 (verificados: Engine 28.0.1 / Compose 2.33.1). Internet para descargar imágenes/dependencias si no están en caché.
- Repositorios hermanos, en **la ruta elegida por cada integrante**:

```text
SIGA/
  siga-identity-service/
  siga-infrastructure/
  siga-documentation/
```

Abrir PowerShell **en siga-identity-service**. Los comandos siguientes usan rutas relativas; no dependen de C:\Users\PC. Si la política local bloquea scripts:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned -Force
java --version
docker compose version
```

RemoteSigned afecta solo esta sesión; una política corporativa puede impedirlo y requiere intervención del administrador, no cambiar políticas permanentes.

## 2. Primera instalación

```powershell
$instance = 'team'
$offset = 0
./scripts/Initialize-Local.ps1 -Instance $instance -PortOffset $offset
./scripts/Start-Infrastructure.ps1 -Instance $instance
./scripts/Start-Local.ps1 -Instance $instance -Background
./scripts/Test-LocalHealth.ps1 -Instance $instance
```

El inicializador genera secretos nuevos y claves RSA/AES; **no copiar contraseñas de otra persona**. Referencias sin secretos: Identity `.env.example` e Infrastructure `compose/.env.team.example`. Los valores reales se generan en:

- Infrastructure `compose/.env.<instance>`: proyecto, puertos y contraseñas de PostgreSQL, IAM, pruebas, RabbitMQ y MinIO.
- Identity `.local/<instance>/.env` y `.local/<instance>/.secrets/`: bootstrap, cifrado MFA y claves RSA.
- Salida de compilación: `.local/<instance>/target`; copia ejecutable inmutable en `.local/<instance>/runtime`; PID, ruta del JAR y logs en `.local/<instance>/identity.*`.

Esos archivos están ignorados por Git; preservarlos junto a sus volúmenes. El archivo de Identity contiene rutas de claves de **esa instalación** y no debe copiarse a otro equipo. Repetir Init conserva lo existente, incluidos los puertos; una configuración incompleta falla sin regenerar secretos sobre datos.

El proyecto Docker será `siga-team`, con nombres y volúmenes propios. PostgreSQL crea base `siga`, rol `siga_iam`, base `siga_identity_local_test` y rol `siga_iam_test`; este último **no tiene CONNECT sobre siga**. Flyway de Identity crea/posee exclusivamente `iam` y aplica V1–V4. No ejecutar el SQL consolidado de la documentación.

Start espera salud de PostgreSQL/Redis/RabbitMQ y verifica ambos logins. Start-Local compila sin repetir pruebas, copia el JAR a `runtime` con su SHA-256 en el nombre, inicia esa copia y espera readiness (hasta 45 s). Un build posterior puede reemplazar archivos en `target` sin modificar el artefacto abierto por la JVM. No iniciar dos procesos para la misma instancia.

## 3. Direcciones y servicios compartidos

Todos los puertos publicados y ambos listeners de Identity se limitan a **127.0.0.1**.

| Servicio | Puerto con offset 0 | Ensayo comprobado con offset 20000 |
|---|---|---|
| PostgreSQL | 15432 | 35432 |
| Redis | 6379 | 26379 |
| RabbitMQ / UI | 5672 / 15672 | 25672 / 35672 |
| MinIO / UI (opcional) | 9000 / 9001 | 29000 / 29001 |
| Identity API | 8081 | 28081 |
| Management | 9081 | 29081 |

API: `http://127.0.0.1:8081/api/v1`. Health/probes: `http://127.0.0.1:9081/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`. Métricas: `http://127.0.0.1:9081/actuator/prometheus`. Sumar el offset a esos puertos si se configuró uno.

Probes sin detalles internos. Prometheus puede consultarlas **desde el host Windows**, no desde otro equipo/contenedor; no abrir management a LAN para resolverlo. Liveness mide el proceso; readiness incluye DB/Redis. Las métricas están denegadas en el puerto API. La política de otros entornos está pendiente.

| Trabajo del integrante | Qué ejecutar |
|---|---|
| Backend Identity | Identity + PostgreSQL/Redis/RabbitMQ de esta guía; MinIO no es dependencia de IAM |
| Backend de otros componentes | Reutilizar una sola infraestructura por equipo; arrancar su servicio según su propio repo. Sus schemas/roles/migraciones no se provisionan con esta guía |
| Evidence/almacenamiento | Además necesita MinIO; activar como abajo. La integración de Evidence no se valida aquí |
| Web/Mobile | Identity y Gateway/servicios que consuma su flujo; arrancar cliente según su repo. No levantar otro stack de dependencias por cada componente |
| QA de Identity | La base dedicada y Redis DB 1 de su instancia; nunca ejecutar tests contra un entorno operativo compartido |
| Observabilidad | Scraper en el mismo host para este perfil; integración de Prometheus en contenedor pendiente |

“Compartido” significa una instancia por **equipo de desarrollo**, entre sus componentes. Los bindings locales no convierten el portátil de un integrante en servidor para todos. Los logins de otros bounded contexts deben provisionarse con su ownership; no usar `siga_iam` para ellos.

MinIO opcional y estado del proyecto:

```powershell
./scripts/Start-Infrastructure.ps1 -Instance $instance -WithMinio
../siga-infrastructure/scripts/Team-Local.ps1 -Action Config -Instance $instance
../siga-infrastructure/scripts/Team-Local.ps1 -Action Status -Instance $instance
```

`compose.team.yml` fija **las cuatro imágenes por digest**, no latest: PostgreSQL 17.11, Redis 7.4.11, RabbitMQ 4.3.5 y MinIO RELEASE.2025-09-07T16-13-09Z fueron las versiones verificadas. Los digests completos están en ese Compose. La configuración histórica conserva sus etiquetas; no es la receta reproducible para nuevas instalaciones.

## 4. Arranque habitual y parada

En cada terminal nueva establecer `$instance` con el mismo nombre. No ejecutar Init ni cambiar contraseñas para un arranque habitual.

```powershell
$instance = 'team'
./scripts/Start-Infrastructure.ps1 -Instance $instance
./scripts/Start-Local.ps1 -Instance $instance -Background
./scripts/Test-LocalHealth.ps1 -Instance $instance
```

Parar primero Identity y después las dependencias, conservando datos:

```powershell
./scripts/Stop-Local.ps1 -Instance $instance
../siga-infrastructure/scripts/Team-Local.ps1 -Action Stop -Instance $instance
```

Stop-Local comprueba que el PID corresponde al JAR de esa instancia. Stop de Compose incluye MinIO si se arrancó. No usar down -v, prune ni borrar volúmenes. Para volver a iniciar MinIO añadir `-WithMinio` al arranque de infraestructura.

## 5. Pruebas seguras

```powershell
./scripts/Test-Local.ps1 -Instance $instance -Tests TestDatabaseSafetyTest
./scripts/Test-Local.ps1 -Instance $instance -Tests IdentityIntegrationTest
```

Estas son las verificaciones afectadas y ejecutadas: 15 casos de protección y nueve de integración. Las dos pruebas criptográficas y los 24 checks HTTP históricos no se repiten por configuración. Sin `-Tests`, el script permite ejecutar toda la suite cuando sea necesario.

Las pruebas usan **servicios externos de Compose**, no Testcontainers. Su usuario es `siga_iam_test`, base dedicada y Redis DB 1 reservada: los fixtures truncan tablas de pruebas y limpian esa DB Redis. El guard del classpath de tests valida URL/base/usuario y bloquea conexiones alternativas **antes de crear DataSource/Flyway u otros singletons**; no depende de BeforeEach ni del script PowerShell. Las URLs con parámetros y los overrides de conexión se rechazan. PostgreSQL añade separación de permisos en las nuevas instancias.

Salida de tests y empaquetado en `.local/<instance>/test-build`, separada del JAR activo; reportes en `surefire-reports/`. No ejecutar Maven directamente contra `siga` ni usar usuarios administradores para tests. CI conserva servicios externos propios y usa ahora el login de pruebas; su ejecución remota no está validada.

## 6. Conflictos y errores frecuentes

- **Puerto ocupado:** Init falla antes de guardar secretos. Elegir una instancia nueva y un offset libre (se comprobó `repro-20260926` / `20000` coexistiendo con el entorno anterior). `PortOffset` solo se aplica al crear configuración, no mueve datos existentes.
- **Docker no responde:** iniciar Desktop en modo Linux. No instalar otro PostgreSQL ni recuperar contraseñas de Windows.
- **Volumen existente sin env / password authentication failed:** recuperar el archivo correspondiente. POSTGRES_PASSWORD no cambia un volumen inicializado. No borrar ni reinicializar.
- **Claves incompletas:** recuperar el par RSA y AES correspondiente; regenerarlas rompería sesiones/MFA existentes.
- **PID aún activo:** Start-Local no inicia otro proceso para la misma instancia. Usar Stop-Local antes de volver a arrancarla. Builds posteriores escriben en `target`, las pruebas en `test-build` y el proceso conserva su copia inmutable en `runtime`.
- **Readiness timeout:** revisar `.local/<instance>/identity.stdout.log` y `identity.stderr.log` de forma privada; comprobar Status de Compose. Los logs de tests pueden contener datos de autenticación: no publicarlos.
- **TEST_DATABASE_GUARD:** usar el script y la instancia correcta. No desactivar el guard para forzar una prueba.
- **Métricas 401 en 8081:** usar el puerto management 9081 (más offset), desde el host; otros actuators siguen bloqueados.

Comandos comprobados en esta máquina con `$instance='repro-20260926'` y `$offset=20000`, incluyendo primera instalación, repetición de Init, parada/rearranque, salud, pruebas y persistencia. Se usaron volúmenes/credenciales nuevos, pero las imágenes y dependencias Maven ya estaban en caché; no se afirma una instalación completa de Windows/Docker ni una descarga sin caché. En otro equipo los nombres/ruta y disponibilidad de puertos se adaptan.

El ensayo se dejó detenido al finalizar, con volúmenes y credenciales conservados. Puede reanudarse usando el bloque de arranque habitual con `$instance='repro-20260926'`.

## Entorno anterior y decisiones pendientes

El `siga-local` anterior conserva sus volúmenes, datos, credenciales y claves. Al reanudarlo, su proceso se recuperó desde una copia de runtime separada del antiguo `.local/target` y expone management en 9081; no se migraron ni reinicializaron sus volúmenes. Los scripts sin `-Instance` conservan el arranque anterior en primer plano, **pero no son el procedimiento para un integrante nuevo**. No adoptar las credenciales del entorno anterior en el nuevo.

[V4, contratos y refresh web: propuesta no aprobada](PROPUESTAS_CONTRATO_Y_REFRESH.md). [Evidencia y archivos de esta continuación](CIERRE_LOCAL_2026-09-26.md). No se ha implementado login Web, BFF ni cookies; Gateway/clientes/Audit y observabilidad en contenedores siguen fuera de esta validación.
