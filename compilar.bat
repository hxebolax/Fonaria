@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Fonaria - Compilar e instalar
cd /d "%~dp0"

set "RAIZ=%~dp0"
set "RAIZ=%RAIZ:~0,-1%"
set "JAVA_HOME=%RAIZ%\entorno\jdk"
set "ANDROID_HOME=%RAIZ%\entorno\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "ANDROID_AVD_HOME=%RAIZ%\entorno\avd"
set "GRADLE_USER_HOME=%RAIZ%\entorno\gradle-home"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"
set "GRADLE=%RAIZ%\entorno\gradle\bin\gradle.bat"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
set "EMULADOR=%ANDROID_HOME%\emulator\emulator.exe"
set "APK=%RAIZ%\Fonaria.apk"
set "PAQUETE=com.xebolax.fonaria"
set "PS=powershell -NoProfile -ExecutionPolicy Bypass -File"

rem Uso sin menu: compilar.bat compilar ^| instalar ^| todo ^| nativo ^| modelo ^| emulador ^| pruebas ^| publicar ^| entorno
if /i "%~1"=="compilar" goto :compilar_solo
if /i "%~1"=="instalar" goto :instalar_solo
if /i "%~1"=="todo" goto :todo
if /i "%~1"=="nativo" goto :nativo_solo
if /i "%~1"=="modelo" goto :modelo_solo
if /i "%~1"=="emulador" goto :emulador_solo
if /i "%~1"=="pruebas" goto :pruebas_solo
if /i "%~1"=="publicar" goto :publicar_solo
if /i "%~1"=="entorno" goto :entorno_solo

:menu
echo.
echo ============================================
echo   Fonaria para Android
echo ============================================
echo   1. Compilar el APK
echo   2. Compilar e instalar en el movil conectado por USB
echo   3. Instalar el APK ya compilado
echo   4. Ver moviles conectados
echo   5. Abrir la maquina virtual de pruebas (emulador)
echo   6. Pasar las pruebas automaticas
echo   7. Compilar el motor nativo (Rust)
echo   8. Compilar y publicar la version (servidor y GitHub)
echo   9. Preparar o reparar el entorno (JDK, SDK, NDK, Gradle, emulador, modelo)
echo   0. Salir
echo.
set "OPCION="
set /p "OPCION=Elige una opcion (0-9): "
if "%OPCION%"=="1" call :compilar & goto :menu
if "%OPCION%"=="2" call :todo_menu & goto :menu
if "%OPCION%"=="3" call :instalar & goto :menu
if "%OPCION%"=="4" call :dispositivos & goto :menu
if "%OPCION%"=="5" call :emulador & goto :menu
if "%OPCION%"=="6" call :pruebas & goto :menu
if "%OPCION%"=="7" call :nativo & goto :menu
if "%OPCION%"=="8" call :publicar & goto :menu
if "%OPCION%"=="9" call :preparar & goto :menu
if "%OPCION%"=="0" exit /b 0
echo Opcion no valida.
goto :menu

:todo_menu
call :compilar || exit /b 1
call :instalar
exit /b %errorlevel%

