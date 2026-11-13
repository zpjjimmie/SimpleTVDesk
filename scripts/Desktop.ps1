param([Parameter(Mandatory)][ValidateSet('Set','Restore')][string]$Mode,
      [string]$Serial, [string]$AdbPath)
$ErrorActionPreference = 'Stop'
if (!$AdbPath) {
    $local = Join-Path $PSScriptRoot '../.tools/adb/platform-tools/adb.exe'
    if (Test-Path $local) { $AdbPath = (Resolve-Path $local).Path }
    else { $AdbPath = (Get-Command adb -CommandType Application).Source }
}
function Read-Adb([string[]]$arguments) {
    $lines = & $AdbPath @arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $arguments" }
    return ($lines -join "`n").Trim()
}
$devices = @((Read-Adb @('devices')) -split '\r?\n' | ForEach-Object {
    if ($_ -match '^(\S+)\s+device$') { $Matches[1] }
})
if (!$Serial -and $devices.Count -eq 1) { $Serial = $devices[0] }
if (!$Serial -or $Serial -notin $devices) { throw 'Connect and authorize one TV, or use -Serial <adb-device>.' }
function Shell([string[]]$arguments) { Read-Adb (@('-s',$Serial,'shell') + $arguments) }
$userId = Shell @('am','get-current-user')
if ($userId -notmatch '^\d+$') { throw 'Could not identify the active Android user.' }
$model = Shell @('getprop','ro.product.model')
$deviceId = Shell @('getprop','ro.serialno')
if (!$deviceId -or $deviceId -eq 'unknown') { $deviceId = $Serial }
$identity = "$model/$deviceId/$userId"
$hash = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($identity))
$name = [BitConverter]::ToString($hash).Replace('-','')
$folder = Join-Path $PSScriptRoot 'home-backups'
$backup = Join-Path $folder "$name.json"
$ours = 'cn.simpletvdesk/.HomeActivity'
$intent = @('-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
function Find-Home {
    $reply = Shell (@('cmd','package','resolve-activity','--brief','--user',$userId) + $intent)
    return ($reply -split '\r?\n')[-1].Trim()
}
function Same-Home([string]$left, [string]$right) {
    return ($left -replace '^([^/]+)/\.','$1/$1.') -ceq ($right -replace '^([^/]+)/\.','$1/$1.')
}
$current = Find-Home
Write-Host "Device: $model ($Serial), user $userId. HOME: $current"
if ($Mode -eq 'Set') {
    if (Same-Home $current $ours) { Write-Host 'Already set.'; return }
    $apps = (Shell (@('cmd','package','query-activities','--components','--user',$userId) + $intent)) -split '\r?\n'
    $candidates = @($apps | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[\w.]+/[\w.$]+$' })
    if (!@($candidates | Where-Object { Same-Home $_ $ours }).Count) { throw 'Install SimpleTVDesk.apk first or check firmware HOME support.' }
    $old = @($candidates | Where-Object { !(Same-Home $_ $ours) })
    if (@($old | Where-Object { Same-Home $_ $current }).Count) { $previous = $current }
    elseif ($current -match '^android/.*ResolverActivity$' -and $old.Count -eq 1) { $previous = $old[0] }
    else { throw 'Original HOME is ambiguous. Select your original default launcher on the TV first.' }
    New-Item -ItemType Directory -Force $folder | Out-Null
    $temp = "$backup.$([Guid]::NewGuid()).tmp"
    @{ device=$identity; original=$previous } | ConvertTo-Json | Set-Content -LiteralPath $temp -Encoding UTF8
    Move-Item -LiteralPath $temp -Destination $backup -Force
    $target = $ours
} else {
    if (!(Test-Path -LiteralPath $backup)) { throw 'No original HOME backup found for this device/user.' }
    $record = Get-Content -Raw -LiteralPath $backup | ConvertFrom-Json
    $target = $record.original
    if ($record.device -ne $identity -or $target -notmatch '^[\w.]+/[\w.$]+$') { throw 'Invalid HOME backup.' }
}
$result = Shell @('cmd','package','set-home-activity','--user',$userId,$target)
$actual = Find-Home
if (!(Same-Home $actual $target)) {
    if ($Mode -eq 'Set') {
        $rollback = Shell @('cmd','package','set-home-activity','--user',$userId,$previous)
        $restored = Find-Home
        throw "Firmware did not apply HOME. Expected $target, found $actual ($result). Restore result: $rollback. Current HOME: $restored"
    }
    throw "Restore failed. Expected $target, found $actual. $result"
}
Write-Host "Verified HOME: $actual. Press HOME on the remote to test."
