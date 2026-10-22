Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$res = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\windows\res'))

$GEO_TAIL = 64,214, 150,214, 176,256, 150,298, 64,298, 98,256
$GEO = @{
    regular = @{ shaft = 150, 244, 220, 24; head = 352,196, 456,256, 352,316; pivot = 30 }
    bold    = @{ shaft = 150, 236, 220, 40; head = 352,186, 466,256, 352,326; pivot = 40 }
}

$BRAND = @{ tile = '#221A4D'; tail = '#5C7CFF'; shaft = '#F3EEFF'; head = '#B79CFF' }
$MARK = @{ tail = '#5C7CFF'; body = '#6E54E8'; head = '#9D7DFF' }
$STATES = [ordered]@{
    'Running'   = '#5BD68A', '#14A050'
    'Off'       = '#9AA0A8', '#6B7178'
    'Proxy'     = '#4DA3FF', '#1F6FE5'
    'Dns'       = '#B48CFF', '#7C4DFF'
    'Proxy-Dns' = '#B48CFF', '#1F6FE5'
    'Tun'       = '#FF6B6B', '#D92B2B'
}

function Col($hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Poly($pts) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $a = for ($i = 0; $i -lt $pts.Count; $i += 2) { New-Object System.Drawing.PointF([single]$pts[$i], [single]$pts[$i + 1]) }
    $p.AddPolygon([System.Drawing.PointF[]]$a); $p
}
function RoundRect($x, $y, $w, $h, $r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90); $p.CloseFigure(); $p
}
function Brush($c) { New-Object System.Drawing.SolidBrush $c }

function DrawVane($g, $form, $tail, $body, $head, $hole) {
    $f = $GEO[$form]; $state = $g.Save()
    $g.TranslateTransform(256, 256); $g.RotateTransform(-30); $g.TranslateTransform(-256, -256)
    $g.FillPath((Brush $tail), (Poly $GEO_TAIL))
    $g.FillPath((Brush $body), (RoundRect $f.shaft[0] $f.shaft[1] $f.shaft[2] $f.shaft[3] ($f.shaft[3] / 2)))
    $g.FillPath((Brush $head), (Poly $f.head))
    $r = $f.pivot
    if ($hole) {
        $g.FillEllipse((Brush $hole), 256 - $r, 256 - $r, 2 * $r, 2 * $r)
        $pen = New-Object System.Drawing.Pen $body, 14
        $g.DrawEllipse($pen, 256 - $r, 256 - $r, 2 * $r, 2 * $r)
    } else {
        $g.FillEllipse((Brush $body), 256 - $r, 256 - $r, 2 * $r, 2 * $r)
    }
    $g.Restore($state)
}

function NewCanvas($px) {
    $bmp = New-Object System.Drawing.Bitmap $px, $px, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.PixelOffsetMode = 'HighQuality'; $g.Clear([System.Drawing.Color]::Transparent)
    $g.ScaleTransform($px / 512, $px / 512)
    $bmp, $g
}

function RenderState($px, $c) {
    $bmp, $g = NewCanvas $px
    $g.TranslateTransform(270, 242); $g.ScaleTransform(1.18, 1.18); $g.TranslateTransform(-265, -256)
    DrawVane $g 'bold' (Col $c[0]) (Col $c[1]) (Col $c[1]) $null
    $g.Dispose(); $bmp
}

function RenderMark($px) {
    $bmp, $g = NewCanvas $px
    $g.TranslateTransform(270, 242); $g.ScaleTransform(1.18, 1.18); $g.TranslateTransform(-265, -256)
    DrawVane $g 'bold' (Col $MARK.tail) (Col $MARK.body) (Col $MARK.head) $null
    $g.Dispose(); $bmp
}

function RenderTile($px) {
    $bmp, $g = NewCanvas $px
    $g.FillPath((Brush (Col $BRAND.tile)), (RoundRect 16 16 480 480 112))
    if ($px -le 32) {
        $g.TranslateTransform(256, 256); $g.ScaleTransform(1.04, 1.04); $g.TranslateTransform(-260, -256)
        DrawVane $g 'bold' (Col $BRAND.tail) (Col $BRAND.shaft) (Col $BRAND.head) $null
    } else {
        DrawVane $g 'regular' (Col $BRAND.tail) (Col $BRAND.shaft) (Col $BRAND.head) (Col $BRAND.tile)
    }
    $g.Dispose(); $bmp
}

function PngBytes($bmp) { $ms = New-Object System.IO.MemoryStream; $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png); , $ms.ToArray() }

function DibBytes($bmp) {
    $s = $bmp.Width; $ms = New-Object System.IO.MemoryStream; $w = New-Object System.IO.BinaryWriter $ms
    $w.Write([uint32]40); $w.Write([int32]$s); $w.Write([int32]($s * 2)); $w.Write([uint16]1); $w.Write([uint16]32)
    $w.Write([uint32]0); $w.Write([uint32]0); $w.Write([int32]0); $w.Write([int32]0); $w.Write([uint32]0); $w.Write([uint32]0)
    for ($y = $s - 1; $y -ge 0; $y--) { for ($x = 0; $x -lt $s; $x++) { $c = $bmp.GetPixel($x, $y); $w.Write([byte]$c.B); $w.Write([byte]$c.G); $w.Write([byte]$c.R); $w.Write([byte]$c.A) } }
    $maskRow = [int]([math]::Ceiling($s / 32) * 4); $w.Write((New-Object byte[] ($maskRow * $s)))
    $w.Flush(); , $ms.ToArray()
}

function WriteIco($path, $sizes) {
    $images = New-Object System.Collections.Generic.List[byte[]]
    foreach ($s in $sizes) { $b = RenderTile $s; if ($s -ge 256) { $images.Add((PngBytes $b)) } else { $images.Add((DibBytes $b)) } }
    $fs = [System.IO.File]::Create($path); $w = New-Object System.IO.BinaryWriter $fs
    $w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $s = $sizes[$i]; $b = if ($s -ge 256) { 0 } else { $s }
        $w.Write([byte]$b); $w.Write([byte]$b); $w.Write([byte]0); $w.Write([byte]0)
        $w.Write([uint16]1); $w.Write([uint16]32); $w.Write([uint32]$images[$i].Length); $w.Write([uint32]$offset)
        $offset += $images[$i].Length
    }
    foreach ($img in $images) { $w.Write($img) }
    $w.Close()
}

foreach ($name in $STATES.Keys) {
    $bmp = RenderState 512 $STATES[$name]
    $bmp.Save((Join-Path $res "public\$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}
(RenderTile 256).Save((Join-Path $res 'public\App.png'), [System.Drawing.Imaging.ImageFormat]::Png)
(RenderTile 32).Save((Join-Path $res 'public\App-32.png'), [System.Drawing.Imaging.ImageFormat]::Png)
foreach ($s in 24, 48, 128) { (RenderMark $s).Save((Join-Path $res "public\Mark-$s.png"), [System.Drawing.Imaging.ImageFormat]::Png) }
WriteIco (Join-Path $res 'Lumvane.ico') @(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)
(RenderTile 512).Save((Join-Path $PSScriptRoot 'icon-512.png'), [System.Drawing.Imaging.ImageFormat]::Png)
"ok: windows\res\Lumvane.ico, windows\res\public\*.png, design\icon-512.png"
