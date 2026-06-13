[CmdletBinding()]
param(
    [string]$Serial = "1A131FDEE003EE",
    [string]$TestClass = "com.example.flowtrack.e2e.features.FeatureModulesE2ETest"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$prepareScript = Join-Path $repoRoot "scripts\e2e\Prepare-AndroidE2E.ps1"
$resultDir = Join-Path $repoRoot "app\build\e2e\features"
$resultFile = Join-Path $resultDir "feature-modules-instrumentation.txt"

if (-not (Test-Path -LiteralPath $prepareScript -PathType Leaf)) {
    throw "No se encontro el script de preparacion E2E."
}

New-Item -ItemType Directory -Path $resultDir -Force | Out-Null

& $prepareScript -Serial $Serial
if ($LASTEXITCODE -ne 0) {
    throw "La preparacion del Pixel fallo."
}

& adb -s $Serial shell am force-stop com.example.flowtrack
& adb -s $Serial shell input keyevent KEYCODE_HOME
& adb -s $Serial shell wm dismiss-keyguard

$instrumentationArgs = @(
    "-s", $Serial,
    "shell", "am", "instrument",
    "-w", "-r",
    "-e", "class", $TestClass,
    "com.example.flowtrack.test/androidx.test.runner.AndroidJUnitRunner"
)

$output = & adb @instrumentationArgs 2>&1
$exitCode = $LASTEXITCODE
$output | Set-Content -LiteralPath $resultFile -Encoding utf8
$output | Write-Host

if ($exitCode -ne 0 -or -not ($output -match "OK \(")) {
    throw "La suite de modulos fallo. Resultado: $resultFile"
}

Write-Host "Suite de modulos completada. Resultado sanitizado: $resultFile"
