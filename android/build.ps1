param([string]$Task = "assembleDebug")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME)    { $env:JAVA_HOME    = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User") }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable("ANDROID_HOME", "User") }
if (-not (Test-Path "local.properties")) {
    "sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/') | Set-Content -Encoding ascii local.properties
}
if ($Task -eq "screenshots") {
    & .\gradlew.bat testDebugUnitTest -Pscreenshots
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Screenshots: ..\docs\screenshots\android-*.png"
    exit 0
}
& .\gradlew.bat $Task
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Task -like "assemble*") { Write-Host "APK: app\build\outputs\apk\<buildType>\app-<abi>-<buildType>.apk (phone: arm64-v8a)" }
