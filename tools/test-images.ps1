$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME=Join-Path $root '.tools/jdk'
$sdk=Join-Path $root '.tools/android-sdk'
& (Join-Path $sdk 'cmdline-tools/latest/bin/sdkmanager.bat') "--sdk_root=$sdk" 'system-images;android-29;google_apis;x86_64' 'system-images;android-33;google_apis;x86_64'
if($LASTEXITCODE -ne 0){throw 'Test image installation failed'}
