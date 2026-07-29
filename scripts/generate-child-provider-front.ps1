param(
    [string]$Output = "src/main/resources/assets/sequenced_pattern_provider/textures/block/child_pattern_provider_front.png"
)

Add-Type -AssemblyName System.Drawing

$absoluteOutput = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\$Output"))
$directory = Split-Path -Parent $absoluteOutput
[System.IO.Directory]::CreateDirectory($directory) | Out-Null

$bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function Set-Pixel([int]$x, [int]$y, [string]$hex) {
    $bitmap.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($hex))
}

$casing = @('#6f6754', '#766d58', '#665f4e', '#80765e')
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $edge = $x -eq 0 -or $x -eq 15 -or $y -eq 0 -or $y -eq 15
        if ($edge) {
            Set-Pixel $x $y ($(if ((($x + $y) % 3) -eq 0) { '#3d3a31' } else { '#4b4639' }))
        } else {
            Set-Pixel $x $y $casing[(($x * 3 + $y * 5) % $casing.Count)]
        }
    }
}

# Recessed square housing.
for ($y = 2; $y -le 13; $y++) {
    for ($x = 2; $x -le 13; $x++) {
        Set-Pixel $x $y '#292a27'
    }
}
for ($x = 3; $x -le 12; $x++) {
    Set-Pixel $x 3 '#aa8a4c'
    Set-Pixel $x 12 '#59492d'
}
for ($y = 3; $y -le 12; $y++) {
    Set-Pixel 3 $y '#947640'
    Set-Pixel 12 $y '#4f422d'
}
for ($y = 4; $y -le 11; $y++) {
    for ($x = 4; $x -le 11; $x++) {
        Set-Pixel $x $y ($(if ((($x + $y) % 2) -eq 0) { '#20292c' } else { '#252f31' }))
    }
}

# Bright output chevron/port, designed to stay readable at 16x16.
foreach ($point in @(@(7,5),@(8,5),@(7,6),@(8,6))) { Set-Pixel $point[0] $point[1] '#8ff6ff' }
foreach ($point in @(@(6,7),@(7,7),@(8,7),@(9,7))) { Set-Pixel $point[0] $point[1] '#49dce9' }
foreach ($point in @(@(5,8),@(6,8),@(7,8),@(8,8),@(9,8),@(10,8))) { Set-Pixel $point[0] $point[1] '#249dac' }
foreach ($point in @(@(6,9),@(7,9),@(8,9),@(9,9))) { Set-Pixel $point[0] $point[1] '#176d79' }
foreach ($point in @(@(7,10),@(8,10))) { Set-Pixel $point[0] $point[1] '#104a53' }

# Small brass extraction slot under the indicator.
for ($x = 5; $x -le 10; $x++) { Set-Pixel $x 11 ($(if ($x -eq 5 -or $x -eq 10) { '#4d402c' } else { '#b28d47' })) }

$bitmap.Save($absoluteOutput, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
Write-Output $absoluteOutput
