# Lumen Launcher als GitHub-Release veroeffentlichen.
# Baut die APK, laedt sie als Release hoch - danach melden alle Boxen mit Lumen "Update verfuegbar".
# Voraussetzung (einmalig): winget install GitHub.cli   und danach   gh auth login
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot
$repo = 'holgie1705/LumenLauncher'

$ver = (Select-String -Path 'app\build.gradle.kts' -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
Write-Host ""
Write-Host "=== Lumen Launcher $ver veroeffentlichen ===" -ForegroundColor Cyan

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI fehlt. Einmal installieren:  winget install GitHub.cli" -ForegroundColor Yellow
    Write-Host "Danach ein neues Konsolenfenster oeffnen und:  gh auth login" -ForegroundColor Yellow
    exit 1
}
gh auth status *> $null
if ($LASTEXITCODE -ne 0) { Write-Host "Bitte einmal anmelden:  gh auth login" -ForegroundColor Yellow; exit 1 }

gh release view "v$ver" --repo $repo *> $null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Release v$ver gibt es schon. Erst versionName/versionCode in app\build.gradle.kts erhoehen." -ForegroundColor Yellow
    exit 1
}

Write-Host "Baue APK ..."
gradle clean assembleRelease
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }

# Quellcode der neuen Version committen (nur App-Code und Doku - keine privaten Dateien wie Screenshots)
git add app README.md CHANGELOG.md release-notes.md .gitignore release.ps1 release.bat LICENSE licenses build.gradle.kts settings.gradle.kts gradle.properties 2>$null
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    git commit -m "Lumen Launcher $ver"
    Write-Host "Quellcode als 'Lumen Launcher $ver' committet." -ForegroundColor Green
}

$apk = "LumenLauncher-$ver.apk"
Copy-Item 'app\build\outputs\apk\release\app-release.apk' $apk -Force

# Release-Notes: oberster Abschnitt aus release-notes.md (ohne Ueberschrift, bis zur ersten ---)
$notes = New-Object System.Collections.Generic.List[string]
$started = $false
foreach ($l in [System.IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'release-notes.md'), [System.Text.Encoding]::UTF8)) {
    if ($l -match '^---') { break }
    if (-not $started -and $l -match '^## ') { $started = $true; continue }
    $notes.Add($l)
}
$tmp = Join-Path $env:TEMP "lumen-notes-$ver.md"
[System.IO.File]::WriteAllText($tmp, ($notes -join "`n").Trim(), (New-Object System.Text.UTF8Encoding $false))

git push
gh release create "v$ver" $apk --repo $repo --title "Lumen Launcher $ver" --notes-file $tmp --latest
if ($LASTEXITCODE -ne 0) { Write-Host "Hochladen fehlgeschlagen." -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "Fertig: https://github.com/$repo/releases/tag/v$ver" -ForegroundColor Green
Write-Host "Die Boxen melden das Update beim naechsten Pruefen (spaetestens nach 6 Stunden, sofort ueber Einstellungen -> System -> Nach Updates suchen)."
