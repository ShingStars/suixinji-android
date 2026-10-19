$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $root '.tools'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
function Fetch([string]$url, [string]$path) {
    if (!(Test-Path -LiteralPath $path)) {
        & curl.exe -fL --retry 3 --connect-timeout 30 --output $path $url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $url" }
    }
}
$jdkZip = Join-Path $toolsDir 'jdk.zip'
Fetch 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' $jdkZip
if (!(Test-Path (Join-Path $toolsDir 'jdk'))) {
    Expand-Archive -LiteralPath $jdkZip -DestinationPath (Join-Path $toolsDir 'jdk-unpack') -Force
    $jdkSource = Get-ChildItem (Join-Path $toolsDir 'jdk-unpack') -Directory | Select-Object -First 1
    Move-Item -LiteralPath $jdkSource.FullName -Destination (Join-Path $toolsDir 'jdk')
}
Fetch 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' (Join-Path $toolsDir 'android-commandline.zip')
$sdkDir = Join-Path $toolsDir 'android-sdk'
if (!(Test-Path (Join-Path $sdkDir 'cmdline-tools/latest/bin/sdkmanager.bat'))) {
    Expand-Archive -LiteralPath (Join-Path $toolsDir 'android-commandline.zip') -DestinationPath (Join-Path $toolsDir 'commandline-unpack') -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $sdkDir 'cmdline-tools') | Out-Null
    Move-Item -LiteralPath (Join-Path $toolsDir 'commandline-unpack/cmdline-tools') -Destination (Join-Path $sdkDir 'cmdline-tools/latest')
}
Fetch 'https://repo.huaweicloud.com/gradle/gradle-8.13-bin.zip' (Join-Path $toolsDir 'gradle.zip')
Fetch 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256' (Join-Path $toolsDir 'gradle.sha256')
if ((Get-FileHash (Join-Path $toolsDir 'gradle.zip') -Algorithm SHA256).Hash.ToLowerInvariant() -ne (Get-Content -Raw (Join-Path $toolsDir 'gradle.sha256')).Trim().ToLowerInvariant()) { throw 'Gradle checksum mismatch' }
if (!(Test-Path (Join-Path $toolsDir 'gradle-8.13'))) { Expand-Archive -LiteralPath (Join-Path $toolsDir 'gradle.zip') -DestinationPath $toolsDir -Force }
$env:JAVA_HOME = Join-Path $toolsDir 'jdk'
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir
$env:GRADLE_USER_HOME = Join-Path $toolsDir 'gradle-home'
$sdkManager = Join-Path $sdkDir 'cmdline-tools/latest/bin/sdkmanager.bat'
1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkDir" --licenses
if ($LASTEXITCODE -ne 0) { throw 'Android licenses failed' }
& $sdkManager "--sdk_root=$sdkDir" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0' 'emulator' 'system-images;android-36;google_apis;x86_64'
if ($LASTEXITCODE -ne 0) { throw 'Android package installation failed' }
$sdkProperty = $sdkDir.Replace('\', '/').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $root 'local.properties'), "sdk.dir=$sdkProperty`n", [Text.UTF8Encoding]::new($false))
Write-Output 'Android toolchain ready.'
