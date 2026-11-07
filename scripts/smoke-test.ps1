# ============================================================================
# smoke-test.ps1  |  SaveUp Lite
#
# Prueba de humo end-to-end contra la API real (por defecto http://localhost:8080).
# No lleva credenciales embebidas: crea un usuario de prueba con datos aleatorios.
# Requiere una base migrada por Flyway (18 categorías sembradas) y la API arriba.
# Cada paso verifica su resultado; si algo falla, el script termina con código
# distinto de cero (1 = fallos de verificación, 2 = API inalcanzable).
#
# Uso:
#   ./scripts/smoke-test.ps1
#   $env:BASE_URL="http://localhost:9090"; ./scripts/smoke-test.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
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
$reg = Invoke-SaveUpApi -Method POST -Path "/api/v1/usuarios/register" -Body @{
    rut = $rut; nombre = $nombre; apellido = $apellido; email = $email; contrasena = $clave
}
if ($reg.Status -eq 0) {
    Write-Host "No se pudo conectar con la API en $BaseUrl. ¿Está el backend corriendo?" -ForegroundColor Red
    exit 2
}
Check $reg.Ok "POST /api/v1/usuarios/register responde 2xx"
Check ($reg.Body.message -like "*registrado*") "Register devuelve mensaje de éxito"

$login = Invoke-SaveUpApi -Method POST -Path "/api/v1/usuarios/login" -Body @{ email = $email; contrasena = $clave }
Check $login.Ok "POST /api/v1/usuarios/login responde 2xx"
$token = $login.Body.token
Check ([bool]$token) "Login devuelve un token JWT"

Step "2. Saldo inicial en 0"
$saldo0 = Invoke-SaveUpApi -Method GET -Path "/api/v1/saldos/me" -Token $token
Check $saldo0.Ok "GET /api/v1/saldos/me responde 2xx"
Check ([decimal]$saldo0.Body.saldo -eq 0) "Saldo inicial es 0 (obtenido $($saldo0.Body.saldo))"

Step "3. Ingreso 20000 y gasto enviado como +5000 (el signo lo impone el tipo)"
$ingreso = Invoke-SaveUpApi -Method POST -Path "/api/v1/movimientos" -Token $token -Body @{
    monto = 20000; descripcion = "Ingreso smoke"; tipoMovimiento = "INGRESO_GENERAL"
}
Check $ingreso.Ok "POST ingreso responde 2xx"
Check ([decimal]$ingreso.Body.monto -eq 20000) "Ingreso guardado como +20000"

$gasto = Invoke-SaveUpApi -Method POST -Path "/api/v1/movimientos" -Token $token -Body @{
    monto = 5000; descripcion = "Gasto smoke"; tipoMovimiento = "GASTO_GENERAL"
}
Check $gasto.Ok "POST gasto (enviado +5000) responde 2xx"
Check ([decimal]$gasto.Body.monto -eq -5000) "Gasto normalizado a -5000 por SignoMovimiento"
$idGasto = $gasto.Body.id

$saldo15 = Invoke-SaveUpApi -Method GET -Path "/api/v1/saldos/me" -Token $token
Check ([decimal]$saldo15.Body.saldo -eq 15000) "Saldo final es 15000 (obtenido $($saldo15.Body.saldo))"

Step "4. ABONO_META por /api/v1/movimientos debe rechazarse con 400"
$abono = Invoke-SaveUpApi -Method POST -Path "/api/v1/movimientos" -Token $token -Body @{
    monto = 1000; descripcion = "Abono indebido"; tipoMovimiento = "ABONO_META"
}
CheckStatus $abono 400 "POST ABONO_META rechazado (se crea solo desde /api/v1/metas)"

# ---------------------------------------------------------------------------
Step "5. Crear deuda con payload antiguo (sin bloque condiciones)"
$deudaLegacy = Invoke-SaveUpApi -Method POST -Path "/api/v1/deudas" -Token $token -Body @{
    nombre = "Deuda legacy smoke"; montoTotal = 12000; cantidadCuotas = 12; tipoDeuda = "OTRO"
}
Check $deudaLegacy.Ok "POST /api/v1/deudas (payload antiguo) responde 2xx"
$idLegacy = $deudaLegacy.Body.id
Check ($deudaLegacy.Body.modalidadCalculo -eq "SIN_INTERES") "Payload antiguo => modalidad SIN_INTERES"
Check ([int]$deudaLegacy.Body.cantidadCuotas -eq 12) "Payload antiguo => 12 cuotas"
Check ([decimal]$deudaLegacy.Body.montoTotal -eq 12000) "Payload antiguo => montoTotal 12000"

