param([string]$Serial, [string]$AdbPath)
& "$PSScriptRoot/Desktop.ps1" -Mode Restore -Serial $Serial -AdbPath $AdbPath
