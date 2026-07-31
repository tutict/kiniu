$ErrorActionPreference = "Stop"

$RepositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RuntimeName = "no-token-test"
$BackendPort = 18081
$OriginalToken = $env:KINIU_LOCAL_TOKEN

try {
    $env:KINIU_LOCAL_TOKEN = "inherited-token-must-be-cleared"
    $StartArguments = @{
        BackendOnly = $true
        NoLocalToken = $true
        NoBrowser = $true
        SkipInstall = $true
        CleanLogs = $true
        BackendPort = $BackendPort
        RuntimeName = $RuntimeName
    }
    & (Join-Path $RepositoryRoot "start.ps1") @StartArguments

    $Response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$BackendPort/learn/progress"
    if ($Response.StatusCode -ne 200) {
        throw "Expected an unauthenticated request to succeed with -NoLocalToken."
    }
    Write-Output "no-local-token inheritance test passed"
} finally {
    $StopArguments = @{
        BackendOnly = $true
        BackendPort = $BackendPort
        RuntimeName = $RuntimeName
        CleanRunFiles = $true
    }
    & (Join-Path $RepositoryRoot "stop.ps1") @StopArguments
    if ($null -eq $OriginalToken) {
        Remove-Item Env:KINIU_LOCAL_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:KINIU_LOCAL_TOKEN = $OriginalToken
    }
}
