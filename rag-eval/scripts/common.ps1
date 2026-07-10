Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RagEvalBaseUrl {
    if ($env:RAG_EVAL_BASE_URL) {
        return $env:RAG_EVAL_BASE_URL.TrimEnd("/")
    }
    return "http://127.0.0.1:8080"
}

function New-RagEvalHttpClient {
    param([int]$TimeoutSeconds = 60)
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    return $client
}

function Invoke-RagEvalJsonRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [ValidateSet("GET", "POST", "PUT", "DELETE")][string]$Method = "GET",
        [string]$Token,
        $Body,
        [int]$TimeoutSeconds = 60
    )

    $client = New-RagEvalHttpClient -TimeoutSeconds $TimeoutSeconds
    try {
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Uri)
        if ($Token) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        }
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 10 -Compress
            $request.Content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, "application/json")
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
    }
}

function Get-RagEvalLocalDir {
    $localDir = Join-Path $PSScriptRoot "..\local"
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null
    return [IO.Path]::GetFullPath($localDir)
}

function Get-RagEvalLatestManifestPath {
    $pointer = Join-Path (Get-RagEvalLocalDir) "latest-manifest.txt"
    if (-not (Test-Path -LiteralPath $pointer)) {
        throw "未找到本轮 RAG 评测 manifest，请先运行 setup.ps1。"
    }
    $manifestPath = (Get-Content -LiteralPath $pointer -Raw).Trim()
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw "manifest 不存在: $manifestPath"
    }
    return $manifestPath
}

function Get-RagEvalLatestManifest {
    return Get-Content -LiteralPath (Get-RagEvalLatestManifestPath) -Raw | ConvertFrom-Json
}
