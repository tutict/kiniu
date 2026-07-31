param(
    [int]$BackendPort = 18080,
    [int]$FrontendPort = 13000
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$FrontendDir = Join-Path $Root "kiniu-front\nuxt-app"
$RuntimeName = "e2e"
$RuntimeDir = Join-Path $Root ".run\$RuntimeName"
$ExitCode = 1

& (Join-Path $Root "scripts\tests\no-local-token.tests.ps1")

try {
    $StartArguments = @{
        NoBrowser = $true
        SkipInstall = $true
        CleanLogs = $true
        BackendPort = $BackendPort
        FrontendPort = $FrontendPort
        RuntimeName = $RuntimeName
    }
    & (Join-Path $Root "start.ps1") @StartArguments

    $env:KINIU_E2E_FRONTEND_URL = "http://127.0.0.1:$FrontendPort"
    $env:KINIU_E2E_BACKEND_PORT = [string]$BackendPort
    Push-Location $FrontendDir
    try {
        & npx.cmd playwright test
        $ExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $StopArguments = @{
        BackendPort = $BackendPort
        FrontendPort = $FrontendPort
        RuntimeName = $RuntimeName
        CleanRunFiles = $true
    }
    & (Join-Path $Root "stop.ps1") @StopArguments
    if (Test-Path -LiteralPath $RuntimeDir) {
        Write-Warning "E2E runtime directory was not cleaned: $RuntimeDir"
        $ExitCode = 1
    }
}

exit $ExitCode
