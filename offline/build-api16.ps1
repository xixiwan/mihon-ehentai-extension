# Builds the compile-only extensions-lib 1.6 ("tachiyomix") API jar that the 1.6 extension
# compiles against, from the sources checked in under offline/api16.
#
# Those sources are the ones Mihon 0.21.2 itself ships (source-api + the few
# core/common network helpers), plus small stand-ins for the app-side types they
# reference. They are compileOnly: the APK only carries the extension's own classes,
# and the app supplies the real implementations at runtime.
#
#   offline/api16/src/eu/kanade/tachiyomi/source/**   copied verbatim from mihon-0.21.2
#   offline/api16/src/eu/kanade/tachiyomi/util/**     copied verbatim (JsoupExtensions)
#   offline/api16/src/eu/kanade/tachiyomi/network/**  Requests/ProgressListener copied,
#                                                     NetworkHelper/OkHttpExtensions stubbed
#   offline/api16/src/tachiyomi/**                    RxCoroutineBridge copied
#   offline/api16/src/androidx/compose/runtime/**     annotation-only stand-in
#   offline/api16/src/mihon/core/common/extensions/** stand-in for JsonObject.EMPTY

. "$PSScriptRoot\env.ps1"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$sdk = Get-SdkRoot
$bt = Get-BuildTools $sdk
$androidJar = "$sdk\platforms\android-36\android.jar"
if (-not (Test-Path $androidJar)) { throw "missing $androidJar" }

New-Item -ItemType Directory -Force -Path $build | Out-Null

# ------------------------------------------------ androidx.preference java stubs
# extensions-lib ships these as java stubs; the app provides the real classes.
# offline/libs/androidx-preference-stubs.jar is the checked-in build of them; if it is
# missing they are compiled from the extensions-lib sources under _ref.
$stubSrc = "$repo\_ref\extlib-1.4.4\extensions-lib-1.4.4\library\src\main\java"
$stubJar = "$libs\androidx-preference-stubs.jar"
if (-not (Test-Path $stubJar)) {
    $stubJar = "$build\androidx-preference-stubs.jar"
}
if (-not (Test-Path $stubJar) -and (Test-Path $stubSrc)) {
    Write-Host "compiling androidx.preference stubs"
    $javaSrcs = Get-ChildItem "$stubSrc\android", "$stubSrc\androidx" -Recurse -Filter *.java | ForEach-Object FullName
    Remove-Item "$build\pref-stubs" -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path "$build\pref-stubs" | Out-Null
    Invoke-Javac $androidJar "$build\pref-stubs" $javaSrcs
    & jar cf $stubJar -C "$build\pref-stubs" .
    if ($LASTEXITCODE -ne 0) { throw "jar (preference stubs) failed" }
}
if (-not (Test-Path $stubJar)) {
    throw "androidx.preference stubs are missing: need offline\libs\androidx-preference-stubs.jar or $stubSrc"
}
$cp = @($androidJar)
if (Test-Path $stubJar) { $cp += $stubJar }
$cp += $depCp
$classpath = $cp -join ";"

# ------------------------------------------------------------ compile the API
Write-Host "`n=== compiling extensions-lib 1.6 API (offline/api16) ==="
Remove-Item "$build\api16" -Recurse -Force -ErrorAction SilentlyContinue
Invoke-Kotlinc $classpath "$build\api16" (Get-KtSources "$offline\api16\src")

$outJar = "$build\extensions-lib-1.6.jar"
Remove-Item $outJar -Force -ErrorAction SilentlyContinue
& jar cf $outJar -C "$build\api16" .
if ($LASTEXITCODE -ne 0) { throw "jar (api16) failed" }

Write-Host "API jar     : $outJar ($((Get-Item $outJar).Length) bytes)"
Write-Host "core classes:"
& jar tf $outJar | Where-Object { $_ -match '^(eu/kanade/tachiyomi/source|eu/kanade/tachiyomi/network)' } |
    Select-Object -First 20