:compilar_solo
call :compilar
exit /b %errorlevel%
:instalar_solo
call :instalar
exit /b %errorlevel%
:todo
call :compilar || exit /b 1
call :instalar
exit /b %errorlevel%
:nativo_solo
call :nativo
exit /b %errorlevel%
:modelo_solo
call :modelo
exit /b %errorlevel%
:emulador_solo
call :emulador
exit /b %errorlevel%
:pruebas_solo
call :pruebas
exit /b %errorlevel%
:publicar_solo
call :publicar
exit /b %errorlevel%
:entorno_solo
call :preparar
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
:entorno
if exist "%JAVA_HOME%\bin\java.exe" if exist "%GRADLE%" if exist "%ANDROID_HOME%\platforms\android-35" if exist "%ANDROID_HOME%\ndk" exit /b 0
echo.
echo [Entorno] Falta el entorno de compilacion. Se descargara en la carpeta "entorno":
echo           JDK 17, Android SDK y NDK, Gradle y el emulador (unos 5 GB).
echo           Al instalar el Android SDK se aceptan sus licencias de Google.
:preparar
%PS% "%RAIZ%\herramientas\preparar_entorno.ps1"
if errorlevel 1 (
	echo [ERROR] No se pudo preparar el entorno.
	exit /b 1
)
call :modelo
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
rem El modelo y las voces espanolas que van en el APK (no estan en git: pesan unos 100 MB)
:modelo
if exist "%RAIZ%\modelo\apk\modelo\model.q8.gguf" if exist "%RAIZ%\modelo\apk\voces" exit /b 0
echo.
echo [Modelo] Preparando el modelo y las voces del APK...
%PS% "%RAIZ%\herramientas\preparar_modelo.ps1"
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
rem El motor nativo (Rust): solo hace falta compilarlo si cambia la carpeta "nativo"
:nativo
call :entorno || exit /b 1
echo.
echo [Nativo] Compilando el motor de voz para arm64, armv7 y x86_64. Tarda varios minutos...
%PS% "%RAIZ%\herramientas\compilar_nativo.ps1"
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
:firma
if exist "%RAIZ%\firma\firma.properties" exit /b 0
echo.
echo [Firma] Creando la firma propia de la aplicacion (firma\fonaria.jks)...
echo         Guardala: sin ella no podras actualizar la app sin desinstalarla.
if not exist "%RAIZ%\firma" mkdir "%RAIZ%\firma"
for /f %%c in ('powershell -NoProfile -Command "-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 24 | ForEach-Object {[char]$_})"') do set "CLAVE=%%c"
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -v -keystore "%RAIZ%\firma\fonaria.jks" -alias fonaria -keyalg RSA -keysize 2048 -validity 10000 -storepass !CLAVE! -keypass !CLAVE! -dname "CN=Fonaria, O=XeBoLaX, C=ES" >nul 2>&1
if errorlevel 1 (
	echo [ERROR] No se pudo crear la firma.
	exit /b 1
)
(
	echo archivo=firma/fonaria.jks
	echo alias=fonaria
	echo clave=!CLAVE!
) > "%RAIZ%\firma\firma.properties"
exit /b 0

rem ---------------------------------------------------------------------------
:compilar
call :entorno || exit /b 1
call :modelo || exit /b 1
if not exist "%RAIZ%\app\src\main\jniLibs\arm64-v8a\libfonaria.so" (
	call :nativo || exit /b 1
)
call :firma || exit /b 1
echo.
echo [Compilar] Compilando el APK. La primera vez tarda varios minutos...
call "%GRADLE%" --no-daemon -p "%RAIZ%" assembleRelease
if errorlevel 1 (
	echo.
	echo [ERROR] La compilacion ha fallado. Revisa los mensajes de arriba.
	exit /b 1
)
copy /y "%RAIZ%\app\build\outputs\apk\release\app-release.apk" "%APK%" >nul
echo.
echo [OK] APK listo: %APK%
exit /b 0

rem ---------------------------------------------------------------------------
:pruebas
call :entorno || exit /b 1
echo.
echo [Pruebas] Pruebas en el PC (diccionario, frases, deletreo, versiones)...
call "%GRADLE%" --no-daemon -p "%RAIZ%" testDebugUnitTest
if errorlevel 1 (
	echo [ERROR] Alguna prueba ha fallado. El informe esta en app\build\reports\tests.
	exit /b 1
)
echo [OK] Pruebas en el PC superadas.
rem Las pruebas del motor hablan de verdad: solo en el emulador (nunca se toca un movil real)
"%ADB%" start-server >nul 2>&1
set "EMU="
for /f "tokens=1" %%a in ('call "%ADB%" devices ^| findstr /b "emulator-"') do set "EMU=%%a"
if not defined EMU (
	echo [AVISO] Sin el emulador abierto no se pasan las pruebas del motor ^(opcion 5^).
	exit /b 0
)
set "ANDROID_SERIAL=%EMU%"
echo.
echo [Pruebas] Pruebas del motor en el emulador %EMU% (voces, idiomas, deletreo)...
call "%GRADLE%" --no-daemon -p "%RAIZ%" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=%PAQUETE%.MotorTest
if errorlevel 1 (
	echo [ERROR] Alguna prueba del motor ha fallado. Informe: app\build\reports\androidTests\connected
	exit /b 1
)
echo [OK] Todas las pruebas han pasado.
exit /b 0

