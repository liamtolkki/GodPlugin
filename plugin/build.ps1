[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pluginDirectory = $PSScriptRoot
$serverRoot = [IO.Path]::GetFullPath((Join-Path $pluginDirectory '..\..\..'))
$buildDirectory = Join-Path $pluginDirectory 'build'
$classesDirectory = Join-Path $buildDirectory 'classes'
$resourcesDirectory = Join-Path $pluginDirectory 'src\main\resources'
$sourceDirectory = Join-Path $pluginDirectory 'src\main\java'
$outputJar = Join-Path $buildDirectory 'God.jar'
$jdkBin = 'C:\Program Files\Java\jdk-26.0.1\bin'
$javac = Join-Path $jdkBin 'javac.exe'
$jar = Join-Path $jdkBin 'jar.exe'

$requiredTools = @($javac, $jar)
foreach ($tool in $requiredTools) {
    if (!(Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Required JDK tool not found: $tool"
    }
}

$classpathEntries = @(Get-ChildItem -LiteralPath (Join-Path $serverRoot 'libraries') -Recurse -Filter '*.jar' -File |
    Sort-Object FullName |
    Select-Object -ExpandProperty FullName)
if ($classpathEntries.Count -eq 0) {
    throw 'No cached Paper runtime libraries were found.'
}
$classpath = $classpathEntries -join [IO.Path]::PathSeparator
$sources = @(Get-ChildItem -LiteralPath $sourceDirectory -Recurse -Filter '*.java' -File | Select-Object -ExpandProperty FullName)
if ($sources.Count -eq 0) {
    throw 'No Java source files were found.'
}

New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null

& $javac `
    --release 25 `
    -encoding UTF-8 `
    -classpath $classpath `
    -d $classesDirectory `
    @sources
if ($LASTEXITCODE -ne 0) {
    throw 'Java compilation failed.'
}

Copy-Item -Path (Join-Path $resourcesDirectory '*') -Destination $classesDirectory -Recurse -Force
New-Item -ItemType Directory -Path $buildDirectory -Force | Out-Null
& $jar --create --file $outputJar -C $classesDirectory .
if ($LASTEXITCODE -ne 0) {
    throw 'JAR packaging failed.'
}

Write-Output $outputJar
