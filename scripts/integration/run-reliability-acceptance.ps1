[CmdletBinding()]
param(
    [ValidateSet('home-server-frp', 'home-server-lan')]
    [string]$SshAlias = 'home-server-frp'
)

$ErrorActionPreference = 'Stop'
$itRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$itRunId = (Get-Date -Format 'yyyyMMddHHmmss') + '_' + ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$itReportDirectory = Join-Path $itRoot "artifacts/reliability/$itRunId"
$itReportPath = Join-Path $itReportDirectory 'acceptance-summary.json'
$itSteps = [System.Collections.Generic.List[object]]::new()
$itServers = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
[void](New-Item -ItemType Directory -Path $itReportDirectory -Force)

function Invoke-ItStep {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][scriptblock]$Action)
    $itStarted = Get-Date
    try {
        $global:LASTEXITCODE = 0
        & $Action
        if ($LASTEXITCODE -ne 0) {
            throw "$Name exited with code $LASTEXITCODE"
        }
        $itSteps.Add([ordered]@{ name = $Name; result = 'PASS'; durationMs = [int]((Get-Date) - $itStarted).TotalMilliseconds })
    }
    catch {
        $itSteps.Add([ordered]@{ name = $Name; result = 'FAILED'; durationMs = [int]((Get-Date) - $itStarted).TotalMilliseconds; errorType = $_.Exception.GetType().Name })
        throw
    }
}

function Start-ItVite {
    param([Parameter(Mandatory)][string]$Directory, [Parameter(Mandatory)][int]$Port)
    $itInfo = [System.Diagnostics.ProcessStartInfo]::new((Get-Command node -CommandType Application).Source)
    $itInfo.WorkingDirectory = $Directory
    $itInfo.UseShellExecute = $false
    $itInfo.CreateNoWindow = $true
    foreach ($itArgument in @((Join-Path $Directory 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', [string]$Port, '--strictPort')) {
        [void]$itInfo.ArgumentList.Add($itArgument)
    }
    $itProcess = [System.Diagnostics.Process]::new()
    $itProcess.StartInfo = $itInfo
    [void]$itProcess.Start()
    $itServers.Add($itProcess)
}

function Wait-ItPort {
    param([Parameter(Mandatory)][int]$Port)
    for ($itAttempt = 0; $itAttempt -lt 50; $itAttempt++) {
        $itClient = [System.Net.Sockets.TcpClient]::new()
        try {
            $itConnect = $itClient.ConnectAsync('127.0.0.1', $Port)
            if ($itConnect.Wait(300) -and $itClient.Connected) { return }
        }
        catch { }
        finally { $itClient.Dispose() }
        Start-Sleep -Milliseconds 200
    }
    throw "Vite port $Port did not become ready"
}

$itResult = 'FAILED'
try {
    Push-Location $itRoot
    try {
        Invoke-ItStep 'backend-unit-and-contract-tests' { & mvn -q test }
        foreach ($itClass in @(
            'ChatTurnMySqlIntegrationTest',
            'IngestionRunLeaseMySqlIntegrationTest',
            'IngestionRunCommandMySqlIntegrationTest',
            'IngestionSchemaMySqlIntegrationTest',
            'IngestionGenerationGuardMySqlIntegrationTest'
        )) {
            Invoke-ItStep "mysql-$itClass" { & "$PSScriptRoot/test-chatturn-mysql.ps1" -SshAlias $SshAlias -TestClass $itClass }
        }
        Invoke-ItStep 'mysql-redis-rabbitmq-elasticsearch-isolation' { & "$PSScriptRoot/test-reliability-stack.ps1" -SshAlias $SshAlias }
        Invoke-ItStep 'rag-evaluation-metrics' { & python -m unittest discover -s rag-eval/tests -p 'test_*.py' -v }
        Invoke-ItStep 'user-frontend-tests-and-build' {
            Push-Location frontend
            try { & npm run test:unit; if ($LASTEXITCODE -eq 0) { & npm run build } }
            finally { Pop-Location }
        }
        Invoke-ItStep 'admin-frontend-build' {
            Push-Location frontend_admin
            try { & npm run build }
            finally { Pop-Location }
        }
        Invoke-ItStep 'frontend-visual-and-keyboard-qa' {
            Start-ItVite -Directory (Join-Path $itRoot 'frontend') -Port 5178
            Start-ItVite -Directory (Join-Path $itRoot 'frontend_admin') -Port 5179
            Wait-ItPort -Port 5178
            Wait-ItPort -Port 5179
            & python scripts/integration/check-retrieval-ui.py
            if ($LASTEXITCODE -eq 0) { & python scripts/integration/check-cleanup-ui.py }
        }
        Invoke-ItStep 'worktree-whitespace-check' { & git diff --check }
        $itResult = 'PASS'
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($itServer in $itServers) {
        if (-not $itServer.HasExited) {
            $itServer.Kill($true)
            $itServer.WaitForExit()
        }
        $itServer.Dispose()
    }
    $itSummary = [ordered]@{
        runId = $itRunId
        result = $itResult
        steps = $itSteps
        productionDeployment = 'NOT_RUN'
        productionDataMigration = 'NOT_RUN'
        realModelQualityEvaluation = 'NOT_RUN'
        finishedAt = (Get-Date).ToUniversalTime().ToString('o')
    }
    $itSummary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $itReportPath -Encoding utf8NoBOM
    Write-Output "RELIABILITY_ACCEPTANCE_SUMMARY=$itReportPath"
}
