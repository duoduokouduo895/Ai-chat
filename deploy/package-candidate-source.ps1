param(
    [Parameter(Mandatory = $false)]
    [string]$ReleaseName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-candidate'
)

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'package-release-source.ps1'
& $script -ReleaseName $ReleaseName
