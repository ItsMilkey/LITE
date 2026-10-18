# ============================================================================
# smoke-test.ps1  |  SaveUp Lite
#
# Prueba de humo end-to-end contra la API real (por defecto http://localhost:8080).
# No lleva credenciales embebidas: crea un usuario de prueba con datos aleatorios.
# Cada paso verifica su resultado; si algo falla, el script termina con código
# distinto de cero (1 = fallos de verificación, 2 = API inalcanzable).
#
# Uso:
#   ./scripts/smoke-test.ps1
#   $env:BASE_URL="http://localhost:9090"; ./scripts/smoke-test.ps1
# ============================================================================

[CmdletBinding()]
param(
    [string]$BaseUrl = $env:BASE_URL
)

if (-not $BaseUrl) { $BaseUrl = "http://localhost:8080" }
$BaseUrl = $BaseUrl.TrimEnd('/')

$script:Failures = 0

function Step([string]$Message) {
    Write-Host ""
    Write-Host "== $Message" -ForegroundColor Cyan
}

function Check {
    param([bool]$Condition, [string]$Message)
    if ($Condition) {
        Write-Host "  [OK]   $Message" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $Message" -ForegroundColor Red
        $script:Failures++
    }
}

function CheckStatus {
    param($Response, [int]$Expected, [string]$Message)
    Check ($Response.Status -eq $Expected) "$Message (esperado HTTP $Expected, obtenido $($Response.Status))"
}

# Llama a la API. Devuelve Ok, Status (código HTTP) y Body.
# En éxito Invoke-RestMethod no expone el código; se reporta 2xx como 200.
# En error se captura el código real y el cuerpo { "error": "..." }.
function Invoke-SaveUpApi {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body,
        [string]$Token,
        [hashtable]$Query
    )

    $uri = "$BaseUrl$Path"
    if ($Query -and $Query.Count -gt 0) {
        $pairs = foreach ($k in $Query.Keys) {
            "$k=$([uri]::EscapeDataString([string]$Query[$k]))"
        }
        $uri = $uri + "?" + ($pairs -join "&")
    }

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $splat = @{ Uri = $uri; Method = $Method; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $Body) {
        $splat["ContentType"] = "application/json"
        $splat["Body"] = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    try {
        $result = Invoke-RestMethod @splat
        return [pscustomobject]@{ Ok = $true; Status = 200; Body = $result }
    } catch {
        $status = 0
        $body = $null
        $resp = $_.Exception.Response
        if ($resp) { try { $status = [int]$resp.StatusCode } catch { $status = 0 } }

        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            try { $body = $_.ErrorDetails.Message | ConvertFrom-Json } catch { $body = $_.ErrorDetails.Message }
        } elseif ($resp) {
            try {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $text = $reader.ReadToEnd()
                try { $body = $text | ConvertFrom-Json } catch { $body = $text }
            } catch { }
        }
        return [pscustomobject]@{ Ok = $false; Status = $status; Body = $body }
    }
}

function Get-ErrorText($Response) {
    if ($Response.Body -and $Response.Body.error) { return $Response.Body.error }
    return ""
}

# ---------------------------------------------------------------------------
# Datos aleatorios del usuario de prueba (nada hardcodeado)
# ---------------------------------------------------------------------------
$stamp  = Get-Date -Format "yyyyMMddHHmmssfff"
$rut    = "$(Get-Random -Minimum 1000000 -Maximum 100000000)-$(Get-Random -Minimum 0 -Maximum 10)"
$nombre = "Smoke"
$apellido = "Test"
$email  = "smoke.$stamp@example.com"
$clave  = "Smoke$((Get-Random -Minimum 100000 -Maximum 999999))a"

Write-Host "SaveUp Lite - smoke test" -ForegroundColor White
Write-Host "BASE_URL = $BaseUrl"
Write-Host "Usuario de prueba: RUT $rut / $email"

# ---------------------------------------------------------------------------
Step "1. Registro y login"
$reg = Invoke-SaveUpApi -Method POST -Path "/api/usuarios/register" -Body @{
    rut = $rut; nombre = $nombre; apellido = $apellido; email = $email; contrasena = $clave
}
if ($reg.Status -eq 0) {
    Write-Host "No se pudo conectar con la API en $BaseUrl. ¿Está el backend corriendo?" -ForegroundColor Red
    exit 2
}
Check $reg.Ok "POST /api/usuarios/register responde 2xx"
Check ($reg.Body.message -like "*registrado*") "Register devuelve mensaje de éxito"

