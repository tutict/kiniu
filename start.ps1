param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipInstall,
    [switch]$NoBrowser,
    [switch]$CleanLogs,
    [switch]$EnableDevtools,
    [switch]$NoLocalToken,
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
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"

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
function Test-NuxtDevServerRunning {
    $ExistingNode = Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -and $_.CommandLine.Contains($FrontendDir) -and $_.CommandLine.Contains("nuxt") -and $_.CommandLine.Contains("dev")
    } | Select-Object -First 1
    if ($ExistingNode) { return $true }

    $LockCandidates = @(
        (Join-Path $FrontendDir ".nuxt\nuxt.lock"),
        (Join-Path $FrontendDir ".nuxt\dev\server.lock"),
        (Join-Path $FrontendDir "node_modules\.cache\nuxt\.nuxt\dev\server.lock")
    )
    foreach ($LockPath in $LockCandidates) {
        if (-not (Test-Path -LiteralPath $LockPath)) { continue }
        try {
            $Lock = Get-Content -LiteralPath $LockPath -Raw | ConvertFrom-Json
            if ($Lock.pid -and (Get-Process -Id ([int]$Lock.pid) -ErrorAction SilentlyContinue)) {
                return $true
            }
            Remove-Item -LiteralPath $LockPath -Force -ErrorAction Stop
            Write-Step "Removed stale Nuxt dev lock: $LockPath"
        } catch {
            throw "Cannot verify Nuxt dev lock ${LockPath}: $($_.Exception.Message)"
        }
    }
    return $false
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

function New-LocalToken {
    $Bytes = [byte[]]::new(24)
    $Generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $Generator.GetBytes($Bytes)
    } finally {
        $Generator.Dispose()
    }
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
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
        throw "$Name port $Port is already in use. Stop the existing service or choose another port."
    }

    $StdoutPath = Join-Path $LogDir "$Name.$RunStamp.log"
    $StderrPath = Join-Path $LogDir "$Name.$RunStamp.err.log"
    $PidPath = Join-Path $RunDir "$Name.pid"
    $CommandParts = @($EnvironmentCommands | Where-Object { $_ }) + @($Command)
    $CmdLine = ($CommandParts -join ' && ')

    $RedirectedCmdLine = "$CmdLine 1> `"$StdoutPath`" 2> `"$StderrPath`""
    $StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $StartInfo.FileName = "cmd.exe"
    $StartInfo.Arguments = "/d /s /c `"$RedirectedCmdLine`""
    $StartInfo.WorkingDirectory = $WorkingDirectory
    $StartInfo.UseShellExecute = $false
    $StartInfo.CreateNoWindow = $true

    $Process = [System.Diagnostics.Process]::Start($StartInfo)
    if (-not $Process) { throw "Failed to start $Name." }

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
    Get-ChildItem -LiteralPath $LogDir -File -Filter "*.log" -ErrorAction SilentlyContinue | ForEach-Object {
        $LogFile = $_.FullName
        try {
            Remove-Item -LiteralPath $LogFile -Force -ErrorAction Stop
        } catch {
            Write-Warning "Could not remove log file ${LogFile}: $($_.Exception.Message)"
        }
    }
}

if (-not $FrontendOnly -and -not (Test-CommandAvailable "mvn")) {
    throw "mvn is not available. Install Maven or add it to PATH."
}
if (-not $BackendOnly -and -not (Test-CommandAvailable "npm.cmd")) {
    throw "npm.cmd is not available. Install Node.js or add it to PATH."
}
if (-not $BackendOnly -and (Test-NuxtDevServerRunning)) {
    throw "A Nuxt dev server is already running for $FrontendDir. Stop it before starting another frontend instance."
}

if (-not $FrontendOnly) { Stop-PidFileProcess (Join-Path $RunDir "backend.pid") "backend" }
if (-not $BackendOnly) { Stop-PidFileProcess (Join-Path $RunDir "frontend.pid") "frontend" }

if (-not $FrontendOnly -and -not $NoLocalToken -and [string]::IsNullOrWhiteSpace($LocalToken)) {
    $LocalToken = New-LocalToken
    Set-Content -LiteralPath (Join-Path $RunDir "local-token") -Value $LocalToken -Encoding ascii
    Write-Step "Generated local access token in .run\\local-token"
}

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
if (-not $FrontendOnly -and -not $NoLocalToken) { Write-Host "  Token:    $RunDir\\local-token" }
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