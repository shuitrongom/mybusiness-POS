@echo off
REM ===========================================================================
REM instalar-agente.bat  -  Instalacion de UN CLIC del agente PuntoNube Print.
REM
REM Que hace (idempotente: se puede correr varias veces sin problema):
REM   1. Copia puntonube-print.exe y config.json a %LOCALAPPDATA%\PuntoNubePrint.
REM   2. Registra una TAREA PROGRAMADA "PuntoNube Print" que arranca el agente
REM      AUTOMATICAMENTE cada vez que el usuario inicia sesion (onlogon). Asi,
REM      si se apaga y se prende el equipo, el agente VUELVE a arrancar solo
REM      (NO es ejecucion unica).
REM   3. Arranca el agente de inmediato tras instalar.
REM
REM NOTA: schtasks /create puede requerir ejecutar este .bat como Administrador.
REM       Si falla la creacion de la tarea, clic derecho > "Ejecutar como
REM       administrador".
REM ===========================================================================
setlocal
set "DEST=%LOCALAPPDATA%\PuntoNubePrint"
set "SRC=%~dp0"
set "TASKNAME=PuntoNube Print"

echo.
echo === Instalando PuntoNube Print ===
echo Destino: "%DEST%"

REM --- 1. Crear carpeta destino ---
if not exist "%DEST%" mkdir "%DEST%"

REM --- 2. Copiar el ejecutable (obligatorio) ---
if not exist "%SRC%dist\puntonube-print.exe" (
  echo [ERROR] No se encontro "%SRC%dist\puntonube-print.exe".
  echo         Genera el ejecutable primero con:  npm install  y  npm run build:exe
  echo.
  pause
  exit /b 1
)
copy /y "%SRC%dist\puntonube-print.exe" "%DEST%\puntonube-print.exe" >nul
if errorlevel 1 (
  echo [ERROR] No se pudo copiar el ejecutable a "%DEST%".
  pause
  exit /b 1
)

REM --- Copiar config.json editable (solo si aun no existe, para no pisar
REM     ajustes del cliente). Si no hay en origen, el agente usa sus defaults. ---
if exist "%SRC%config.json" (
  if not exist "%DEST%\config.json" copy /y "%SRC%config.json" "%DEST%\config.json" >nul
)

REM --- 3. Registrar arranque automatico con Tarea Programada (onlogon).
REM     /f sobrescribe si ya existe (idempotente). /rl limited = sin elevacion. ---
schtasks /create /tn "%TASKNAME%" /tr "\"%DEST%\puntonube-print.exe\"" /sc onlogon /rl limited /f
if errorlevel 1 (
  echo.
  echo [AVISO] No se pudo crear la tarea programada. Normalmente es por falta de
  echo         permisos: cierra esta ventana y vuelve a ejecutar este archivo con
  echo         clic derecho ^> "Ejecutar como administrador".
  echo.
  pause
  exit /b 1
)

REM --- 4. Arranque inmediato tras instalar ---
REM     Se detiene cualquier instancia previa para que tome la version nueva.
taskkill /f /im puntonube-print.exe >nul 2>&1
schtasks /run /tn "%TASKNAME%" >nul 2>&1
if errorlevel 1 (
  REM Si /run falla (p.ej. sesion no interactiva), arrancar el exe directo.
  start "" "%DEST%\puntonube-print.exe"
)

echo.
echo === Listo ===
echo El agente quedo instalado y arranca SOLO con Windows (tarea "%TASKNAME%").
echo Verifica abriendo en el navegador:  http://127.0.0.1:9100/status
echo Para desinstalar, ejecuta: desinstalar-agente.bat
echo.
pause
endlocal