Step "6. Crear deuda con condiciones (TASA_CONOCIDA) y cuotasPagadasPrevias"
$deudaTasa = Invoke-SaveUpApi -Method POST -Path "/api/v1/deudas" -Token $token -Body @{
    nombre = "Deuda tasa smoke"; tipoDeuda = "PERSONAL"; cuotasPagadasPrevias = 1
    condiciones = @{ modalidad = "TASA_CONOCIDA"; montoCapital = 100000; cantidadCuotas = 10; tasaMensual = 1.5 }
}
Check $deudaTasa.Ok "POST /api/v1/deudas (TASA_CONOCIDA) responde 2xx"
$idTasa = $deudaTasa.Body.id
Check ($deudaTasa.Body.modalidadCalculo -eq "TASA_CONOCIDA") "Deuda persistida como TASA_CONOCIDA"
Check ([int]$deudaTasa.Body.cuotasPagadasPrevias -eq 1) "cuotasPagadasPrevias = 1"
Check ([decimal]$deudaTasa.Body.tasaMensual -eq 1.5) "tasaMensual en porcentaje = 1,5"

Step "7. Lectura de deudas: /me, /{id}, /amortizacion y /pagos"
$deudasMe = Invoke-SaveUpApi -Method GET -Path "/api/v1/deudas/me" -Token $token
Check $deudasMe.Ok "GET /api/v1/deudas/me responde 2xx"
$ids = @($deudasMe.Body) | ForEach-Object { $_.id }
Check ($ids -contains $idLegacy) "La deuda legacy aparece en /me"
Check ($ids -contains $idTasa) "La deuda con tasa aparece en /me"

$detalle = Invoke-SaveUpApi -Method GET -Path "/api/v1/deudas/$idTasa" -Token $token
Check $detalle.Ok "GET /api/v1/deudas/{id} responde 2xx"
Check ($detalle.Body.id -eq $idTasa) "El detalle corresponde a la deuda con tasa"

$amort = Invoke-SaveUpApi -Method GET -Path "/api/v1/deudas/$idTasa/amortizacion" -Token $token
Check $amort.Ok "GET /api/v1/deudas/{id}/amortizacion responde 2xx"
Check (@($amort.Body).Count -eq 10) "La amortización tiene 10 cuotas (obtenidas $(@($amort.Body).Count))"

$pagos0 = Invoke-SaveUpApi -Method GET -Path "/api/v1/deudas/$idTasa/pagos" -Token $token
Check $pagos0.Ok "GET /api/v1/deudas/{id}/pagos responde 2xx"
Check ([int]$pagos0.Body.totalItems -eq 0) "Historial de pagos inicia vacío"

Step "8. Pagos: CUOTA_CALCULADA y MONTO_PERSONALIZADO"
$pagoCalc = Invoke-SaveUpApi -Method POST -Path "/api/v1/deudas/$idTasa/pagar" -Token $token -Body @{
    descripcion = "Pago cuota calculada"; modoPago = "CUOTA_CALCULADA"
}
Check $pagoCalc.Ok "Pago CUOTA_CALCULADA responde 2xx"
Check ($pagoCalc.Body.estado -eq "PENDIENTE") "La deuda sigue PENDIENTE tras una cuota"

$pagoPers = Invoke-SaveUpApi -Method POST -Path "/api/v1/deudas/$idTasa/pagar" -Token $token -Body @{
    descripcion = "Pago parcial 5000"; modoPago = "MONTO_PERSONALIZADO"; monto = 5000
}
Check $pagoPers.Ok "Pago MONTO_PERSONALIZADO responde 2xx"

$pagos2 = Invoke-SaveUpApi -Method GET -Path "/api/v1/deudas/$idTasa/pagos" -Token $token
Check ([int]$pagos2.Body.totalItems -eq 2) "El historial registra 2 pagos"

