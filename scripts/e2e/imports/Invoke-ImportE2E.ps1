[CmdletBinding()]
param(
    [string]$Serial = "1A131FDEE003EE",
    [string]$PackageName = "com.example.flowtrack",
    [int]$ImportTimeoutSeconds = 120,
    [string]$CaseFilter
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$e2eRoot = Join-Path $repoRoot "app\build\e2e"
$runnerComponent = "com.example.flowtrack/.MainActivity"
$remoteRoot = "/sdcard/Download/FlowTrack-E2E-Imports"
$remoteDump = "/sdcard/flowtrack-import-e2e.xml"

$fixtureDefinitions = @(
    [pscustomobject]@{ Case = "BANRESERVAS_PDF"; Bank = "BANRESERVAS"; Label = "BanReservas"; Source = "docs\03-fixtures\Banreservas.pdf"; Remote = "flowtrack_e2e_01.pdf" },
    [pscustomobject]@{ Case = "POPULAR_CSV"; Bank = "POPULAR"; Label = "Banco Popular"; Source = "docs\03-fixtures\Banco Popular Dominicano 026.csv"; Remote = "flowtrack_e2e_02.csv" },
    [pscustomobject]@{ Case = "POPULAR_PDF"; Bank = "POPULAR"; Label = "Banco Popular"; Source = "docs\03-fixtures\banco popular estado.pdf"; Remote = "flowtrack_e2e_03.pdf" },
    [pscustomobject]@{ Case = "QIK_PDF"; Bank = "QIK"; Label = "Qik"; Source = "docs\03-fixtures\Qik.pdf"; Remote = "flowtrack_e2e_04.pdf" },
    [pscustomobject]@{ Case = "CIBAO_XLS"; Bank = "CIBAO"; Label = "Asociacion Cibao"; Source = "docs\03-fixtures\Asociacion Cibao.xls"; Remote = "flowtrack_e2e_05.xls" },
    [pscustomobject]@{ Case = "BHD_PDF"; Bank = "BHD"; Label = "BHD Leon"; Source = "docs\03-fixtures\bhd.pdf"; Remote = "flowtrack_e2e_06.pdf" }
)

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & adb -s $Serial @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "ADB command failed."
    }
    return $output
}

function Get-LatestRunDirectory {
    $latest = Get-ChildItem -LiteralPath $e2eRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latest) {
        throw "No E2E run directory was found."
    }
    return $latest.FullName
}

function Get-CenterFromBounds {
    param([Parameter(Mandatory = $true)][string]$Bounds)

    if ($Bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Invalid UI bounds."
    }
    $left = [int]$matches[1]
    $top = [int]$matches[2]
    $right = [int]$matches[3]
    $bottom = [int]$matches[4]
    return [pscustomobject]@{
        X = [int](($left + $right) / 2)
        Y = [int](($top + $bottom) / 2)
    }
}

function Get-UiDocument {
    Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remoteDump) | Out-Null
    Invoke-Adb -Arguments @("pull", $remoteDump, $script:dumpPath) | Out-Null
    return [xml](Get-Content -LiteralPath $script:dumpPath -Raw)
}

function Find-UiNode {
    param(
        [Parameter(Mandatory = $true)][xml]$Document,
        [string]$Text,
        [string]$Description,
        [string]$TextContains
    )

    foreach ($node in $Document.SelectNodes("//node")) {
        if ($Text -and $node.text -eq $Text) { return $node }
        if ($Description -and $node.'content-desc' -eq $Description) { return $node }
        if ($TextContains -and $node.text -like "*$TextContains*") { return $node }
    }
    return $null
}

function Tap-UiNode {
    param([Parameter(Mandatory = $true)]$Node)

    $target = $Node
    while (
        $null -ne $target.ParentNode -and
        $target.Name -eq "node" -and
        $target.clickable -ne "true"
    ) {
        $target = $target.ParentNode
    }
    if ($target.Name -ne "node") {
        $target = $Node
    }
    $center = Get-CenterFromBounds -Bounds $target.bounds
    Invoke-Adb -Arguments @("shell", "input", "tap", "$($center.X)", "$($center.Y)") | Out-Null
    Start-Sleep -Milliseconds 800
}

