param(
    [switch]$Execute
)

. (Join-Path $PSScriptRoot "common.ps1")

$manifestPath = Get-LatestManifestPath
$users = @(Get-Content $manifestPath -Raw | ConvertFrom-Json)
$baseUrl = Get-PerfBaseUrl

Write-Host "本轮账号清单: $manifestPath"
Write-Host "账号数量: $($users.Count)"
$users | Select-Object username, email | Format-Table -AutoSize

if (-not $Execute) {
    Write-Host ""
    Write-Host "当前仅展示清单，尚未删除。确认无误后运行:"
    Write-Host "pwsh -NoProfile -File .\perf\scripts\cleanup.ps1 -Execute"
    exit 0
}

$headers = Get-PerfAdminHeaders
$deleted = @()
$failed = @()

function Find-ExactPerfUser {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username
    )

    $encoded = [Uri]::EscapeDataString($Username)
    $page = Invoke-PerfRequest -Uri "$baseUrl/api/admin/users?page=0&size=100&keyword=$encoded&guestOnly=false" -Headers $headers
    return @($page.items | Where-Object { $_.username -eq $Username })
}

try {
    Invoke-PerfRequest -Uri "$baseUrl/api/admin/users?page=0&size=1&guestOnly=false" -Headers $headers | Out-Null
} catch {
    throw "管理员 JWT 校验失败，停止清理。请重新登录管理端并更新 PERF_ADMIN_TOKEN。原始错误: $($_.Exception.Message)"
}

foreach ($user in $users) {
    try {
        $exactMatches = @(Find-ExactPerfUser -Username $user.username)
        if ($exactMatches.Count -eq 0) {
            Write-Host "已不存在，跳过: $($user.username)"
            continue
        }
        if ($exactMatches.Count -gt 1) {
            throw "精确匹配数量为 $($exactMatches.Count)，拒绝删除"
        }
        $userId = $exactMatches[0].userId
        Invoke-PerfRequest -Uri "$baseUrl/api/admin/users/$userId" -Method DELETE -Headers $headers -TimeoutSec 180 | Out-Null
        $deleted += $user.username
        Write-Host "已删除: $($user.username) (userId=$userId)"
    } catch {
        $failed += [pscustomobject]@{ username = $user.username; error = $_.Exception.Message }
        Write-Warning "删除失败: $($user.username) - $($_.Exception.Message)"
    }
}

$residual = @()
foreach ($user in $users) {
    try {
        $exactMatches = @(Find-ExactPerfUser -Username $user.username)
        if ($exactMatches.Count -gt 0) {
            $residual += $user.username
        }
    } catch {
        $residual += $user.username
        $failed += [pscustomobject]@{ username = $user.username; error = "删除后复查失败: $($_.Exception.Message)" }
    }
}

Write-Host ""
Write-Host "删除成功: $($deleted.Count)"
Write-Host "删除失败: $($failed.Count)"
Write-Host "残留账号: $($residual.Count)"
if ($failed.Count -gt 0 -or $residual.Count -gt 0) {
    $failed | Format-Table -AutoSize
    if ($residual.Count -gt 0) {
        Write-Warning "仍有残留账号:"
        $residual | ForEach-Object { Write-Warning $_ }
    }
    exit 1
}
