param([string]$Jdk = $env:JAVA_HOME, [string]$Sdk = $env:ANDROID_HOME,
      [string]$Platform, [string]$BuildTools)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$Jdk) { $Jdk = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName }
if (!$Platform) {
    if ($Sdk) { $Platform = Join-Path $Sdk 'platforms/android-35' }
    else { $Platform = '.tools/platform/android-35' }
}
if (!$BuildTools) {
    if ($Sdk) { $BuildTools = Join-Path $Sdk 'build-tools/35.0.0' }
    else { $BuildTools = '.tools/build/android-15' }
}
$api = (Resolve-Path "$Platform/android.jar").Path
$bin = (Resolve-Path $BuildTools).Path
$env:JAVA_HOME = (Resolve-Path $Jdk).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
function Run([string]$program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "$program exited with $LASTEXITCODE" }
}
$work = (New-Item -ItemType Directory -Force "build/$([Guid]::NewGuid())").FullName
$classes = New-Item -ItemType Directory "$work/classes"
$out = (New-Item -ItemType Directory -Force dist).FullName
Run "$bin/aapt2.exe" @('compile','--dir','app/res','-o',"$work/res.zip")
Run "$bin/aapt2.exe" @('link','-o',"$work/base.apk",'-I',$api,'--manifest','app/AndroidManifest.xml',"$work/res.zip")
Run 'javac' (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',"$bin/core-lambda-stubs.jar;$api",'-d',$classes.FullName) + @(Get-ChildItem app/src -Recurse -Filter *.java | ForEach-Object FullName))
Run 'jar' @('cf',"$work/app.jar",'-C',$classes.FullName,'.')
Run 'java' @('-cp',"$bin/lib/d8.jar",'com.android.tools.r8.D8','--min-api','21','--lib',$api,'--output',$work,"$work/app.jar")
Run 'jar' @('uf',"$work/base.apk",'-C',$work,'classes.dex')
Run "$bin/zipalign.exe" @('-f','4',"$work/base.apk","$work/aligned.apk")
$sign = (New-Item -ItemType Directory -Force '.tools/signing').FullName
$key = "$sign/simpletvdesk.jks"
$pass = "$sign/password.txt"
if (!(Test-Path $key)) {
    [Guid]::NewGuid().ToString('N') | Set-Content -Encoding ascii $pass
    Run 'keytool' @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-storepass:file',$pass,'-alias','desktop','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=SimpleTVDesk')
}
$apk = "$out/SimpleTVDesk.apk"
Run 'java' @('-jar',"$bin/lib/apksigner.jar",'sign','--ks',$key,'--ks-pass',"file:$pass",'--v4-signing-enabled','false','--out',$apk,"$work/aligned.apk")
Run 'java' @('-jar',"$bin/lib/apksigner.jar",'verify','--verbose',$apk)
Run "$bin/zipalign.exe" @('-c','4',$apk)
Get-Item -LiteralPath $apk | Select-Object FullName,Length
