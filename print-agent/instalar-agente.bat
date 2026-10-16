@echo off
setlocal EnableExtensions

REM ===========================================================================
REM instalar-agente.bat  -  Instalacion de UN CLIC del agente PuntoNube Print.
REM
REM Copia el .exe, config.json y el lanzador invisible iniciar-oculto.vbs a
REM %LOCALAPPDATA%\PuntoNubePrint, registra una Tarea Programada onlogon que
REM arranca el agente SIN ventana cada vez que se inicia sesion, y lo arranca
REM de inmediato. Idempotente.
REM
REM Si la creacion de la tarea falla por permisos, ejecutar este .bat como
REM Administrador (clic derecho - Ejecutar como administrador).
REM ===========================================================================

set "DEST=%LOCALAPPDATA%\PuntoNubePrint"
set "SRC=%~dp0"
set "TASKNAME=PuntoNube Print"

echo.
echo === Instalando PuntoNube Print ===
echo Destino: %DEST%

if not exist "%DEST%" mkdir "%DEST%"

if not exist "%SRC%dist\puntonube-print.exe" (
  echo [ERROR] No se encontro el ejecutable dist\puntonube-print.exe
  echo Genera el ejecutable primero con: npm install  y  npm run build:exe
  pause
  exit /b 1
)

copy /y "%SRC%dist\puntonube-print.exe" "%DEST%\puntonube-print.exe" >nul
if errorlevel 1 (
  echo [ERROR] No se pudo copiar el ejecutable al destino.
  pause
  exit /b 1
)

if exist "%SRC%config.json" if not exist "%DEST%\config.json" copy /y "%SRC%config.json" "%DEST%\config.json" >nul

if not exist "%SRC%iniciar-oculto.vbs" (
  echo [ERROR] No se encontro el lanzador iniciar-oculto.vbs
  pause
  exit /b 1
)
copy /y "%SRC%iniciar-oculto.vbs" "%DEST%\iniciar-oculto.vbs" >nul

REM Registrar arranque automatico (onlogon) ejecutando el lanzador VBS con
REM wscript, que abre el .exe SIN ventana. Las comillas internas del /tr se
REM escriben duplicadas ("") para que cmd y schtasks las interpreten bien.
set "TASKRUN=wscript.exe ""%DEST%\iniciar-oculto.vbs"""
schtasks /create /tn "%TASKNAME%" /tr "%TASKRUN%" /sc onlogon /rl limited /f
if errorlevel 1 (
  echo.
  echo [AVISO] No se pudo crear la tarea programada. Suele ser por permisos:
  echo cierra esta ventana y ejecuta el .bat como Administrador.
  pause
  exit /b 1
)

REM Arranque inmediato (oculto). Se detiene cualquier instancia previa.
taskkill /f /im puntonube-print.exe >nul 2>&1
schtasks /run /tn "%TASKNAME%" >nul 2>&1
if errorlevel 1 wscript.exe "%DEST%\iniciar-oculto.vbs"

echo.
echo === Listo ===
echo El agente arranca solo con Windows (tarea "%TASKNAME%") y SIN ventana.
echo Verifica en el navegador: http://127.0.0.1:9100/status
echo Para desinstalar: desinstalar-agente.bat
echo.
pause
endlocal
