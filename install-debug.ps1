$ErrorActionPreference = 'Stop'

$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($adb) {
    $adbPath = $adb.Source
} else {
    $adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
}
if (-not (Test-Path -LiteralPath $adbPath)) {
    throw 'ADB не найден. Установите Android SDK Platform-Tools и добавьте adb в PATH.'
}

$deviceOutput = & $adbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Не удалось получить список устройств ADB.' }
$devices = @($deviceOutput | ForEach-Object {
    if ($_ -match '^([^\s:]+)\s+device\s*$' -and $Matches[1] -notmatch '^emulator-') {
        $Matches[1]
    }
})
if ($devices.Count -ne 1) {
    $deviceOutput | Write-Host
    throw 'Подключите и авторизуйте ровно один физический телефон через USB.'
}

$previousSerial = $env:ANDROID_SERIAL
try {
    $env:ANDROID_SERIAL = $devices[0]
    Write-Host "Установка debug на $($devices[0])"
    Push-Location $PSScriptRoot
    try {
        & (Join-Path $PSScriptRoot 'gradlew.bat') ':app:installDebug'
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle installDebug завершился с кодом $LASTEXITCODE." }
} finally {
    $env:ANDROID_SERIAL = $previousSerial
}
