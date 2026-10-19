param([string]$Api = '36', [int]$Port = 5554)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Add-Type -TypeDefinition 'using System; using System.Text; using System.Runtime.InteropServices; public static class ShortPaths { [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] public static extern uint GetShortPathName(string path,StringBuilder result,uint count); }'
$buffer=[Text.StringBuilder]::new(4096)
[void][ShortPaths]::GetShortPathName((Join-Path $root '.tools'),$buffer,4096)
$toolRoot=$buffer.ToString()
if (!$toolRoot) { $toolRoot=Join-Path $root '.tools' }
$env:JAVA_HOME=Join-Path $toolRoot 'jdk'
$env:ANDROID_HOME=Join-Path $toolRoot 'android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:ANDROID_AVD_HOME=Join-Path $toolRoot 'avd'
$env:ANDROID_USER_HOME=Join-Path $toolRoot 'android-user'
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME,$env:ANDROID_USER_HOME | Out-Null
$name="Suixinji$Api"
$avd=Join-Path $env:ANDROID_AVD_HOME "$name.avd"
if (!(Test-Path -LiteralPath $avd)) {
    'no' | & (Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/avdmanager.bat') create avd -n $name -k "system-images;android-$Api;google_apis;x86_64" --path $avd
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed' }
}
$config=Join-Path $avd 'config.ini'
$lines=@(Get-Content -LiteralPath $config)
$settings=@{'hw.lcd.width'='1080';'hw.lcd.height'='2340';'hw.lcd.density'='420';'skin.name'='1080x2340';'skin.path'='_no_skin';'hw.keyboard'='yes'}
foreach($key in $settings.Keys){$lines=@($lines | Where-Object {$_ -notmatch ('^'+[regex]::Escape($key)+'=')});$lines+="$key=$($settings[$key])"}
[IO.File]::WriteAllLines($config,$lines,[Text.UTF8Encoding]::new($false))
$args=@('-avd',$name,'-port',$Port,'-timezone','Asia/Shanghai','-no-window','-no-audio','-no-boot-anim','-gpu','swiftshader_indirect','-memory','2048','-no-snapshot','-no-metrics')
$proc=Start-Process -FilePath (Join-Path $env:ANDROID_HOME 'emulator/emulator.exe') -ArgumentList $args -WindowStyle Hidden -RedirectStandardOutput (Join-Path $root ".tools/emulator-$Api-out.log") -RedirectStandardError (Join-Path $root ".tools/emulator-$Api-error.log") -PassThru
Write-Output "Emulator process $($proc.Id) (API $Api, port $Port)"
