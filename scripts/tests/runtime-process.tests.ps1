$ErrorActionPreference = "Stop"

$ScriptsDir = Split-Path -Parent $PSScriptRoot
$RepositoryRoot = Split-Path -Parent $ScriptsDir
. (Join-Path $ScriptsDir "runtime-process.ps1")

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$TestRoot = Join-Path $RepositoryRoot ("output\runtime-process-tests\" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $TestRoot | Out-Null
$RecordPath = Join-Path $TestRoot "test.pid"
$Marker = "Start-Sleep -Seconds 30"
$Process = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-Command", $Marker -WindowStyle Hidden -PassThru

try {
    Write-KiniuProcessRecord -Path $RecordPath -Process $Process -Name "test" -CommandMarker $Marker
    $Record = Get-Content -LiteralPath $RecordPath -Raw | ConvertFrom-Json
    $Record.startTimeFileTimeUtc = "0"
    $Record | ConvertTo-Json -Compress | Set-Content -LiteralPath $RecordPath -Encoding utf8

    $Rejected = Stop-KiniuRecordedProcess -Path $RecordPath -Name "test" -ExpectedCommandMarker $Marker
    Assert-True (-not $Rejected.stopped) "A mismatched process identity must not be stopped."
    Assert-True ($null -ne (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) "The mismatched process should still be running."

    Write-KiniuProcessRecord -Path $RecordPath -Process $Process -Name "test" -CommandMarker $Marker
    $IncompleteRecord = Get-Content -LiteralPath $RecordPath -Raw | ConvertFrom-Json
    $IncompleteRecord.PSObject.Properties.Remove("startTimeFileTimeUtc")
    $IncompleteRecord | ConvertTo-Json -Compress | Set-Content -LiteralPath $RecordPath -Encoding utf8
    $Incomplete = Stop-KiniuRecordedProcess -Path $RecordPath -Name "test" -ExpectedCommandMarker $Marker
    Assert-True (-not $Incomplete.stopped) "An incomplete versioned process record must not be trusted."
    Assert-True ($Incomplete.reason -eq "invalid-record") "An incomplete process record should be rejected as invalid."
    Assert-True ($null -ne (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) "A process referenced by an incomplete record should still be running."

    Set-Content -LiteralPath $RecordPath -Value $Process.Id -Encoding ascii
    $Legacy = Stop-KiniuRecordedProcess -Path $RecordPath -Name "test" -ExpectedCommandMarker $Marker
    Assert-True (-not $Legacy.stopped) "A legacy PID-only record must not be trusted."
    Assert-True ($Legacy.reason -eq "legacy-record") "A legacy PID-only record should report why it was rejected."
    Assert-True ($null -ne (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) "A process referenced only by a legacy PID record should still be running."

    Write-KiniuProcessRecord -Path $RecordPath -Process $Process -Name "test" -CommandMarker $Marker
    $Stopped = Stop-KiniuRecordedProcess -Path $RecordPath -Name "test" -ExpectedCommandMarker $Marker
    $Process.WaitForExit(5000) | Out-Null
    Assert-True $Stopped.stopped "A matching recorded process should be stopped."
    Assert-True ($null -eq (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) "The matching process should no longer be running."
    Write-Output "runtime-process identity tests passed"
} finally {
    if ($null -ne (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $Process.Id -Force
    }
    Remove-Item -LiteralPath $TestRoot -Recurse -Force -ErrorAction SilentlyContinue
}
