[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$reviewRoot = $PSScriptRoot
. (Join-Path $reviewRoot 'Release-Common.ps1')
$repository = 'bhadri-Raj-T/WiFiTrustFusion'
$tag = 'v1.1-demo'
$out = Join-Path $reviewRoot 'Final_APK'
$recordFile = Join-Path $out 'RELEASE_BUILD.json'
if (-not (Test-Path -LiteralPath $recordFile -PathType Leaf)) { throw 'Run Build-ReviewApk.ps1 successfully in this review worktree first.' }
$record = Get-Content -LiteralPath $recordFile -Raw | ConvertFrom-Json
if ($record.formatVersion -ne 1 -or $record.appId -ne 'com.bhadrirajt.wifitrustfusion' -or $record.versionName -ne '1.1-demo' -or $record.versionCode -ne 3 -or $record.buildType -ne 'debug' -or
    $record.unitTests.passed -lt 1 -or $record.unitTests.failures -ne 0 -or $record.unitTests.errors -ne 0) {
    throw 'A successful 1.1-demo build and unit-test result is required.'
}
$apk = Join-Path $out 'WiFiTrustFusion_Final.apk'
$shaFile = Join-Path $out 'WiFiTrustFusion_Final.sha256'
$manifest = Join-Path $out 'SOURCE_MANIFEST.txt'
foreach ($path in @($apk,$shaFile,$manifest,(Join-Path $out 'BUILD_SUMMARY.md'))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'A required APK/checksum/build record is missing. Rebuild first.' }
}
$apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($apkHash -ne $record.apkSha256 -or (Get-Content -LiteralPath $shaFile -Raw).Trim() -ne "$apkHash  WiFiTrustFusion_Final.apk") {
    throw 'APK/checksum mismatch. Rebuild this source; do not substitute an older APK.'
}
$source = Get-ReviewSourceStamp -Root $reviewRoot
if ($source.Hash -ne $record.sourceFingerprintSha256 -or
    [System.IO.File]::ReadAllText($manifest).Replace("`r`n","`n") -ne $source.Manifest) {
    throw 'Public source/docs changed after the successful build. Build again before publishing.'
}
$pushed = $false
try {
    Push-Location $reviewRoot; $pushed = $true
    $top = (Invoke-ReviewTool -Executable git -CommandArgs @('rev-parse','--show-toplevel') | Out-String).Trim()
    if ([System.IO.Path]::GetFullPath($top).TrimEnd('\','/') -ne [System.IO.Path]::GetFullPath($reviewRoot).TrimEnd('\','/')) {
        throw 'Run this helper from the prepared review worktree.'
    }
    $branch = (Invoke-ReviewTool -Executable git -CommandArgs @('branch','--show-current') | Out-String).Trim()
    if ($branch -notin @('', 'main')) { throw 'Use the prepared detached review worktree or main; this helper will not commit on a development/data branch.' }
    $origin = (Invoke-ReviewTool -Executable git -CommandArgs @('remote','get-url','origin') | Out-String).Trim()
    if ($origin -notmatch '(?i)^(https://github\.com/|git@github\.com:)bhadri-Raj-T/WiFiTrustFusion(?:\.git)?/?$') {
        throw 'Origin is not the requested WiFiTrustFusion repository. No remote was changed.'
    }
    Invoke-ReviewTool -Executable git -CommandArgs @('var','GIT_AUTHOR_IDENT') | Out-Null
    Invoke-ReviewTool -Executable git -CommandArgs @('fetch','origin','refs/heads/main:refs/remotes/origin/main')
    & git merge-base --is-ancestor origin/main HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Remote main advanced outside this worktree. Prepare again from the updated main; no force-push is used.' }
    $allowed = @{}
    foreach ($relative in @(Get-ReviewPublicPaths -Root $reviewRoot)) { $allowed[$relative] = $true }
    foreach ($relative in @(Invoke-ReviewTool -Executable git -CommandArgs @('diff','HEAD','--name-only'))) {
        if (-not $allowed.ContainsKey($relative)) { throw "Unrelated tracked edit found: $relative. It will not be included automatically." }
    }
    Invoke-ReviewTool -Executable git -CommandArgs @('add','--pathspec-from-file=REPOSITORY_FILES.txt')
    foreach ($relative in @(Invoke-ReviewTool -Executable git -CommandArgs @('diff','--cached','--name-only'))) {
        if (-not $allowed.ContainsKey($relative)) { throw "Unrelated staged file: $relative. Review it before publishing." }
    }
    & git diff --cached --quiet
    $diffExit = $LASTEXITCODE
    if ($diffExit -eq 1) {
        Invoke-ReviewTool -Executable git -CommandArgs @('commit','-m','Third review: connected Wi-Fi analytics, safety demonstrations and project documentation')
    } elseif ($diffExit -ne 0) { throw 'Could not inspect staged changes.' }
    if ((Get-ReviewSourceStamp -Root $reviewRoot).Hash -ne $record.sourceFingerprintSha256) { throw 'Public files changed during commit. Rebuild before pushing.' }
    $head = (Invoke-ReviewTool -Executable git -CommandArgs @('rev-parse','HEAD') | Out-String).Trim()
    $localTags = @(Invoke-ReviewTool -Executable git -CommandArgs @('tag','--list',$tag))
    if ($localTags -contains $tag) {
        $existing = (Invoke-ReviewTool -Executable git -CommandArgs @('rev-parse',"$tag^{commit}") | Out-String).Trim()
        if ($existing -ne $head) { throw 'Local v1.1-demo points elsewhere; it was not moved.' }
    } else {
        Invoke-ReviewTool -Executable git -CommandArgs @('tag','-a',$tag,'-m','WiFiTrustFusion 1.1-demo - Third Review',$head)
    }
    $remoteTags = @(Invoke-ReviewTool -Executable git -CommandArgs @('ls-remote','--tags','origin',"refs/tags/$tag", "refs/tags/$tag^{}"))
    if ($remoteTags.Count -gt 0) {
        $peeled = @($remoteTags | Where-Object { $_ -match '\^\{\}$' })
        $row = if ($peeled.Count -gt 0) { $peeled[0] } else { $remoteTags[0] }
        if (($row -split '\s+')[0] -ne $head) { throw 'Remote v1.1-demo points elsewhere; it was not moved. Use a new version for different code.' }
    }
    # Explicit destination updates main only. Development/data branches are untouched.
    Invoke-ReviewTool -Executable git -CommandArgs @('push','origin','HEAD:refs/heads/main')
    Invoke-ReviewTool -Executable git -CommandArgs @('push','origin',('refs/tags/{0}:refs/tags/{0}' -f $tag))
    $urls = "Repository: https://github.com/$repository`nMain commit: $head`nCreate release: https://github.com/$repository/releases/new`nTag: $tag`nAPK SHA-256: $apkHash`n"
    Write-ReviewUtf8 -Path (Join-Path $out 'PUBLISHED_URLS.txt') -Content $urls
    Write-Host 'MAIN COMMIT AND TAG PUSHED.' -ForegroundColor Green
    Write-Host "Commit: $head"
    Write-Host "APK assets: $out"
    Write-Host "Now open https://github.com/$repository/releases/new, choose existing tag $tag and attach the APK/checksum/build summary."
    Write-Host 'The GitHub release page itself has not been published by this Git-only helper.'
} finally { if ($pushed) { Pop-Location } }
