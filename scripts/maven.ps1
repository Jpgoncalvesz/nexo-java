param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$taskMavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
if ($taskMavenCommand) {
    $taskMavenExecutable = $taskMavenCommand.Source
} else {
    $taskIdeRoot = Join-Path $env:ProgramFiles 'JetBrains'
    $taskMavenExecutable = Get-ChildItem -LiteralPath $taskIdeRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $taskMavenExecutable) { throw 'Instale Maven 3.9+ ou abra o pom.xml no IntelliJ IDEA e use a janela Maven.' }
if (-not $MavenArgs) { $MavenArgs = @('verify') }
Push-Location (Split-Path -Parent $PSScriptRoot)
try { & $taskMavenExecutable @MavenArgs; $taskMavenExit = $LASTEXITCODE } finally { Pop-Location }
exit $taskMavenExit