function Find-AndTap {
    param(
        [string]$Text,
        [string]$Description,
        [string]$TextContains,
        [int]$ScrollAttempts = 0
    )

    for ($attempt = 0; $attempt -le $ScrollAttempts; $attempt++) {
        $document = Get-UiDocument
        $node = Find-UiNode -Document $document -Text $Text -Description $Description -TextContains $TextContains
        if ($null -ne $node) {
            Tap-UiNode -Node $node
            return $true
        }
        if ($attempt -lt $ScrollAttempts) {
            Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "1900", "540", "600", "450") | Out-Null
            Start-Sleep -Milliseconds 600
        }
    }
    return $false
}

function Wait-ForKnownNode {
    param(
        [string]$Text,
        [string]$Description,
        [string]$TextContains,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $document = Get-UiDocument
        $node = Find-UiNode -Document $document -Text $Text -Description $Description -TextContains $TextContains
        if ($null -ne $node) { return $node }
        Start-Sleep -Seconds 1
    }
    return $null
}

function Open-ImportScreen {
    Invoke-Adb -Arguments @("shell", "am", "force-stop", "com.google.android.documentsui") -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_HOME") | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "start", "-S", "-W", "-n", $runnerComponent) | Out-Null
    $bottomNavigation = Wait-ForKnownNode -Text "Transacciones" -TimeoutSeconds 20
    if ($null -eq $bottomNavigation) {
        throw "NAVIGATION_BLOCKED"
    }
    Tap-UiNode -Node $bottomNavigation
    $importButton = Wait-ForKnownNode -Description "Importar" -TimeoutSeconds 15
    if ($null -eq $importButton) {
        throw "IMPORT_ENTRY_BLOCKED"
    }
    Tap-UiNode -Node $importButton
    if ($null -eq (Wait-ForKnownNode -Text "Importar estado" -TimeoutSeconds 15)) {
        throw "IMPORT_SCREEN_BLOCKED"
    }
}

function Select-Bank {
    param([Parameter(Mandatory = $true)][string]$Bank)

    $labelCandidates = switch ($Bank) {
        "BANRESERVAS" { @("BanReservas") }
        "POPULAR" { @("Banco Popular") }
        "QIK" { @("Qik") }
        "CIBAO" { @("Asociacion Cibao", "Asociación Cibao") }
        "BHD" { @("BHD Leon", "BHD León") }
        default { @() }
    }
    foreach ($candidate in $labelCandidates) {
        if (Find-AndTap -Text $candidate -ScrollAttempts 4) { return }
    }
    throw "BANK_SELECTOR_BLOCKED"
}

function Open-DocumentPicker {
    if (Find-AndTap -TextContains "Toca para seleccionar archivo" -ScrollAttempts 6) {
        Start-Sleep -Seconds 1
        return
    }

    $document = Get-UiDocument
    $heading = Find-UiNode -Document $document -TextContains "Selecciona el archivo"
    if ($null -eq $heading) { throw "FILE_PICKER_ENTRY_BLOCKED" }
    if ($heading.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "FILE_PICKER_ENTRY_BLOCKED"
    }
    $tapY = [Math]::Min(([int]$matches[4] + 180), 2150)
    Invoke-Adb -Arguments @("shell", "input", "tap", "540", "$tapY") | Out-Null
    Start-Sleep -Seconds 1
}

function Select-Document {
    param([Parameter(Mandatory = $true)][string]$FileName)

    if (Find-AndTap -Text $FileName -ScrollAttempts 2) { return }

    $document = Get-UiDocument
    $roots = Find-UiNode -Document $document -Description "Show roots"
    if ($null -eq $roots) {
        $roots = Find-UiNode -Document $document -Description "Mostrar raíces"
    }
    if ($null -ne $roots) {
        Tap-UiNode -Node $roots
        $openedDownloads = Find-AndTap -Text "Downloads" -ScrollAttempts 2
        if (-not $openedDownloads) {
            $openedDownloads = Find-AndTap -Text "Descargas" -ScrollAttempts 2
        }
        if ($openedDownloads) {
            $null = Find-AndTap -Text "FlowTrack-E2E-Imports" -ScrollAttempts 3
            if (Find-AndTap -Text $FileName -ScrollAttempts 5) { return }
        }
    }
    throw "DOCUMENT_SELECTION_BLOCKED"
}

