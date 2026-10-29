$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
function Get-Archive($url, $name) {
    $destination = Join-Path $toolRoot $name
    if (-not (Test-Path -LiteralPath $destination)) {
        Write-Host "Baixando $name"
        Invoke-WebRequest -Uri $url -OutFile "$destination.partial"
        Move-Item -LiteralPath "$destination.partial" -Destination $destination
    }
    return $destination
}
if (-not (Test-Path -LiteralPath (Join-Path $toolRoot 'jdk'))) {
    $archive = Get-Archive 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk17.zip'
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $toolRoot 'java-unpacked') -Force
    $jdkSource = (Get-ChildItem (Join-Path $toolRoot 'java-unpacked') -Directory | Select-Object -First 1).FullName
    $jdkTarget = Join-Path $toolRoot 'jdk'
    if (-not $jdkSource.StartsWith($toolRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Origem Java inesperada' }
    Move-Item -LiteralPath $jdkSource -Destination $jdkTarget
}
$env:JAVA_HOME = Join-Path $toolRoot 'jdk'
$env:ANDROID_HOME = Join-Path $toolRoot 'android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-cache'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
if (-not (Test-Path -LiteralPath (Join-Path $toolRoot 'gradle-8.11.1'))) {
    $archive = Get-Archive 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle-8.11.1-bin.zip'
    Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot -Force
}
$sdkManager = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/repository2-1.xml' -OutFile (Join-Path $toolRoot 'repository.xml')
    [xml]$repository = Get-Content -LiteralPath (Join-Path $toolRoot 'repository.xml')
    $sdkUrl = $repository.'sdk-repository'.remotePackage | Where-Object { $_.path -eq 'cmdline-tools;latest' } | ForEach-Object { $_.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | ForEach-Object { $_.complete.url } }
    $archive = Get-Archive "https://dl.google.com/android/repository/$sdkUrl" 'android-commandline.zip'
    $extractRoot = Join-Path $toolRoot 'sdk-unpacked'
    Expand-Archive -LiteralPath $archive -DestinationPath $extractRoot -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $env:ANDROID_HOME 'cmdline-tools') | Out-Null
    $sdkSource = Join-Path $extractRoot 'cmdline-tools'
    $sdkTarget = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest'
    if (-not $sdkSource.StartsWith($toolRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Origem SDK inesperada' }
    Move-Item -LiteralPath $sdkSource -Destination $sdkTarget
}
foreach ($package in @(
    @{ Url='https://dl.google.com/android/repository/platform-35_r02.zip'; Name='platform35.zip'; Target='platforms/android-35' },
    @{ Url='https://dl.google.com/android/repository/build-tools_r35_windows.zip'; Name='buildtools35.zip'; Target='build-tools/35.0.0' }
)) {
    $target = Join-Path $env:ANDROID_HOME $package.Target
    if (-not (Test-Path -LiteralPath $target)) {
        $archive = Get-Archive $package.Url $package.Name
        $unpacked = Join-Path $toolRoot ($package.Name + '-unpacked')
        Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -Force
        $source = (Get-ChildItem -LiteralPath $unpacked -Directory | Select-Object -First 1).FullName
        if (-not $source.StartsWith($toolRoot, [StringComparison]::OrdinalIgnoreCase) -or -not $target.StartsWith($toolRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Destino SDK inesperado' }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        Move-Item -LiteralPath $source -Destination $target
    }
}
"sdk.dir=$($env:ANDROID_HOME.Replace('\','/'))" | Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties')
Write-Output 'Ferramentas Android preparadas.'
