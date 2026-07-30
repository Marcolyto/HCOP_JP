# Video demostrativo del flujo oncológico

Esta carpeta contiene una canalización reproducible para crear un video MP4
subtitulado del circuito de siete pasos de HCOP_JP. El render final es
`1920x1080`, H.264, 30 fps, `yuv420p`, con subtítulos SRT incrustados.

La generación de cuadros y video no modifica la aplicación ni la base de
datos. Las capturas deben obtenerse usando exclusivamente un paciente
ficticio.

La única operación que guarda datos es `seed-demo.ps1`, y sólo cuando el
operador la ejecuta expresamente. El script acepta únicamente `localhost`, no
elimina datos, busca primero por el DNI ficticio `99000001` y reutiliza los
registros marcados con `hcop-demo-seven-step-flow-v1`.

## Archivos

- `storyboard.json`: orden, duración, títulos y recorrido del cursor.
- `seed-demo.ps1`: crea o reutiliza de forma idempotente el paciente ficticio,
  su diagnóstico, tratamiento y primera aplicación.
- `render_storyboard.py`: convierte capturas PNG en cuadros con Pillow.
- `render-video.ps1`: encuentra Python y FFmpeg, genera los cuadros y arma el
  MP4 con subtítulos.
- `../../docs/media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.srt`:
  subtítulos editables.

## Crear o recuperar el caso ficticio

Con el servidor local iniciado:

```powershell
$credencial = Get-Credential
.\scripts\demo-video\seed-demo.ps1 -Credential $credencial
```

Resultado esperado:

- paciente `DEMO FLUJO, ANA`, DNI `99000001`;
- diagnóstico completo AJCC, SNOMED CT y CIE-10;
- protocolo con ciclos y días de aplicación;
- primera aplicación validada por Farmacia y ubicada en un bloque libre.

Para revisar qué haría sin guardar:

```powershell
.\scripts\demo-video\seed-demo.ps1 -Credential $credencial -WhatIf
```

## Capturas requeridas

Abra el navegador a `1920x1080`, use datos ficticios y guarde:

```text
docs/media/demo-flujo-7-pasos/capturas/
├── 01-prescripcion.png
├── 02-farmacia-reserva.png
├── 03-agendamiento.png
├── 04-triaje.png
├── 05-preparacion.png
├── 06-administracion.png
└── 07-cierre.png
```

Antes de capturar, confirme que no aparezcan nombres, documentos, números de
afiliado ni otros datos reales. Use el mismo zoom del navegador en las siete
escenas. Las coordenadas de `cursor_path` son píxeles del video final y se
pueden ajustar sin cambiar el programa.

## Requisitos

```powershell
py -m pip install Pillow
winget install Gyan.FFmpeg
```

`render-video.ps1` busca FFmpeg en el `PATH`, en los enlaces y paquetes de
WinGet, en ubicaciones habituales de Windows y en
`C:\Proyectos\VM\tools\ffmpeg\bin`.

## Validar antes de capturar

```powershell
py .\scripts\demo-video\render_storyboard.py `
  --validate-only --allow-missing

py .\scripts\demo-video\render_storyboard.py --self-test
```

El primer comando valida estructura, tiempos y coordenadas aunque las capturas
todavía no existan. El segundo crea y elimina automáticamente un fixture
temporal de seis cuadros.

## Generar el MP4

Desde la raíz de `HCOP_JP`:

```powershell
.\scripts\demo-video\render-video.ps1
```

Salida:

```text
docs/media/demo-flujo-7-pasos/flujo-oncologico-7-pasos.mp4
```

Los cuadros intermedios se eliminan sólo después de un render correcto. Para
conservarlos:

```powershell
.\scripts\demo-video\render-video.ps1 -KeepFrames
```

Para volver a ensamblar el video sin renderizar otra vez los cuadros:

```powershell
.\scripts\demo-video\render-video.ps1 -SkipFrameRender -KeepFrames
```

## Editar el ritmo

Cada escena dura diez segundos y coincide con un bloque del SRT. Si cambia
`duration_seconds`, actualice también los tiempos del archivo SRT.

En cada escena se puede modificar:

- `title` y `caption`;
- `transition_seconds`;
- `title_duration_seconds`;
- `cursor_path`, usando puntos `{ "t", "x", "y", "click" }`.

El cursor interpola suavemente los puntos. `click: true` agrega un halo de
clic. La transición al siguiente paso es un fundido simple.