Step "9. Sobrepago => 409"
$sobrepago = Invoke-SaveUpApi -Method POST -Path "/api/v1/deudas/$idTasa/pagar" -Token $token -Body @{
    descripcion = "Sobrepago"; modoPago = "MONTO_PERSONALIZADO"; monto = 999999999999
}
CheckStatus $sobrepago 409 "Sobrepago rechazado (409): $(Get-ErrorText $sobrepago)"

# ---------------------------------------------------------------------------
Step "10. Caso límite: editar cuotasPagadasPrevias con pagos ya registrados"
$pagoLegacy = Invoke-SaveUpApi -Method POST -Path "/api/v1/deudas/$idLegacy/pagar" -Token $token -Body @{
    descripcion = "Pago inicial legacy"; modoPago = "MONTO_PERSONALIZADO"; monto = 2000
}
Check $pagoLegacy.Ok "Se registra un pago en la deuda legacy (ahora tiene pagos)"

$editBloqueado = Invoke-SaveUpApi -Method PUT -Path "/api/v1/deudas/$idLegacy" -Token $token -Body @{
    nombre = "Deuda legacy editada"; montoTotal = 5000
}
CheckStatus $editBloqueado 409 "Editar las condiciones financieras con pagos => 409"

$editPrevias = Invoke-SaveUpApi -Method PUT -Path "/api/v1/deudas/$idLegacy" -Token $token -Body @{
    nombre = "Deuda legacy editada"; tipoDeuda = "OTRO"; cuotasPagadasPrevias = 10
}
Check $editPrevias.Ok "Editar solo cuotasPagadasPrevias (con pagos) responde 2xx"
Check ([int]$editPrevias.Body.cuotasPagadasPrevias -eq 10) "cuotasPagadasPrevias quedó en 10"
Check ([decimal]$editPrevias.Body.montoPagadoPrevio -eq 10000) "montoPagadoPrevio recalculado a 10000"
Check ($editPrevias.Body.estado -eq "PAGADA") "Al cubrir el total con las previas, la deuda queda PAGADA"

# ---------------------------------------------------------------------------
Step "11. Simulación de crédito (sin persistencia)"
$sim = Invoke-SaveUpApi -Method POST -Path "/api/v1/simulaciones/credito" -Token $token -Body @{
    modalidad = "SIN_INTERES"; montoCapital = 100000; cantidadCuotas = 10
}
Check $sim.Ok "POST /api/v1/simulaciones/credito responde 2xx"
Check ([decimal]$sim.Body.valorCuota -eq 10000) "Valor de cuota = 10000"
Check (@($sim.Body.tablaAmortizacion).Count -eq 10) "La tabla de amortización tiene 10 cuotas"

Step "12. Filtros y PUT parcial de movimientos"
$filtroTipo = Invoke-SaveUpApi -Method GET -Path "/api/v1/movimientos/paginados/me" -Token $token -Query @{
    tipoMovimiento = "GASTO_GENERAL"
}
Check $filtroTipo.Ok "Filtro por tipoMovimiento responde 2xx"
Check ([int]$filtroTipo.Body.totalItems -eq 1) "El filtro GASTO_GENERAL devuelve 1 movimiento"

$filtroQ = Invoke-SaveUpApi -Method GET -Path "/api/v1/movimientos/paginados/me" -Token $token -Query @{
    q = "Ingreso smoke"
}
Check ([int]$filtroQ.Body.totalItems -eq 1) "El filtro q='Ingreso smoke' devuelve 1 movimiento"

$editGasto = Invoke-SaveUpApi -Method PUT -Path "/api/v1/movimientos/$idGasto" -Token $token -Body @{
    descripcion = "Gasto smoke editado"
}
Check $editGasto.Ok "PUT parcial del gasto responde 2xx"
Check ($editGasto.Body.descripcion -eq "Gasto smoke editado") "Se aplicó la nueva descripción"
Check ([decimal]$editGasto.Body.monto -eq -5000) "El monto no enviado se conserva (-5000)"

