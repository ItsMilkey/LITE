# ============================================================================
# v4-diagnostico.ps1  |  SaveUp Lite
#
# Ejecuta el diagnostico READ-ONLY de V4 contra la base apuntada por las mismas
# variables que usa la app (SPRING_DATASOURCE_URL / USERNAME / PASSWORD).
# No crea ni modifica nada: solo hace un SELECT.
#
# Pensado para correr contra Supabase antes de desplegar V8 (validar que no haya
# movimientos con signo invalido que hagan fallar el VALIDATE CONSTRAINT).
#
# Requiere Docker (usa la imagen postgres:16 para tener psql sin instalarlo).
#
# Uso:
#   $env:SPRING_DATASOURCE_URL      = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
#   $env:SPRING_DATASOURCE_USERNAME = "postgres.<ref>"
#   $env:SPRING_DATASOURCE_PASSWORD = "<secreto>"
#   ./scripts/v4-diagnostico.ps1
#
# Codigos de salida: 0 = sin filas invalidas | 1 = hay filas invalidas | 2 = error.
# ============================================================================

[CmdletBinding()]
param(
    [string]$SqlFile,
    [string]$DockerImage = 'postgres:16',
    [string]$SslMode
)

$ErrorActionPreference = 'Stop'

# $PSScriptRoot no siempre esta disponible al enlazar parametros: se resuelve aqui.
if (-not $SqlFile) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    if (-not $scriptDir) { $scriptDir = (Get-Location).Path }
    $SqlFile = Join-Path $scriptDir 'v4-diagnostico.sql'
}

$url  = $env:SPRING_DATASOURCE_URL
$user = $env:SPRING_DATASOURCE_USERNAME
$pass = $env:SPRING_DATASOURCE_PASSWORD

if (-not $url -or -not $user -or -not $pass) {
    Write-Host "Faltan variables: SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD." -ForegroundColor Red
    exit 2
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker no esta disponible en el PATH." -ForegroundColor Red
    exit 2
}
if (-not (Test-Path $SqlFile)) {
    Write-Host "No existe el archivo SQL: $SqlFile" -ForegroundColor Red
    exit 2
}

# jdbc:postgresql://host:port/db?params -> componentes para psql
$libpq = $url -replace '^jdbc:', ''
$uri = [uri]$libpq
$dbHost = $uri.Host
$dbPort = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
$database = $uri.AbsolutePath.Trim('/')
if (-not $database) { $database = 'postgres' }

# sslmode: prioridad param > query de la URL > require (Supabase)
$fromUrl = $null
if ($uri.Query -match 'sslmode=([A-Za-z]+)') { $fromUrl = $Matches[1] }
$ssl = if ($SslMode) { $SslMode } elseif ($fromUrl) { $fromUrl } else { 'require' }

Write-Host "Base destino : $dbHost`:$dbPort/$database" -ForegroundColor Cyan
Write-Host "Usuario      : $user" -ForegroundColor Cyan
Write-Host "sslmode      : $ssl" -ForegroundColor Cyan
Write-Host "SQL          : $SqlFile (solo lectura)" -ForegroundColor Cyan

$sql = Get-Content -Raw -Encoding UTF8 $SqlFile
$output = $sql | docker run --rm -i `
    -e "PGPASSWORD=$pass" `
    -e "PGSSLMODE=$ssl" `
    $DockerImage psql -h $dbHost -p $dbPort -U $user -d $database -v ON_ERROR_STOP=1 -f -

$code = $LASTEXITCODE
Write-Host ""
$output | Write-Host

if ($code -ne 0) {
    Write-Host "psql termino con codigo $code." -ForegroundColor Red
    exit 2
}

$text = ($output | Out-String)
$match = [regex]::Match($text, '\((\d+)\s+rows?\)')
if (-not $match.Success) {
    Write-Host "No se pudo interpretar el resultado de psql." -ForegroundColor Yellow
    exit 2
}

$rows = [int]$match.Groups[1].Value
Write-Host ""
if ($rows -eq 0) {
    Write-Host "OK: 0 movimientos con signo invalido. V8 puede validar el CHECK sin riesgo." -ForegroundColor Green
    exit 0
} else {
    Write-Host "ATENCION: $rows fila(s) con signo invalido. Corrigelas antes de desplegar V8." -ForegroundColor Red
    Write-Host "El ALTER TABLE movimiento VALIDATE CONSTRAINT fallaria y revertiria V8." -ForegroundColor Red
    exit 1
}
