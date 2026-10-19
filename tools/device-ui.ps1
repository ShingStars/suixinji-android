param([ValidateSet('snapshot','tap','scroll')][string]$Action='snapshot',[string]$Text='',[string]$Serial='emulator-5554',[string]$Name='ui')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $root '.tools/android-sdk/platform-tools/adb.exe'
$artifacts=Join-Path $root 'qa/artifacts'
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
if($Name -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid artifact name'}
$remote="/data/local/tmp/sxj-$Name.xml"
$dump=''
for($attempt=0;$attempt -lt 4;$attempt++){
    $dump=(& $adb -s $Serial shell uiautomator dump $remote 2>&1) -join "`n"
    if($dump -match 'dumped to:'){break}
    Start-Sleep -Milliseconds 400
}
if($dump -notmatch 'dumped to:'){throw "Could not capture a fresh UI tree: $dump"}
& $adb -s $Serial pull $remote (Join-Path $artifacts "$Name.xml") 2>&1 | Out-Null
if($LASTEXITCODE -ne 0){throw 'Fresh UI tree could not be retrieved'}
[xml]$xml=Get-Content -Raw -LiteralPath (Join-Path $artifacts "$Name.xml")
if($Action -eq 'snapshot') {
    $xml.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { "$($_.text) | $($_.'content-desc') | $($_.bounds)" }
    & $adb -s $Serial shell screencap -p /data/local/tmp/sxj-screen.png
    & $adb -s $Serial pull /data/local/tmp/sxj-screen.png (Join-Path $artifacts "$Name.png") 2>&1 | Out-Null
} elseif($Action -eq 'tap') {
    $node=$xml.SelectNodes('//node') | Where-Object { $_.text -eq $Text -or $_.'content-desc' -eq $Text } | Select-Object -First 1
    if(!$node){throw "UI target not found: $Text"}
    $coords=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    $x=[int](($coords[0]+$coords[2])/2);$y=[int](($coords[1]+$coords[3])/2)
    & $adb -s $Serial shell input tap $x $y
    Write-Output "Tapped '$Text' at UI-derived ($x,$y)"
} else {
    $node=$xml.SelectNodes('//node') | Where-Object {$_.scrollable -eq 'true'} | Select-Object -First 1
    if(!$node){throw 'No scrollable node'}
    $coords=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    $x=[int](($coords[0]+$coords[2])/2);$span=$coords[3]-$coords[1]
    $from=[int]($coords[1]+$span*.8);$to=[int]($coords[1]+$span*.25)
    & $adb -s $Serial shell input swipe $x $from $x $to 400
}
