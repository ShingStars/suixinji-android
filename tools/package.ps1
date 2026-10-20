param([string]$Version='1.1.2',[int]$DeviceTests=15,[int]$UnitTests=27,[string[]]$Apis=@('29','33'))
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$out=Join-Path $root 'deliverables'
$evidence=Join-Path $root 'qa/evidence'
$screens=Join-Path $out 'screenshots'
New-Item -ItemType Directory -Force -Path $out,$evidence,$screens | Out-Null
foreach($api in $Apis){
    $log=Join-Path $root "qa/artifacts/instrumentation-api$api.log"
    if((Get-Content -Raw -LiteralPath $log) -notmatch ("OK \("+$DeviceTests+" tests\)")){throw "Final device evidence is not passing for API $api"}
    Copy-Item -LiteralPath $log -Destination $evidence -Force
}
$unitFiles=Get-ChildItem (Join-Path $root 'app/build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml'
$unitCount=0
foreach($file in $unitFiles){[xml]$xml=Get-Content -Raw -LiteralPath $file.FullName;if([int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0){throw 'Unit tests are failing'};$unitCount+=[int]$xml.testsuite.tests;Copy-Item -LiteralPath $file.FullName -Destination $evidence -Force}
if($unitCount -ne $UnitTests){throw 'Unit test evidence is incomplete'}
foreach($variant in @('debug','release')){
    $lint=Join-Path $root "app/build/reports/lint-results-$variant.txt"
    if((Get-Content -Raw -LiteralPath $lint) -notmatch '0 errors,'){throw "Lint $variant is not passing"}
    Copy-Item -LiteralPath $lint -Destination $evidence -Force
}
foreach($name in @('timer-background-alarm.txt','timer-finished-notification.txt')){Copy-Item -LiteralPath (Join-Path $root "qa/artifacts/$name") -Destination $evidence -Force}
foreach($name in @('release-today','release-calendar','release-records','release-focus','release-dark-settings','release-calendar-font130')){Copy-Item -LiteralPath (Join-Path $root "qa/artifacts/$name.png") -Destination (Join-Path $screens "$name.png") -Force}
Copy-Item -LiteralPath (Join-Path $root 'app/build/outputs/apk/release/app-release.apk') -Destination (Join-Path $out "Suixinji-$Version.apk") -Force
Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination $out -Force
Copy-Item -LiteralPath (Join-Path $root 'qa/TEST_REPORT.md') -Destination $out -Force
Copy-Item -LiteralPath (Join-Path $root 'THIRD_PARTY_NOTICES.md') -Destination $out -Force
Copy-Item -LiteralPath (Join-Path $root 'docs/ANDROID_IMPORT.md') -Destination $out -Force
Copy-Item -LiteralPath (Join-Path $root 'docs/screenshots/v112-browser.png') -Destination $screens -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
function CreateZip([string]$path,[System.IO.FileInfo[]]$files,[string]$base){
    if(!$path.StartsWith($out+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive path outside deliverables'}
    if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path}
    $zip=[IO.Compression.ZipFile]::Open($path,[IO.Compression.ZipArchiveMode]::Create)
    try{foreach($file in $files){$entry=[IO.Path]::GetRelativePath($base,$file.FullName).Replace('\','/');[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$entry,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$zip.Dispose()}
}
$sourceFiles=@()
foreach($folder in @('app/src','app/schemas','tools','docs','gradle/wrapper')){$sourceFiles+=Get-ChildItem -LiteralPath (Join-Path $root $folder) -Recurse -File}
foreach($name in @('.gitignore','LICENSE','CHANGELOG.md','README.md','THIRD_PARTY_NOTICES.md','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','app/proguard-rules.pro','app/gradle.lockfile','qa/TEST_REPORT.md','qa/TEST_REPORT_1.0.0.md','qa/TEST_REPORT_1.1.0.md','qa/TEST_REPORT_1.1.1.md')){$sourceFiles+=Get-Item -Force -LiteralPath (Join-Path $root $name)}
$sourcePath=Join-Path $out "Suixinji-$Version-source.zip"
CreateZip $sourcePath $sourceFiles $root
$check=[IO.Compression.ZipFile]::OpenRead($sourcePath)
try{if($check.Entries | Where-Object {$_.FullName -match '(^|/)(\.tools|build|signing.properties|local.properties)(/|$)|\.(jks|keystore)$'}){throw 'Private or generated files in source archive'};Write-Output "Source archive verified: $($check.Entries.Count) files"}finally{$check.Dispose()}
$hashLines=@("Suixinji-$Version.apk","Suixinji-$Version-source.zip",'README.md','TEST_REPORT.md') | ForEach-Object {$hash=(Get-FileHash -LiteralPath (Join-Path $out $_) -Algorithm SHA256).Hash.ToLowerInvariant();"$hash  $_"}
$hashLines | Set-Content -LiteralPath (Join-Path $out 'SHA256SUMS.txt') -Encoding utf8
$bundleFiles=Get-ChildItem -LiteralPath $out -File | Where-Object {$_.Name -in @("Suixinji-$Version.apk","Suixinji-$Version-source.zip","README.md","TEST_REPORT.md","ANDROID_IMPORT.md","THIRD_PARTY_NOTICES.md","SHA256SUMS.txt")}
$bundleFiles+=Get-ChildItem -LiteralPath $screens -File
CreateZip (Join-Path $out "Suixinji-$Version-delivery.zip") $bundleFiles $out
Get-ChildItem -LiteralPath $out -File | Select-Object Name,Length | Format-Table -AutoSize
