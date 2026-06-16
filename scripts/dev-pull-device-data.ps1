# Dev-only: pull kakeibo data from a debug build on a connected device.
# Usage (repo root):
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-pull-device-data.ps1
#
# Output: backups/dev/YYYY-MM-DD_HHmmss/kakeibo.db and receipts/ (if any)

$ErrorActionPreference = "Stop"
$pkg = "work.temp1209.kakeibo"

function Get-AdbPath {
    if (Get-Command adb -ErrorAction SilentlyContinue) { return "adb" }
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { $_ -and (Test-Path $_) }
    foreach ($sdk in $candidates) {
        $adb = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path $adb) { return $adb }
    }
    throw "adb not found. Add Android SDK platform-tools to PATH, or set ANDROID_HOME."
}

$adb = Get-AdbPath
$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$outDir = (Resolve-Path (Join-Path $PSScriptRoot "..\backups\dev\$stamp") -ErrorAction SilentlyContinue)
if (-not $outDir) {
    $outDir = New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot "..\backups\dev\$stamp")
}
$outDir = $outDir.FullName

Write-Host "Output: $outDir"

$adbDevices = & $adb devices 2>&1 | Out-String
if ($adbDevices -notmatch "(?m)^\S+\s+device\s*$") {
    throw "No adb device found. Run adb devices and connect Pixel 8a first."
}

$dbPath = Join-Path $outDir "kakeibo.db"
Write-Host "Pulling Room DB (kakeibo.db)..."
cmd /c "`"$adb`" exec-out run-as $pkg cat databases/kakeibo.db > `"$dbPath`""
if (-not (Test-Path $dbPath) -or (Get-Item $dbPath).Length -eq 0) {
    throw "Failed to pull DB (debug build required; run-as must work)."
}
$dbSize = (Get-Item $dbPath).Length
Write-Host "  -> $dbSize bytes"

Write-Host "Pulling receipt images if present..."
$imgDir = Join-Path $outDir "receipts"
New-Item -ItemType Directory -Force -Path $imgDir | Out-Null
$shCmd = "cd files/receipts 2>/dev/null && tar cf - ."
cmd /c "`"$adb`" exec-out run-as $pkg sh -c `"$shCmd`" > `"$imgDir\_receipts.tar`" 2>nul"
$tarPath = Join-Path $imgDir "_receipts.tar"
if ((Test-Path $tarPath) -and (Get-Item $tarPath).Length -gt 0) {
    tar xf $tarPath -C $imgDir 2>$null
    Remove-Item $tarPath -Force -ErrorAction SilentlyContinue
    Write-Host "  -> receipts/ saved"
} else {
    Remove-Item $tarPath -Force -ErrorAction SilentlyContinue
    Write-Host "  -> no images or tar unavailable (DB backup only)"
}

Write-Host "Done. Copy backups/dev/ to another drive before large refactors."
Write-Host "Restore: prefer Drive merge in app; manual DB restore needs run-as on device."
