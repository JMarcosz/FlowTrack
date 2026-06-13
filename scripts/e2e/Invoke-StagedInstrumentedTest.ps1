[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$FixturePath,

    [string]$TestClass =
        "com.example.flowtrack.data.parsers.bhd.BhdPdfParserInstrumentedTest",

    [string]$InstrumentationArgument = "bhdFixturePath",
    [string]$Serial = "1A131FDEE003EE",
    [switch]$SkipBuildInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$packageName = "com.example.flowtrack"
$runnerComponent =
    "com.example.flowtrack.test/androidx.test.runner.AndroidJUnitRunner"
$stagedName = "flowtrack-e2e-staged.pdf"
$remoteTemporary = "/data/local/tmp/$stagedName"
$targetRelative = "cache/$stagedName"
$prepareScript = Join-Path $PSScriptRoot "Prepare-AndroidE2E.ps1"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$AdbArguments)

    $output = & adb -s $Serial @AdbArguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB fallo durante la ejecucion E2E."
    }
    return $output
}

function Remove-StagedFixture {
    & adb -s $Serial shell run-as $packageName rm -f $targetRelative 2>$null | Out-Null
    & adb -s $Serial shell rm -f $remoteTemporary 2>$null | Out-Null
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "ADB no esta disponible en PATH."
}

if (-not $SkipBuildInstall) {
    & $prepareScript -Serial $Serial
    if ($LASTEXITCODE -ne 0) {
        throw "La preparacion Android E2E fallo."
    }
} else {
    $state = (& adb -s $Serial get-state 2>$null | Select-Object -First 1).Trim()
    if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
        throw "El dispositivo E2E requerido no esta conectado o autorizado."
    }
}

$fixture = (Resolve-Path -LiteralPath $FixturePath).Path

try {
    Remove-StagedFixture
    Write-Host "Transfiriendo fixture de forma temporal..."
    Invoke-Adb -AdbArguments @("push", $fixture, $remoteTemporary) | Out-Null
    Invoke-Adb -AdbArguments @(
        "shell", "run-as", $packageName, "mkdir", "-p", "cache"
    ) | Out-Null
    Invoke-Adb -AdbArguments @(
        "shell", "run-as", $packageName, "cp", $remoteTemporary, $targetRelative
    ) | Out-Null

    Write-Host "Ejecutando la clase instrumentada..."
    $result = Invoke-Adb -AdbArguments @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", $TestClass,
        "-e", $InstrumentationArgument, $stagedName,
        $runnerComponent
    )
    $result | Write-Output

    $summary = $result -join "`n"
    if ($summary -match "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=") {
        throw "La prueba instrumentada reporto un fallo."
    }
    if ($summary -notmatch "OK \(\d+ test") {
        throw "La prueba instrumentada no reporto una finalizacion correcta."
    }
} finally {
    Write-Host "Eliminando fixtures temporales del dispositivo..."
    Remove-StagedFixture
}
