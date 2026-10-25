# Shared paths and helpers for the offline extension builds.
#
# Everything used here already exists on this machine: the Android SDK, the Kotlin
# compiler from the local Gradle cache, and the extension's compile-only libraries in
# offline/libs. Nothing in this pipeline downloads anything and no Gradle daemon runs.

$ErrorActionPreference = "Stop"

$offline = $PSScriptRoot
$repo = Split-Path $offline -Parent
$libs = "$offline\libs"
$build = "$offline\build"
$gcache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"

function Resolve-CachedJar([string]$pattern) {
    $f = Get-ChildItem $pattern -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $f) { throw "not in the local Gradle cache: $pattern" }
    return $f.FullName
}

# Highest cached version wins, so bumping the cache never needs a script edit.
function Resolve-CachedJarNewest([string]$groupPath) {
    $dirs = Get-ChildItem "$gcache\$groupPath" -Directory -ErrorAction SilentlyContinue |
        Sort-Object { try { [version]($_.Name -replace '[^0-9.].*$', '') } catch { [version]'0.0.0' } }
    if (-not $dirs) { throw "not in the local Gradle cache: $groupPath" }
    $jar = Get-ChildItem $dirs[-1].FullName -Recurse -Filter *.jar -File |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        Select-Object -First 1
    if (-not $jar) { throw "no jar in $($dirs[-1].FullName)" }
    return $jar.FullName
}

$stdlib = Resolve-CachedJar "$gcache\org.jetbrains.kotlin\kotlin-stdlib\2.3.20\*\kotlin-stdlib-2.3.20.jar"
$kcompiler = Resolve-CachedJar "$gcache\org.jetbrains.kotlin\kotlin-compiler-embeddable\2.3.20\*\kotlin-compiler-embeddable-2.3.20.jar"
$junit = Resolve-CachedJar "$gcache\junit\junit\4.13.2\*\junit-4.13.2.jar"
$hamcrest = Resolve-CachedJar "$gcache\org.hamcrest\hamcrest-core\1.3\*\hamcrest-core-1.3.jar"

$coroutines = Resolve-CachedJarNewest "org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm"
$serializationCore = Resolve-CachedJarNewest "org.jetbrains.kotlinx\kotlinx-serialization-core-jvm"
$serializationJson = Resolve-CachedJarNewest "org.jetbrains.kotlinx\kotlinx-serialization-json-jvm"

# The compiler needs a few jars of its own.
$compilerCp = "$kcompiler;$stdlib"
foreach ($extra in @(
        "org.jetbrains.kotlin\kotlin-reflect\*\*\kotlin-reflect-2*.jar",
        "org.jetbrains.kotlin\kotlin-script-runtime\2.3.20\*\*.jar",
        "org.jetbrains.kotlin\kotlin-daemon-embeddable\2.3.20\*\*.jar",
        "org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm\1.9.0\*\*.jar",
        "org.jetbrains\annotations\23.0.0\*\*.jar")) {
    $jar = (Get-ChildItem "$gcache\$extra" -File -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1).FullName
    if ($jar) { $compilerCp = "$compilerCp;$jar" }
}

# The extension's own compile dependencies (checked in under offline/libs).
$depCp = @(
    "$libs\rxjava-1.3.8.jar",
    "$libs\jsoup-1.22.2.jar",
    "$libs\okhttp-jvm-5.4.0.jar",
    "$libs\okio-jvm.jar",
    "$libs\injekt-api.jar",
    "$libs\injekt-core.jar",
    $coroutines,
    $serializationCore,
    $serializationJson,
    $stdlib
) -join ";"

function Get-SdkRoot {
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    $line = Get-Content "$repo\local.properties" | Where-Object { $_ -match "^sdk\.dir=" }
    if (-not $line) { throw "no ANDROID_HOME and no sdk.dir in local.properties" }
    return ($line -replace "^sdk\.dir=", "") -replace "\\\\", "\" -replace "\\:", ":"
}

function Get-BuildTools($sdk) {
    return (Get-ChildItem "$sdk\build-tools" -Directory |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
}

# Note: native commands are never piped here. `2>&1` would turn kotlinc/javac stderr
# (JVM deprecation warnings) into ErrorRecords, which terminate the script under
# $ErrorActionPreference = 'Stop' even though the tool succeeded.
function Invoke-Kotlinc($classpath, $out, $sources) {
    # An empty classpath must be omitted: PowerShell drops the null argument and the
    # following switch (-d) would be swallowed as its value.
    $cliArgs = @("-Xmx3g", "-cp", $compilerCp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib", "-nowarn", "-jvm-target", "17")
    if ($classpath) { $cliArgs += @("-classpath", $classpath) }
    $cliArgs += @("-d", $out)
    & java @cliArgs $sources
    if ($LASTEXITCODE -ne 0) { throw "kotlinc failed (output: $out)" }
}

function Invoke-Javac($classpath, $out, $sources) {
    if ($classpath) {
        & javac -nowarn -encoding UTF-8 -cp $classpath -d $out $sources
    } else {
        & javac -nowarn -encoding UTF-8 -d $out $sources
    }
    if ($LASTEXITCODE -ne 0) { throw "javac failed (output: $out)" }
}

function Get-KtSources($dir) {
    Get-ChildItem $dir -Recurse -Filter *.kt -File | ForEach-Object FullName
}
