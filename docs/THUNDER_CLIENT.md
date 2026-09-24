# Guion manual Thunder Client

1. Abrir Thunder Client en VS Code. Usar New Request si no se dispone de importación con licencia.
2. Base URL `http://localhost:8081/api/v1`, JSON, Content-Type application/json.
3. Copiar password de IAM_BOOTSTRAP_PASSWORD desde .env al campo local, sin compartirlo.
4. Login, enrolamiento inicial y MFA según README. Copiar challengeId, código actual y después accessToken/refreshToken a variables del entorno.
5. Ejecutar los requests en el orden de la colección. Después de crear usuario y rol, copiar id a userId/roleId. Después de refresh, sustituir ambos tokens. No ejecutar enrolamiento si el usuario ya lo confirmó.
6. Los ejemplos de cuenta/rol deben usar nombres nuevos si se repite la colección. testPassword debe tener al menos 12 caracteres.
7. Verificar códigos esperados en los tests de cada solicitud; los mensajes de error deben ser application/problem+json e incluir correlationId.

Escenarios negativos: contraseña inválida→401; recurso protegido sin token→401; usuario sin permiso→403; usuario duplicado→409; MFA repetido→401; 5 fallos de login bloquean durante 15 min; refresh reutilizado invalida toda su familia. Logout invalida la familia de la sesión actual.

Para MFA del entorno usado por smoke.py, el secreto queda únicamente en .local/demo-mfa.json. Añadirlo al autenticador para obtener códigos actuales. Esperar al siguiente intervalo de 30 s si el mismo código acaba de usarse en las pruebas.

La ejecución verificada automáticamente usa JUnit y scripts/smoke.py. No se presenta como evidencia de pruebas ejecutadas dentro de Thunder Client.
