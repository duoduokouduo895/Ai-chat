param(
    [ValidatePattern("^[a-zA-Z0-9_-]+$")][string]$Label = "snapshot"
)

. (Join-Path $PSScriptRoot "common.ps1")

$baseUrl = Get-PerfBaseUrl
$resultsDir = Join-Path $PSScriptRoot "..\results"
New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outputPath = Join-Path $resultsDir "metrics-$Label-$timestamp.json"

$snapshot = [ordered]@{
    capturedAt = (Get-Date).ToString("o")
    label = $Label
    baseUrl = $baseUrl
    health = Invoke-PerfRequest -Uri "$baseUrl/actuator/health"
}

if ($env:PERF_ADMIN_TOKEN) {
    $headers = Get-PerfAdminHeaders
    try {
        $snapshot.actuatorMetrics = Invoke-PerfRequest -Uri "$baseUrl/actuator/metrics" -Headers $headers
    } catch {
        $snapshot.actuatorMetricsError = $_.Exception.Message
    }
    try {
        $snapshot.apiPerformance = Invoke-PerfRequest -Uri "$baseUrl/api/admin/performance/apis?limit=100" -Headers $headers
    } catch {
        $snapshot.apiPerformanceError = $_.Exception.Message
    }
} else {
    $snapshot.note = "未设置 PERF_ADMIN_TOKEN，仅采集公开 health。"
}

$snapshot | ConvertTo-Json -Depth 20 | Set-Content -Path $outputPath -Encoding utf8
Write-Host "指标快照已保存: $outputPath"
Write-Host "Health: $($snapshot.health.status)"
