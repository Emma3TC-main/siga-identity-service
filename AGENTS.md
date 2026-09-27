# Identity: instrucciones locales

- Fuente de verdad: `../siga-documentation/SIGA_Documentacion_Tecnica_Final_v1.2/`.
- Prioridad: manual técnico y ADR aprobados, `database/logical_model.md`, `database/physical_model.sql`, `database/dictionary.md`, `diagramas/**/*.puml`, `api/*.yaml`; reglas funcionales en `especificaciones/` y `trazabilidad/`.
- Excluir `diagramas_render/` y `out/`. Las copias de `docs/` y `docs/DECISIONES.md` no sustituyen decisiones canónicas. Registrar discrepancias; no cambiar contratos para acomodar código.
- Identity es propietario de `iam` y Flyway. No aplicar el SQL consolidado ni escribir otros schemas; no editar migraciones aplicadas.
- Instalaciones nuevas: `../siga-infrastructure/compose/compose.team.yml` mediante `Initialize-Local.ps1` y parámetros `-Instance`. `compose.local.yml` conserva el entorno previo. Seguir la guía única `docs/LOCAL.md`; no iniciar el Compose autónomo de Identity en paralelo.
- Antes de cambios, revisar status, ramas y reflog. Preservar cambios locales, secretos y volúmenes. No reset/clean/rebase/merge/push ni cloud en tareas locales.
- Comprobar contrato Identity y pruebas aplicables con Java 21. Suite sobre `siga_identity_local_test` (CI: `siga_identity_test`), login `siga_iam_test` y Redis DB 1 dedicada: borra datos de prueba. Conservar el guard global del classpath de tests, ejecutado antes de Flyway/conexiones. No describir los servicios Compose externos como Testcontainers.
- Compilar en `.local/<instance>/target` para preservar `target/` versionado y evitar bloquear JAR de otra instancia. No añadir secretos, `.local/` ni `target/` al índice Git.
- Informar verificaciones ejecutadas/fallidas/no ejecutables; no inferir CI verde a partir de un merge.