Step "13. Catálogo T10a y sugerencia T10b"
$cats = Invoke-SaveUpApi -Method GET -Path "/api/v1/categorias" -Token $token
Check $cats.Ok "GET /api/v1/categorias responde 2xx"
Check (@($cats.Body).Count -eq 18) "El catálogo tiene 18 categorías (obtenidas $(@($cats.Body).Count))"
$alimentacion = @($cats.Body) | Where-Object { $_.codigo -eq "ALIMENTACION_BASE" } | Select-Object -First 1
$vivienda = @($cats.Body) | Where-Object { $_.codigo -eq "VIVIENDA_SUMINISTROS" } | Select-Object -First 1
$ocio = @($cats.Body) | Where-Object { $_.codigo -eq "OCIO_RECREACION" } | Select-Object -First 1
Check ($null -ne $alimentacion) "Existe la categoría ALIMENTACION_BASE"
Check ($null -ne $vivienda) "Existe la categoría VIVIENDA_SUMINISTROS"
Check ($null -ne $ocio) "Existe la categoría OCIO_RECREACION"

$sug = Invoke-SaveUpApi -Method GET -Path "/api/v1/categorias/sugerencia" -Token $token -Query @{
    texto = "supermercado"; contexto = "GASTO"
}
Check $sug.Ok "GET /api/v1/categorias/sugerencia responde 2xx"
Check (@($sug.Body.sugerencias).Count -ge 1) "La sugerencia devuelve al menos una categoría"
Check ($sug.Body.sugerencias[0].codigo -eq "ALIMENTACION_BASE") "supermercado sugiere ALIMENTACION_BASE"
Check ($sug.Body.sugerencias[0].motivo -eq "PALABRA_CLAVE") "El motivo es PALABRA_CLAVE"
Check ([decimal]$sug.Body.sugerencias[0].confianza -eq 0.90) "La confianza es 0,90"

Step "14. Presupuesto por categoría T5 y estado neutro T10a"
$putLim = Invoke-SaveUpApi -Method PUT -Path "/api/v1/presupuestos/categorias/me" -Token $token -Body @{
    categorias = @(@{ categoriaId = $alimentacion.id; limiteMensual = 1000 })
}
Check $putLim.Ok "PUT /api/v1/presupuestos/categorias/me responde 2xx"
Check (@($putLim.Body).Count -eq 1) "Se persiste 1 límite de categoría"

$getLim = Invoke-SaveUpApi -Method GET -Path "/api/v1/presupuestos/categorias/me" -Token $token
Check $getLim.Ok "GET /api/v1/presupuestos/categorias/me responde 2xx"
Check (@($getLim.Body).Count -eq 1) "El límite persiste (1 registro)"

# Gasto de 800 con categoría: 800/1000 = 80 % => CERCA_DEL_PLAN (borde inferior).
$gastoCat = Invoke-SaveUpApi -Method POST -Path "/api/v1/movimientos" -Token $token -Body @{
    monto = 800; descripcion = "Gasto categoria 800"; tipoMovimiento = "GASTO_GENERAL"
    categoriaId = $alimentacion.id
}
Check $gastoCat.Ok "POST gasto con categoría responde 2xx"

$ejCat = Invoke-SaveUpApi -Method GET -Path "/api/v1/presupuestos/categorias/ejecucion/me" -Token $token
Check $ejCat.Ok "GET /api/v1/presupuestos/categorias/ejecucion/me responde 2xx"
$ejAlim = @($ejCat.Body) | Where-Object { $_.categoriaId -eq $alimentacion.id } | Select-Object -First 1
Check ($null -ne $ejAlim) "La ejecución incluye ALIMENTACION_BASE"
Check ([decimal]$ejAlim.gastado -eq 800) "gastado = 800"
Check ([decimal]$ejAlim.porcentajeUsado -eq 80) "porcentajeUsado = 80,00"
Check ($ejAlim.estado -eq "CERCA_DEL_PLAN") "80 % exacto es CERCA_DEL_PLAN"

$ejGen = Invoke-SaveUpApi -Method GET -Path "/api/v1/presupuestos/ejecucion/me" -Token $token
Check $ejGen.Ok "GET /api/v1/presupuestos/ejecucion/me responde 2xx"

$hist = Invoke-SaveUpApi -Method GET -Path "/api/v1/presupuestos/historial/me" -Token $token -Query @{
    desde = "2026-01"; hasta = "2026-06"
}
Check $hist.Ok "GET /api/v1/presupuestos/historial/me responde 2xx"

