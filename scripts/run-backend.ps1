[CmdletBinding()]
param(
    [switch]$BuildOnly,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ApplicationArgs
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

Push-Location -LiteralPath $repoRoot
try {
    & mvn -B -ntp -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Backend package failed (Maven exit code $LASTEXITCODE)."
    }

    [xml]$appPom = Get-Content -LiteralPath (Join-Path $repoRoot 'pom.xml') -Raw
    $artifactId = [string]$appPom.project.artifactId
    $version = [string]$appPom.project.version
    if ([string]::IsNullOrWhiteSpace($version)) {
        $version = [string]$appPom.project.parent.version
    }

    $jarPath = Join-Path $repoRoot "target/$artifactId-$version.jar"
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Backend JAR not found: $jarPath"
    }

    if ($BuildOnly) {
        Write-Host "Backend JAR ready: $jarPath"
        return
    }

    & java -jar $jarPath @ApplicationArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
