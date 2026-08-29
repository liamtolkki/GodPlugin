[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$godDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$economy = Get-Content -Raw -LiteralPath (Join-Path $godDirectory 'economy.json') | ConvertFrom-Json -AsHashtable
$aliases = Get-Content -Raw -LiteralPath (Join-Path $godDirectory 'aliases.json') | ConvertFrom-Json -AsHashtable
$config = Get-Content -Raw -LiteralPath (Join-Path $godDirectory 'config.json') | ConvertFrom-Json -AsHashtable

foreach ($required in @('version', 'buy', 'sell', 'services', 'earnings', 'limits')) {
    if (!$economy.ContainsKey($required)) { throw "economy.json is missing $required" }
}
if ($config.mode -notin @('on', 'off', 'listen')) { throw 'config.json mode must be on, off, or listen' }
if (!$aliases.ContainsKey('aliases')) { throw 'aliases.json is missing aliases' }

foreach ($menuName in @('buy', 'sell')) {
    foreach ($entry in $economy[$menuName].GetEnumerator()) {
        if ($entry.Key -notmatch '^[a-z0-9_.-]+:[a-z0-9_./-]+$') { throw "Invalid material key: $($entry.Key)" }
        if ([int]$entry.Value.itemQuantity -lt 1 -or [int]$entry.Value.favorQuantity -lt 1) {
            throw "Invalid price quantities for $($entry.Key)"
        }
    }
}

foreach ($material in $economy.buy.Keys) {
    if (!$economy.sell.ContainsKey($material)) { continue }
    $buy = $economy.buy[$material]
    $sell = $economy.sell[$material]
    $buyFavorPerItem = [double]$buy.favorQuantity / [double]$buy.itemQuantity
    $sellFavorPerItem = [double]$sell.favorQuantity / [double]$sell.itemQuantity
    if ($sellFavorPerItem -ge $buyFavorPerItem) {
        throw "Arbitrage risk: $material sells for at least its buy cost"
    }
}

foreach ($requiredService in @('savedLocationTeleport', 'crossDimensionSavedLocationTeleport', 'returnToLastDeath')) {
    if (!$economy.services.ContainsKey($requiredService)) { throw "Missing service price: $requiredService" }
}
foreach ($requiredLimit in @('maximumBalance', 'dailyEarnedFavor', 'weeklyEarnedFavor', 'dailyOfferingFavor', 'maximumItemsPerTransaction')) {
    if (!$economy.limits.ContainsKey($requiredLimit)) { throw "Missing economy limit: $requiredLimit" }
}

Write-Output 'GOD configuration tests passed.'

$serverRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$jdkBin = 'C:\Program Files\Java\jdk-26.0.1\bin'
$javac = Join-Path $jdkBin 'javac.exe'
$java = Join-Path $jdkBin 'java.exe'
$testClasses = Join-Path $PSScriptRoot 'build\test-classes'
$classpathEntries = @(Get-ChildItem -LiteralPath (Join-Path $serverRoot 'libraries') -Recurse -Filter '*.jar' -File |
    Sort-Object FullName |
    Select-Object -ExpandProperty FullName)
$classpath = $classpathEntries -join [IO.Path]::PathSeparator
$testSources = @(
    (Join-Path $PSScriptRoot 'src\main\java\dev\liamtolkkinen\god\EconomyManager.java'),
    (Join-Path $PSScriptRoot 'src\test\java\dev\liamtolkkinen\god\EconomyManagerTest.java')
)

New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
& $javac --release 25 -encoding UTF-8 -classpath $classpath -d $testClasses @testSources
if ($LASTEXITCODE -ne 0) { throw 'Economy transaction test compilation failed.' }

& $java -classpath ($testClasses + [IO.Path]::PathSeparator + $classpath) dev.liamtolkkinen.god.EconomyManagerTest
if ($LASTEXITCODE -ne 0) { throw 'Economy transaction tests failed.' }