$login = Invoke-SaveUpApi -Method POST -Path "/api/usuarios/login" -Body @{ email = $email; contrasena = $clave }
Check $login.Ok "POST /api/usuarios/login responde 2xx"
$token = $login.Body.token
Check ([bool]$token) "Login devuelve un token JWT"

Step "2. Saldo inicial en 0"
$saldo0 = Invoke-SaveUpApi -Method GET -Path "/api/saldos/me" -Token $token
Check $saldo0.Ok "GET /api/saldos/me responde 2xx"
Check ([decimal]$saldo0.Body.saldo -eq 0) "Saldo inicial es 0 (obtenido $($saldo0.Body.saldo))"

Step "3. Ingreso 20000 y gasto enviado como +5000 (el signo lo impone el tipo)"
$ingreso = Invoke-SaveUpApi -Method POST -Path "/api/movimientos" -Token $token -Body @{
    monto = 20000; descripcion = "Ingreso smoke"; tipoMovimiento = "INGRESO_GENERAL"
}
Check $ingreso.Ok "POST ingreso responde 2xx"
Check ([decimal]$ingreso.Body.monto -eq 20000) "Ingreso guardado como +20000"

$gasto = Invoke-SaveUpApi -Method POST -Path "/api/movimientos" -Token $token -Body @{
    monto = 5000; descripcion = "Gasto smoke"; tipoMovimiento = "GASTO_GENERAL"
}
Check $gasto.Ok "POST gasto (enviado +5000) responde 2xx"
Check ([decimal]$gasto.Body.monto -eq -5000) "Gasto normalizado a -5000 por SignoMovimiento"
$idGasto = $gasto.Body.id

$saldo15 = Invoke-SaveUpApi -Method GET -Path "/api/saldos/me" -Token $token
Check ([decimal]$saldo15.Body.saldo -eq 15000) "Saldo final es 15000 (obtenido $($saldo15.Body.saldo))"

Step "4. ABONO_META por /api/movimientos debe rechazarse con 400"
$abono = Invoke-SaveUpApi -Method POST -Path "/api/movimientos" -Token $token -Body @{
    monto = 1000; descripcion = "Abono indebido"; tipoMovimiento = "ABONO_META"
}
CheckStatus $abono 400 "POST ABONO_META rechazado (se crea solo desde /api/metas)"

# ---------------------------------------------------------------------------
Step "5. Crear deuda con payload antiguo (sin bloque condiciones)"
$deudaLegacy = Invoke-SaveUpApi -Method POST -Path "/api/deudas" -Token $token -Body @{
    nombre = "Deuda legacy smoke"; montoTotal = 12000; cantidadCuotas = 12; tipoDeuda = "OTRO"
}
Check $deudaLegacy.Ok "POST /api/deudas (payload antiguo) responde 2xx"
$idLegacy = $deudaLegacy.Body.id
Check ($deudaLegacy.Body.modalidadCalculo -eq "SIN_INTERES") "Payload antiguo => modalidad SIN_INTERES"
Check ([int]$deudaLegacy.Body.cantidadCuotas -eq 12) "Payload antiguo => 12 cuotas"
Check ([decimal]$deudaLegacy.Body.montoTotal -eq 12000) "Payload antiguo => montoTotal 12000"

Step "6. Crear deuda con condiciones (TASA_CONOCIDA) y cuotasPagadasPrevias"
$deudaTasa = Invoke-SaveUpApi -Method POST -Path "/api/deudas" -Token $token -Body @{
    nombre = "Deuda tasa smoke"; tipoDeuda = "PERSONAL"; cuotasPagadasPrevias = 1
    condiciones = @{ modalidad = "TASA_CONOCIDA"; montoCapital = 100000; cantidadCuotas = 10; tasaMensual = 1.5 }
}
Check $deudaTasa.Ok "POST /api/deudas (TASA_CONOCIDA) responde 2xx"
$idTasa = $deudaTasa.Body.id
Check ($deudaTasa.Body.modalidadCalculo -eq "TASA_CONOCIDA") "Deuda persistida como TASA_CONOCIDA"
Check ([int]$deudaTasa.Body.cuotasPagadasPrevias -eq 1) "cuotasPagadasPrevias = 1"
Check ([decimal]$deudaTasa.Body.tasaMensual -eq 1.5) "tasaMensual en porcentaje = 1,5"

