param([Parameter(Mandatory=$true)][string]$Destination, [switch]$EngineUpdates)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$classes = Join-Path $root 'app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes'
if (!(Test-Path $classes)) { throw 'Compile the exact release first; release javac classes are missing.' }
$filteredAar = Join-Path $root 'app/build/generated/xray/libv2ray.aar'
if (!(Test-Path $filteredAar)) { throw 'Filtered release AAR is missing; build the exact release first.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($filteredAar)
try {
    foreach ($entry in @('assets/geoip.dat', 'assets/geosite.dat', 'assets/geoip-only-cn-private.dat')) {
        if ($archive.GetEntry($entry)) { throw "Filtered AAR still contains $entry" }
    }
} finally { $archive.Dispose() }
$buildConfig = Join-Path $classes 'com/example/tunnel/BuildConfig.class'
$configText = (& javap -constants $buildConfig | Out-String)
if ($LASTEXITCODE -ne 0 -or $configText -notmatch 'ENGINE_UPDATE_FEED = "([^"]*)";') { throw 'Cannot inspect the compiled ENGINE_UPDATE_FEED.' }
$compiledFeedEnabled = $Matches[1].Length -gt 0
if ($compiledFeedEnabled -ne [bool]$EngineUpdates) { throw 'EngineUpdates switch does not match the compiled release BuildConfig.' }
$appGradle = Get-Content -Raw "$root/app/build.gradle"
foreach ($pair in @(@('applicationId', 'APPLICATION_ID', "'([^']+)'", '"([^"]+)"'), @('versionName', 'VERSION_NAME', "'([^']+)'", '"([^"]+)"'), @('versionCode', 'VERSION_CODE', '(\d+)', '(\d+)'))) {
    $sourceMatch = [regex]::Match($appGradle, "\b$($pair[0])\s+$($pair[2])")
    $classMatch = [regex]::Match($configText, "\b$($pair[1])\s*=\s*$($pair[3]);")
    if (!$sourceMatch.Success -or !$classMatch.Success -or $sourceMatch.Groups[1].Value -cne $classMatch.Groups[1].Value) { throw "Compiled $($pair[1]) does not match app/build.gradle; rebuild the exact release." }
}
$provider = "def engineUpdateFeed = providers.gradleProperty('engineUpdateFeed').getOrElse('').trim()"
$sourceSets = '(?s) if \(!engineUpdateFeed\.isEmpty\(\)\) \{\s*sourceSets \{\s*debug\.manifest\.srcFile ''src/engineUpdates/AndroidManifest\.xml''\s*release\.manifest\.srcFile ''src/engineUpdates/AndroidManifest\.xml''\s*\}\s*\}'
$field = "  buildConfigField 'String', 'ENGINE_UPDATE_FEED', groovy.json.JsonOutput.toJson(engineUpdateFeed)"
if (!$appGradle.Contains($provider) -or !$appGradle.Contains($field) -or !$appGradle.Contains('buildFeatures { buildConfig true }') -or ([regex]::Matches($appGradle, $sourceSets).Count -ne 1)) { throw 'app/build.gradle changed; update and review the kit generator before exporting.' }
$appGradle = $appGradle.Replace($provider, '').Replace($field, '').Replace('buildFeatures { buildConfig true }', 'buildFeatures { buildConfig false }')
$appGradle = [regex]::Replace($appGradle, $sourceSets, '')
$appGradle += "`ndependencies { implementation files('libs/application-classes.jar') }`n"
$dest = [IO.Path]::GetFullPath($Destination)
if (Test-Path $dest) { throw 'Destination already exists. Choose a new path.' }
New-Item -ItemType Directory -Path "$dest/app/src/main", "$dest/app/libs" -Force | Out-Null
Copy-Item "$root/gradle" "$dest/gradle" -Recurse
Copy-Item "$root/gradlew", "$root/gradlew.bat", "$root/gradle.properties", "$root/build.gradle", "$root/LICENSE" $dest
Copy-Item "$root/app/src/main/res", "$root/app/src/main/assets" "$dest/app/src/main" -Recurse
$manifest = if ($EngineUpdates) { "$root/app/src/engineUpdates/AndroidManifest.xml" } else { "$root/app/src/main/AndroidManifest.xml" }
Copy-Item $manifest "$dest/app/src/main/AndroidManifest.xml"
Copy-Item "$root/app/engine-rules.pro" "$dest/app/engine-rules.pro"
Copy-Item "$root/app/libs/zxing-core-3.5.3.jar", "$root/app/libs/androidsvg-1.4.aar", $filteredAar "$dest/app/libs"
& jar cf "$dest/app/libs/application-classes.jar" -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Could not package compiled application classes.' }
@'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = 'VexisRelink'
include ':app'
'@ | Set-Content -Encoding utf8 "$dest/settings.gradle"
$appGradle | Set-Content -Encoding utf8 "$dest/app/build.gradle"
Copy-Item "$PSScriptRoot/README.md" "$dest/README.md"
Get-FileHash "$dest/app/libs/application-classes.jar", "$dest/app/libs/libv2ray.aar" -Algorithm SHA256 | Format-Table Path, Hash
Write-Host 'Candidate kit created. Verify exact build inputs, legal materials, signatures, and runtime before distribution.'
