@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Compilando 8K PRO
set "ROOT=%~dp0"
set "PROJ=%ROOT%8KPRO\OwnTV"
set "LOG=%ROOT%compilacion_8kpro.log"
set "OUT=%ROOT%8KPRO_APK"
echo [%date% %time%] Inicio > "%LOG%"

echo == 1/4 Descomprimiendo proyecto ==
if not exist "%PROJ%\gradlew.bat" (
  powershell -NoProfile -Command "Expand-Archive -Force -LiteralPath '%ROOT%8KPRO_OwnTV.zip' -DestinationPath '%ROOT%8KPRO'" >> "%LOG%" 2>&1
)
if not exist "%PROJ%\gradlew.bat" ( echo ERROR: no se pudo descomprimir >> "%LOG%" & echo ERROR: no se pudo descomprimir & goto :fin )

echo == 2/4 Buscando Java (JBR de Android Studio) ==
set "JH="
for %%D in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio1\jbr") do (
  if not defined JH if exist "%%~D\bin\java.exe" set "JH=%%~D"
)
if defined JH goto :havejava
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%buscar_java.ps1" > "%TEMP%\8kpro_jh.txt" 2>> "%LOG%"
set /p JH=<"%TEMP%\8kpro_jh.txt"
:havejava
if not defined JH if defined JAVA_HOME set "JH=%JAVA_HOME%"
if not defined JH ( echo ERROR: no encuentro Java/Android Studio >> "%LOG%" & echo ERROR: no encuentro Java & goto :fin )
set "JAVA_HOME=%JH%"
echo JAVA_HOME=%JAVA_HOME% >> "%LOG%"

echo == 3/4 Buscando Android SDK ==
set "SDK=%ANDROID_HOME%"
if not defined SDK set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not exist "%SDK%\platform-tools" if not exist "%SDK%\platforms" ( echo ERROR: no encuentro el Android SDK en %SDK% >> "%LOG%" & echo ERROR: no encuentro el SDK & goto :fin )
set "ANDROID_HOME=%SDK%"
set "SDKF=%SDK:\=/%"
> "%PROJ%\local.properties" echo sdk.dir=%SDKF%
echo ANDROID_HOME=%SDK% >> "%LOG%"
dir /b "%SDK%\platforms" >> "%LOG%" 2>&1

echo == 4/4 Compilando APK (varios minutos la primera vez) ==
pushd "%PROJ%"
if not exist "%ROOT%server_url.txt" ( echo ERROR: falta server_url.txt con la DNS >> "%LOG%" & echo ERROR: falta server_url.txt con la DNS & goto :fin )
set /p SERVERURL=<"%ROOT%server_url.txt"
echo DNS: leida de server_url.txt >> "%LOG%"
set "TMDBARG="
if exist "%ROOT%tmdb_key.txt" (
  set /p TMDBKEY=<"%ROOT%tmdb_key.txt"
  set "TMDBARG=-Pbrand.tmdbApiKey=!TMDBKEY!"
  echo Clave TMDB: encontrada >> "%LOG%"
) else (
  echo AVISO: sin tmdb_key.txt, los metadatos TMDB no funcionaran >> "%LOG%"
)
call gradlew.bat :app:assembleStandardDebug "-Pbrand.serverUrl=!SERVERURL!" !TMDBARG! --stacktrace >> "%LOG%" 2>&1
set "RC=%ERRORLEVEL%"
popd
echo GRADLE_EXIT=%RC% >> "%LOG%"
if not "%RC%"=="0" ( echo ERROR: la compilacion fallo, ver compilacion_8kpro.log & goto :fin )

if not exist "%OUT%" mkdir "%OUT%"
copy /y "%PROJ%\app\build\outputs\apk\standard\debug\app-standard-debug.apk" "%OUT%\8KPRO.apk" >> "%LOG%" 2>&1
echo APK_OK >> "%LOG%"
echo.
echo LISTO: %OUT%\8KPRO.apk
:fin
echo [%date% %time%] Fin >> "%LOG%"
echo.
echo Puedes cerrar esta ventana.
pause
