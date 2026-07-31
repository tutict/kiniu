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
    [string]$LocalToken = $env:KINIU_LOCAL_TOKEN,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$')]
    [string]$RuntimeName = "default"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "kiniu-back"
$FrontendDir = Join-Path $Root "kiniu-front\nuxt-app"
. (Join-Path $Root "scripts\runtime-process.ps1")
$LogRoot = Join-Path $Root "logs"
$RunRoot = Join-Path $Root ".run"
$LogDir = if ($RuntimeName -eq "default") { $LogRoot } else { Join-Path $LogRoot $RuntimeName }
$RunDir = if ($RuntimeName -eq "default") { $RunRoot } else { Join-Path $RunRoot $RuntimeName }
$NuxtBuildDir = if ($RuntimeName -eq "default") { Join-Path $FrontendDir ".nuxt" } else { Join-Path $RunDir "nuxt" }
$TokenPath = Join-Path $RunDir "local-token"
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
function Test-NuxtDevServerRunning([string]$BuildDirectory) {
    $LockCandidates = @(
        (Join-Path $BuildDirectory "nuxt.lock"),
        (Join-Path $BuildDirectory "dev\server.lock")
    )
    if ($BuildDirectory -eq (Join-Path $FrontendDir ".nuxt")) {
        $LockCandidates += Join-Path $FrontendDir "node_modules\.cache\nuxt\.nuxt\dev\server.lock"
    }
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

function Stop-RecordedService([string]$PidFile, [string]$Name, [string]$CommandMarker) {
    $Result = Stop-KiniuRecordedProcess -Path $PidFile -Name $Name -ExpectedCommandMarker $CommandMarker
    if ($Result.stopped) {
        Write-Step "Stopped stale $Name pid=$($Result.pid)"
    } elseif ($Result.reason -in @("legacy-record", "command-mismatch", "name-mismatch", "marker-mismatch", "start-time-mismatch")) {
        Write-Warning "Refusing to stop pid=$($Result.pid) for $Name because process identity did not match ($($Result.reason))."
    }
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

function New-CmdUnset([string]$Name) {
    return 'set "' + $Name + '="'
}

function Start-LoggedService(
    [string]$Name,
    [int]$Port,
    [string]$WorkingDirectory,
    [string]$Command,
    [string]$CommandMarker,
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

    Write-KiniuProcessRecord -Path $PidPath -Process $Process -Name $Name -CommandMarker $CommandMarker
    Write-Step "Started $Name pid=$($Process.Id)"
    return $Process
}

function Stop-StartedProcesses([array]$Processes) {
    foreach ($Process in $Processes) {
        if ($Process -and -not $Process.HasExited) {
            Stop-KiniuProcessTree -ProcessId $Process.Id
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
if (-not $BackendOnly -and (Test-NuxtDevServerRunning $NuxtBuildDir)) {
    throw "A Nuxt dev server is already running for runtime '$RuntimeName'. Stop it before starting another frontend instance."
}

if (-not $FrontendOnly) { Stop-RecordedService (Join-Path $RunDir "backend.pid") "backend" "spring-boot:run" }
if (-not $BackendOnly) { Stop-RecordedService (Join-Path $RunDir "frontend.pid") "frontend" "npm.cmd run dev" }

if ($NoLocalToken) {
    $LocalToken = ""
    Remove-Item -LiteralPath $TokenPath -Force -ErrorAction SilentlyContinue
} elseif ([string]::IsNullOrWhiteSpace($LocalToken)) {
    if ($FrontendOnly -and (Test-Path -LiteralPath $TokenPath)) {
        $LocalToken = (Get-Content -LiteralPath $TokenPath -Raw).Trim()
        Write-Step "Loaded local access token from $TokenPath"
    } else {
        $LocalToken = New-LocalToken
        Write-Step "Generated local access token for runtime '$RuntimeName'"
    }
}
if (-not [string]::IsNullOrWhiteSpace($LocalToken)) {
    $LocalToken = $LocalToken.Trim()
    Set-Content -LiteralPath $TokenPath -Value $LocalToken -Encoding ascii
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
        (New-CmdSet "SERVER_ADDRESS" "127.0.0.1"),
        (New-CmdSet "GAME_SECURITY_ALLOWED_ORIGINS" "http://localhost:$FrontendPort,http://127.0.0.1:$FrontendPort")
    )
    if ($RuntimeName -ne "default") {
        $BackendEnv += New-CmdSet "GAME_LEARNING_PROGRESS_PATH" (Join-Path $RunDir "learning-progress.json")
    }
    if ($LocalToken -and $LocalToken.Trim()) {
        $BackendEnv += New-CmdSet "KINIU_LOCAL_TOKEN" $LocalToken.Trim()
        Write-Step "Local token enabled for backend and browser bootstrap."
    } else {
        $BackendEnv += New-CmdUnset "KINIU_LOCAL_TOKEN"
    }
    $Started += Start-LoggedService -Name "backend" -Port $BackendPort -WorkingDirectory $BackendDir -Command "mvn spring-boot:run" -CommandMarker "spring-boot:run" -EnvironmentCommands $BackendEnv
}

if (-not $BackendOnly) {
    $DevtoolsValue = if ($EnableDevtools) { "true" } else { "false" }
    $FrontendEnv = @(
        (New-CmdSet "HOST" "127.0.0.1"),
        (New-CmdSet "PORT" ([string]$FrontendPort)),
        (New-CmdSet "NUXT_DEVTOOLS_ENABLED" $DevtoolsValue),
        (New-CmdSet "NUXT_BUILD_DIR" $NuxtBuildDir),
        (New-CmdSet "NUXT_PUBLIC_KINIU_BACKEND_URL" "http://127.0.0.1:$BackendPort")
    )
    $FrontendEnv += if ([string]::IsNullOrWhiteSpace($LocalToken)) {
        New-CmdUnset "NUXT_PUBLIC_KINIU_LOCAL_TOKEN"
    } else {
        New-CmdSet "NUXT_PUBLIC_KINIU_LOCAL_TOKEN" $LocalToken
    }
    $Started += Start-LoggedService -Name "frontend" -Port $FrontendPort -WorkingDirectory $FrontendDir -Command "npm.cmd run dev -- --host 127.0.0.1 --port $FrontendPort" -CommandMarker "npm.cmd run dev" -EnvironmentCommands $FrontendEnv
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
if (-not [string]::IsNullOrWhiteSpace($LocalToken)) { Write-Host "  Token:    $TokenPath" }
Write-Host "  Logs:     $LogDir"
Write-Host "  PID files: $RunDir"
Write-Host "  Runtime:  $RuntimeName"
if (-not $BackendOnly -and $EnableDevtools) { Write-Host "  Nuxt DevTools: enabled" }
Write-Host ""
Write-Host "Stop commands:"
if (-not $FrontendOnly) { Write-Host "  Stop backend:  .\stop.ps1 -BackendOnly -BackendPort $BackendPort -RuntimeName $RuntimeName" }
if (-not $BackendOnly) { Write-Host "  Stop frontend: .\stop.ps1 -FrontendOnly -FrontendPort $FrontendPort -RuntimeName $RuntimeName" }

if (-not $NoBrowser -and -not $BackendOnly) {
    Start-Process -WindowStyle Hidden "http://127.0.0.1:$FrontendPort"
}
