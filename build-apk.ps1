#requires -version 5.1
<#
.SYNOPSIS
  Builds, aligns and signs 课序 / CourseFlow without Gradle.

.DESCRIPTION
  Only needs JDK 17 and Android SDK build-tools 34.0.0 / platform android-34.
  The default paths point at the shared _tools folder next to this project.

.EXAMPLE
  pwsh -File .\build-apk.ps1
#>
[CmdletBinding()]
param(
    [string]$JdkPath = '',
    [string]$SdkPath = '',
    [int]$VersionCode = 5,
    [string]$VersionName = '1.2.2',
    [switch]$Demo,
    [string]$OutputSuffix = '',
    [string]$AppLabel = '时屿'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($JdkPath)) {
    $localJdk = Join-Path $PSScriptRoot '..\_tools\jdk'
    $JdkPath = if (Test-Path $localJdk) { $localJdk } else { 'C:\Program Files\Android\Android Studio\jbr' }
}
if ([string]::IsNullOrWhiteSpace($SdkPath)) {
    $localSdk = Join-Path $PSScriptRoot '..\_tools\sdk'
    $SdkPath = if (Test-Path $localSdk) { $localSdk } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}

$projectRoot = $PSScriptRoot
$appRoot = Join-Path $projectRoot 'app\src\main'
$buildRoot = Join-Path $projectRoot 'build\manual'
$distRoot = Join-Path $projectRoot 'dist'
$packageName = 'com.etherealnymph.courseflow'
$buildTools = Join-Path $SdkPath 'build-tools\34.0.0'
$androidJar = Join-Path $SdkPath 'platforms\android-34\android.jar'
$java = Join-Path $JdkPath 'bin\java.exe'
$javac = Join-Path $JdkPath 'bin\javac.exe'
$keytool = Join-Path $JdkPath 'bin\keytool.exe'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8Jar = Join-Path $buildTools 'lib\d8.jar'
$apksignerJar = Join-Path $buildTools 'lib\apksigner.jar'
$keystore = Join-Path $projectRoot 'keystore\courseflow-demo.jks'
$keystorePassword = $env:APK_KEYSTORE_PASSWORD
if ([string]::IsNullOrWhiteSpace($keystorePassword)) {
    throw 'Set APK_KEYSTORE_PASSWORD before creating or signing an APK.'
}
$keyAlias = 'courseflow'

foreach ($required in @($java, $javac, $aapt2, $zipalign, $androidJar, $d8Jar, $apksignerJar)) {
    if (-not (Test-Path $required)) { throw "Missing build tool: $required" }
}

$env:JAVA_HOME = $JdkPath
$env:Path = "$(Join-Path $JdkPath 'bin');$env:Path"

if (Test-Path $buildRoot) { Remove-Item $buildRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path `
    (Join-Path $buildRoot 'compiled'), `
    (Join-Path $buildRoot 'gen'), `
    (Join-Path $buildRoot 'classes'), `
    (Join-Path $buildRoot 'dex'), `
    $distRoot | Out-Null

$manifestText = Get-Content (Join-Path $appRoot 'AndroidManifest.xml') -Raw
if ($manifestText -notmatch 'package\s*=') {
    $manifestText = $manifestText -replace '<manifest\s', "<manifest package=`"$packageName`" "
}
$manifestText = $manifestText.Replace('android:label="@string/app_name"',
    'android:label="' + $AppLabel + '"')
$manifestFile = Join-Path $buildRoot 'AndroidManifest.xml'
[System.IO.File]::WriteAllText($manifestFile, $manifestText, [System.Text.UTF8Encoding]::new($false))

$configPath = Join-Path $appRoot 'res\values\config.xml'
$configOriginal = [System.IO.File]::ReadAllText($configPath)
if ($Demo) {
    $config = '<?xml version="1.0" encoding="utf-8"?><resources><bool name="demo_mode">true</bool></resources>'
    [System.IO.File]::WriteAllText($configPath, $config,
        [System.Text.UTF8Encoding]::new($false))
}

try {
    Write-Host '[1/7] Compiling resources' -ForegroundColor Cyan
    & $aapt2 compile --dir (Join-Path $appRoot 'res') -o (Join-Path $buildRoot 'compiled.zip')
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

    Write-Host '[2/7] Linking resources and generating R.java' -ForegroundColor Cyan
    $linkArgs = @(
        '-o', (Join-Path $buildRoot 'app-unsigned.apk'),
        '-I', $androidJar,
        '--manifest', $manifestFile,
        '--java', (Join-Path $buildRoot 'gen'),
        '--min-sdk-version', '29',
        '--target-sdk-version', '34',
        '--version-code', $VersionCode,
        '--version-name', $VersionName,
        '--auto-add-overlay',
        '-A', (Join-Path $appRoot 'assets'),
        (Join-Path $buildRoot 'compiled.zip')
    )
    & $aapt2 link @linkArgs
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }
} finally {
    [System.IO.File]::WriteAllText($configPath, $configOriginal,
        [System.Text.UTF8Encoding]::new($false))
}

Write-Host '[3/7] Compiling Java sources' -ForegroundColor Cyan
$javaFiles = @(
    Get-ChildItem (Join-Path $appRoot 'java') -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName }
    Get-ChildItem (Join-Path $buildRoot 'gen') -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName }
)
& $javac `
    -encoding UTF-8 `
    --release 11 `
    -Xlint:-options `
    -classpath $androidJar `
    -d (Join-Path $buildRoot 'classes') `
    @javaFiles
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '[4/7] Dexing classes' -ForegroundColor Cyan
$classFiles = @(
    Get-ChildItem (Join-Path $buildRoot 'classes') -Recurse -Filter '*.class' |
        ForEach-Object { $_.FullName }
)
& $java -cp $d8Jar com.android.tools.r8.D8 `
    --lib $androidJar `
    --min-api 29 `
    --release `
    --output (Join-Path $buildRoot 'dex') `
    @classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[5/7] Packaging classes.dex' -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
$apkPath = Join-Path $buildRoot 'app-unsigned.apk'
$dexPath = Join-Path $buildRoot 'dex\classes.dex'
$zip = [System.IO.Compression.ZipFile]::Open($apkPath, 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, $dexPath, 'classes.dex',
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $zip.Dispose() }

Write-Host '[6/7] Aligning APK' -ForegroundColor Cyan
$aligned = Join-Path $buildRoot 'app-aligned.apk'
& $zipalign -p -f 4 $apkPath $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '[7/7] Signing APK' -ForegroundColor Cyan
if (-not (Test-Path $keystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $keystore) | Out-Null
    & $keytool -genkeypair `
        -keystore $keystore `
        -storepass $keystorePassword `
        -alias $keyAlias `
        -keypass $keystorePassword `
        -dname 'CN=CourseFlow, O=Ethereal Nymph, C=CN' `
        -keyalg RSA -keysize 2048 -validity 10000 -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

$output = Join-Path $distRoot "CourseFlow-$VersionName$OutputSuffix.apk"
& $java -jar $apksignerJar sign `
    --ks $keystore `
    --ks-pass "pass:$keystorePassword" `
    --key-pass "pass:$keystorePassword" `
    --ks-key-alias $keyAlias `
    --v1-signing-enabled false `
    --v2-signing-enabled true `
    --v3-signing-enabled true `
    --out $output `
    $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& $java -jar $apksignerJar verify --verbose $output
if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }

$size = (Get-Item $output).Length
Write-Host ''
Write-Host "Built: $output" -ForegroundColor Green
Write-Host ("Size : {0:N0} bytes" -f $size) -ForegroundColor Green
