param(
    [string]$Version = "0.1.0",
    [string]$AppName = "intune-bulk-actions"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

Write-Host "Building jar..."
& .\mvnw.cmd -q clean package

$appDir = Join-Path $scriptDir "dist\$AppName"
if (Test-Path $appDir) {
    Write-Host "Removing existing app image..."
    Remove-Item -Recurse -Force $appDir
}

$jpackageExe = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $jpackageExe)) {
    $fallback = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\jpackage.exe"
    if (Test-Path $fallback) {
        $jpackageExe = $fallback
    } else {
        throw "jpackage.exe not found. Set JAVA_HOME to a full JDK (not Android Studio JBR)."
    }
}

Write-Host "Creating app image..."
& $jpackageExe `
    --type app-image `
    --win-console `
    --name $AppName `
    --input ".\target" `
    --main-jar "$AppName-$Version.jar" `
    --main-class "com.mkylm.intunebulk.Main" `
    --dest ".\dist"

$exePath = Join-Path $appDir "$AppName.exe"
if (Test-Path $exePath) {
    Write-Host "Done: $exePath"
} else {
    throw "App image build finished, but launcher was not found at: $exePath"
}
