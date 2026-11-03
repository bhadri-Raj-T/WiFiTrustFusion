[CmdletBinding()]
param(
    [string]$OriginalProjectRoot = 'D:\Important\Forensics Analytics\WiFiTrustFusion'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$reviewRoot = $PSScriptRoot
. (Join-Path $reviewRoot 'Release-Common.ps1')
$androidRoot = Join-Path $reviewRoot 'android'
$exportRoot = Join-Path $reviewRoot 'Final_APK'
$wrapper = Join-Path $androidRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Android Gradle wrapper is missing.' }
$versionFile = Get-Content -LiteralPath (Join-Path $androidRoot 'app/build.gradle.kts') -Raw
if ($versionFile -notmatch 'versionName\s*=\s*"1\.1-demo"' -or $versionFile -notmatch 'versionCode\s*=\s*3\b') {
    throw 'This review package expects source version 1.1-demo, code 3. Update the version/release documentation deliberately before changing it.'
}

New-Item -ItemType Directory -Path $exportRoot -Force | Out-Null
$buildRecord = Join-Path $exportRoot 'RELEASE_BUILD.json'
if (Test-Path -LiteralPath $buildRecord -PathType Leaf) { Remove-Item -LiteralPath $buildRecord }

# Only local SDK settings and the existing license are copied from the old project.
if ($OriginalProjectRoot -and (Test-Path -LiteralPath $OriginalProjectRoot -PathType Container)) {
    $oldSdk = Join-Path $OriginalProjectRoot 'android/local.properties'
    $newSdk = Join-Path $androidRoot 'local.properties'
    if (-not (Test-Path -LiteralPath $newSdk) -and (Test-Path -LiteralPath $oldSdk -PathType Leaf)) {
        Copy-Item -LiteralPath $oldSdk -Destination $newSdk
        Write-Host 'Reused the existing local SDK configuration (ignored by Git).'
    }
    $oldLicense = Join-Path $OriginalProjectRoot 'LICENSE'
    $newLicense = Join-Path $reviewRoot 'LICENSE'
    if (-not (Test-Path -LiteralPath $newLicense) -and (Test-Path -LiteralPath $oldLicense -PathType Leaf)) {
        Copy-Item -LiteralPath $oldLicense -Destination $newLicense
        Write-Host 'Preserved the original project LICENSE.'
    }
}

$started = [DateTimeOffset]::UtcNow
$before = Get-ReviewSourceStamp -Root $reviewRoot
$logPath = Join-Path $exportRoot ('Build_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.log')
$transcribing = $false
$pushed = $false
try {
    Start-Transcript -Path $logPath -Force | Out-Null
    $transcribing = $true
    Write-Host 'Building the review source and running local unit tests on this Windows laptop.' -ForegroundColor Cyan
    Push-Location $androidRoot
    $pushed = $true
    Invoke-ReviewTool -Executable $wrapper -CommandArgs @(
        ':app:clean', ':app:assembleDebug', ':app:testDebugUnitTest',
        '--no-daemon', '--no-configuration-cache', '-Pksp.incremental=false'
    )
    Pop-Location
    $pushed = $false

    $resultsRoot = Join-Path $androidRoot 'app/build/test-results/testDebugUnitTest'
    $xmlFiles = @(Get-ChildItem -LiteralPath $resultsRoot -Filter 'TEST-*.xml' -File)
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    foreach ($file in $xmlFiles) {
        [xml]$suite = Get-Content -LiteralPath $file.FullName -Raw
        $tests += [int]$suite.testsuite.tests
        $failures += [int]$suite.testsuite.failures
        $errors += [int]$suite.testsuite.errors
        $skipped += [int]$suite.testsuite.skipped
    }
    if ($tests -lt 1 -or ($tests - $skipped) -lt 1 -or $failures -ne 0 -or $errors -ne 0) {
        throw "Unit-test verification failed: total=$tests, skipped=$skipped, failures=$failures, errors=$errors."
    }
    $after = Get-ReviewSourceStamp -Root $reviewRoot
    if ($before.Hash -ne $after.Hash) {
        throw 'A public source/configuration/document file changed during the build. Review the change, then build again.'
    }

    $builtApk = Join-Path $androidRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (-not (Test-Path -LiteralPath $builtApk -PathType Leaf)) { throw 'Expected app-debug.apk is missing.' }
    $finalApk = Join-Path $exportRoot 'WiFiTrustFusion_Final.apk'
    Copy-Item -LiteralPath $builtApk -Destination $finalApk -Force
    $apkHash = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($apkHash -ne (Get-FileHash -LiteralPath $builtApk -Algorithm SHA256).Hash.ToLowerInvariant()) {
        throw 'Exported APK differs from the Gradle output.'
    }
    Write-ReviewUtf8 -Path (Join-Path $exportRoot 'WiFiTrustFusion_Final.sha256') -Content "$apkHash  WiFiTrustFusion_Final.apk`n"
    Write-ReviewUtf8 -Path (Join-Path $exportRoot 'SOURCE_MANIFEST.txt') -Content $after.Manifest
    $completed = [DateTimeOffset]::UtcNow
    $record = [ordered]@{
        formatVersion = 1
        appId = 'com.bhadrirajt.wifitrustfusion'
        versionName = '1.1-demo'
        versionCode = 3
        buildType = 'debug'
        startedUtc = $started.ToString('o')
        completedUtc = $completed.ToString('o')
        unitTests = [ordered]@{ total = $tests; passed = ($tests - $skipped); skipped = $skipped; failures = $failures; errors = $errors }
        instrumentedTestsExecuted = $false
        manualPhoneCheckVerifiedByScript = $false
        apkFile = 'WiFiTrustFusion_Final.apk'
        apkSha256 = $apkHash
        sourceFileCount = $after.FileCount
        sourceFingerprintSha256 = $after.Hash
        sourceHashPolicy = 'UTF-8 text without BOM, CRLF normalised to LF; PNG/WEBP/JAR binary bytes unchanged; sorted manifest lines'
        command = 'gradlew.bat :app:clean :app:assembleDebug :app:testDebugUnitTest --no-daemon --no-configuration-cache -Pksp.incremental=false'
        signing = 'Local Gradle debug signing configuration; signing key is not included'
    }
    $summary = @(
        '# WiFiTrustFusion 1.1-demo - Local Build Summary', '',
        "Completed (UTC): $($completed.ToString('o'))", '',
        '- Source version: 1.1-demo, code 3.',
        '- Android build type: debug; classroom prerelease.',
        '- Clean assembly and local JVM unit-test task: completed successfully.',
        "- Unit tests: $tests total; $($tests - $skipped) passed; $skipped skipped; $failures failures; $errors errors.",
        '- Android instrumented tests: not run by this script.',
        '- Manual phone verification: separate student check; not observed by this script.',
        "- Source files in fingerprint: $($after.FileCount).", '',
        "APK SHA-256: $apkHash", '',
        "Source fingerprint SHA-256: $($after.Hash)", '',
        'Command:', '',
        '    gradlew.bat :app:clean :app:assembleDebug :app:testDebugUnitTest --no-daemon --no-configuration-cache -Pksp.incremental=false', '',
        'Signing uses the local Gradle debug configuration. This summary contains no SDK path or signing key.',
        'SOURCE_MANIFEST.txt normalises text line endings to LF and hashes binary assets unchanged.',
        'A matching fingerprint associates these files with this build; it does not guarantee byte-identical rebuilds.', ''
    ) -join "`n"
    Write-ReviewUtf8 -Path (Join-Path $exportRoot 'BUILD_SUMMARY.md') -Content $summary
    # The success marker is written last. A failed attempt cannot reuse it.
    Write-ReviewUtf8 -Path $buildRecord -Content (($record | ConvertTo-Json -Depth 5) + "`n")
    Write-Host "BUILD AND UNIT TESTS PASSED: $($tests - $skipped) passed; $skipped skipped." -ForegroundColor Green
    Write-Host "FINAL APK: $finalApk" -ForegroundColor Green
    Write-Host "SHA-256: $apkHash"
    Write-Host 'Install this export and complete docs/DEMO_GUIDE.md before publishing.'
} finally {
    if ($pushed) { Pop-Location }
    if ($transcribing) { Stop-Transcript | Out-Null }
}