Step "7. Lectura de deudas: /me, /{id}, /amortizacion y /pagos"
$deudasMe = Invoke-SaveUpApi -Method GET -Path "/api/deudas/me" -Token $token
Check $deudasMe.Ok "GET /api/deudas/me responde 2xx"
$ids = @($deudasMe.Body) | ForEach-Object { $_.id }
Check ($ids -contains $idLegacy) "La deuda legacy aparece en /me"
Check ($ids -contains $idTasa) "La deuda con tasa aparece en /me"

$detalle = Invoke-SaveUpApi -Method GET -Path "/api/deudas/$idTasa" -Token $token
Check $detalle.Ok "GET /api/deudas/{id} responde 2xx"
Check ($detalle.Body.id -eq $idTasa) "El detalle corresponde a la deuda con tasa"

$amort = Invoke-SaveUpApi -Method GET -Path "/api/deudas/$idTasa/amortizacion" -Token $token
Check $amort.Ok "GET /api/deudas/{id}/amortizacion responde 2xx"
Check (@($amort.Body).Count -eq 10) "La amortización tiene 10 cuotas (obtenidas $(@($amort.Body).Count))"

$pagos0 = Invoke-SaveUpApi -Method GET -Path "/api/deudas/$idTasa/pagos" -Token $token
Check $pagos0.Ok "GET /api/deudas/{id}/pagos responde 2xx"
Check ([int]$pagos0.Body.totalItems -eq 0) "Historial de pagos inicia vacío"

Step "8. Pagos: CUOTA_CALCULADA y MONTO_PERSONALIZADO"
$pagoCalc = Invoke-SaveUpApi -Method POST -Path "/api/deudas/$idTasa/pagar" -Token $token -Body @{
    descripcion = "Pago cuota calculada"; modoPago = "CUOTA_CALCULADA"
}
Check $pagoCalc.Ok "Pago CUOTA_CALCULADA responde 2xx"
Check ($pagoCalc.Body.estado -eq "PENDIENTE") "La deuda sigue PENDIENTE tras una cuota"

$pagoPers = Invoke-SaveUpApi -Method POST -Path "/api/deudas/$idTasa/pagar" -Token $token -Body @{
    descripcion = "Pago parcial 5000"; modoPago = "MONTO_PERSONALIZADO"; monto = 5000
}
Check $pagoPers.Ok "Pago MONTO_PERSONALIZADO responde 2xx"

$pagos2 = Invoke-SaveUpApi -Method GET -Path "/api/deudas/$idTasa/pagos" -Token $token
Check ([int]$pagos2.Body.totalItems -eq 2) "El historial registra 2 pagos"

Step "9. Sobrepago => 409"
$sobrepago = Invoke-SaveUpApi -Method POST -Path "/api/deudas/$idTasa/pagar" -Token $token -Body @{
    descripcion = "Sobrepago"; modoPago = "MONTO_PERSONALIZADO"; monto = 999999999999
}
CheckStatus $sobrepago 409 "Sobrepago rechazado (409): $(Get-ErrorText $sobrepago)"

# ---------------------------------------------------------------------------
Step "10. Caso límite: editar cuotasPagadasPrevias con pagos ya registrados"
$pagoLegacy = Invoke-SaveUpApi -Method POST -Path "/api/deudas/$idLegacy/pagar" -Token $token -Body @{
    descripcion = "Pago inicial legacy"; modoPago = "MONTO_PERSONALIZADO"; monto = 2000
}
Check $pagoLegacy.Ok "Se registra un pago en la deuda legacy (ahora tiene pagos)"

$editBloqueado = Invoke-SaveUpApi -Method PUT -Path "/api/deudas/$idLegacy" -Token $token -Body @{
    nombre = "Deuda legacy editada"; montoTotal = 5000
}
CheckStatus $editBloqueado 409 "Editar las condiciones financieras con pagos => 409"

$editPrevias = Invoke-SaveUpApi -Method PUT -Path "/api/deudas/$idLegacy" -Token $token -Body @{
    nombre = "Deuda legacy editada"; tipoDeuda = "OTRO"; cuotasPagadasPrevias = 10
}
Check $editPrevias.Ok "Editar solo cuotasPagadasPrevias (con pagos) responde 2xx"
Check ([int]$editPrevias.Body.cuotasPagadasPrevias -eq 10) "cuotasPagadasPrevias quedó en 10"
Check ([decimal]$editPrevias.Body.montoPagadoPrevio -eq 10000) "montoPagadoPrevio recalculado a 10000"
Check ($editPrevias.Body.estado -eq "PAGADA") "Al cubrir el total con las previas, la deuda queda PAGADA"

