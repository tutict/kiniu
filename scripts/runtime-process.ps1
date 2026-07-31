Set-StrictMode -Version Latest

function Write-KiniuProcessRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][System.Diagnostics.Process]$Process,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$CommandMarker
    )

    $Process.Refresh()
    $Record = [ordered]@{
        version = 1
        pid = $Process.Id
        name = $Name
        commandMarker = $CommandMarker
        startTimeFileTimeUtc = [string]$Process.StartTime.ToFileTimeUtc()
    }
    $TemporaryPath = "$Path.tmp"
    $Record | ConvertTo-Json -Compress | Set-Content -LiteralPath $TemporaryPath -Encoding utf8
    Move-Item -LiteralPath $TemporaryPath -Destination $Path -Force
}

function Read-KiniuProcessRecord {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $Text = (Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue).Trim()
    if (-not $Text) { return $null }

    $LegacyPid = 0
    if ([int]::TryParse($Text, [ref]$LegacyPid)) {
        return [pscustomobject]@{
            version = 0
            pid = $LegacyPid
            name = ""
            commandMarker = ""
            startTimeFileTimeUtc = ""
        }
    }

    try {
        $Record = $Text | ConvertFrom-Json
        $RequiredProperties = @("version", "pid", "name", "commandMarker", "startTimeFileTimeUtc")
        $PropertyNames = @($Record.PSObject.Properties.Name)
        foreach ($PropertyName in $RequiredProperties) {
            if ($PropertyNames -notcontains $PropertyName) { return $null }
        }

        $Version = 0
        $ProcessId = 0
        $StartTimeFileTimeUtc = [long]0
        if (-not [int]::TryParse([string]$Record.version, [ref]$Version) -or $Version -ne 1) { return $null }
        if (-not [int]::TryParse([string]$Record.pid, [ref]$ProcessId) -or $ProcessId -le 0) { return $null }
        if ([string]::IsNullOrWhiteSpace([string]$Record.name)) { return $null }
        if ([string]::IsNullOrWhiteSpace([string]$Record.commandMarker)) { return $null }
        if (-not [long]::TryParse([string]$Record.startTimeFileTimeUtc, [ref]$StartTimeFileTimeUtc) -or $StartTimeFileTimeUtc -le 0) { return $null }
        return $Record
    } catch {
        return $null
    }
}

function Get-KiniuProcessIdentity {
    param(
        [Parameter(Mandatory = $true)]$Record,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedCommandMarker
    )

    $ProcessId = [int]$Record.pid
    $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $Process) {
        return [pscustomobject]@{ exists = $false; matches = $false; reason = "not-running"; process = $null }
    }
    if ([int]$Record.version -lt 1) {
        return [pscustomobject]@{ exists = $true; matches = $false; reason = "legacy-record"; process = $Process }
    }

    $CimProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    $CommandLine = if ($CimProcess -and $CimProcess.CommandLine) { [string]$CimProcess.CommandLine } else { "" }
    if (-not $CommandLine -or $CommandLine.IndexOf($ExpectedCommandMarker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        return [pscustomobject]@{ exists = $true; matches = $false; reason = "command-mismatch"; process = $Process }
    }
    if ($Record.name -and [string]$Record.name -ne $Name) {
        return [pscustomobject]@{ exists = $true; matches = $false; reason = "name-mismatch"; process = $Process }
    }
    if ($Record.commandMarker -and [string]$Record.commandMarker -ne $ExpectedCommandMarker) {
        return [pscustomobject]@{ exists = $true; matches = $false; reason = "marker-mismatch"; process = $Process }
    }
    if ($Record.startTimeFileTimeUtc) {
        $Process.Refresh()
        if ([string]$Process.StartTime.ToFileTimeUtc() -ne [string]$Record.startTimeFileTimeUtc) {
            return [pscustomobject]@{ exists = $true; matches = $false; reason = "start-time-mismatch"; process = $Process }
        }
    }
    return [pscustomobject]@{ exists = $true; matches = $true; reason = "matched"; process = $Process }
}

function Stop-KiniuProcessTree {
    param([Parameter(Mandatory = $true)][int]$ProcessId)

    $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $Process) { return }

    $Children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($Child in $Children) {
        Stop-KiniuProcessTree -ProcessId ([int]$Child.ProcessId)
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-KiniuRecordedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedCommandMarker
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{ stopped = $false; reason = "record-not-found"; pid = 0 }
    }

    $Record = Read-KiniuProcessRecord -Path $Path
    if (-not $Record) {
        Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
        return [pscustomobject]@{ stopped = $false; reason = "invalid-record"; pid = 0 }
    }

    $Identity = Get-KiniuProcessIdentity -Record $Record -Name $Name -ExpectedCommandMarker $ExpectedCommandMarker
    if (-not $Identity.exists) {
        Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
        return [pscustomobject]@{ stopped = $false; reason = "not-running"; pid = [int]$Record.pid }
    }
    if (-not $Identity.matches) {
        return [pscustomobject]@{ stopped = $false; reason = $Identity.reason; pid = [int]$Record.pid }
    }

    Stop-KiniuProcessTree -ProcessId ([int]$Record.pid)
    Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    return [pscustomobject]@{ stopped = $true; reason = "stopped"; pid = [int]$Record.pid }
}
