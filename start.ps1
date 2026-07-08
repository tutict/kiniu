param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipInstall,
    [switch]$NoBrowser,
    [switch]$CleanLogs,
    [switch]$EnableDevtools,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000,
    [string]$LocalToken = $env:KINIU_LOCAL_TOKEN
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "kiniu-back"
$FrontendDir = Join-Path $Root "kiniu-front\nuxt-app"
$LogDir = Join-Path $Root "logs"
$RunDir = Join-Path $Root ".run"

function Write-Step([string]$Message) {
    Write-Host "[kiniu] $Message"
}

function Test-CommandAvailable([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-PortOpen([int]$Port) {
    $Client = [Net.Sockets.TcpClient]::new()
    try {
        $Async = $Client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $Async.AsyncWaitHandle.WaitOne(250)) { return $false }
        $Client.EndConnect($Async)
        return $true
    } catch {
        return $false
    } finally {
        $Client.Close()
    }
}

function Wait-Port([int]$Port, [string]$Name, [int]$TimeoutSeconds = 90) {
    $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $Deadline) {
        if (Test-PortOpen $Port) {
            Write-Step "$Name is listening on 127.0.0.1:$Port"
            return $true
        }
        Start-Sleep -Milliseconds 800
    }
    Write-Warning "$Name did not become ready on 127.0.0.1:$Port within ${TimeoutSeconds}s. Check logs."
    return $false
}

function Stop-ProcessTree([int]$ProcessId, [string]$Name) {
    $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $Process) { return }

    $Children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($Child in $Children) {
        Stop-ProcessTree -ProcessId ([int]$Child.ProcessId) -Name "$Name child"
    }

    Write-Step "Stopping stale $Name pid=$ProcessId"
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-PidFileProcess([string]$PidFile, [string]$Name) {
    if (-not (Test-Path -LiteralPath $PidFile)) { return }
    $PidText = Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    $ProcessId = 0
    if ([int]::TryParse($PidText, [ref]$ProcessId)) {
        Stop-ProcessTree -ProcessId $ProcessId -Name $Name
    }
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
}

