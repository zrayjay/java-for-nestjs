param([string]$OutputDirectory = 'target/diagnostics')
$ErrorActionPreference = 'Stop'
$repoDirectory = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
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
function Invoke-BoundedJdkTool {
    param([string]$Executable, [string[]]$Arguments, [string]$LogName, [int]$TimeoutSeconds = 15)
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.WorkingDirectory = $repoDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $toolProcess = [System.Diagnostics.Process]::new()
    $toolProcess.StartInfo = $info
    $toolStarted = $false
    try {
        if (-not $toolProcess.Start()) { throw "Could not start $LogName" }
        $toolStarted = $true
        $readOut = $toolProcess.StandardOutput.ReadToEndAsync()
        $readError = $toolProcess.StandardError.ReadToEndAsync()
        $completed = $toolProcess.WaitForExit($TimeoutSeconds * 1000)
        if (-not $completed) {
            $toolProcess.Kill($true)
            if (-not $toolProcess.WaitForExit(5000)) { throw "Could not terminate timed-out $LogName" }
        }
        $stdout = $readOut.GetAwaiter().GetResult()
        $stderr = $readError.GetAwaiter().GetResult()
        $stdout | Set-Content -LiteralPath (Join-Path $outputPath "$LogName.stdout.txt")
        $stderr | Set-Content -LiteralPath (Join-Path $outputPath "$LogName.stderr.txt")
        if (-not $completed) { throw "JDK tool timed out: $LogName" }
        if ($toolProcess.ExitCode -ne 0) { throw "JDK tool failed: $LogName; inspect its captured logs" }
        return $stdout + $stderr
    } finally {
        if ($toolStarted -and -not $toolProcess.HasExited) { $toolProcess.Kill($true); [void]$toolProcess.WaitForExit(5000) }
        $toolProcess.Dispose()
    }
}
$classes = Join-Path $outputPath 'classes'
New-Item -ItemType Directory -Path $classes | Out-Null
Invoke-BoundedJdkTool $javacTool @('--release', '21', '-d', $classes, 'examples/jvm-diagnostics/DiagnosticsWorkload.java') 'compile' | Out-Null
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
    $threadText = Invoke-BoundedJdkTool $jcmdTool @("$labProcessId", 'Thread.print', '-l') 'thread-tool' 8
    $threadText | Set-Content -LiteralPath (Join-Path $outputPath 'threads.txt')
    if (($threadText -join "`n") -notmatch '(?s)"lab-blocked"[^\n]*\n\s+java.lang.Thread.State: BLOCKED') { throw 'Expected monitor contention was not observed' }
    $histogram = Invoke-BoundedJdkTool $jcmdTool @("$labProcessId", 'GC.class_histogram') 'histogram' 8
    $byteArrays = [regex]::Match($histogram, '(?m)^\s*\d+:\s+(\d+)\s+(\d+)\s+\[B\s')
    if (-not $byteArrays.Success -or [long]$byteArrays.Groups[2].Value -lt 16777216) { throw 'Expected retained byte-array footprint not observed' }
    Invoke-BoundedJdkTool $jcmdTool @("$labProcessId", 'GC.heap_dump', (Join-Path $outputPath 'lab.hprof')) 'heap-command' 8 | Out-Null
    if (-not $labProcess.WaitForExit(25000)) { throw 'Fixture did not finish' }
    if ($labProcess.ExitCode -ne 0) { throw 'Fixture failed' }
} finally {
    if (-not $labProcess.HasExited) { $labProcess.Kill(); $labProcess.WaitForExit() }
    $labProcess.Dispose()
}
$jfrPath = Join-Path $outputPath 'lab.jfr'
$summary = Invoke-BoundedJdkTool $jfrTool @('summary', $jfrPath) 'summary-tool'
$summary | Set-Content -LiteralPath (Join-Path $outputPath 'jfr-summary.txt')
$events = @{}
foreach ($eventName in @('jdk.ExecutionSample', 'jdk.GarbageCollection', 'jdk.JavaMonitorEnter', 'jdk.ObjectAllocationSample')) {
    $pattern = '(?m)^\s*' + [regex]::Escape($eventName) + '\s+(\d+)\s+'
    $matched = [regex]::Match(($summary -join "`n"), $pattern)
    if (-not $matched.Success -or [long]$matched.Groups[1].Value -lt 1) { throw "Missing actual JFR event: $eventName" }
    $events[$eventName] = [long]$matched.Groups[1].Value
}
$samples = Invoke-BoundedJdkTool $jfrTool @('print', '--events', 'jdk.ExecutionSample', '--stack-depth', '16', $jfrPath) 'sample-tool'
if (($samples -join "`n") -notmatch 'DiagnosticsWorkload.cpu') { throw 'CPU samples do not identify the workload' }
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
$report = @{ jdk = (Invoke-BoundedJdkTool $javaTool @('-version') 'version').Trim(); events = $events; heapBytes = (Get-Item -LiteralPath $heapPath).Length; byteArrayInstances = [long]$byteArrays.Groups[1].Value; byteArrayBytes = [long]$byteArrays.Groups[2].Value; monitorBlocked = $true; output = $relativeOutput }
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputPath 'report.json')
$report | ConvertTo-Json -Depth 4
