param(
    [string]$ConcurrencyLevels = "1,3,5,10",
    [ValidateRange(1, 100)][int]$RequestsPerLevel = 10,
    [string]$Model = "chat-fast",
    [long[]]$KnowledgeBaseIds,
    [ValidateRange(5, 300)][int]$TimeoutSeconds = 90
)

. (Join-Path $PSScriptRoot "common.ps1")

$baseUrl = Get-PerfBaseUrl
$manifestPath = Get-LatestManifestPath
$users = @(Get-Content $manifestPath -Raw | ConvertFrom-Json)
$resultsDir = Join-Path $PSScriptRoot "..\results"
New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outputPath = Join-Path $resultsDir "sse-$timestamp.csv"
$prompt = "请只用一句话解释 SSE 的用途。"
$concurrencyValues = @($ConcurrencyLevels.Split(",") | ForEach-Object { [int]$_.Trim() })

function Invoke-SseRequest {
    param(
        $User,
        [int]$Sequence,
        [string]$RequestBaseUrl,
        [string]$RequestModel,
        [long[]]$RequestKnowledgeBaseIds,
        [int]$RequestTimeoutSeconds,
        [string]$RequestTimestamp,
        [string]$RequestPrompt
    )
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($RequestTimeoutSeconds)
    try {
        $loginBody = @{ username = $User.username; password = $User.password } | ConvertTo-Json -Compress
        $loginContent = [System.Net.Http.StringContent]::new($loginBody, [Text.Encoding]::UTF8, "application/json")
        $loginResponse = $client.PostAsync("$RequestBaseUrl/api/users/login", $loginContent).GetAwaiter().GetResult()
        $loginResponse.EnsureSuccessStatusCode()
        $token = (($loginResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json).token).Trim()

        $body = @{
            prompt = $RequestPrompt
            model = $RequestModel
            clientMessageId = "perf-sse-$RequestTimestamp-$Sequence"
            saveMessage = $false
            streaming = $true
        }
        if ($RequestKnowledgeBaseIds -and $RequestKnowledgeBaseIds.Count -gt 0) {
            $body.knowledgeBaseIds = $RequestKnowledgeBaseIds
        }

        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$RequestBaseUrl/api/ai/chat")
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
        $request.Headers.Accept.Add([System.Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new("text/event-stream"))
        $request.Content = [System.Net.Http.StringContent]::new(($body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, "application/json")

        $watch = [Diagnostics.Stopwatch]::StartNew()
        $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        $response.EnsureSuccessStatusCode()
        $reader = [IO.StreamReader]::new($response.Content.ReadAsStream())
        $ttftMs = $null
        $completed = $false
        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()
            if ($line -and $line.StartsWith("data:")) {
                $data = $line.Substring(5).TrimStart()
                if ($data -eq "[DONE]") {
                    $completed = $true
                    break
                }
                if ($null -eq $ttftMs -and $data) {
                    $ttftMs = $watch.ElapsedMilliseconds
                }
            }
        }
        $watch.Stop()
        return [pscustomobject]@{
            sequence = $Sequence
            username = $User.username
            success = $completed
            ttftMs = $ttftMs
            totalMs = $watch.ElapsedMilliseconds
            error = if ($completed) { "" } else { "未收到 [DONE]" }
        }
    } catch {
        return [pscustomobject]@{
            sequence = $Sequence
            username = $User.username
            success = $false
            ttftMs = $null
            totalMs = $null
            error = $_.Exception.Message
        }
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

$allResults = @()
foreach ($concurrency in $concurrencyValues) {
    Write-Host "运行 SSE 专项: 并发=$concurrency 请求数=$RequestsPerLevel"
    $jobs = @()
    for ($index = 0; $index -lt $RequestsPerLevel; $index++) {
        $user = $users[$index % $users.Count]
        while (@($jobs | Where-Object { $_.State -eq "Running" }).Count -ge $concurrency) {
            Start-Sleep -Milliseconds 100
        }
        $jobs += Start-ThreadJob -ScriptBlock ${function:Invoke-SseRequest} -ArgumentList $user, ($index + 1), $baseUrl, $Model, $KnowledgeBaseIds, $TimeoutSeconds, $timestamp, $prompt
    }
    $levelResults = @($jobs | Wait-Job | Receive-Job)
    $jobs | Remove-Job
    $levelResults | ForEach-Object { $_ | Add-Member -NotePropertyName concurrency -NotePropertyValue $concurrency; $allResults += $_ }

    $success = @($levelResults | Where-Object success).Count
    $errorRate = [Math]::Round((($RequestsPerLevel - $success) * 100.0) / $RequestsPerLevel, 2)
    Write-Host "完成率=$success/$RequestsPerLevel 错误率=$errorRate%"
    if ($errorRate -gt 1) {
        Write-Warning "错误率超过 1%，停止继续升压。"
        break
    }
}

$allResults | Export-Csv -Path $outputPath -NoTypeInformation -Encoding utf8
Write-Host "SSE 结果已保存: $outputPath"
