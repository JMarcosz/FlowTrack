[CmdletBinding()]
param(
    [string]$Serial = "1A131FDEE003EE"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$gradle = Join-Path $repoRoot "gradlew.bat"
$appApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$AdbArguments)

    & adb -s $Serial @AdbArguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB fallo durante la preparacion E2E."
    }
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "ADB no esta disponible en PATH."
}
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "No se encontro el Gradle wrapper."
}

$state = (& adb -s $Serial get-state 2>$null | Select-Object -First 1).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
    throw "El dispositivo E2E requerido no esta conectado o autorizado."
}

$modelOutput = & adb -s $Serial shell getprop ro.product.model
$modelExitCode = $LASTEXITCODE
$deviceOutput = & adb -s $Serial shell getprop ro.product.device
$deviceExitCode = $LASTEXITCODE
$modelName = ($modelOutput | Select-Object -First 1).Trim()
$deviceName = ($deviceOutput | Select-Object -First 1).Trim()
if (
    $modelExitCode -ne 0 -or
    $deviceExitCode -ne 0 -or
    $modelName -ne "Pixel 6 Pro" -or
    $deviceName -ne "raven"
) {
    throw "El serial indicado no corresponde al Pixel 6 Pro requerido."
}

Write-Host "Pixel 6 Pro verificado. Construyendo APKs de prueba..."
& $gradle :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) {
    throw "La construccion de APKs E2E fallo."
}

if (-not (Test-Path -LiteralPath $appApk -PathType Leaf)) {
    throw "No se genero el APK debug esperado."
}
if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) {
    throw "No se genero el APK androidTest esperado."
}

Write-Host "Instalando APKs en el dispositivo verificado..."
Invoke-Adb -AdbArguments @("install", "-r", "-t", $appApk)
Invoke-Adb -AdbArguments @("install", "-r", "-t", $testApk)
Write-Host "Preparacion Android E2E completada."