Step "15. Plan del mes T10c: ingreso, partida, lote de montos e imputación"
$mes = (Get-Date).ToString("yyyy-MM")
$plan = Invoke-SaveUpApi -Method PUT -Path "/api/v1/planes/$mes/ingreso-referencia" -Token $token -Body @{
    monto = 1000000
}
Check $plan.Ok "PUT ingreso de referencia responde 2xx"
Check ([decimal]$plan.Body.ingresoReferencia -eq 1000000) "Ingreso de referencia = 1.000.000"
Check ($plan.Body.existe -eq $true) "El plan queda creado"

$planPartida = Invoke-SaveUpApi -Method POST -Path "/api/v1/planes/$mes/partidas" -Token $token -Body @{
    nombre = "Arriendo smoke"; categoriaId = $vivienda.id; montoPlanificado = 250000
}
Check $planPartida.Ok "POST partida responde 2xx"
Check (@($planPartida.Body.partidas).Count -eq 1) "El plan tiene 1 partida"
$partidaId = $planPartida.Body.partidas[0].id
Check ($planPartida.Body.partidas[0].categoriaCodigo -eq "VIVIENDA_SUMINISTROS") "La partida hereda la categoría"

$patch = Invoke-SaveUpApi -Method PATCH -Path "/api/v1/planes/$mes/montos" -Token $token -Body @{
    cambios = @(@{ partidaId = $partidaId; monto = 300000 })
}
Check $patch.Ok "PATCH /planes/{mes}/montos responde 2xx"
Check ([decimal]$patch.Body.resumen.planificadoTotal -eq 300000) "planificadoTotal = 300.000 tras el lote"
Check ([decimal]$patch.Body.resumen.remanenteLibre -eq 700000) "remanenteLibre = 700.000"
Check ([decimal]$patch.Body.resumen.margenSeguridadPct -eq 70) "margenSeguridadPct = 70,00"

$gastoPlan = Invoke-SaveUpApi -Method POST -Path "/api/v1/movimientos" -Token $token -Body @{
    monto = 8000; descripcion = "Gasto imputado a partida"; tipoMovimiento = "GASTO_GENERAL"
    categoriaId = $vivienda.id; partidaId = $partidaId
}
Check $gastoPlan.Ok "POST gasto con partidaId responde 2xx"
Check ($gastoPlan.Body.partidaId -eq $partidaId) "El movimiento quedó imputado a la partida"

$planGet = Invoke-SaveUpApi -Method GET -Path "/api/v1/planes/$mes" -Token $token -Query @{
    incluirReferencias = "true"
}
Check $planGet.Ok "GET /planes/{mes} responde 2xx"
$partidaGet = @($planGet.Body.partidas) | Where-Object { $_.id -eq $partidaId } | Select-Object -First 1
Check ($null -ne $partidaGet) "La partida aparece en el plan"
Check ([decimal]$partidaGet.real -eq 8000) "real de la partida = 8.000"
Check ([decimal]$partidaGet.diferencia -eq 292000) "diferencia = 292.000"
Check ([decimal]$partidaGet.porcentajeUsado -eq 2.66) "porcentajeUsado truncado = 2,66"
Check ($partidaGet.estado -eq "BAJO_EL_PLAN") "La partida está BAJO_EL_PLAN"
Check (@($planGet.Body.repartoPorTipo).Count -eq 3) "repartoPorTipo trae NECESIDAD, DESEO y OTROS"

Step "16. Copia de mes y grupos T10c/T10d"
$mesSiguiente = (Get-Date).AddMonths(1).ToString("yyyy-MM")
$copia = Invoke-SaveUpApi -Method POST -Path "/api/v1/planes/$mesSiguiente/copiar-desde/$mes" -Token $token
Check $copia.Ok "POST copiar-desde responde 2xx"
Check (@($copia.Body.partidas).Count -eq 1) "La copia trae 1 partida"
Check ([decimal]$copia.Body.resumen.planificadoTotal -eq 300000) "La copia conserva el monto planificado"
Check ([decimal]$copia.Body.ingresoReferencia -eq 1000000) "La copia conserva el ingreso si el destino no tenía"
$copiadaId = $copia.Body.partidas[0].id

