[CmdletBinding()]
param(
    [string]$Serial = "1A131FDEE003EE",
    [string]$TestClass = "com.example.flowtrack.e2e.system.SystemLifecycleE2ETest"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$prepareScript = Join-Path $repoRoot "scripts\e2e\Prepare-AndroidE2E.ps1"
$resultDir = Join-Path $repoRoot "app\build\e2e\system"
$packageName = "com.example.flowtrack"
$runner = "com.example.flowtrack.test/androidx.test.runner.AndroidJUnitRunner"
$permission = "android.permission.POST_NOTIFICATIONS"

New-Item -ItemType Directory -Path $resultDir -Force | Out-Null

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB fallo: adb -s $Serial $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Invoke-Instrumentation {
    param(
        [string]$Method,
        [hashtable]$ExtraArgs = @{}
    )
    $selector = "$TestClass#$Method"
    $args = @("shell", "am", "instrument", "-w", "-r", "-e", "class", $selector)
    foreach ($entry in $ExtraArgs.GetEnumerator()) {
        $args += @("-e", [string]$entry.Key, [string]$entry.Value)
    }
    $args += $runner

    $output = Invoke-Adb @args
    $resultFile = Join-Path $resultDir "$Method.txt"
    $output | Set-Content -LiteralPath $resultFile -Encoding utf8
    $output | Write-Host
    if (-not ($output -match "OK \(")) {
        throw "La prueba $selector fallo. Resultado: $resultFile"
    }
}

function Get-PermissionGranted {
    $dump = Invoke-Adb shell dumpsys package $packageName
    $runtimeLine = $dump | Select-String -Pattern "android.permission.POST_NOTIFICATIONS: granted="
    return [bool]($runtimeLine -match "granted=true")
}

function Get-WifiEnabled {
    $status = Invoke-Adb shell cmd wifi status
    return [bool]($status -match "Wi-Fi is enabled")
}

function Get-MobileDataEnabled {
    $value = (Invoke-Adb shell settings get global mobile_data | Select-Object -Last 1).Trim()
    return $value -eq "1"
}

function Save-SanitizedDumps {
    param([string]$Prefix)
    (Invoke-Adb shell dumpsys notification --noredact |
        Select-String -Pattern "com.example.flowtrack|recordatorios_pago|resumenes|alertas|push" -Context 1,2) |
        Set-Content -LiteralPath (Join-Path $resultDir "$Prefix-notification.txt") -Encoding utf8
    (Invoke-Adb shell dumpsys jobscheduler |
        Select-String -Pattern "com.example.flowtrack|SystemJobService" -Context 1,3) |
        Set-Content -LiteralPath (Join-Path $resultDir "$Prefix-jobscheduler.txt") -Encoding utf8
    (Invoke-Adb shell dumpsys alarm |
        Select-String -Pattern "com.example.flowtrack|NotificationAlarmReceiver" -Context 1,3) |
        Set-Content -LiteralPath (Join-Path $resultDir "$Prefix-alarm.txt") -Encoding utf8
}

function Test-SystemBroadcast {
    param(
        [string]$Action,
        [string]$Name
    )
    $output = & adb -s $Serial shell am broadcast -a $Action -n `
        "$packageName/.core.notifications.NotificationBootReceiver" 2>&1
    $resultFile = Join-Path $resultDir "broadcast-$Name.txt"
    $output | Set-Content -LiteralPath $resultFile -Encoding utf8
    if ($LASTEXITCODE -eq 0 -and $output -match "Broadcast completed") {
        Write-Host "PASS broadcast $Action"
    } else {
        Write-Host "BLOCKED broadcast $Action por proteccion del sistema. Ver $resultFile"
    }
}

if (-not (Test-Path -LiteralPath $prepareScript -PathType Leaf)) {
    throw "No se encontro el script de preparacion E2E."
}

$permissionInitiallyGranted = $false
$wifiInitiallyEnabled = $false
$mobileDataInitiallyEnabled = $false

try {
    & $prepareScript -Serial $Serial
    if ($LASTEXITCODE -ne 0) {
        throw "La preparacion del Pixel fallo."
    }

    $model = (Invoke-Adb shell getprop ro.product.model | Select-Object -Last 1).Trim()
    if ($model -ne "Pixel 6 Pro") {
        throw "Se esperaba Pixel 6 Pro y se detecto '$model'."
    }

    $permissionInitiallyGranted = Get-PermissionGranted
    $wifiInitiallyEnabled = Get-WifiEnabled
    $mobileDataInitiallyEnabled = Get-MobileDataEnabled
    Save-SanitizedDumps -Prefix "before"

    Invoke-Adb shell pm revoke $packageName $permission | Out-Null
    Invoke-Instrumentation -Method "permisoNotificaciones_coincideConEstadoPreparadoPorAdb" `
        -ExtraArgs @{ expectedNotificationPermission = "false" }

    Invoke-Adb shell pm grant $packageName $permission | Out-Null
    Invoke-Instrumentation -Method "canalesNotificacion_estanRegistradosConImportanciaValida"
    Invoke-Instrumentation -Method "permisoNotificaciones_coincideConEstadoPreparadoPorAdb" `
        -ExtraArgs @{ expectedNotificationPermission = "true" }
    Invoke-Instrumentation -Method "pantallaNotificaciones_esAccesibleSinModificarPreferencias"
    Invoke-Instrumentation -Method "pruebaSintetica_conPermisoConcedidoSePublicaYSeLimpia"
    Invoke-Instrumentation -Method "rutasDeNotificacion_abrenDestinosPublicosEsperados"

    Invoke-Adb shell am force-stop $packageName | Out-Null
    Invoke-Adb shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Instrumentation -Method "relanzamiento_conservaSesionYDashboard"

    if ($wifiInitiallyEnabled) {
        Invoke-Adb shell svc wifi disable | Out-Null
    }
    if ($mobileDataInitiallyEnabled) {
        Invoke-Adb shell svc data disable | Out-Null
    }
    Start-Sleep -Seconds 3
    Invoke-Instrumentation -Method "lecturaOffline_muestraDashboardYTransaccionesDesdeCache" `
        -ExtraArgs @{ expectedOffline = "true" }

    Test-SystemBroadcast -Action "android.intent.action.TIMEZONE_CHANGED" -Name "timezone"
    Test-SystemBroadcast -Action "android.intent.action.BOOT_COMPLETED" -Name "boot"
    Save-SanitizedDumps -Prefix "after"
} finally {
    if ($wifiInitiallyEnabled) {
        & adb -s $Serial shell svc wifi enable | Out-Null
    } else {
        & adb -s $Serial shell svc wifi disable | Out-Null
    }
    if ($mobileDataInitiallyEnabled) {
        & adb -s $Serial shell svc data enable | Out-Null
    } else {
        & adb -s $Serial shell svc data disable | Out-Null
    }
    if ($permissionInitiallyGranted) {
        & adb -s $Serial shell pm grant $packageName $permission | Out-Null
    } else {
        & adb -s $Serial shell pm revoke $packageName $permission | Out-Null
    }
    & adb -s $Serial shell am force-stop $packageName | Out-Null
    & adb -s $Serial shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 | Out-Null
}

Write-Host "Suite de sistema completada. Evidencias sanitizadas: $resultDir"
