param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$CleanRunFiles,
    [switch]$ForcePortKill,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunDir = Join-Path $Root ".run"

function Write-Step([string]$Message) {
    Write-Host "[kiniu] $Message"
}

function Stop-ProcessTree([int]$ProcessId, [string]$Name) {
    $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $Process) {
        Write-Step "$Name pid=$ProcessId is not running"
        return
    }

    $Children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($Child in $Children) {
        Stop-ProcessTree -ProcessId ([int]$Child.ProcessId) -Name "$Name child"
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
    Write-Step "Stopped $Name pid=$ProcessId"
}

function Stop-PidFile([string]$PidFile, [string]$Name) {
    if (-not (Test-Path -LiteralPath $PidFile)) {
        Write-Step "$Name pid file not found"
        return
    }

    $PidText = Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    $ProcessId = 0
    if (-not [int]::TryParse($PidText, [ref]$ProcessId)) {
        Write-Step "$Name pid file is invalid"
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
        return
    }

    Stop-ProcessTree -ProcessId $ProcessId -Name $Name
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
}

function Stop-PortOwner([int]$Port, [string]$Name) {
    if (-not (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
        return
    }

    $Connections = Get-NetTCPConnection -LocalAddress "127.0.0.1" -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($Connection in $Connections) {
        $OwningPid = [int]$Connection.OwningProcess
        if ($OwningPid -le 0 -or $OwningPid -eq $PID) { continue }
        Stop-ProcessTree -ProcessId $OwningPid -Name "$Name port $Port owner"
    }
}

if ($BackendOnly -and $FrontendOnly) {
    throw "BackendOnly and FrontendOnly cannot be used together."
}

if (Test-Path -LiteralPath $RunDir) {
    if (-not $FrontendOnly) { Stop-PidFile (Join-Path $RunDir "backend.pid") "backend" }
    if (-not $BackendOnly) { Stop-PidFile (Join-Path $RunDir "frontend.pid") "frontend" }
} else {
    Write-Step "No .run directory found"
}

if ($ForcePortKill) {
    if (-not $FrontendOnly) { Stop-PortOwner -Port $BackendPort -Name "backend" }
    if (-not $BackendOnly) { Stop-PortOwner -Port $FrontendPort -Name "frontend" }
}

if ($CleanRunFiles -and (Test-Path -LiteralPath $RunDir)) {
    Get-ChildItem -LiteralPath $RunDir -File -ErrorAction SilentlyContinue | Remove-Item -Force
    Write-Step "Cleaned .run files"
}