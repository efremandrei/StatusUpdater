param([string]$JavaHome)
$ErrorActionPreference = 'Stop'
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$taskRoot = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $env:USERPROFILE '.android\statusupdater-password.dpapi'
$loadedSecret = $false
try {
    if (-not $env:STATUSUPDATER_STORE_PASSWORD -and (Test-Path -LiteralPath $secretPath)) {
        $taskSecure = Get-Content -LiteralPath $secretPath | ConvertTo-SecureString
        $taskPlain = [System.Net.NetworkCredential]::new('', $taskSecure).Password
        $env:STATUSUPDATER_STORE_PASSWORD = $taskPlain
        $env:STATUSUPDATER_KEY_PASSWORD = $taskPlain
        $loadedSecret = $true
    }
    if (-not $env:STATUSUPDATER_STORE_PASSWORD -or -not $env:STATUSUPDATER_KEY_PASSWORD) {
        throw 'Set the release signing environment variables described in README.md.'
    }
    Push-Location -LiteralPath $taskRoot
    try {
        & .\gradlew.bat testReleaseUnitTest lintRelease assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release checks or build failed.' }
        $meta = Get-Content 'app\build\outputs\apk\release\output-metadata.json' -Raw | ConvertFrom-Json
        $output = $meta.elements[0]
        New-Item -ItemType Directory -Force 'dist' | Out-Null
        $name = 'StatusUpdater-v{0}-build-{1}.apk' -f $output.versionName,$output.versionCode
        Copy-Item -LiteralPath ('app\build\outputs\apk\release\' + $output.outputFile) -Destination (Join-Path 'dist' $name)
        $hash = (Get-FileHash -LiteralPath (Join-Path 'dist' $name) -Algorithm SHA256).Hash.ToLowerInvariant()
        [System.IO.File]::WriteAllText((Join-Path $taskRoot ('dist\' + $name + '.sha256')), "$hash  $name`n")
        Write-Output "Built and checked: dist/$name"
    } finally { Pop-Location }
} finally {
    if ($loadedSecret) {
        Remove-Item Env:STATUSUPDATER_STORE_PASSWORD,Env:STATUSUPDATER_KEY_PASSWORD -ErrorAction SilentlyContinue
        $taskPlain = $null
    }
}
