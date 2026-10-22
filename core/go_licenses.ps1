param(
    [Parameter(Mandatory)][string]$Dir,
    [Parameter(Mandatory)][string]$Goos,
    [Parameter(Mandatory)][string]$Tags,
    [Parameter(Mandatory)][string]$Out,
    [string]$Goarch = "amd64",
    [string]$Package = ".",
    [string[]]$ExtraModule = @()
)
$ErrorActionPreference = "Stop"

$saved = @{ GOOS = $env:GOOS; GOARCH = $env:GOARCH; CGO_ENABLED = $env:CGO_ENABLED; GOFLAGS = $env:GOFLAGS }
$env:GOOS = $Goos; $env:GOARCH = $Goarch; $env:CGO_ENABLED = "1"; $env:GOFLAGS = "-mod=mod"
Push-Location $Dir
try {
    $lines = & go list -deps -tags $Tags -f '{{with .Module}}{{if not .Main}}{{.Path}}|{{.Version}}|{{.Dir}}{{end}}{{end}}' $Package
    if ($LASTEXITCODE) { throw "go list failed" }
    foreach ($m in $ExtraModule) {
        $info = (& go mod download -json $m) -join "`n" | ConvertFrom-Json
        if (-not $info.Dir) { throw "go mod download $m failed" }
        $lines += "$($info.Path)|$($info.Version)|$($info.Dir)"
    }
    $goVersion = (& go env GOVERSION).Trim()
    $lines += "Go runtime and standard library|$goVersion|$((& go env GOROOT).Trim())"
} finally {
    Pop-Location
    foreach ($k in $saved.Keys) { Set-Item "env:$k" $(if ($null -eq $saved[$k]) { "" } else { $saved[$k] }) }
}

$modules = $lines | Where-Object { $_ } | Sort-Object -Unique | ForEach-Object {
    $p = $_.Split("|"); [pscustomobject]@{ Path = $p[0]; Version = $p[1]; Dir = $p[2] }
} | Where-Object { $_.Path -ne "lumshared" }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("Go modules linked into the core ($Goos/$Goarch) and their licenses")
[void]$sb.AppendLine("Go-модули, собранные в ядро ($Goos/$Goarch), и их лицензии")
[void]$sb.AppendLine("")
$missing = @()
foreach ($m in $modules) {
    $files = Get-ChildItem -LiteralPath $m.Dir -File | Where-Object { $_.Name -match '^(LICEN[CS]E|COPYING|NOTICE|UNLICENSE)([-._].*)?$' } | Sort-Object Name
    [void]$sb.AppendLine(("=" * 78))
    [void]$sb.AppendLine("$($m.Path) $($m.Version)".Trim())
    [void]$sb.AppendLine(("=" * 78))
    if (-not $files) {
        $missing += $m.Path
        [void]$sb.AppendLine("No license file in the module; see https://pkg.go.dev/$($m.Path)")
        [void]$sb.AppendLine("")
        continue
    }
    foreach ($f in $files) {
        if ($files.Count -gt 1) { [void]$sb.AppendLine("--- $($f.Name) ---") }
        [void]$sb.AppendLine(([IO.File]::ReadAllText($f.FullName)).TrimEnd())
        [void]$sb.AppendLine("")
    }
}
$outFull = [IO.Path]::GetFullPath($Out)
New-Item -ItemType Directory -Force (Split-Path $outFull) | Out-Null
[IO.File]::WriteAllText($outFull, ($sb.ToString() -replace "`r`n", "`n"), (New-Object System.Text.UTF8Encoding $false))
Write-Host "Go licenses: $($modules.Count) modules -> $outFull"
if ($missing) { Write-Warning "No license file: $($missing -join ', ')" }