# ---------------------------------------------------------------------------
Step "11. Simulación de crédito (sin persistencia)"
$sim = Invoke-SaveUpApi -Method POST -Path "/api/simulaciones/credito" -Token $token -Body @{
    modalidad = "SIN_INTERES"; montoCapital = 100000; cantidadCuotas = 10
}
Check $sim.Ok "POST /api/simulaciones/credito responde 2xx"
Check ([decimal]$sim.Body.valorCuota -eq 10000) "Valor de cuota = 10000"
Check (@($sim.Body.tablaAmortizacion).Count -eq 10) "La tabla de amortización tiene 10 cuotas"

Step "12. Filtros y PUT parcial de movimientos"
$filtroTipo = Invoke-SaveUpApi -Method GET -Path "/api/movimientos/paginados/me" -Token $token -Query @{
    tipoMovimiento = "GASTO_GENERAL"
}
Check $filtroTipo.Ok "Filtro por tipoMovimiento responde 2xx"
Check ([int]$filtroTipo.Body.totalItems -eq 1) "El filtro GASTO_GENERAL devuelve 1 movimiento"

$filtroQ = Invoke-SaveUpApi -Method GET -Path "/api/movimientos/paginados/me" -Token $token -Query @{
    q = "Ingreso smoke"
}
Check ([int]$filtroQ.Body.totalItems -eq 1) "El filtro q='Ingreso smoke' devuelve 1 movimiento"

$editGasto = Invoke-SaveUpApi -Method PUT -Path "/api/movimientos/$idGasto" -Token $token -Body @{
    descripcion = "Gasto smoke editado"
}
Check $editGasto.Ok "PUT parcial del gasto responde 2xx"
Check ($editGasto.Body.descripcion -eq "Gasto smoke editado") "Se aplicó la nueva descripción"
Check ([decimal]$editGasto.Body.monto -eq -5000) "El monto no enviado se conserva (-5000)"

Step "13. Presupuesto por categoría: límites, ejecución e historial"
$cats = Invoke-SaveUpApi -Method GET -Path "/api/categorias" -Token $token
Check $cats.Ok "GET /api/categorias responde 2xx"
Check (@($cats.Body).Count -ge 1) "Hay categorías sembradas disponibles"
$comida = @($cats.Body) | Where-Object { $_.nombre -eq "Comida" } | Select-Object -First 1
Check ($null -ne $comida) "Existe la categoría 'Comida'"

$putLim = Invoke-SaveUpApi -Method PUT -Path "/api/presupuestos/categorias/me" -Token $token -Body @{
    categorias = @(@{ categoriaId = $comida.id; limiteMensual = 1000 })
}
Check $putLim.Ok "PUT /api/presupuestos/categorias/me responde 2xx"
Check (@($putLim.Body).Count -eq 1) "Se persiste 1 límite de categoría"

$getLim = Invoke-SaveUpApi -Method GET -Path "/api/presupuestos/categorias/me" -Token $token
Check $getLim.Ok "GET /api/presupuestos/categorias/me responde 2xx"
Check (@($getLim.Body).Count -eq 1) "El límite persiste (1 registro)"

$ejCat = Invoke-SaveUpApi -Method GET -Path "/api/presupuestos/categorias/ejecucion/me" -Token $token
Check $ejCat.Ok "GET /api/presupuestos/categorias/ejecucion/me responde 2xx"

$ejGen = Invoke-SaveUpApi -Method GET -Path "/api/presupuestos/ejecucion/me" -Token $token
Check $ejGen.Ok "GET /api/presupuestos/ejecucion/me responde 2xx"

$hist = Invoke-SaveUpApi -Method GET -Path "/api/presupuestos/historial/me" -Token $token -Query @{
    desde = "2026-01"; hasta = "2026-06"
}
Check $hist.Ok "GET /api/presupuestos/historial/me responde 2xx"

# ---------------------------------------------------------------------------
Write-Host ""
if ($script:Failures -eq 0) {
    Write-Host "SMOKE TEST OK - todas las verificaciones pasaron." -ForegroundColor Green
    exit 0
} else {
    Write-Host "SMOKE TEST FALLÓ - $($script:Failures) verificación(es) fallida(s)." -ForegroundColor Red
    exit 1
}
