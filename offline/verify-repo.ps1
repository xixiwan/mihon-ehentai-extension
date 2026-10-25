# Checks the extension repo metadata against the APKs it serves.
#
# This is the check that would have caught the Mihon 0.21 install failure: the app refuses
# to install an extension unless the APK's signing certificate digest appears as the store's
# signingKey (index.v2.json `signingKey`, repo.json `meta.signingKeyFingerprint`).
#
#   pwsh -NoProfile -File offline\verify-repo.ps1
#
# Exits 1 if any check fails.

. "$PSScriptRoot\env.ps1"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$sdk = Get-SdkRoot
$bt = Get-BuildTools $sdk
$aapt2 = "$bt\aapt2.exe"; $apksigner = "$bt\apksigner.bat"

$failures = New-Object System.Collections.Generic.List[string]

function Fail($message) {
    $failures.Add($message)
    Write-Host "  FAIL  $message" -ForegroundColor Red
}

function Ok($message) { Write-Host "  ok    $message" -ForegroundColor Green }

# ------------------------------------------------------------------ metadata
$repoRoot = $repo
$repoMeta = Get-Content "$repoRoot\repo.json" -Raw | ConvertFrom-Json
$v2 = Get-Content "$repoRoot\index.v2.json" -Raw | ConvertFrom-Json
$legacy = Get-Content "$repoRoot\index.min.json" -Raw | ConvertFrom-Json

$fingerprint = $v2.signingKey
Write-Host "`n=== signing key ==="
if ($fingerprint -notmatch '^[0-9a-f]{64}$') {
    Fail "index.v2.json signingKey is not a lowercase SHA-256 hex digest: '$fingerprint'"
} else {
    Ok "index.v2.json signingKey = $fingerprint"
}
if ($repoMeta.meta.signingKeyFingerprint -ne $fingerprint) {
    Fail "repo.json meta.signingKeyFingerprint '$($repoMeta.meta.signingKeyFingerprint)' != index.v2.json signingKey"
} else {
    Ok "repo.json meta.signingKeyFingerprint matches"
}

# ------------------------------------------------------------------ per-APK checks
function Get-ApkSignatures($apk) {
    $out = & $apksigner verify -v --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed on $apk" }
    return @($out | Select-String -Pattern '^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$' |
        ForEach-Object { $_.Matches[0].Groups[1].Value })
}

function Get-ApkPackage($apk) {
    $line = & $aapt2 dump badging $apk | Where-Object { $_ -match "^package: name=" } | Select-Object -First 1
    if (-not $line) { throw "no package line in badging of $apk" }
    return [pscustomobject]@{
        pkg         = [regex]::Match($line, "name='([^']+)'").Groups[1].Value
        versionCode = [long][regex]::Match($line, "versionCode='(\d+)'").Groups[1].Value
        versionName = [regex]::Match($line, "versionName='([^']*)'").Groups[1].Value
    }
}

function Get-ApkMetaValue($apk, $name) {
    $tree = & $aapt2 dump xmltree --file AndroidManifest.xml $apk
    for ($i = 0; $i -lt $tree.Count; $i++) {
        if ($tree[$i] -match [regex]::Escape('"' + $name + '"')) {
            $next = $tree[$i + 1]
            $m = [regex]::Match($next, 'android:value\(0x[0-9a-f]+\)=("?)([^"()]+)\1')
            if ($m.Success) { return $m.Groups[2].Value.Trim() }
        }
    }
    return $null
}

function Check-Extension($label, $pkgName, $apkName, $versionCode, $versionName, $libVersion) {
    Write-Host "`n=== $label ==="
    $apk = Join-Path "$repoRoot\apk" $apkName
    if (-not (Test-Path $apk)) {
        Fail "$label : missing $apk"
        return
    }

    $sigs = Get-ApkSignatures $apk
    if ($sigs.Count -eq 0) {
        Fail "$label : no signature in $apk"
    } elseif ($sigs -notcontains $fingerprint) {
        Fail "$label : signed by $($sigs -join ', '), not by the published signing key"
    } else {
        Ok "$label : signature matches the published key"
    }

    $p = Get-ApkPackage $apk
    if ($p.pkg -ne $pkgName) { Fail "$label : APK package '$($p.pkg)' != metadata '$pkgName'" }
    else { Ok "$label : package $($p.pkg)" }

    if ($p.versionCode -ne $versionCode) { Fail "$label : APK versionCode $($p.versionCode) != metadata $versionCode" }
    if ($p.versionName -ne $versionName) { Fail "$label : APK versionName '$($p.versionName)' != metadata '$versionName'" }

    $apkLib = Get-ApkMetaValue $apk 'tachiyomix.extensionLib'
    $derived = $p.versionName -replace '\.[^.]*$', '$($ext.pkg)'
    if ($apkLib -eq $null) {
        Ok "$label : no tachiyomix.extensionLib (older apps derive lib version from versionName: $derived)"
    } elseif ($apkLib -ne $libVersion) {
        Fail "$label : APK tachiyomix.extensionLib '$apkLib' != metadata extensionLib '$libVersion'"
    } else {
        Ok "$label : tachiyomix.extensionLib = $apkLib"
    }

    $warning = Get-ApkMetaValue $apk 'tachiyomix.contentWarning'
    if ($warning -ne '2') {
        Fail "$label : APK tachiyomix.contentWarning '$warning' != 2 (NSFW), the index would be filtered out unless NSFW is enabled"
    } else {
        Ok "$label : tachiyomix.contentWarning = 2 (NSFW)"
    }
}

# v2 index: every entry must describe a matching APK, and every APK in apk/ must be listed.
$listed = @{}
foreach ($ext in $v2.extensionList.extensions) {
    $apkName = Split-Path -Leaf ([Uri]$ext.resources.apkUrl).AbsolutePath
    $listed[$apkName] = $true
    Check-Extension $ext.name $ext.packageName $apkName $ext.versionCode $ext.versionName $ext.extensionLib
}

# legacy index.min.json entries point at apk/<apk> as well
Write-Host "`n=== index.min.json (legacy clients: Tachimanga, older forks) ==="
foreach ($ext in $legacy) {
    $apkName = $ext.apk
    $listed[$apkName] = $true
    $apk = Join-Path "$repoRoot\apk" $apkName
    if (-not (Test-Path $apk)) {
        Fail "index.min.json : missing apk/$apkName"
        continue
    }
    $p = Get-ApkPackage $apk
    if ($p.pkg -ne $ext.pkg) { Fail "index.min.json : $apkName package '$($p.pkg)' != '$($ext.pkg)'" }
    if ($p.versionCode -ne $ext.code) { Fail "index.min.json : $apkName versionCode $($p.versionCode) != $($ext.code)" }
    if ($p.versionName -ne $ext.version) { Fail "index.min.json : $apkName versionName '$($p.versionName)' != '$($ext.version)'" }
    Ok "index.min.json : $($ext.name) -> $apkName ($($p.versionName))"
}

Write-Host "`n=== stray APKs in apk/ ==="
foreach ($f in Get-ChildItem "$repoRoot\apk" -Filter *.apk) {
    if (-not $listed.ContainsKey($f.Name)) {
        Write-Host "  note  $($f.Name) is not referenced by the indexes" -ForegroundColor Yellow
    }
}

Write-Host ""
if ($failures.Count -gt 0) {
    Write-Host "FAILED: $($failures.Count) problem(s)" -ForegroundColor Red
    exit 1
}
Write-Host "All repo metadata checks passed." -ForegroundColor Green
