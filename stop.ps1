param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$CleanRunFiles,
    [switch]$ForcePortKill,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$')]
    [string]$RuntimeName = "default"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $Root "scripts\runtime-process.ps1")
$RunRoot = Join-Path $Root ".run"
$RunDir = if ($RuntimeName -eq "default") { $RunRoot } else { Join-Path $RunRoot $RuntimeName }

function Write-Step([string]$Message) {
    Write-Host "[kiniu] $Message"
}

function Stop-PidFile([string]$PidFile, [string]$Name, [string]$CommandMarker) {
    if (-not (Test-Path -LiteralPath $PidFile)) {
        Write-Step "$Name pid file not found"
        return
    }
    $Result = Stop-KiniuRecordedProcess -Path $PidFile -Name $Name -ExpectedCommandMarker $CommandMarker
    if ($Result.stopped) {
        Write-Step "Stopped $Name pid=$($Result.pid)"
    } elseif ($Result.reason -eq "not-running") {
        Write-Step "$Name pid=$($Result.pid) is not running"
    } elseif ($Result.reason -eq "invalid-record") {
        Write-Step "$Name pid file is invalid"
    } else {
        Write-Warning "Refusing to stop pid=$($Result.pid) for $Name because process identity did not match ($($Result.reason))."
    }
}

function Stop-PortOwner([int]$Port, [string]$Name) {
    if (-not (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
        return
    }

    $Connections = Get-NetTCPConnection -LocalAddress "127.0.0.1" -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($Connection in $Connections) {
        $OwningPid = [int]$Connection.OwningProcess
        if ($OwningPid -le 0 -or $OwningPid -eq $PID) { continue }
        Stop-KiniuProcessTree -ProcessId $OwningPid
        Write-Step "Stopped $Name port $Port owner pid=$OwningPid"
    }
}

if ($BackendOnly -and $FrontendOnly) {
    throw "BackendOnly and FrontendOnly cannot be used together."
}

if (Test-Path -LiteralPath $RunDir) {
    if (-not $FrontendOnly) { Stop-PidFile (Join-Path $RunDir "backend.pid") "backend" "spring-boot:run" }
    if (-not $BackendOnly) { Stop-PidFile (Join-Path $RunDir "frontend.pid") "frontend" "npm.cmd run dev" }
} else {
    Write-Step "No .run directory found"
}

if ($ForcePortKill) {
    if (-not $FrontendOnly) { Stop-PortOwner -Port $BackendPort -Name "backend" }
    if (-not $BackendOnly) { Stop-PortOwner -Port $FrontendPort -Name "frontend" }
}

if ($CleanRunFiles -and (Test-Path -LiteralPath $RunDir)) {
    $RemainingPidFile = Get-ChildItem -LiteralPath $RunDir -Filter "*.pid" -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($RemainingPidFile) {
        Write-Warning "Skipped cleanup for $RunDir because an unmatched PID record remains."
    } else {
        Remove-Item -LiteralPath $RunDir -Recurse -Force
        Write-Step "Cleaned runtime directory: $RunDir"
    }
}
