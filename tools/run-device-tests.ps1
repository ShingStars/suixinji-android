param([string]$Serial='emulator-5554',[string]$Api='36')
$ErrorActionPreference='Stop'
if(!$Serial.StartsWith('emulator-')){throw 'This resettable test workflow is restricted to project emulators.'}
$root=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $root '.tools/android-sdk/platform-tools/adb.exe'
& $adb -s $Serial wait-for-device
$deadline=(Get-Date).AddMinutes(4)
while((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1'){if((Get-Date) -gt $deadline){throw 'Emulator did not finish booting'};Start-Sleep -Seconds 2}
& $adb -s $Serial shell am force-stop cn.suixinji.app
& $adb -s $Serial shell wm size reset
& $adb -s $Serial shell wm density reset
foreach($key in @('window_animation_scale','transition_animation_scale','animator_duration_scale')){& $adb -s $Serial shell settings put global $key 0}
& $adb -s $Serial install -r (Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk')
if($LASTEXITCODE -ne 0){throw 'App installation failed'}
& $adb -s $Serial shell pm clear cn.suixinji.app | Out-Null
& $adb -s $Serial install -r (Join-Path $root 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
if($LASTEXITCODE -ne 0){throw 'Test installation failed'}
$output=& $adb -s $Serial shell am instrument -w -r cn.suixinji.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$output | Set-Content -LiteralPath (Join-Path $root "qa/artifacts/instrumentation-api$Api.log") -Encoding utf8
$output | Select-Object -Last 12
if(($output -join "`n") -notmatch 'OK \(\d+ tests\)'){throw "Device tests failed on API $Api"}
