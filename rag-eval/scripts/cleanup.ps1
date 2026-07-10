param([switch]$Execute)

. (Join-Path $PSScriptRoot "common.ps1")

$baseUrl = Get-RagEvalBaseUrl
$localDir = Get-RagEvalLocalDir
$manifestPath = Get-RagEvalLatestManifestPath
$manifest = Get-RagEvalLatestManifest

Write-Host "本轮 RAG 评测资源:"
Write-Host "manifest: $manifestPath"
Write-Host "账号: $($manifest.username)"
Write-Host "知识库 ID: $($manifest.knowledgeBaseId)"
Write-Host "文档:"
$manifest.documents | Format-Table id, fileName -AutoSize

if (-not $Execute) {
    Write-Host ""
    Write-Host "当前仅展示清单，尚未删除。确认后运行:"
    Write-Host "pwsh -NoProfile -File .\rag-eval\scripts\cleanup.ps1 -Execute"
    exit 0
}

if (-not $env:RAG_EVAL_ADMIN_TOKEN) {
    throw "缺少 RAG_EVAL_ADMIN_TOKEN。请设置临时管理员 JWT 后重试。"
}
$adminToken = $env:RAG_EVAL_ADMIN_TOKEN
Write-Host "校验管理员 JWT"
Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/admin/users?page=0&size=1&guestOnly=false" -Token $adminToken | Out-Null

function Remove-RagEvalDocumentIfPresent {
    param($Document)

    try {
        Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/documents/$($Document.id)" -Token $adminToken | Out-Null
    } catch {
        if ($_.Exception.Message -match "HTTP 404|HTTP 403|HTTP 400.*文档不存在") {
            return
        }
        throw
    }

    Write-Host "逐个删除文档: $($Document.id) $($Document.fileName)"
    Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/documents/$($Document.id)" -Method DELETE -Token $adminToken -TimeoutSeconds 180 | Out-Null
}

if ($manifest.knowledgeBaseId) {
    Write-Host "删除专用知识库及其文档: $($manifest.knowledgeBaseId)"
    try {
        Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/bases/$($manifest.knowledgeBaseId)" -Method DELETE -Token $adminToken -TimeoutSeconds 180 | Out-Null
    } catch {
        if ($_.Exception.Message -match "HTTP 404|HTTP 403|HTTP 400.*知识库不存在") {
            Write-Warning "知识库已不存在或不可访问，继续复查账号。"
        } else {
            Write-Warning "知识库批量删除失败，降级为逐文档清理后重试: $($_.Exception.Message)"
            foreach ($document in $manifest.documents) {
                Remove-RagEvalDocumentIfPresent -Document $document
            }
            Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/bases/$($manifest.knowledgeBaseId)" -Method DELETE -Token $adminToken -TimeoutSeconds 180 | Out-Null
        }
    }

    try {
        $exists = Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/bases/$($manifest.knowledgeBaseId)/exists" -Token $adminToken
        if ([bool]$exists) {
            throw "知识库删除后仍然存在: $($manifest.knowledgeBaseId)"
        }
    } catch {
        if ($_.Exception.Message -notmatch "HTTP 404|HTTP 403|HTTP 400.*知识库不存在") {
            throw
        }
        Write-Host "知识库复查通过: 已不存在。"
    }
} else {
    Write-Host "本轮尚未创建知识库，跳过知识库删除。"
}

foreach ($document in $manifest.documents) {
    try {
        Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/knowledge/documents/$($document.id)" -Token $adminToken | Out-Null
        throw "文档删除后仍然存在: $($document.id) $($document.fileName)"
    } catch {
        if ($_.Exception.Message -notmatch "HTTP 404|HTTP 403|HTTP 400.*文档不存在") {
            throw
        }
    }
}
Write-Host "文档复查通过: 残留=0。"

$encoded = [Uri]::EscapeDataString($manifest.username)
$page = Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/admin/users?page=0&size=100&keyword=$encoded&guestOnly=false" -Token $adminToken
$matches = @($page.items | Where-Object { $_.username -eq $manifest.username })
if ($matches.Count -gt 1) {
    throw "账号精确匹配数量为 $($matches.Count)，拒绝删除。"
}
if ($matches.Count -eq 0) {
    Write-Host "评测账号已不存在，跳过。"
}

for ($attempt = 1; $attempt -le 3 -and $matches.Count -eq 1; $attempt++) {
    $userId = $matches[0].userId
    Write-Host "删除评测账号: $($manifest.username) (userId=$userId, attempt=$attempt)"
    try {
        Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/admin/users/$userId" -Method DELETE -Token $adminToken -TimeoutSeconds 180 | Out-Null
    } catch {
        Write-Warning "账号删除请求失败，将按残留情况决定是否重试: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 2
    $page = Invoke-RagEvalJsonRequest -Uri "$baseUrl/api/admin/users?page=0&size=100&keyword=$encoded&guestOnly=false" -Token $adminToken
    $matches = @($page.items | Where-Object { $_.username -eq $manifest.username })
}

$residualAccounts = $matches
if ($residualAccounts.Count -gt 0) {
    throw "账号删除后仍有残留: $($manifest.username)"
}

$manifestFullPath = [IO.Path]::GetFullPath($manifestPath)
if (-not $manifestFullPath.StartsWith($localDir, [StringComparison]::OrdinalIgnoreCase)) {
    throw "拒绝删除 local 目录外的 manifest: $manifestFullPath"
}
Remove-Item -LiteralPath $manifestFullPath -Force
$pointer = Join-Path $localDir "latest-manifest.txt"
if (Test-Path -LiteralPath $pointer) {
    Remove-Item -LiteralPath $pointer -Force
}
Write-Host "清理完成: 账号残留=0，知识库及文档删除请求已完成，本地敏感 manifest 已移除。"
