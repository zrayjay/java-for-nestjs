param([string]$GradleCommand = 'gradle')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$project = Join-Path $repoRoot 'examples/gradle-reference'
$output = Join-Path $project 'build/greeting.txt'
$versionOutput = & $GradleCommand --version 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $versionOutput -notmatch 'Gradle 9\.2\.1\b') { throw 'Gradle 9.2.1 required' }
$common = @('-p', $project, '--build-cache', '--offline', '--console=plain', '--no-daemon')
function Invoke-LabGradle([string[]]$GoalArguments) {
    $result = & $GradleCommand @common @GoalArguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw $result }
    return $result
}
$first = Invoke-LabGradle -GoalArguments @('generateGreeting', '-Plearner=NestJS', '--rerun-tasks')
if ($first -notmatch '(?m)^> Task :generateGreeting\s*$' -or $first -match ':generateGreeting (UP-TO-DATE|FROM-CACHE)') {
    throw "Expected actual first task execution: $first"
}
if ((Get-Content -Raw -LiteralPath $output).Trim() -ne 'Hello, NestJS!') { throw 'First output mismatch' }
$same = Invoke-LabGradle -GoalArguments @('generateGreeting', '-Plearner=NestJS')
if ($same -notmatch ':generateGreeting UP-TO-DATE') { throw "Expected UP-TO-DATE: $same" }
# Remove only this known generated file, never a cache directory or arbitrary computed tree.
Remove-Item -LiteralPath $output
$cached = Invoke-LabGradle -GoalArguments @('generateGreeting', '-Plearner=NestJS')
if ($cached -notmatch ':generateGreeting FROM-CACHE') { throw "Expected FROM-CACHE: $cached" }
if ((Get-Content -Raw -LiteralPath $output).Trim() -ne 'Hello, NestJS!') { throw 'Cached output mismatch' }
$changed = Invoke-LabGradle -GoalArguments @('verifyGreeting', '-Plearner=Java')
if ((Get-Content -Raw -LiteralPath $output).Trim() -ne 'Hello, Java!') { throw 'Changed input did not change output' }
$graph = Invoke-LabGradle -GoalArguments @('verifyGreeting', '-Plearner=Java', '--dry-run')
$producer = $graph.IndexOf(':generateGreeting SKIPPED')
$consumer = $graph.IndexOf(':verifyGreeting SKIPPED')
if ($producer -lt 0 -or $consumer -le $producer) { throw "Expected dependency order: $graph" }
Write-Output 'GRADLE: fixed input/output; UP-TO-DATE; FROM-CACHE; input change; producer-before-consumer graph verified'
