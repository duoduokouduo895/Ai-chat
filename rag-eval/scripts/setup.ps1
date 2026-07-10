param(
    [ValidateRange(60, 3600)][int]$EmbeddingTimeoutSeconds = 900,
    [ValidateRange(1, 30)][int]$PollSeconds = 3
)

. (Join-Path $PSScriptRoot "common.ps1")

$baseUrl = Get-RagEvalBaseUrl
$localDir = Get-RagEvalLocalDir
$corpusDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\dataset\corpus"))
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$username = "rag_eval_$timestamp"
$password = "RagEval_$timestamp!"
$email = "$username@example.test"
$manifestPath = Join-Path $localDir "manifest-$timestamp.json"

Write-Host "创建本轮 RAG 评测账号: $username"
Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/users/register" -Method POST -Body @{
    username = $username
    password = $password
    email = $email
} | Out-Null

$login = Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/users/login" -Method POST -Body @{
    username = $username
    password = $password
}
$token = $login.token
if (-not $token) {
    throw "普通用户登录成功但未返回 Token。"
}

$documents = @()
$manifest = [ordered]@{
    batch = $timestamp
    username = $username
    password = $password
    email = $email
    userToken = $token
    knowledgeBaseId = $null
    documents = $documents
    ingestionCompleted = $false
}
function Save-RagEvalManifest {
    $manifest.documents = $documents
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Set-Content -LiteralPath (Join-Path $localDir "latest-manifest.txt") -Value $manifestPath -Encoding utf8
}
Save-RagEvalManifest

Write-Host "创建私有评测知识库"
$knowledgeBase = Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/bases" -Method POST -Token $token -Body @{
    name = "RAG 评测专用知识库 $timestamp"
    description = "由 rag-eval/setup.ps1 创建，仅用于本轮可控语料评测。"
    isPublic = $false
}
if (-not $knowledgeBase.id) {
    throw "知识库创建成功但未返回 id。"
}
$manifest.knowledgeBaseId = $knowledgeBase.id
Save-RagEvalManifest

foreach ($file in Get-ChildItem -LiteralPath $corpusDir -File -Filter "*.md" | Sort-Object Name) {
    Write-Host "上传语料: $($file.Name)"
    $client = New-RagEvalHttpClient -TimeoutSeconds 120
    $content = $null
    $stream = $null
    try {
        $content = [System.Net.Http.MultipartFormDataContent]::new()
        $stream = [IO.File]::OpenRead($file.FullName)
        $fileContent = [System.Net.Http.StreamContent]::new($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new("text/markdown")
        $content.Add($fileContent, "file", $file.Name)
        $content.Add([System.Net.Http.StringContent]::new([string]$knowledgeBase.id), "knowledgeBaseId")
        $content.Add([System.Net.Http.StringContent]::new("auto"), "parseMode")
        $content.Add([System.Net.Http.StringContent]::new("true"), "enableVectorization")
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$baseUrl/api/knowledge/documents/upload")
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
        $request.Content = $content
        $response = $client.Send($request)
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase): $raw"
        }
        $document = $raw | ConvertFrom-Json
        $documents += [pscustomobject]@{ id = $document.id; fileName = $document.fileName }
        Save-RagEvalManifest
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        if ($null -ne $content) { $content.Dispose() }
        $client.Dispose()
    }
}

Write-Host "等待 8 份文档完成向量化"
$corpusCount = @(Get-ChildItem -LiteralPath $corpusDir -File -Filter "*.md").Count
if ($documents.Count -ne 8 -or $documents.Count -ne $corpusCount) {
    throw "语料上传数量异常: expected=8 actual=$($documents.Count)"
}
$deadline = (Get-Date).AddSeconds($EmbeddingTimeoutSeconds)
do {
    $pending = @()
    foreach ($document in $documents) {
        $status = Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/documents/$($document.id)/embedding-status" -Token $token
        Write-Host "$($document.fileName): parse=$($status.parseStatus) embedding=$($status.embeddingStatus) progress=$($status.embeddingProgress)%"
        if (-not [bool]$status.shouldBeEmbedded) {
            $pending += $document
        }
    }
    if ($pending.Count -eq 0) {
        $manifest.ingestionCompleted = $true
        Save-RagEvalManifest
        Write-Host "所有语料已完成向量化。"
        Write-Host "manifest: $manifestPath"
        exit 0
    }
    if ((Get-Date) -ge $deadline) {
        throw "等待向量化超时。manifest 已保留，请检查文档处理记录后再继续。"
    }
    Start-Sleep -Seconds $PollSeconds
} while ($true)
