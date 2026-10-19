param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest'))
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root '.tools/jdk'
$env:ANDROID_HOME = Join-Path $root '.tools/android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $root '.tools/gradle-home'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($root))).Substring(0,16)
$mutex=[Threading.Mutex]::new($false,"Local\SuixinjiBuild_$hash")
$acquired=$false
try {
    $acquired=$mutex.WaitOne([TimeSpan]::FromMinutes(30))
    if(!$acquired){throw 'Another project build is still running'}
    Push-Location $root
    try { & (Join-Path $root '.tools/gradle-8.13/bin/gradle.bat') @Tasks --console=plain; if ($LASTEXITCODE -ne 0) { throw "Build failed ($LASTEXITCODE)" } } finally { Pop-Location }
} finally {if($acquired){$mutex.ReleaseMutex()};$mutex.Dispose()}
