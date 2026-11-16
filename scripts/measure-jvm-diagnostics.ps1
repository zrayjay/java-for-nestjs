param([string]$OutputDirectory = 'target/diagnostics')
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '../examples/jvm-diagnostics/measure-jvm.ps1') -OutputDirectory $OutputDirectory