$delCopia = Invoke-SaveUpApi -Method DELETE -Path "/api/v1/planes/$mesSiguiente/partidas/$copiadaId" -Token $token
Check $delCopia.Ok "DELETE de la partida copiada responde 2xx"

$grupo = Invoke-SaveUpApi -Method POST -Path "/api/v1/planes/grupos" -Token $token -Body @{
    nombre = "Fijos smoke"; orden = 1
}
Check $grupo.Ok "POST /planes/grupos responde 2xx"
$grupoId = $grupo.Body.id
$grupos = Invoke-SaveUpApi -Method GET -Path "/api/v1/planes/grupos" -Token $token
Check (@($grupos.Body).Count -eq 1) "GET /planes/grupos devuelve 1 grupo"
$grupoEdit = Invoke-SaveUpApi -Method PUT -Path "/api/v1/planes/grupos/$grupoId" -Token $token -Body @{
    nombre = "Fijos smoke editado"
}
Check $grupoEdit.Ok "PUT /planes/grupos/{id} responde 2xx"
Check ($grupoEdit.Body.nombre -eq "Fijos smoke editado") "El grupo se renombró"
Check (Invoke-SaveUpApi -Method DELETE -Path "/api/v1/planes/grupos/$grupoId" -Token $token).Ok "DELETE /planes/grupos/{id} responde 2xx"

Step "17. Preferencias y sugerencia de regla T10d"
$prefs = Invoke-SaveUpApi -Method GET -Path "/api/v1/categorias/preferencias" -Token $token
Check $prefs.Ok "GET /categorias/preferencias responde 2xx"
Check (@($prefs.Body).Count -eq 18) "Las preferencias cubren las 18 categorías"
$ocioPref = @($prefs.Body) | Where-Object { $_.codigo -eq "OCIO_RECREACION" } | Select-Object -First 1
Check ($ocioPref.tipoPorDefecto -eq "DESEO") "OCIO_RECREACION es DESEO por defecto"

$putPref = Invoke-SaveUpApi -Method PUT -Path "/api/v1/categorias/$($ocio.id)/tipo-preferido" -Token $token -Body @{
    tipo = "NECESIDAD"
}
Check $putPref.Ok "PUT tipo-preferido responde 2xx"
Check ($putPref.Body.tipoEfectivo -eq "NECESIDAD") "El tipo efectivo pasa a NECESIDAD"
Check (Invoke-SaveUpApi -Method DELETE -Path "/api/v1/categorias/$($ocio.id)/tipo-preferido" -Token $token).Ok "DELETE tipo-preferido responde 2xx"

$regla = Invoke-SaveUpApi -Method GET -Path "/api/v1/presupuestos/sugerencia-regla" -Token $token
Check $regla.Ok "GET /presupuestos/sugerencia-regla responde 2xx"
Check ($null -eq $regla.Body.sugerencia) "Con menos de 2 meses cerrados, sugerencia es null"
Check ($regla.Body.motivo -eq "DATOS_INSUFICIENTES") "El motivo es DATOS_INSUFICIENTES"

Step "18. Metas de ahorro F7"
$fechaLimite = (Get-Date).AddMonths(6).ToString("yyyy-MM-dd")
$meta = Invoke-SaveUpApi -Method POST -Path "/api/v1/metas" -Token $token -Body @{
    nombre = "Meta smoke"; montoObjetivo = 100000; fechaLimite = $fechaLimite
}
Check $meta.Ok "POST /metas responde 2xx"
$metaId = $meta.Body.id
$metas = Invoke-SaveUpApi -Method GET -Path "/api/v1/metas/me" -Token $token
Check (@($metas.Body | Where-Object { $_.id -eq $metaId }).Count -eq 1) "La meta aparece en GET /metas/me"
Check ([decimal]$meta.Body.montoActual -eq 0) "La meta nace con montoActual 0"

$abonoMeta = Invoke-SaveUpApi -Method POST -Path "/api/v1/metas/$metaId/abonar" -Token $token -Body @{
    monto = 30000; descripcion = "Abono smoke"
}
Check $abonoMeta.Ok "POST /metas/{id}/abonar responde 2xx"
Check ([decimal]$abonoMeta.Body.montoActual -eq 30000) "montoActual = 30.000 tras el abono"

