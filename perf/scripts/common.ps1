Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-PerfBaseUrl {
    if ($env:PERF_BASE_URL) {
        return $env:PERF_BASE_URL.TrimEnd("/")
    }
    return "http://127.0.0.1:8080"
}

function Get-PerfAdminHeaders {
    if (-not $env:PERF_ADMIN_TOKEN) {
        throw "缺少 PERF_ADMIN_TOKEN。请先设置临时管理员 JWT。"
    }
    return @{ Authorization = "Bearer $($env:PERF_ADMIN_TOKEN)" }
}

function Invoke-PerfRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [ValidateSet("GET", "POST", "PUT", "DELETE")][string]$Method = "GET",
        [hashtable]$Headers,
        $Body,
        [int]$TimeoutSec = 30
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)
    try {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::new($Method),
            $Uri
        )
        if ($Headers) {
            foreach ($entry in $Headers.GetEnumerator()) {
                $request.Headers.TryAddWithoutValidation($entry.Key, [string]$entry.Value) | Out-Null
            }
        }
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 10 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json,
                [Text.Encoding]::UTF8,
                "application/json"
            )
        }

        $response = $client.Send($request)
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase): $content"
        }
        if (-not $content) {
            return $null
        }
        return $content | ConvertFrom-Json
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

function Get-LatestManifestPath {
    $localDir = Join-Path $PSScriptRoot "..\local"
    $latestFile = Join-Path $localDir "latest-manifest.txt"
    if (-not (Test-Path $latestFile)) {
        throw "未找到本轮账号清单。请先运行 setup-users.ps1。"
    }
    $manifestPath = (Get-Content $latestFile -Raw).Trim()
    if (-not (Test-Path $manifestPath)) {
        throw "账号清单不存在: $manifestPath"
    }
    return $manifestPath
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    if (-not $Values -or $Values.Count -eq 0) {
        return 0
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Percentile * $sorted.Count) - 1
    return [Math]::Round($sorted[[Math]::Max(0, $index)], 2)
}
