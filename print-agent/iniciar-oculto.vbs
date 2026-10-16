' ===========================================================================
' iniciar-oculto.vbs  -  Lanzador INVISIBLE del agente PuntoNube Print.
'
' El ejecutable puntonube-print.exe es una app de consola, asi que Windows le
' abre una ventana negra. Para produccion eso no sirve: el cajero podria
' cerrarla y matar la impresion. Este script arranca el .exe en SEGUNDO PLANO,
' SIN ventana (intWindowStyle = 0) y sin esperar (bWaitOnReturn = False).
'
' La Tarea Programada ("PuntoNube Print") ejecuta ESTE .vbs en lugar del .exe,
' por lo que el agente corre oculto y el cajero no tiene nada que cerrar.
'
' Busca el .exe junto a este mismo script (la carpeta de instalacion).
' ===========================================================================
Option Explicit

Dim shell, fso, carpeta, exe
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

' Carpeta donde vive este .vbs (= carpeta de instalacion del agente).
carpeta = fso.GetParentFolderName(WScript.ScriptFullName)
exe = carpeta & "\puntonube-print.exe"

If fso.FileExists(exe) Then
  ' 0 = ventana oculta ; False = no esperar a que termine (el agente es de larga vida).
  shell.Run """" & exe & """", 0, False
End If

Set shell = Nothing
Set fso = Nothing
