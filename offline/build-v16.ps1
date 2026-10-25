# Assembles the 1.6 ("tachiyomix") extension APK with the Android SDK build-tools only —
# no Gradle, no network:
#
#   kotlinc/javac -> the compile-only 1.6 API jar (offline/build/extensions-lib-1.6.jar,
#                    built by build-api16.ps1) is NOT bundled
#   d8 --min-api 21 -> classes.dex
#   aapt2 link    -> resources.arsc + binary AndroidManifest.xml with the version
#   zipalign/apksigner -> an APK signed with the same key as the 1.4 build, so the
#                    signing-key fingerprint published in repo.json / index.v2.json matches
#
# Sources: the site logic shared with the 1.4 build (everything in :ehentai except its
# Observable main class) plus this module's suspend main class.

. "$PSScriptRoot\env.ps1"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$pkg = "eu.kanade.tachiyomi.extension.en.ehentai.v16"
$verCode = 1
$verName = "1.6.0"
$minSdk = 21
$targetSdk = 36
$apkName = "tachiyomi-en.ehentai-v$verName.apk"

$apiJar = "$build\extensions-lib-1.6.jar"
if (-not (Test-Path $apiJar)) {
    throw "missing $apiJar — run offline\build-api16.ps1 first"
}

$sdk = Get-SdkRoot
$bt = Get-BuildTools $sdk
$androidJar = "$sdk\platforms\android-36\android.jar"
$aapt2 = "$bt\aapt2.exe"; $d8 = "$bt\d8.bat"; $zipalign = "$bt\zipalign.exe"; $apksigner = "$bt\apksigner.bat"
foreach ($f in @($androidJar, $aapt2, $d8, $zipalign, $apksigner)) {
    if (-not (Test-Path $f)) { throw "missing tool: $f" }
}
Write-Host "build-tools : $bt"

$w = "$build\v16"
# Stale class files must not survive into the dex: kotlinc does not remove classes whose
# source is gone.
foreach ($stale in @("$w\classes", "$w\classes.jar", "$w\out", "$w\base.apk", "$w\unsigned.apk", "$w\aligned.apk", "$w\rjava")) {
    Remove-Item $stale -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path "$w\classes", "$w\out" | Out-Null

# ------------------------------------------------- 1) compile the extension
Write-Host "`n=== 1/6 compiling (shared site logic + 1.6 main class) ==="
$shared = Get-KtSources "$repo\ehentai\src\main\kotlin" |
    Where-Object { $_ -notmatch '\\extension\\en\\ehentai\\Ehentai\.kt$' }
$own = Get-KtSources "$repo\ehentai-v16\src\main\kotlin"
$sources = @($shared) + @($own)
Write-Host ("sources     : {0} shared + {1} module = {2} files" -f $shared.Count, $own.Count, $sources.Count)

$prefStubs = "$libs\androidx-preference-stubs.jar"
if (-not (Test-Path $prefStubs)) { $prefStubs = "$build\androidx-preference-stubs.jar" }
if (-not (Test-Path $prefStubs)) {
    throw "missing androidx-preference-stubs.jar — run offline\build-api16.ps1 first"
}
$compileCp = @($androidJar, $apiJar, $prefStubs, $depCp) -join ";"
Invoke-Kotlinc $compileCp "$w\classes" $sources
& jar cf "$w\classes.jar" -C "$w\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar (extension) failed" }

# ------------------------------------------------- 2) dex
Write-Host "`n=== 2/6 d8 (--min-api $minSdk) ==="
& $d8 --release --min-api $minSdk --lib $androidJar --output "$w\out" "$w\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
$newDex = "$w\out\classes.dex"
Write-Host ("classes.dex : {0:N0} bytes" -f (Get-Item $newDex).Length)

# ------------------------------------------------- 3) resources + manifest
Write-Host "`n=== 3/6 aapt2 compile/link (versionCode $verCode, versionName $verName) ==="
$manifest = [IO.File]::ReadAllText("$repo\ehentai-v16\src\main\AndroidManifest.xml")
$anchor = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
if (-not $manifest.Contains($anchor)) { throw "manifest anchor not found" }
# AGP injects the namespace; aapt2 needs it in the manifest itself.
$manifest = $manifest.Replace($anchor, '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + $pkg + '">')
[IO.File]::WriteAllText("$w\AndroidManifest.xml", $manifest, (New-Object System.Text.UTF8Encoding($false)))

& $aapt2 compile --dir "$repo\ehentai-v16\src\main\res" -o "$w\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& $aapt2 link -o "$w\base.apk" -I $androidJar --manifest "$w\AndroidManifest.xml" "$w\res.zip" `
    --min-sdk-version $minSdk --target-sdk-version $targetSdk `
    --version-code $verCode --version-name $verName --java "$w\rjava"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ------------------------------------------------- 4) assemble
Write-Host "`n=== 4/6 assembling ==="
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item "$w\base.apk" "$w\unsigned.apk" -Force
$zip = [IO.Compression.ZipFile]::Open("$w\unsigned.apk", "Update")
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $newDex, "classes.dex", [IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

& $zipalign -p -f 4 "$w\unsigned.apk" "$w\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ------------------------------------------------- 5) sign
Write-Host "`n=== 5/6 signing with keystore/debug.keystore (v1 + v2) ==="
$signed = "$w\signed.apk"
Remove-Item $signed -Force -ErrorAction SilentlyContinue
& $apksigner sign --ks "$repo\keystore\debug.keystore" --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled false `
    --out $signed "$w\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

# Publish into apk/ (that is what the indexes serve); if the existing file cannot be
# replaced the built APK stays in the work directory and is reported instead.
$apk = "$repo\apk\$apkName"
New-Item -ItemType Directory -Force -Path "$repo\apk" | Out-Null
try {
    Copy-Item $signed $apk -Force -ErrorAction Stop
} catch {
    $apk = $signed
    Write-Host "note        : apk/$apkName is not replaceable here, keeping $signed" -ForegroundColor Yellow
}
Write-Host "APK         : $apk ($((Get-Item $apk).Length) bytes)"

# ------------------------------------------------- 6) verify
Write-Host "`n=== 6/6 verification ==="
Write-Host "--- badging ---"
& $aapt2 dump badging $apk | Where-Object { $_ -match '^(package|uses-feature|meta-data|application-label|sdkVersion|targetSdkVersion)' }
Write-Host "`n--- signature ---"
& $apksigner verify -v --print-certs $apk | Select-String -Pattern "Verified using v2|certificate SHA-256"
Write-Host "`n--- classes in dex (main class must be present) ---"
& "$bt\dexdump.exe" -f "$apk" 2>$null | Select-String -Pattern "ehentai" | Select-Object -First 5
