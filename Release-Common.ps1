# Shared source checks. This file does not run Gradle or publish anything by itself.
Set-StrictMode -Version Latest

function Invoke-ReviewTool {
    param([string]$Executable, [string[]]$CommandArgs)
    & $Executable @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed (exit $LASTEXITCODE): $Executable $($CommandArgs -join ' ')"
    }
}

function Invoke-ReviewProbe {
    param([string]$Executable, [string[]]$CommandArgs)
    # Windows PowerShell 5.1 can turn redirected native stderr into an error
    # record. A nonzero exit is expected for existence probes, so capture it.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $Executable @CommandArgs 2>$null)
        $exitCode = $LASTEXITCODE
        return [pscustomobject]@{ ExitCode = $exitCode; Output = (($output | Out-String).Trim()) }
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Get-ReviewPublicPaths {
    param([string]$Root)
    $listPath = Join-Path $Root 'REPOSITORY_FILES.txt'
    if (-not (Test-Path -LiteralPath $listPath -PathType Leaf)) {
        throw 'REPOSITORY_FILES.txt is missing. Extract the complete review package.'
    }
    $paths = @(Get-Content -LiteralPath $listPath -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })
    foreach ($relative in $paths) {
        if ($relative -ne $relative.Trim() -or $relative.Contains('\') -or
            $relative.StartsWith('/') -or $relative.Contains(':') -or
            $relative.Split('/') -contains '..' -or $relative.Split('/') -contains '.') {
            throw "Invalid public file-list entry: $relative"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $Root $relative) -PathType Leaf)) {
            throw "Required public file is missing: $relative"
        }
    }
    if (Test-Path -LiteralPath (Join-Path $Root 'LICENSE') -PathType Leaf) { $paths += 'LICENSE' }
    return @($paths | Sort-Object -Unique)
}

function Get-ReviewSourceStamp {
    param([string]$Root)
    $paths = @(Get-ReviewPublicPaths -Root $Root)
    $allowed = @{}
    foreach ($relative in $paths) { $allowed[$relative] = $true }
    # Extra files in compiler input directories must not enter an APK without
    # being part of the explicit publication list.
    foreach ($relativeDir in @('android/app/src', 'android/app/schemas', 'android/gradle')) {
        $directory = Join-Path $Root $relativeDir
        foreach ($item in @(Get-ChildItem -LiteralPath $directory -Recurse -File -Force)) {
            $relative = $item.FullName.Substring($Root.TrimEnd('\','/').Length + 1).Replace('\','/')
            if (-not $allowed.ContainsKey($relative)) {
                throw "Unexpected source/configuration file: $relative. Review it and update REPOSITORY_FILES.txt before building."
            }
        }
    }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $lines = New-Object 'System.Collections.Generic.List[string]'
    try {
        foreach ($relative in $paths) {
            $full = Join-Path $Root $relative
            $extension = [System.IO.Path]::GetExtension($full).ToLowerInvariant()
            if ($extension -in @('.png', '.webp', '.jar')) {
                $bytes = [System.IO.File]::ReadAllBytes($full)
            } else {
                $content = [System.IO.File]::ReadAllText($full, $utf8).Replace("`r`n", "`n")
                $bytes = $utf8.GetBytes($content)
            }
            $hash = ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
            $lines.Add("$hash  $relative")
        }
        $manifest = [string]::Join("`n", $lines.ToArray()) + "`n"
        $stamp = ([System.BitConverter]::ToString($sha.ComputeHash($utf8.GetBytes($manifest)))).Replace('-','').ToLowerInvariant()
        return [pscustomobject]@{ Hash = $stamp; Manifest = $manifest; FileCount = $paths.Count }
    } finally {
        $sha.Dispose()
    }
}

function Write-ReviewUtf8 {
    param([string]$Path, [string]$Content)
    [System.IO.File]::WriteAllText($Path, $Content, (New-Object System.Text.UTF8Encoding($false)))
}
