# Security Policy — BioGuard WearOS

## Versiones soportadas

La seguridad se aplica a la rama `main` y a las releases firmadas generadas desde ella.
No se mantienen versiones antiguas de la aplicación: los usuarios deben actualizar a la
última release.

## Reportar una vulnerabilidad

Por favor, **no** abras un issue público para reportar problemas de seguridad.

Usa una de estas vías:

1. **GitHub Security Advisory (recomendado):** `Security` → `Report a vulnerability`
   en el repositorio.
2. **Correo privado del mantenedor:** indicado en el perfil del propietario del repo.

### Qué incluir en el reporte

- Tipo de vulnerabilidad (divulgación de datos, MITM, inyección, etc.).
- Endpoint / componente afectado (API, almacenamiento local, CI/CD...).
- Pasos para reproducirla y, si es posible, un PoC.
- Impacto estimado (datos expuestos, alcance, condiciones).

### SLA

| Respuesta inicial | Resolución (según severidad) |
|-------------------|------------------------------|
| 72 horas | Crítica/Alta: 7 días · Media: 30 días · Baja: 90 días |

Los detalles del reporte se mantienen privados hasta que se publique una corrección
(divulgación coordinada). El reportero será reconocido si lo desea.

## Superficie de seguridad y alcance

Dentro del alcance:

- Aplicación Wear OS (lógica de datos, red, almacenamiento local).
- Pipeline de CI/CD de este repositorio (workflows de `.github/`).

Fuera del alcance (gestionados por terceros):

- El backend `https://bioguard-api-lkvnq.ondigitalocean.app` (infraestructura
  de DigitalOcean / plataforma desplegada por el equipo del backend).
- Dependencias de terceros: reporta CVEs vía la base de datos correspondiente;
  aquí se mitigan actualizando dependencias o añadiendo supresiones documentadas.

## Medidas de seguridad del proyecto (DevSecOps)

| Control | Implementación |
|---------|----------------|
| SAST (código) | CodeQL (`codeql.yml`) + Android Lint en CI |
| SCA (dependencias) | OWASP Dependency-Check con NVD API (`ci.yml`, `build.gradle.kts`) |
| Secret scanning | Gitleaks en CI (`security-scan.yml`) + secret scanning nativo de GitHub |
| Supply-chain | OSSF Scorecard (`scorecard.yml`) + Dependabot |
| Tráfico de red | Solo HTTPS; cleartext bloqueado (`network_security_config.xml`) |
| TLS | Certificate pinning del backend en `NetworkModule.kt` |
| Datos en repositorio | Tokens JWT cifrados AES/GCM con clave en Android Keystore; BD Room cifrada con SQLCipher |
| Backups | `allowBackup=false` + `data_extraction_rules.xml` bloquea exportación |
| Firma de APK | Credenciales fuera del repo (`keystore.properties` gitignored o secrets de GitHub) |
