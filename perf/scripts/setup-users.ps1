param(
    [ValidateRange(1, 500)][int]$Count = 50,
    [string]$Password = "PerfOnly_2026!"
)

. (Join-Path $PSScriptRoot "common.ps1")

$baseUrl = Get-PerfBaseUrl
$batch = Get-Date -Format "yyyyMMdd_HHmmss"
$localDir = Join-Path $PSScriptRoot "..\local"
New-Item -ItemType Directory -Path $localDir -Force | Out-Null

$manifestPath = Join-Path $localDir "users-$batch.json"
$csvPath = Join-Path $localDir "users-$batch.csv"
$users = @()

Write-Host "开始创建 $Count 个专用压测账号，批次: $batch"
for ($index = 1; $index -le $Count; $index++) {
    $suffix = "{0:D3}" -f $index
    $username = "perf_${batch}_$suffix"
    $email = "$username@example.test"
    $body = @{
        username = $username
        password = $Password
        email = $email
    }

    try {
        $response = Invoke-PerfRequest -Uri "$baseUrl/api/users/register" -Method POST -Body $body
        if (-not $response.success) {
            throw "注册接口未返回 success=true"
        }
        $users += [pscustomobject]@{
            batch = $batch
            username = $username
            password = $Password
            email = $email
        }
        Write-Host "[$index/$Count] 已创建 $username"
    } catch {
        Write-Warning "创建失败: $username - $($_.Exception.Message)"
        break
    }
}

if ($users.Count -eq 0) {
    throw "没有成功创建任何压测账号。"
}

$users | ConvertTo-Json -Depth 4 | Set-Content -Path $manifestPath -Encoding utf8
$csvLines = @("username,password,email")
$csvLines += $users | ForEach-Object { "$($_.username),$($_.password),$($_.email)" }
$csvLines | Set-Content -Path $csvPath -Encoding utf8
Set-Content -Path (Join-Path $localDir "latest-manifest.txt") -Value $manifestPath -Encoding utf8
Set-Content -Path (Join-Path $localDir "latest-users-csv.txt") -Value $csvPath -Encoding utf8

Write-Host ""
Write-Host "账号创建完成: $($users.Count)/$Count"
Write-Host "Manifest: $manifestPath"
Write-Host "JMeter CSV: $csvPath"
Write-Host "压测结束后请运行 cleanup.ps1 查看并删除本轮账号。"
