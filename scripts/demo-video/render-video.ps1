[CmdletBinding()]
param(
    [string]$Storyboard = '',
    [string]$Subtitles = '',
    [string]$Output = '',
    [string]$FramesDirectory = '',
    [switch]$SkipFrameRender,
    [switch]$SkipSubtitleGeneration,
    [switch]$KeepFrames,
    [switch]$PublishHelpCopy,
    [switch]$ShowHelp
)

$ErrorActionPreference = 'Stop'

if ($ShowHelp) {
    @'
Genera el video demostrativo de HCOP_JP.

Uso:
  .\render-video.ps1
  .\render-video.ps1 -KeepFrames
  .\render-video.ps1 -SkipFrameRender
  .\render-video.ps1 -PublishHelpCopy

Parámetros:
  -Storyboard         JSON con escenas, capturas y movimientos del cursor.
  -Subtitles          SRT generado desde el campo subtitle del storyboard.
  -Output             MP4 de salida (1920x1080, H.264, yuv420p).
  -FramesDirectory    Directorio temporal para la secuencia PNG.
  -SkipFrameRender    Reutiliza cuadros ya renderizados.
  -SkipSubtitleGeneration
                      Conserva el SRT indicado sin regenerarlo.
  -KeepFrames         Conserva los cuadros tras generar el MP4.
  -PublishHelpCopy    Copia el MP4 al directorio público de Ayuda.
'@ | Write-Host
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Storyboard)) {
    $Storyboard = Join-Path $PSScriptRoot 'storyboard-detallado.json'
}
if ([string]::IsNullOrWhiteSpace($Subtitles)) {
    $Subtitles = Join-Path $PSScriptRoot '..\..\docs\media\demo-flujo-7-pasos\circuito-hospital-dia-paso-a-paso.srt'
}
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $PSScriptRoot '..\..\docs\media\demo-flujo-7-pasos\circuito-hospital-dia-paso-a-paso.mp4'
}
if ([string]::IsNullOrWhiteSpace($FramesDirectory)) {
    $FramesDirectory = Join-Path $PSScriptRoot '..\..\docs\media\demo-flujo-7-pasos\.frames'
}

function Find-Ffmpeg {
    $command = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $directCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\ffmpeg.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\ffmpeg\bin\ffmpeg.exe'),
        'C:\Program Files\ffmpeg\bin\ffmpeg.exe',
        'C:\ffmpeg\bin\ffmpeg.exe',
        'C:\Proyectos\VM\tools\ffmpeg\bin\ffmpeg.exe'
    )
    foreach ($candidate in $directCandidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $wingetRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    if (Test-Path -LiteralPath $wingetRoot -PathType Container) {
        $wingetCandidate = Get-ChildItem -LiteralPath $wingetRoot `
            -Filter ffmpeg.exe -File -Recurse -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($wingetCandidate) {
            return $wingetCandidate.FullName
        }
    }
    throw 'No se encontró ffmpeg.exe. Instálelo con: winget install Gyan.FFmpeg'
}

function Find-Python {
    foreach ($name in @('python.exe', 'python3.exe')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }
    $pyLauncher = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($pyLauncher) {
        return $pyLauncher.Source
    }
    throw 'No se encontró Python. Instale Python 3 y Pillow.'
}

function ConvertTo-FfmpegFilterPath([string]$PathValue) {
    $resolved = (Resolve-Path -LiteralPath $PathValue).Path.Replace('\', '/')
    $resolved = $resolved.Replace(':', '\:')
    return $resolved.Replace("'", "\'")
}

$storyboardPath = (Resolve-Path -LiteralPath $Storyboard).Path
$subtitlePath = [System.IO.Path]::GetFullPath($Subtitles)
$outputPath = [System.IO.Path]::GetFullPath($Output)
$framesPath = [System.IO.Path]::GetFullPath($FramesDirectory)
$outputParent = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $outputParent -Force | Out-Null

$python = Find-Python
if (-not $SkipSubtitleGeneration) {
    & $python (Join-Path $PSScriptRoot 'render_storyboard.py') `
        --storyboard $storyboardPath `
        --validate-only `
        --write-srt $subtitlePath
    if ($LASTEXITCODE -ne 0) {
        throw 'Falló la generación sincronizada de subtítulos.'
    }
}
if (-not (Test-Path -LiteralPath $subtitlePath -PathType Leaf)) {
    throw "No existe el archivo de subtítulos: $subtitlePath"
}

if (-not $SkipFrameRender) {
    & $python (Join-Path $PSScriptRoot 'render_storyboard.py') `
        --storyboard $storyboardPath `
        --output-dir $framesPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Falló la generación de cuadros.'
    }
}

$manifestPath = Join-Path $framesPath 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "No existe el manifiesto de cuadros: $manifestPath"
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 |
    ConvertFrom-Json
$fps = [int]$manifest.fps
if ($fps -le 0) {
    throw 'El manifiesto contiene una velocidad de cuadros inválida.'
}

$ffmpeg = Find-Ffmpeg
$subtitleFilterPath = ConvertTo-FfmpegFilterPath $subtitlePath
$subtitleFilter = "subtitles='$subtitleFilterPath':force_style='FontName=Arial,FontSize=24,Bold=1,PrimaryColour=&H00FF6500,OutlineColour=&H00FFFFFF,BackColour=&H28000000,BorderStyle=3,Outline=8,Shadow=0,MarginV=44,Alignment=2'"
$framePattern = Join-Path $framesPath 'frame_%06d.png'

Write-Host "FFmpeg: $ffmpeg"
Write-Host "Salida: $outputPath"

& $ffmpeg -hide_banner -y `
    -framerate $fps -i $framePattern `
    -f lavfi -i 'anullsrc=r=48000:cl=stereo' `
    -vf $subtitleFilter `
    -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p `
    -r $fps -c:a aac -b:a 128k -shortest -movflags '+faststart' `
    $outputPath

if ($LASTEXITCODE -ne 0) {
    throw 'FFmpeg no pudo generar el MP4.'
}

if (-not $KeepFrames) {
    $safeFramesRoot = [System.IO.Path]::GetFullPath(
        (Join-Path $PSScriptRoot '..\..\docs\media')
    )
    if (-not $framesPath.StartsWith(
        $safeFramesRoot,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Por seguridad no se eliminarán cuadros fuera de $safeFramesRoot"
    }
    Remove-Item -LiteralPath $framesPath -Recurse -Force
}

if ($PublishHelpCopy) {
    $publicHelpPath = [System.IO.Path]::GetFullPath(
        (Join-Path $PSScriptRoot '..\..\src\main\resources\static\help\media\circuito-hospital-dia-paso-a-paso.mp4')
    )
    New-Item -ItemType Directory -Path (Split-Path -Parent $publicHelpPath) -Force |
        Out-Null
    Copy-Item -LiteralPath $outputPath -Destination $publicHelpPath -Force
    Write-Host "Copia pública de Ayuda: $publicHelpPath"
}

Write-Host "Video generado correctamente: $outputPath"