$retiroMeta = Invoke-SaveUpApi -Method POST -Path "/api/v1/metas/$metaId/retirar" -Token $token -Body @{
    monto = 10000; descripcion = "Retiro smoke"
}
Check $retiroMeta.Ok "POST /metas/{id}/retirar responde 2xx"
Check ([decimal]$retiroMeta.Body.montoActual -eq 20000) "montoActual = 20.000 tras el retiro"

$retiroExcesivo = Invoke-SaveUpApi -Method POST -Path "/api/v1/metas/$metaId/retirar" -Token $token -Body @{
    monto = 999999; descripcion = "Retiro excesivo"
}
Check ($retiroExcesivo.Status -eq 409) "Retirar más de lo ahorrado -> 409"
Check ($retiroExcesivo.Body.codigo -eq "CONFLICTO_REGLA_NEGOCIO") "codigo CONFLICTO_REGLA_NEGOCIO"

$metaEdit = Invoke-SaveUpApi -Method PUT -Path "/api/v1/metas/$metaId" -Token $token -Body @{
    nombre = "Meta smoke editada"; montoObjetivo = 120000; fechaLimite = $fechaLimite
}
Check ($metaEdit.Body.nombre -eq "Meta smoke editada") "PUT /metas/{id} actualiza el nombre"
Check (Invoke-SaveUpApi -Method DELETE -Path "/api/v1/metas/$metaId" -Token $token).Ok "DELETE /metas/{id} responde 2xx"
$metasFinal = Invoke-SaveUpApi -Method GET -Path "/api/v1/metas/me" -Token $token
Check (@($metasFinal.Body | Where-Object { $_.id -eq $metaId }).Count -eq 0) "La meta ya no aparece tras eliminarla"

Step "19. Reportes CSV/PDF F8"
$csvUri = "$BaseUrl/api/v1/reportes/movimientos/exportar?alcance=COMPLETO&formato=CSV"
try {
    $csvReporte = Invoke-WebRequest -Uri $csvUri -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing -TimeoutSec 30
    $csvOk = $true
} catch {
    $csvOk = $false
}
Check $csvOk "GET reporte CSV responde 2xx"
Check (([string]$csvReporte.Headers["Content-Type"]) -like "text/csv*") "Content-Type text/csv"
Check (([string]$csvReporte.Headers["Content-Disposition"]) -like "*reporte_saveup_completo.csv*") "Content-Disposition con nombre CSV"
Check ($csvReporte.Content -match "Fecha") "El CSV incluye cabecera"

$pdfPath = Join-Path $env:TEMP "saveup-smoke-reporte.pdf"
$pdfUri = "$BaseUrl/api/v1/reportes/movimientos/exportar?alcance=MENSUAL&formato=PDF&mes=$((Get-Date).Month)&anio=$((Get-Date).Year)"
try {
    Invoke-WebRequest -Uri $pdfUri -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing -OutFile $pdfPath -TimeoutSec 30
    $pdfOk = $true
} catch {
    $pdfOk = $false
}
Check $pdfOk "GET reporte PDF responde 2xx"
if ($pdfOk) {
    $pdfBytes = [System.IO.File]::ReadAllBytes($pdfPath)
    Check ($pdfBytes.Length -gt 100) "El PDF no está vacío"
    Check ([System.Text.Encoding]::ASCII.GetString($pdfBytes[0..3]) -eq "%PDF") "El PDF empieza con %PDF"
    Remove-Item $pdfPath -ErrorAction SilentlyContinue
}

$badReporte = Invoke-SaveUpApi -Method GET -Path "/api/v1/reportes/movimientos/exportar" -Token $token -Query @{
    alcance = "MENSUAL"; formato = "CSV"; mes = 13; anio = 2026
}
Check ($badReporte.Status -eq 400) "Mes fuera de rango -> 400"
Check ($badReporte.Body.codigo -eq "DATOS_INVALIDOS") "codigo DATOS_INVALIDOS"

# ---------------------------------------------------------------------------
Write-Host ""
if ($script:Failures -eq 0) {
    Write-Host "SMOKE TEST OK - todas las verificaciones pasaron." -ForegroundColor Green
    exit 0
} else {
    Write-Host "SMOKE TEST FALLÓ - $($script:Failures) verificación(es) fallida(s)." -ForegroundColor Red
    exit 1
}
