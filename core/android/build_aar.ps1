param(
    [string]$Targets = "android/arm64,android/arm,android/amd64",
    [string]$Out = "",
    [int]$AndroidApi = 26,
    [switch]$DebugBuild
)
$ErrorActionPreference = "Stop"
$coreDir = $PSScriptRoot
if (-not $Out) { $Out = Join-Path $coreDir "..\..\android\app\libs\lumcore.aar" }
$Out = [System.IO.Path]::GetFullPath($Out)

foreach ($name in @("JAVA_HOME", "ANDROID_HOME", "ANDROID_NDK_HOME")) {
    if (-not (Get-Item "env:$name" -ErrorAction SilentlyContinue)) {
        $value = [Environment]::GetEnvironmentVariable($name, "User")
        if ($value) { Set-Item "env:$name" $value }
    }
}
if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
if (-not $env:ANDROID_HOME) { throw "ANDROID_HOME is not set and no SDK found" }
if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
if (-not $env:ANDROID_NDK_HOME) {
    $ndkRoot = Join-Path $env:ANDROID_HOME "ndk"
    $ndk = Get-ChildItem $ndkRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "27.*" } | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ndk) { throw "NDK r27 not found under $ndkRoot (install ndk;27.x)" }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw "java not found (set JAVA_HOME to JDK 21)" }
Write-Host "ANDROID_HOME=$env:ANDROID_HOME`nANDROID_NDK_HOME=$env:ANDROID_NDK_HOME`nJAVA_HOME=$env:JAVA_HOME"

$gomobileVersion = "v0.1.12"
$gobin = (& go env GOBIN); if (-not $gobin) { $gobin = Join-Path (& go env GOPATH) "bin" }
$env:PATH = "$gobin;$env:PATH"
if (-not (Get-Command gomobile -ErrorAction SilentlyContinue) -or -not (Get-Command gobind -ErrorAction SilentlyContinue)) {
    Write-Host "Installing sagernet/gomobile $gomobileVersion"
    & go install "github.com/sagernet/gomobile/cmd/gomobile@$gomobileVersion"
    if ($LASTEXITCODE) { throw "go install gomobile failed" }
    & go install "github.com/sagernet/gomobile/cmd/gobind@$gomobileVersion"
    if ($LASTEXITCODE) { throw "go install gobind failed" }
}
function Invoke-Gomobile([string[]]$GmArgs) {
    $exe = (Get-Command gomobile).Source
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.UseShellExecute = $false
    $psi.WorkingDirectory = (Get-Location).Path
    $psi.Arguments = ($GmArgs | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
    foreach ($k in @($psi.EnvironmentVariables.Keys)) {
        if ($k.StartsWith("=")) { $psi.EnvironmentVariables.Remove($k) }
    }
    $p = [System.Diagnostics.Process]::Start($psi)
    $p.WaitForExit()
    $global:LASTEXITCODE = $p.ExitCode
}
Invoke-Gomobile @("init")
if ($LASTEXITCODE) { throw "gomobile init failed" }

$tags = "with_gvisor,with_quic,with_dhcp,with_wireguard,with_utls,with_clash_api,with_tailscale,badlinkname,tfogo_checklinkname0"
$ldflags = "-s -w -buildid= -checklinkname=0"
if ($DebugBuild) { $ldflags = "-checklinkname=0" }

New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null
Push-Location $coreDir
try {
    $env:GOFLAGS = "-mod=mod"
    $bindArgs = @("bind", "-v", "-o", $Out, "-target", $Targets, "-androidapi", "$AndroidApi",
        "-javapkg", "com.lumvane", "-libname", "lumcore", "-tags", $tags, "-trimpath", "-ldflags", $ldflags, "./lumcore")
    Write-Host "gomobile $($bindArgs -join ' ')"
    Invoke-Gomobile $bindArgs
    if ($LASTEXITCODE) { throw "gomobile bind failed ($LASTEXITCODE)" }
} finally { Pop-Location }
& (Join-Path $coreDir "..\go_licenses.ps1") -Dir $coreDir -Goos android -Goarch arm64 -Package ./lumcore -Tags $tags `
    -Out (Join-Path (Split-Path $Out) "lumcore-licenses.txt")
Write-Host "OK: $Out"