function New-CmdSet([string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    if ($Value.Contains('"')) { throw "Environment value for $Name cannot contain double quotes." }
    $Escaped = $Value.Replace('^', '^^').Replace('&', '^&').Replace('|', '^|').Replace('<', '^<').Replace('>', '^>')
    return "set `"$Name=$Escaped`""
}

function Start-LoggedService(
    [string]$Name,
    [int]$Port,
    [string]$WorkingDirectory,
    [string]$Command,
    [string[]]$EnvironmentCommands
) {
    if (Test-PortOpen $Port) {
        Write-Step "$Name port $Port is already in use; leaving existing service alone."
        return $null
    }

    $StdoutPath = Join-Path $LogDir "$Name.log"
    $StderrPath = Join-Path $LogDir "$Name.err.log"
    $PidPath = Join-Path $RunDir "$Name.pid"
    $CommandParts = @($EnvironmentCommands | Where-Object { $_ }) + @($Command)
    $CmdLine = ($CommandParts -join ' && ')

    $Process = Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList @('/d', '/s', '/c', $CmdLine) `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $StdoutPath `
        -RedirectStandardError $StderrPath `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -LiteralPath $PidPath -Value $Process.Id -Encoding ascii
    Write-Step "Started $Name pid=$($Process.Id)"
    return $Process
}

function Stop-StartedProcesses([array]$Processes) {
    foreach ($Process in $Processes) {
        if ($Process -and -not $Process.HasExited) {
            Stop-ProcessTree -ProcessId $Process.Id -Name "started service"
        }
    }
}

if ($BackendOnly -and $FrontendOnly) {
    throw "BackendOnly and FrontendOnly cannot be used together."
}
if (-not (Test-Path -LiteralPath $BackendDir)) { throw "Missing backend directory: $BackendDir" }
if (-not (Test-Path -LiteralPath $FrontendDir)) { throw "Missing frontend directory: $FrontendDir" }

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

if ($CleanLogs) {
    Get-ChildItem -LiteralPath $LogDir -File -Filter "*.log" -ErrorAction SilentlyContinue | Remove-Item -Force
}

if (-not $FrontendOnly -and -not (Test-CommandAvailable "mvn")) {
    throw "mvn is not available. Install Maven or add it to PATH."
}
if (-not $BackendOnly -and -not (Test-CommandAvailable "npm.cmd")) {
    throw "npm.cmd is not available. Install Node.js or add it to PATH."
}

if (-not $FrontendOnly) { Stop-PidFileProcess (Join-Path $RunDir "backend.pid") "backend" }
if (-not $BackendOnly) { Stop-PidFileProcess (Join-Path $RunDir "frontend.pid") "frontend" }

if (-not $BackendOnly -and -not $SkipInstall -and -not (Test-Path -LiteralPath (Join-Path $FrontendDir "node_modules"))) {
    Write-Step "Installing frontend dependencies"
    Push-Location $FrontendDir
    try { npm.cmd install } finally { Pop-Location }
}

$Started = @()

if (-not $FrontendOnly) {
    $BackendEnv = @(
        (New-CmdSet "SERVER_PORT" ([string]$BackendPort)),
        (New-CmdSet "SERVER_ADDRESS" "127.0.0.1")
    )
    if ($LocalToken -and $LocalToken.Trim()) {
        $BackendEnv += New-CmdSet "KINIU_LOCAL_TOKEN" $LocalToken.Trim()
        Write-Step "Local token enabled; use the same value in the frontend settings."
    }
    $Started += Start-LoggedService -Name "backend" -Port $BackendPort -WorkingDirectory $BackendDir -Command "mvn spring-boot:run" -EnvironmentCommands $BackendEnv
}

if (-not $BackendOnly) {
    $DevtoolsValue = if ($EnableDevtools) { "true" } else { "false" }
    $FrontendEnv = @(
        (New-CmdSet "HOST" "127.0.0.1"),
        (New-CmdSet "PORT" ([string]$FrontendPort)),
        (New-CmdSet "NUXT_DEVTOOLS_ENABLED" $DevtoolsValue)
    )
    $Started += Start-LoggedService -Name "frontend" -Port $FrontendPort -WorkingDirectory $FrontendDir -Command "npm.cmd run dev -- --host 127.0.0.1 --port $FrontendPort" -EnvironmentCommands $FrontendEnv
}

$BackendReady = $true
$FrontendReady = $true
if (-not $FrontendOnly) { $BackendReady = Wait-Port $BackendPort "backend" }
if (-not $BackendOnly) { $FrontendReady = Wait-Port $FrontendPort "frontend" }

if (-not $BackendReady -or -not $FrontendReady) {
    Write-Warning "Startup did not fully complete. Stopping processes started by this run."
    Stop-StartedProcesses $Started
    exit 1
}

Write-Host ""
Write-Step "Startup complete"
if (-not $BackendOnly) { Write-Host "  Frontend: http://127.0.0.1:$FrontendPort" }
if (-not $FrontendOnly) { Write-Host "  Backend:  http://127.0.0.1:$BackendPort" }
Write-Host "  Logs:     $LogDir"
Write-Host "  PID files: $RunDir"
if (-not $BackendOnly -and $EnableDevtools) { Write-Host "  Nuxt DevTools: enabled" }
Write-Host ""
Write-Host "Stop commands:"
if (-not $FrontendOnly) { Write-Host "  Stop backend:  .\stop.ps1 -BackendOnly -BackendPort $BackendPort" }
if (-not $BackendOnly) { Write-Host "  Stop frontend: .\stop.ps1 -FrontendOnly -FrontendPort $FrontendPort" }

if (-not $NoBrowser -and -not $BackendOnly) {
    Start-Process -WindowStyle Hidden "http://127.0.0.1:$FrontendPort"
}