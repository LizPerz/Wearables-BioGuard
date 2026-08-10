# Distribución temporal por APK

## Reglas de release

1. Wearable y Móvil deben compilarse con el mismo keystore de release y mantener `applicationId = "com.bioguard.movil"` para Wear OS Data Layer.
2. El secreto `BIOGUARD_KEYSTORE_BASE64` contiene el `.jks` compartido codificado en base64. Alias y contraseñas se guardan exclusivamente como GitHub Secrets.
3. Cada build de `main` genera APK firmado, AAB y `SHA256SUMS.txt`.
4. El wearable solo se comunica con la aplicación móvil mediante Bluetooth/Data Layer; no debe depender de endpoints de Internet.
5. No distribuir builds debug ni APK cuya firma no haya sido validada por `apksigner`.

## Configuración de GitHub Secrets

```text
BIOGUARD_KEYSTORE_BASE64
BIOGUARD_STORE_PASSWORD
BIOGUARD_KEY_ALIAS
BIOGUARD_KEY_PASSWORD
```

El `.jks` debe ser exactamente el mismo usado por Móvil. El valor base64 se genera fuera del repositorio:

```bash
base64 -w 0 bioguard-release.jks
```

## Instalación y actualización

- Verificar el APK contra `SHA256SUMS.txt`.
- Instalarlo en el reloj vinculado al teléfono.
- Instalar actualizaciones sobre la app existente; no desinstalar, porque eso elimina datos y estado local.
- El pipeline incrementa `versionCode` con `github.run_number`.
- Conservar el AAB para la futura migración a Google Play App Signing.
