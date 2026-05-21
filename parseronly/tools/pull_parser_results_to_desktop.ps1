$ErrorActionPreference = "Stop"

$package = "com.example.youtubeparser"
$deviceBase = "/sdcard/Android/data/$package/files/parse_results"
$desktopBase = Join-Path $env:USERPROFILE "OneDrive\Desktop\ParserResults"

function Test-RemoteDir([string]$remotePath) {
    $out = adb shell "if [ -d '$remotePath' ]; then echo exists; fi"
    return ($out -match "exists")
}

function Pull-RemoteDir([string]$remotePath, [string]$localPath) {
    if (-not (Test-RemoteDir $remotePath)) {
        return
    }
    New-Item -ItemType Directory -Path $localPath -Force | Out-Null
    adb pull "$remotePath/." "$localPath" | Out-Null
}

function Pull-RemoteFlatJsonFiles([string]$remotePath, [string]$localPath) {
    if (-not (Test-RemoteDir $remotePath)) {
        return
    }
    New-Item -ItemType Directory -Path $localPath -Force | Out-Null
    $files = adb shell "find '$remotePath' -maxdepth 1 -type f -name '*.json' 2>/dev/null"
    foreach ($file in $files) {
        $remoteFile = $file.Trim()
        if ($remoteFile) {
            adb pull "$remoteFile" "$localPath" | Out-Null
        }
    }
}

New-Item -ItemType Directory -Path $desktopBase -Force | Out-Null

$platforms = @("youtube", "instagram", "tiktok", "unknown")
foreach ($platform in $platforms) {
    Pull-RemoteDir "$deviceBase/$platform/rawjson" (Join-Path $desktopBase "$platform\rawjson")
    Pull-RemoteDir "$deviceBase/$platform/cleanedjson" (Join-Path $desktopBase "$platform\cleanedjson")
}

# Backward compatibility: old flat layout files
Pull-RemoteDir "$deviceBase/rawjson" (Join-Path $desktopBase "legacy\rawjson")
Pull-RemoteDir "$deviceBase/cleanedjson" (Join-Path $desktopBase "legacy\cleanedjson")
Pull-RemoteFlatJsonFiles "$deviceBase" (Join-Path $desktopBase "legacy\cleanedjson")

Write-Host "Pulled parser results to: $desktopBase"
