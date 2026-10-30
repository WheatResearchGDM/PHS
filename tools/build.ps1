param([switch]$Prepare)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Prepare) { & (Join-Path $PSScriptRoot 'prepare-android.ps1') }
$env:JAVA_HOME = Join-Path $projectRoot '.tools/jdk'
$env:ANDROID_HOME = Join-Path $projectRoot '.tools/android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-cache'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$gradle = Join-Path $projectRoot '.tools/gradle-8.11.1/bin/gradle.bat'
$testKey = Join-Path $projectRoot '.tools/debug.keystore'
if (-not (Test-Path -LiteralPath $testKey)) {
    & (Join-Path $env:JAVA_HOME 'bin/keytool.exe') -genkeypair -keystore $testKey -storepass android -keypass android -alias androiddebugkey -dname 'CN=PHS Test Build,O=PHS,C=BR' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar chave local de testes.' }
}
& $gradle -p $projectRoot --no-daemon testDebugUnitTest lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'A compilação ou as verificações falharam.' }
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'dist') | Out-Null
$metadata=Get-Content -LiteralPath (Join-Path $projectRoot 'app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
$version=$metadata.elements[0].versionName
$apk=Join-Path $projectRoot "dist/PHS-Reader-$version.apk"
Copy-Item -LiteralPath (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk') -Destination $apk -Force
& (Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'A assinatura do APK não passou na verificação.' }
Get-FileHash -Algorithm SHA256 -LiteralPath $apk | Format-List
