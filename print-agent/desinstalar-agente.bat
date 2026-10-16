@echo off
REM ===========================================================================
REM desinstalar-agente.bat  -  Limpieza completa del agente PuntoNube Print.
REM
REM Que hace (tolerante a que algo ya no exista; no falla si falta algo):
REM   1. Detiene la tarea programada y el proceso en ejecucion.
REM   2. Borra la tarea programada "PuntoNube Print".
REM   3. Borra la carpeta %LOCALAPPDATA%\PuntoNubePrint (exe, vbs, config, logs).
REM
REM NOTA: borrar la tarea puede requerir ejecutar este .bat como Administrador.
REM ===========================================================================
setlocal
set "DEST=%LOCALAPPDATA%\PuntoNubePrint"
set "TASKNAME=PuntoNube Print"

echo.
echo === Desinstalando PuntoNube Print ===

REM --- 1. Detener tarea y proceso (ignorar errores si no estan activos) ---
schtasks /end /tn "%TASKNAME%" >nul 2>&1
taskkill /f /im puntonube-print.exe >nul 2>&1

REM --- 2. Borrar la tarea programada ---
schtasks /delete /tn "%TASKNAME%" /f >nul 2>&1

REM --- 3. Borrar la carpeta de datos ---
if exist "%DEST%" rmdir /s /q "%DEST%"

echo === Listo ===
echo El agente quedo desinstalado: tarea y carpeta eliminadas.
echo.
pause
endlocal
