param(
    [string]$Scenarios = "me,conversation,search,knowledge-list",
    [string]$Threads = "1,5,10",
    [ValidateRange(1, 3600)][int]$RampSeconds = 30,
    [ValidateRange(1, 86400)][int]$DurationSeconds = 180,
    [ValidateRange(0, 60000)][int]$ThinkTimeMs = 100
)

. (Join-Path $PSScriptRoot "common.ps1")

$baseUrl = Get-PerfBaseUrl
$jmeterHome = if ($env:JMETER_HOME) { $env:JMETER_HOME } else { "E:\Jmeter\apache-jmeter-5.6.3" }
$jmeter = Join-Path $jmeterHome "bin\jmeter.bat"
$jmx = Join-Path $PSScriptRoot "..\jmeter\ai-chat.jmx"
$csvPointer = Join-Path $PSScriptRoot "..\local\latest-users-csv.txt"
$resultsDir = Join-Path $PSScriptRoot "..\results"

if (-not (Test-Path $jmeter)) { throw "未找到 JMeter: $jmeter" }
if (-not (Test-Path $csvPointer)) { throw "未找到账号 CSV。请先运行 setup-users.ps1。" }
New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
$usersCsv = (Get-Content $csvPointer -Raw).Trim()
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$baselineP95 = @{}
$scenarioValues = @($Scenarios.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$threadValues = @($Threads.Split(",") | ForEach-Object { [int]$_.Trim() })

foreach ($scenario in $scenarioValues) {
    foreach ($threadCount in $threadValues) {
        $name = "$scenario-$threadCount-$timestamp"
        $jtl = Join-Path $resultsDir "$name.jtl"
        $report = Join-Path $resultsDir "$name-report"
        Write-Host "运行场景=$scenario 并发=$threadCount"

        & $jmeter -n -t $jmx `
            "-JbaseUrl=$baseUrl" `
            "-Jscenario=$scenario" `
            "-Jthreads=$threadCount" `
            "-JrampSeconds=$RampSeconds" `
            "-JdurationSeconds=$DurationSeconds" `
            "-JthinkTimeMs=$ThinkTimeMs" `
            "-JusersCsv=$usersCsv" `
            -l $jtl -e -o $report
        if ($LASTEXITCODE -ne 0) { throw "JMeter 执行失败: $name" }

        $rows = @(Import-Csv $jtl)
        $sampleRows = @($rows | Where-Object { $_.label -ne "登录" })
        if ($sampleRows.Count -eq 0) { throw "没有采集到业务请求: $name" }
        $errors = @($sampleRows | Where-Object { $_.success -ne "true" }).Count
        $errorRate = [Math]::Round(($errors * 100.0) / $sampleRows.Count, 2)
        $p95 = Get-Percentile -Values @($sampleRows | ForEach-Object { [double]$_.elapsed }) -Percentile 0.95
        Write-Host "请求数=$($sampleRows.Count) 错误率=$errorRate% P95=${p95}ms"

        if (-not $baselineP95.ContainsKey($scenario)) {
            $baselineP95[$scenario] = $p95
        } elseif ($baselineP95[$scenario] -gt 0 -and $p95 -gt ($baselineP95[$scenario] * 3)) {
            throw "P95 超过 1 并发基线 3 倍，停止升压: $scenario"
        }
        if ($errorRate -gt 1) {
            throw "错误率超过 1%，停止升压: $scenario"
        }

        $health = Invoke-PerfRequest -Uri "$baseUrl/actuator/health"
        if ($health.status -ne "UP") {
            throw "服务健康状态异常，停止升压: $($health.status)"
        }
    }
}

Write-Host "普通 API 压测完成。报告目录: $resultsDir"