function Wait-ForImportResult {
    $deadline = (Get-Date).AddSeconds($ImportTimeoutSeconds)
    $processingSeen = $false
    while ((Get-Date) -lt $deadline) {
        $document = Get-UiDocument
        $nodes = $document.SelectNodes("//node")
        foreach ($node in $nodes) {
            $text = [string]$node.text
            if ($text -eq "Procesando archivo...") {
                $processingSeen = $true
            }
            if ($text -match '^(\d+) transacciones importadas desde ') {
                return [pscustomobject]@{
                    Status = "SUCCESS"
                    Count = [int]$matches[1]
                }
            }
            if ($text -eq "Documento protegido") {
                return [pscustomobject]@{
                    Status = "BLOCKED_PROTECTED_DOCUMENT"
                    Count = $null
                }
            }
        }

        if ($processingSeen) {
            $form = Find-UiNode -Document $document -TextContains "Toca para seleccionar archivo"
            if ($null -ne $form) {
                return [pscustomobject]@{
                    Status = "ERROR"
                    Count = $null
                }
            }
        }
        Start-Sleep -Seconds 1
    }
    return [pscustomobject]@{
        Status = "BLOCKED_TIMEOUT"
        Count = $null
    }
}

function Copy-AppDatabase {
    param([Parameter(Mandatory = $true)][string]$Destination)

    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = "adb"
    $startInfo.Arguments = "-s $Serial exec-out run-as $PackageName cat databases/flowtrack_offline.db"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $stream = [System.IO.File]::Create($Destination)
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
    } finally {
        $stream.Dispose()
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "Could not capture the offline database."
    }
}

function Get-CargaIds {
    param([Parameter(Mandatory = $true)][string]$DatabasePath)

    if (-not (Test-Path -LiteralPath $DatabasePath)) { return @() }
    $ids = & sqlite3 $DatabasePath "select entity_id from records where entity_type='CARGA' order by entity_id;"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not query carga IDs."
    }
    return @($ids | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_.Trim() })
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "ADB is not available in PATH."
}
if (-not (Get-Command sqlite3 -ErrorAction SilentlyContinue)) {
    throw "sqlite3 is not available in PATH."
}

$state = (& adb -s $Serial get-state 2>$null | Select-Object -First 1).Trim()
if ($state -ne "device") {
    throw "The requested device is not connected."
}
$model = (Invoke-Adb -Arguments @("shell", "getprop", "ro.product.model") | Select-Object -First 1).Trim()
if ($model -ne "Pixel 6 Pro") {
    throw "The requested serial is not the expected Pixel 6 Pro."
}

$runDirectory = Get-LatestRunDirectory
$script:dumpPath = Join-Path $runDirectory "import-ui-dump.xml"
$resultFileName = if ($CaseFilter) {
    "import-results-$($CaseFilter.ToLowerInvariant()).json"
} else {
    "import-results.json"
}
$resultsPath = Join-Path $runDirectory $resultFileName
$currentDbPath = Join-Path $runDirectory "post-import-offline.db"
$newIdsPath = Join-Path $runDirectory "new-carga-ids.json"
$baselineIdsPath = Join-Path $runDirectory "baseline-cargas.json"
$results = [System.Collections.Generic.List[object]]::new()

