@echo off
echo Construyendo APK de BioGuard...
call gradlew.bat assembleDebug

echo Instalando en el reloj...
"C:\Users\perez\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

echo ¡Listo! La app se ha actualizado en el reloj.