rem ---------------------------------------------------------------------------
:dispositivos
if not exist "%ADB%" call :entorno || exit /b 1
"%ADB%" start-server >nul 2>&1
echo.
"%ADB%" devices -l
exit /b 0

rem ---------------------------------------------------------------------------
:emulador
if not exist "%EMULADOR%" call :preparar || exit /b 1
if not exist "%ANDROID_AVD_HOME%\pruebas.ini" call :preparar || exit /b 1
"%ADB%" start-server >nul 2>&1
for /f "tokens=1" %%a in ('call "%ADB%" devices ^| findstr /b "emulator-"') do (
	echo [Emulador] La maquina virtual ya esta abierta ^(%%a^).
	exit /b 0
)
echo.
echo [Emulador] Abriendo la maquina virtual "pruebas" (Android 15)...
start "Emulador Fonaria" "%EMULADOR%" -avd pruebas -no-snapshot-save -no-boot-anim
exit /b 0

rem ---------------------------------------------------------------------------
:instalar
if not exist "%APK%" (
	echo [ERROR] No existe %APK%. Compila primero.
	exit /b 1
)
if not exist "%ADB%" call :entorno || exit /b 1
"%ADB%" start-server >nul 2>&1
set "DISPOSITIVOS=0"
for /f "skip=1 tokens=1,2" %%a in ('call "%ADB%" devices') do (
	if "%%b"=="device" set /a DISPOSITIVOS+=1
	if "%%b"=="unauthorized" echo [AVISO] El movil %%a no ha autorizado la depuracion USB: acepta el aviso en su pantalla.
)
if "%DISPOSITIVOS%"=="0" (
	echo.
	echo [AVISO] No hay ningun movil conectado y autorizado.
	echo         1. Activa las Opciones de desarrollador y la Depuracion USB.
	echo         2. Conecta el movil por USB y acepta el aviso de depuracion.
	echo         Tambien puedes copiar Fonaria.apk al movil e instalarlo a mano.
	exit /b 1
)
echo.
echo [Instalar] Instalando Fonaria en el movil...
"%ADB%" -d install -r "%APK%" > "%TEMP%\fonaria_adb.txt" 2>&1
type "%TEMP%\fonaria_adb.txt"
findstr /c:"Success" "%TEMP%\fonaria_adb.txt" >nul
if not errorlevel 1 (
	echo [OK] Fonaria instalada. Abriendola...
	"%ADB%" -d shell monkey -p %PAQUETE% -c android.intent.category.LAUNCHER 1 >nul 2>&1
	exit /b 0
)
findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%TEMP%\fonaria_adb.txt" >nul
if not errorlevel 1 (
	echo.
	echo [AVISO] Hay otra Fonaria instalada con una firma distinta.
	set "RESPUESTA="
	set /p "RESPUESTA=Desinstalarla (se borran sus datos) e instalar esta? (s/n): "
	if /i "!RESPUESTA!"=="s" (
		"%ADB%" -d uninstall %PAQUETE%
		"%ADB%" -d install "%APK%"
		exit /b !errorlevel!
	)
)
echo [ERROR] No se pudo instalar.
exit /b 1

rem ---------------------------------------------------------------------------
rem Compila, deja en dist el APK con su version y version.json (SHA-256 y novedades de
rem NOVEDADES.txt), lo copia a la carpeta del servidor y crea la release en GitHub.
:publicar
call :compilar || exit /b 1
%PS% "%RAIZ%\herramientas\publicar_actualizacion.ps1"
exit /b %errorlevel%
