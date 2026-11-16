param([string]$OutputDirectory = 'target/diagnostics')
$ErrorActionPreference = 'Stop'
$repoDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repoDirectory
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must name a full JDK with javac, jcmd and jfr' }
$suffix = if ($IsWindows) { '.exe' } else { '' }
$javaTool = Join-Path $env:JAVA_HOME "bin/java$suffix"
$javacTool = Join-Path $env:JAVA_HOME "bin/javac$suffix"
$jcmdTool = Join-Path $env:JAVA_HOME "bin/jcmd$suffix"
$jfrTool = Join-Path $env:JAVA_HOME "bin/jfr$suffix"
foreach ($toolPath in @($javaTool, $javacTool, $jcmdTool, $jfrTool)) {
    if (-not (Test-Path -LiteralPath $toolPath)) { throw "Missing JDK tool: $toolPath" }
}
$outputPath = [System.IO.Path]::GetFullPath((Join-Path $repoDirectory $OutputDirectory))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $repoDirectory 'target')) + [System.IO.Path]::DirectorySeparatorChar
if (-not $outputPath.StartsWith($targetRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Artifacts must stay under repository target/' }
if (Test-Path -LiteralPath $outputPath) { throw 'Choose a new output directory; existing evidence is never overwritten' }
New-Item -ItemType Directory -Path $outputPath | Out-Null
$classes = Join-Path $outputPath 'classes'
New-Item -ItemType Directory -Path $classes | Out-Null
& $javacTool --release 21 -d $classes examples/jvm-diagnostics/DiagnosticsWorkload.java
if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed' }
$relativeOutput = [System.IO.Path]::GetRelativePath($repoDirectory, $outputPath).Replace('\', '/')
$stdoutPath = Join-Path $outputPath 'java.stdout.txt'
$stderrPath = Join-Path $outputPath 'java.stderr.txt'
$launch = @{
    FilePath = $javaTool
    ArgumentList = @('-Xms64m', '-Xmx96m', "-Xlog:gc*:file=$relativeOutput/gc.log", "-XX:StartFlightRecording=filename=$relativeOutput/lab.jfr,settings=profile,dumponexit=true", '-cp', $classes, 'DiagnosticsWorkload')
    WorkingDirectory = $repoDirectory
    RedirectStandardOutput = $stdoutPath
    RedirectStandardError = $stderrPath
    PassThru = $true
}
if ($IsWindows) { $launch.WindowStyle = 'Hidden' }
$labProcess = Start-Process @launch
try {
    $startupDeadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        if ($labProcess.HasExited) { throw 'Fixture exited before ready; inspect java.stderr.txt' }
        $readyText = if (Test-Path -LiteralPath $stdoutPath) { Get-Content -LiteralPath $stdoutPath -Raw } else { '' }
        if ($readyText -match 'DIAGNOSTICS_READY=([0-9]+)') { break }
        if ([DateTime]::UtcNow -gt $startupDeadline) { throw 'Fixture startup timed out' }
        Start-Sleep -Milliseconds 50
    } while ($true)
    $labProcessId = $labProcess.Id
    $threadText = & $jcmdTool $labProcessId Thread.print -l 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Thread dump failed' }
    $threadText | Set-Content -LiteralPath (Join-Path $outputPath 'threads.txt')
    if (($threadText -join "`n") -notmatch '(?s)"lab-blocked"[^\n]*\n\s+java.lang.Thread.State: BLOCKED') { throw 'Expected monitor contention was not observed' }
    & $jcmdTool $labProcessId GC.heap_dump (Join-Path $outputPath 'lab.hprof') | Set-Content -LiteralPath (Join-Path $outputPath 'heap-command.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Heap dump failed' }
    if (-not $labProcess.WaitForExit(25000)) { throw 'Fixture did not finish' }
    if ($labProcess.ExitCode -ne 0) { throw 'Fixture failed' }
} finally {
    if (-not $labProcess.HasExited) { $labProcess.Kill(); $labProcess.WaitForExit() }
    $labProcess.Dispose()
}
$jfrPath = Join-Path $outputPath 'lab.jfr'
$summary = & $jfrTool summary $jfrPath 2>&1
if ($LASTEXITCODE -ne 0) { throw 'JFR summary failed' }
$summary | Set-Content -LiteralPath (Join-Path $outputPath 'jfr-summary.txt')
$events = @{}
foreach ($eventName in @('jdk.ExecutionSample', 'jdk.GarbageCollection', 'jdk.JavaMonitorEnter', 'jdk.ObjectAllocationSample')) {
    $pattern = '(?m)^\s*' + [regex]::Escape($eventName) + '\s+(\d+)\s+'
    $matched = [regex]::Match(($summary -join "`n"), $pattern)
    if (-not $matched.Success -or [long]$matched.Groups[1].Value -lt 1) { throw "Missing actual JFR event: $eventName" }
    $events[$eventName] = [long]$matched.Groups[1].Value
}
$samples = & $jfrTool print --events jdk.ExecutionSample --stack-depth 16 $jfrPath 2>&1
if ($LASTEXITCODE -ne 0 -or ($samples -join "`n") -notmatch 'DiagnosticsWorkload.cpu') { throw 'CPU samples do not identify the workload' }
$samples | Set-Content -LiteralPath (Join-Path $outputPath 'cpu-samples.txt')
$heapPath = Join-Path $outputPath 'lab.hprof'
$heapStream = [System.IO.File]::OpenRead($heapPath)
try {
    $header = New-Object byte[] 19
    [void]$heapStream.Read($header, 0, $header.Length)
    if (-not [System.Text.Encoding]::ASCII.GetString($header).StartsWith('JAVA PROFILE 1.0.')) { throw 'Heap artifact is not HPROF' }
} finally { $heapStream.Dispose() }
$gc = Get-Content -LiteralPath (Join-Path $outputPath 'gc.log') -Raw
if ($gc -notmatch 'Pause') { throw 'No actual GC pause observed' }
$report = @{ jdk = (& $javaTool -version 2>&1 | Out-String).Trim(); events = $events; heapBytes = (Get-Item -LiteralPath $heapPath).Length; monitorBlocked = $true; output = $relativeOutput }
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputPath 'report.json')
$report | ConvertTo-Json -Depth 4