$baselineDb = Join-Path $runDirectory "baseline-offline.db"
$baselineIds = if (Test-Path -LiteralPath $baselineIdsPath) {
    $baselineDocument = Get-Content -LiteralPath $baselineIdsPath -Raw | ConvertFrom-Json
    if ($null -eq $baselineDocument.ids) { @() } else { @($baselineDocument.ids) }
} elseif (Test-Path -LiteralPath $baselineDb) {
    @(Get-CargaIds -DatabasePath $baselineDb)
} else {
    @()
}
[pscustomobject]@{ ids = @($baselineIds) } |
    ConvertTo-Json -Depth 3 |
    Set-Content -LiteralPath $baselineIdsPath -Encoding utf8

try {
    Invoke-Adb -Arguments @("shell", "rm", "-rf", $remoteRoot) | Out-Null
    Invoke-Adb -Arguments @("shell", "mkdir", "-p", $remoteRoot) | Out-Null

    [array]$selectedFixtures = if ($CaseFilter) {
        @($fixtureDefinitions | Where-Object { $_.Case -eq $CaseFilter })
    } else {
        $fixtureDefinitions
    }
    if ($selectedFixtures.Count -eq 0) {
        throw "Unknown import case filter."
    }

    foreach ($fixture in $selectedFixtures) {
        $stage = "fixture_check"
        $sourcePath = Join-Path $repoRoot $fixture.Source
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            $results.Add([pscustomobject]@{
                case = $fixture.Case
                bank = $fixture.Bank
                status = "BLOCKED_FIXTURE_MISSING"
                count = $null
            })
            continue
        }

        $remotePath = "$remoteRoot/$($fixture.Remote)"
        try {
            $stage = "fixture_push"
            Invoke-Adb -Arguments @("push", $sourcePath, $remotePath) | Out-Null
            Invoke-Adb -Arguments @(
                "shell", "am", "broadcast",
                "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                "-d", "file://$remotePath"
            ) | Out-Null

            $stage = "open_import"
            Open-ImportScreen
            $stage = "select_bank"
            Select-Bank -Bank $fixture.Bank
            $stage = "open_picker"
            Open-DocumentPicker
            $stage = "select_document"
            Select-Document -FileName $fixture.Remote
            $stage = "wait_result"
            $result = Wait-ForImportResult
            $results.Add([pscustomobject]@{
                case = $fixture.Case
                bank = $fixture.Bank
                status = $result.Status
                count = $result.Count
            })
        } catch {
            Write-Verbose (
                "Bank {0} failed at {1} with {2} (line {3})." -f
                $fixture.Bank,
                $stage,
                $_.Exception.GetType().Name,
                $_.InvocationInfo.ScriptLineNumber
            )
            $status = if ($_.Exception.Message -match '^[A-Z_]+$') {
                "BLOCKED_$($_.Exception.Message)"
            } else {
                "BLOCKED_AUTOMATION"
            }
            $results.Add([pscustomobject]@{
                case = $fixture.Case
                bank = $fixture.Bank
                status = $status
                count = $null
            })
        } finally {
            Invoke-Adb -Arguments @("shell", "rm", "-f", $remotePath) -AllowFailure | Out-Null
            Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteDump) -AllowFailure | Out-Null
            Remove-Item -LiteralPath $script:dumpPath -Force -ErrorAction SilentlyContinue
        }
    }

    Copy-AppDatabase -Destination $currentDbPath
    $currentIds = @(Get-CargaIds -DatabasePath $currentDbPath)
    $baselineSet = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($baselineId in $baselineIds) {
        $null = $baselineSet.Add([string]$baselineId)
    }
    $newIds = @($currentIds | Where-Object { -not $baselineSet.Contains($_) })
    [pscustomobject]@{ ids = $newIds } |
        ConvertTo-Json -Depth 3 |
        Set-Content -LiteralPath $newIdsPath -Encoding utf8
} finally {
    $results |
        ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $resultsPath -Encoding utf8
    Invoke-Adb -Arguments @("shell", "rm", "-rf", $remoteRoot) -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteDump) -AllowFailure | Out-Null
    Remove-Item -LiteralPath $script:dumpPath -Force -ErrorAction SilentlyContinue
}

$results | Format-Table case, bank, status, count -AutoSize
Write-Host "Results: $resultsPath"
Write-Host "New carga IDs: $newIdsPath"
