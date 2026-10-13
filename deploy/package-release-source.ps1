param(
    [Parameter(Mandatory = $false)]
    [string]$ReleaseName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-mvp'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
if (-not $repoRoot) {
    throw 'Run this script inside the AI-chat Git repository.'
}

$status = git -C $repoRoot status --porcelain=v1
if ($status) {
    throw 'The Git worktree must be clean before packaging a release.'
}

if ($ReleaseName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$') {
    throw 'ReleaseName must contain 1-80 safe filename characters.'
}

$distDir = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$archive = Join-Path $distDir ($ReleaseName + '.zip')

$paths = @(
    '.dockerignore',
    '.gitattributes',
    'Dockerfile',
    'Dockerfile.runtime-jar',
    'compose.cloud.yaml',
    'deploy',
    'pom.xml',
    'src',
    'versions.env'
)

git -C $repoRoot archive --format=zip --output=$archive HEAD -- $paths
if ($LASTEXITCODE -ne 0) {
    throw 'git archive failed.'
}

$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $archive
[pscustomobject]@{
    Archive = $archive
    Sha256 = $hash.Hash.ToLowerInvariant()
    Bytes = (Get-Item -LiteralPath $archive).Length
    Commit = (git -C $repoRoot rev-parse HEAD).Trim()
}
