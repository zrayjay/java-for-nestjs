param([switch]$Offline, [string]$LocalRepository = '')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = if ($IsWindows) { Join-Path $repoRoot 'mvnw.cmd' } else { Join-Path $repoRoot 'mvnw' }
$common = @('--batch-mode', '--no-transfer-progress', '-pl', 'examples/java-basics/greeting-core')
if ($Offline) { $common += '-o' }
if ($LocalRepository) { $common += "-Dmaven.repo.local=$LocalRepository" }
Push-Location $repoRoot
try {
    & $wrapper @common 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree' '-DoutputFile=target/dependency-tree.json' '-DoutputType=json'
    if ($LASTEXITCODE -ne 0) { throw 'Maven dependency tree failed' }
    & $wrapper @common 'org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom' '-Doutput=target/effective-pom.xml'
    if ($LASTEXITCODE -ne 0) { throw 'Effective POM generation failed' }
    [xml]$model = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'examples/java-basics/greeting-core/target/effective-pom.xml')
    $ns = [System.Xml.XmlNamespaceManager]::new($model.NameTable)
    $ns.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $version = $model.SelectSingleNode('/m:project/m:properties/m:java.version', $ns)
    if ($version.InnerText -ne '21') { throw 'Expected effective java.version 21' }
    $releaseProperty = $model.SelectSingleNode('/m:project/m:properties/m:maven.compiler.release', $ns)
    if ($releaseProperty.InnerText -ne '21') { throw 'Expected effective maven.compiler.release 21' }
    $releases = $model.SelectNodes('//m:plugin[m:artifactId="maven-compiler-plugin"]//m:release', $ns)
    foreach ($release in $releases) {
        if ($release.InnerText -ne '21') { throw 'Compiler release drift' }
    }
    node scripts/verify-build-model.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Build model validation failed' }
    Write-Output 'EFFECTIVE_POM: java.version and maven.compiler.release properties are 21; explicit release overrides checked'
} finally {
    Pop-Location
}
