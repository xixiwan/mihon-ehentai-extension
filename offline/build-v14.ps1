# Assembles the 1.4 extension APK (the build that Tachimanga and older forks load) with
# the Android SDK build-tools only — no Gradle, no network:
#
#   javac/kotlinc  -> extensions-lib 1.4.4 from _ref (compile-only, NOT bundled in the APK)
#   kotlinc        -> the extension itself
#   d8 --min-api 21 -> classes.dex (minSdk 21 makes D8 desugar invokedynamic, which is
#                     what keeps the APK loadable in Tachimanga)
#   aapt2 link     -> resources.arsc + binary AndroidManifest.xml with the version
#   zipalign/apksigner -> signed with keystore/debug.keystore, the key published in
#                     repo.json / index.v2.json

. "$PSScriptRoot\env.ps1"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$pkg = "eu.kanade.tachiyomi.extension.en.ehentai"
$verCode = 6
$verName = "1.4.6"
$minSdk = 21
$targetSdk = 36
$apkName = "tachiyomi-en.ehentai-v$verName.apk"
$libDir = "$repo\_ref\extlib-1.4.4\extensions-lib-1.4.4\library\src\main\java"

$sdk = Get-SdkRoot
$bt = Get-BuildTools $sdk
$androidJar = "$sdk\platforms\android-$targetSdk\android.jar"
$aapt2 = "$bt\aapt2.exe"; $d8 = "$bt\d8.bat"; $zipalign = "$bt\zipalign.exe"; $apksigner = "$bt\apksigner.bat"
foreach ($f in @($androidJar, $aapt2, $d8, $zipalign, $apksigner)) {
    if (-not (Test-Path $f)) { throw "missing tool: $f" }
}
Write-Host "build-tools : $bt"

$w = "$build\v14"
foreach ($stale in @("$w\classes", "$w\classes.jar", "$w\out", "$w\base.apk", "$w\unsigned.apk", "$w\aligned.apk", "$w\rjava")) {
    Remove-Item $stale -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path "$w\extlib", "$w\classes", "$w\out" | Out-Null

# ------------------------------------------------- 1) compile-only API jar
# offline/libs/extlib-1.4.4.jar is the checked-in build of the library sources under
# _ref/extlib-1.4.4; if it is missing, they are compiled from there instead.
$extlibJar = "$libs\extlib-1.4.4.jar"
if (-not (Test-Path $extlibJar)) {
    $extlibJar = "$w\extlib-1.4.4.jar"
}
if (-not (Test-Path $extlibJar) -and -not (Test-Path $libDir)) {
    throw "extensions-lib 1.4.4 is missing: need offline\libs\extlib-1.4.4.jar or $libDir"
}
if (-not (Test-Path $extlibJar)) {
    Write-Host "`n=== 1/6 compiling extensions-lib 1.4.4 (from _ref) ==="
    $stubJar = "$w\extlib-java-stubs.jar"
    $javaSrc = Get-ChildItem "$libDir\android", "$libDir\androidx" -Recurse -Filter *.java | ForEach-Object FullName
    Invoke-Javac $androidJar "$w\extlib-java" $javaSrc
    & jar cf $stubJar -C "$w\extlib-java" .
    if ($LASTEXITCODE -ne 0) { throw "jar (extlib java stubs) failed" }

    $ktSrc = Get-KtSources $libDir
    Invoke-Kotlinc "$stubJar;$depCp;$androidJar" "$w\extlib" $ktSrc
    & jar cf $extlibJar -C "$w\extlib" . -C "$w\extlib-java" .
    if ($LASTEXITCODE -ne 0) { throw "jar (extlib) failed" }
}
Write-Host "extlib jar  : $extlibJar"

# ------------------------------------------------- 2) the extension itself
Write-Host "`n=== 2/6 compiling the extension ==="
# android.jar comes first: extensions-lib ships its own `android.content.Context` stub and
# the platform's Context has to win (the source uses MODE_PRIVATE, which the stub lacks).
$compileCp = @($androidJar, $extlibJar, $depCp) -join ";"
Invoke-Kotlinc $compileCp "$w\classes" (Get-KtSources "$repo\ehentai\src\main\kotlin")
& jar cf "$w\classes.jar" -C "$w\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar (extension) failed" }

# ------------------------------------------------- 3) dex
Write-Host "`n=== 3/6 d8 (--min-api $minSdk) ==="
& $d8 --release --min-api $minSdk --lib $androidJar --output "$w\out" "$w\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
$newDex = "$w\out\classes.dex"
Write-Host ("classes.dex : {0:N0} bytes" -f (Get-Item $newDex).Length)

# ------------------------------------------------- 4) resources + manifest
Write-Host "`n=== 4/6 aapt2 compile/link (versionCode $verCode, versionName $verName) ==="
$manifest = [IO.File]::ReadAllText("$repo\ehentai\src\main\AndroidManifest.xml")
$anchor = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
if (-not $manifest.Contains($anchor)) { throw "manifest anchor not found" }
$manifest = $manifest.Replace($anchor, '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + $pkg + '">')
[IO.File]::WriteAllText("$w\AndroidManifest.xml", $manifest, (New-Object System.Text.UTF8Encoding($false)))

& $aapt2 compile --dir "$repo\ehentai\src\main\res" -o "$w\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& $aapt2 link -o "$w\base.apk" -I $androidJar --manifest "$w\AndroidManifest.xml" "$w\res.zip" `
    --min-sdk-version $minSdk --target-sdk-version $targetSdk `
    --version-code $verCode --version-name $verName --java "$w\rjava"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ------------------------------------------------- 5) assemble + sign
Write-Host "`n=== 5/6 assembling + signing ==="
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item "$w\base.apk" "$w\unsigned.apk" -Force
$zip = [IO.Compression.ZipFile]::Open("$w\unsigned.apk", "Update")
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $newDex, "classes.dex", [IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

& $zipalign -p -f 4 "$w\unsigned.apk" "$w\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

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
& $aapt2 dump badging $apk | Where-Object { $_ -match '^(package|application-label)' }
& $apksigner verify -v --print-certs $apk | Select-String -Pattern "Verified using v2|certificate SHA-256"
