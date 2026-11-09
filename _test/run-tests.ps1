#requires -version 5.1
# Runs the JVM data-layer tests without an Android device.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $root '..\_tools\jdk'
if (-not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
    $jdk = $env:JAVA_HOME
}
$json = Join-Path $PSScriptRoot 'lib\json.jar'
$out = Join-Path $PSScriptRoot 'out'
if (-not (Test-Path $json)) { throw "Missing $json" }
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$sources = @(
    (Join-Path $PSScriptRoot 'src\android\content\Context.java'),
    (Join-Path $PSScriptRoot 'src\android\content\res\Resources.java'),
    (Join-Path $PSScriptRoot 'src\android\util\Log.java'),
    (Join-Path $PSScriptRoot 'src\android\util\Base64.java'),
    (Join-Path $PSScriptRoot 'src\android\graphics\Color.java'),
    (Join-Path $PSScriptRoot 'src\android\icu\util\ChineseCalendar.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\SolarTerms.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\DefaultHolidays.java'),
    (Join-Path $PSScriptRoot 'src\com\etherealnymph\courseflow\R.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\Palette.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\Dates.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\Store.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\WeekSpec.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\TimeTextParser.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\LegacyImport.java'),
    (Join-Path $root 'app\src\main\java\com\etherealnymph\courseflow\PrivateVault.java'),
    (Join-Path $PSScriptRoot 'src\StoreTest.java')
)
& (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 -source 11 -target 11 `
    -cp $json -d $out $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Push-Location $root
try {
    & (Join-Path $jdk 'bin\java.exe') -cp "$out;$json" StoreTest
    if ($LASTEXITCODE -ne 0) { throw 'tests failed' }
} finally {
    Pop-Location
}
