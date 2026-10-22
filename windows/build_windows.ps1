param([switch]$SkipGo, [switch]$SkipCpp, [switch]$Tests, [string]$Version = '1.0.1')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = $PSScriptRoot
$coreSrc = [System.IO.Path]::GetFullPath((Join-Path $root '..\core\windows'))
$tc = Join-Path $root '.toolchain'
$qt = Join-Path $tc 'Qt'
$cmakeBin = (Get-ChildItem $tc -Directory -Filter 'cmake-*' | Select-Object -First 1).FullName + '\bin'
$env:PATH = "$cmakeBin;$tc\ninja;$tc\protoc\bin;$tc\gobin;$qt\bin;$env:PATH"
$deploy = Join-Path $root 'deploy\Lumvane'
$tags = 'with_clash_api,with_gvisor,with_quic,with_wireguard,with_utls,with_dhcp,with_tailscale,badlinkname,tfogo_checklinkname0,with_purego,with_naive_outbound'
$env:GOTOOLCHAIN = 'go1.27.0'

function Step($m) { Write-Host "`n=== $m" -ForegroundColor Cyan }

Step 'Loading MSVC environment'
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$vsPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $vsPath) { throw 'Visual Studio 2022 Build Tools with the C++ workload were not found.' }
$env:PATH = "$(Split-Path $vswhere);$env:PATH"
cmd /c "`"$vsPath\VC\Auxiliary\Build\vcvars64.bat`" >nul && set" | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] }
}

New-Item -ItemType Directory -Force $deploy | Out-Null

if (-not $SkipGo) {
    Step 'Generating protobuf code'
    Push-Location (Join-Path $coreSrc 'gen')
    protoc -I . --go_out=. --go-grpc_out=. libcore.proto
    if ($LASTEXITCODE) { throw 'protoc failed' }
    Pop-Location

    Step 'Building LumvaneCore.exe'
    Push-Location $coreSrc
    $env:GOOS = 'windows'; $env:GOARCH = 'amd64'; $env:CGO_ENABLED = '0'
    $singbox = (go list -m -f '{{.Version}}' github.com/sagernet/sing-box).Trim()
    $ld = "-w -s -X 'github.com/sagernet/sing-box/constant.Version=$singbox' -X 'runtime.godebugDefault=multipathtcp=0,tlssha1=1' -checklinkname=0"
    go build -v -o $deploy -trimpath -ldflags $ld -tags $tags
    if ($LASTEXITCODE) { throw 'go build failed' }
    Pop-Location
}

Push-Location $coreSrc
$cronetModule = ((go mod download -json github.com/sagernet/cronet-go/lib/windows_amd64) -join "`n" | ConvertFrom-Json).Dir
Pop-Location
if (-not $cronetModule) { throw 'cronet-go/lib/windows_amd64 module not found' }
$cronetSrc = Join-Path $cronetModule 'libcronet.dll'
$cronet = Join-Path $deploy 'libcronet.dll'
if (-not (Test-Path $cronet) -or (Get-FileHash $cronet).Hash -ne (Get-FileHash $cronetSrc).Hash) {
    Step 'Copying libcronet.dll (cronet-go module pinned in go.mod)'
    Copy-Item $cronetSrc $cronet -Force
    Set-ItemProperty $cronet -Name IsReadOnly -Value $false
}

if (-not $SkipCpp) {
    Step 'Configuring (CMake + Ninja, Release)'
    $build = Join-Path $root 'build'
    New-Item -ItemType Directory -Force $build | Out-Null
    Copy-Item (Join-Path $root 'res\srslist.h') $build -Force
    $env:INPUT_VERSION = $Version
    Push-Location $build
    cmake -GNinja -DCMAKE_BUILD_TYPE=Release "-DCMAKE_PREFIX_PATH=$qt" ..
    if ($LASTEXITCODE) { throw 'cmake configure failed' }
    Step 'Compiling'
    ninja
    if ($LASTEXITCODE) { throw 'ninja failed' }
    Pop-Location

    Copy-Item (Join-Path $build 'Lumvane.exe') $deploy -Force
    Step 'Bundling Qt runtime (windeployqt)'
    windeployqt --release --no-translations --no-system-d3d-compiler --no-opengl-sw (Join-Path $deploy 'Lumvane.exe')
}

Step 'Collecting licenses'
$repo = Split-Path $root
$licDir = Join-Path $deploy 'licenses'
New-Item -ItemType Directory -Force $licDir | Out-Null
Copy-Item (Join-Path $repo 'LICENSE') (Join-Path $deploy 'LICENSE.txt') -Force
Copy-Item (Join-Path $repo 'THIRD_PARTY_NOTICES.md') $deploy -Force
Copy-Item (Join-Path $repo 'licenses\*') $licDir -Force
& (Join-Path $repo 'core\go_licenses.ps1') -Dir $coreSrc -Goos windows -Tags $tags `
    -Out (Join-Path $licDir 'Go-modules.txt') -ExtraModule 'github.com/sagernet/cronet-go/lib/windows_amd64'

if ($Tests) {
    Step 'Building GUI tests'
    $testBuild = Join-Path $root 'build\tests'
    New-Item -ItemType Directory -Force $testBuild | Out-Null
    Copy-Item (Join-Path $root 'res\srslist.h') $testBuild -Force
    $env:INPUT_VERSION = $Version
    Push-Location $testBuild
    try {
        cmake -GNinja -DCMAKE_BUILD_TYPE=Release -DLUMVANE_TESTS=ON "-DCMAKE_PREFIX_PATH=$qt" $root
        if ($LASTEXITCODE) { throw 'cmake configure (tests) failed' }
        ninja lumvane_tests
        if ($LASTEXITCODE) { throw 'ninja lumvane_tests failed' }
        Step 'Running GUI tests'
        & .\lumvane_tests.exe -platform offscreen -o test-results.txt,txt
        $testExit = $LASTEXITCODE
        if (Test-Path test-results.txt) { Get-Content test-results.txt }
        if ($testExit) { throw "GUI tests failed ($testExit)" }
    } finally { Pop-Location }
}

Step "Done: $deploy"
