param(
    [int]$Runs = 3
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Median([double[]]$values) {
    $sorted = $values | Sort-Object
    return $sorted[[int]([math]::Floor(($sorted.Count - 1) / 2))]
}

function Measure-Assemble([string]$processor, [string]$label) {
    Write-Host "=== $label ($processor) ==="
    $times = @()
    for ($i = 1; $i -le $Runs; $i++) {
        Write-Host "Run $i/$Runs ..."
        & .\gradlew.bat --stop | Out-Null
        if (Test-Path ".\build") { Remove-Item -Recurse -Force ".\build" }
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        & .\gradlew.bat assembleDebug --no-build-cache "-Paa.processor=$processor" | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "assembleDebug failed for processor=$processor"
        }
        $sw.Stop()
        $seconds = [math]::Round($sw.Elapsed.TotalSeconds, 2)
        $times += $seconds
        Write-Host "  elapsed=${seconds}s"
    }
    return [pscustomobject]@{
        Processor = $processor
        Label = $label
        Runs = ($times -join ", ")
        MedianSeconds = (Median $times)
    }
}

$results = @()
$results += Measure-Assemble -processor "kapt" -label "Before (APT via kapt)"
$results += Measure-Assemble -processor "ksp" -label "After (KSP MVP)"

$results | Format-Table -AutoSize
$results | ConvertTo-Json | Set-Content -Encoding UTF8 ".\benchmark-results.json"
Write-Host "Wrote benchmark-results.json"
