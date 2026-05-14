param(
    [Parameter(Mandatory)]
    [string]$Name,

    [ValidateSet("library", "compose")]
    [string]$Type = "library"
)

$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
$moduleDir = Join-Path $repo $Name
$pkg = "com.taisau.android.common.$Name"
$pkgPath = $pkg.Replace('.', '\')

# create directory structure
$null = New-Item -ItemType Directory -Path "$moduleDir\src\main\kotlin\$pkgPath" -Force
$null = New-Item -ItemType Directory -Path "$moduleDir\src\main\res\values" -Force
$null = New-Item -ItemType Directory -Path "$moduleDir\src\test\kotlin\$pkgPath" -Force
$null = New-Item -ItemType Directory -Path "$moduleDir\src\androidTest\kotlin\$pkgPath" -Force

# .gitignore
Set-Content -Path "$moduleDir\.gitignore" -Value '/build'

# consumer-rules.pro (empty)
New-Item -ItemType File -Path "$moduleDir\consumer-rules.pro" -Force | Out-Null

# proguard-rules.pro
Set-Content -Path "$moduleDir\proguard-rules.pro" -Value "# Add project specific ProGuard rules here."

# AndroidManifest.xml
Set-Content -Path "$moduleDir\src\main\AndroidManifest.xml" -Value '<?xml version="1.0" encoding="utf-8"?><manifest />'

# build.gradle.kts
$plugin = if ($Type -eq "compose") {
    '    alias(libs.plugins.taisau.android.library.compose)'
} else {
    '    alias(libs.plugins.taisau.android.library)'
}

$lines = @(
    'plugins {'
    $plugin
    '}'
    ''
    'android {'
    "    namespace = ""$pkg"""
    '}'
)

Set-Content -Path "$moduleDir\build.gradle.kts" -Value ($lines -join "`r`n")

# add to settings.gradle.kts
$settings = Join-Path $repo "settings.gradle.kts"
$line = "include("":$Name"")"
$content = Get-Content $settings
if ($content -notcontains $line) {
    $idx = $content.Count - 1
    $content[$idx] = "$line`r`n$($content[$idx])"
    Set-Content -Path $settings -Value $content
}

Write-Host "Created module ':$Name' at $moduleDir"
