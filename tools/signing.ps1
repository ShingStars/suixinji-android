$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$props=Join-Path $root 'signing.properties'
if(Test-Path -LiteralPath $props){Write-Output 'Existing personal signing configuration retained.';exit 0}
$signDir=Join-Path $root '.tools/signing'
New-Item -ItemType Directory -Force -Path $signDir | Out-Null
$password=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$keyFile=Join-Path $signDir 'suixinji.jks'
& (Join-Path $root '.tools/jdk/bin/keytool.exe') -genkeypair -keystore $keyFile -storetype PKCS12 -alias suixinji -keyalg RSA -keysize 3072 -validity 10000 -storepass $password -keypass $password -dname 'CN=Suixinji Personal, OU=Personal, O=Suixinji, C=CN'
if($LASTEXITCODE -ne 0){throw 'Signing key generation failed'}
[IO.File]::WriteAllText($props,"storeFile=.tools/signing/suixinji.jks`nstorePassword=$password`nkeyAlias=suixinji`nkeyPassword=$password`n",[Text.UTF8Encoding]::new($false))
Write-Output 'Personal release signing key created; excluded from source distribution.'
