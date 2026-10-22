param(
    [ValidateSet("sing-box", "wireguard-go")]
    [string]$Module = "sing-box",
    [switch]$List,
    [string]$Out = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$fork = Join-Path $PSScriptRoot $Module
$path = "github.com/sagernet/$Module"

$line = Select-String -Path (Join-Path $root "windows\go.mod") -Pattern "^\s*$([regex]::Escape($path))\s+(\S+)" | Select-Object -First 1
if (-not $line) { throw "$path is not required in core/windows/go.mod" }
$version = $line.Matches[0].Groups[1].Value

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("vm-upstream-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    Push-Location $tmp
    $env:GOFLAGS = "-mod=mod"
    $json = & go mod download -json "$path@$version" | Out-String
    if ($LASTEXITCODE) { throw "go mod download $path@$version failed" }
    $upstream = ($json | ConvertFrom-Json).Dir
    if (-not $upstream) { throw "no module directory for $path@$version" }

    New-Item -ItemType Junction -Path (Join-Path $tmp "a") -Target $upstream | Out-Null
    New-Item -ItemType Junction -Path (Join-Path $tmp "b") -Target $fork | Out-Null

    $gitArgs = @("-c", "core.autocrlf=false", "-c", "core.quotepath=false", "diff", "--no-index", "--no-color", "--ignore-cr-at-eol", "--diff-filter=AM", "--no-prefix")
    if ($List) {
        $files = & git @gitArgs --name-only a b
        $files | ForEach-Object { $_ -replace '^b/', '' } | Sort-Object
        return
    }
    $patch = & git @gitArgs a b
    $header = "# $path $version -> core/third_party/$Module"
    if ($Out) {
        Set-Content -Path $Out -Value (@($header) + $patch) -Encoding utf8
        Write-Host "Wrote $Out"
    } else {
        $header
        $patch
    }
} finally {
    Pop-Location
    Get-ChildItem $tmp -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Delete() }
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}
