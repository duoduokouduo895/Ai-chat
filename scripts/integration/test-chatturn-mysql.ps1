[CmdletBinding()]
param(
    [string]$SshAlias = 'home-server-frp',
    [string]$MysqlNetwork = 'middleware-net',
    [string]$TestClass = 'ChatTurnMySqlIntegrationTest',
    [string]$CleanupManifest
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/monitored-maven.ps1"
$itRepositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($TestClass -notmatch '^[A-Za-z0-9_,]+$') {
    throw 'TestClass contains unsupported characters'
}
if ($SshAlias -notin @('home-server-frp', 'home-server-lan') -or $MysqlNetwork -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'Integration target must use an approved SSH alias and a literal Docker network name'
}

function Invoke-ItRemoteBash {
    param([Parameter(Mandatory)][string]$Script)

    $itEncoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Script.Replace("`r`n", "`n")))
    $itStartInfo = [System.Diagnostics.ProcessStartInfo]::new('ssh')
    $itStartInfo.UseShellExecute = $false
    $itStartInfo.RedirectStandardInput = $true
    $itStartInfo.RedirectStandardOutput = $true
    $itStartInfo.RedirectStandardError = $true
    $itStartInfo.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($itArgument in @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10', $SshAlias, 'base64 -d | bash')) {
        [void]$itStartInfo.ArgumentList.Add($itArgument)
    }

    $itProcess = [System.Diagnostics.Process]::new()
    $itProcess.StartInfo = $itStartInfo
    [void]$itProcess.Start()
    $itStdoutTask = $itProcess.StandardOutput.ReadToEndAsync()
    $itStderrTask = $itProcess.StandardError.ReadToEndAsync()
    $itProcess.StandardInput.Write($itEncoded)
    $itProcess.StandardInput.Close()
    $itProcess.WaitForExit()
    $itOutput = $itStdoutTask.GetAwaiter().GetResult()
    $itErrorOutput = $itStderrTask.GetAwaiter().GetResult()
    if ($itProcess.ExitCode -ne 0) {
        throw "Remote command failed with exit code $($itProcess.ExitCode); provisioning output withheld to protect credentials"
    }
    return $itOutput
}

$itContainerDiscovery = @'
set -eu
available_kib="$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"
test "$available_kib" -ge 2097152
disk_percent="$(df -P / | awk 'NR == 2 {gsub(/%/, "", $5); print $5}')"
test "$disk_percent" -le 80
test -z "$(docker ps --filter health=unhealthy -q)"
container="$(docker ps --filter label=com.docker.compose.project=aichat-home-middleware --filter label=com.docker.compose.service=mysql --format '{{.Names}}')"
test -n "$container"
test "$(printf '%s\n' "$container" | wc -l)" -eq 1
test "$(docker inspect -f '{{.State.Health.Status}}' "$container")" = healthy
printf '%s' "$container"
'@
$itTunnel = $null
if ($CleanupManifest) {
    $itManifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $itManifest = Get-Content -LiteralPath $itManifestPath -Raw | ConvertFrom-Json -AsHashtable
    $itSuffix = $itManifest.runId
    $itDatabase = $itManifest.database
    $itUsername = $itManifest.username
    $itMysqlContainer = $itManifest.mysqlContainer
    if ($itSuffix -notmatch '^\d{14}_[a-f0-9]{8}$' -or $itManifest.sshAlias -cne $SshAlias `
            -or $itMysqlContainer -notmatch '^[A-Za-z0-9_.-]+$') {
        throw 'Cleanup manifest identity, container or SSH alias is invalid'
    }
    $itCleanupRequired = $true
}
else {
$itMysqlContainer = (Invoke-ItRemoteBash $itContainerDiscovery | Select-Object -First 1).Trim()
if ($itMysqlContainer -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'MySQL container discovery returned an unsafe name'
}

$itSuffix = (Get-Date -Format 'yyyyMMddHHmmss') + '_' + ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$itDatabase = "ai_chat_it_$itSuffix"
$itUsername = "ait_$itSuffix"
$itPassword = ([Guid]::NewGuid().ToString('N')) + 'Aa9'
$itCleanupRequired = $false
$itTunnel = $null
$itManifestDirectory = Join-Path $itRepositoryRoot "artifacts/reliability/$itSuffix"
[void](New-Item -ItemType Directory -Path $itManifestDirectory -Force)
$itManifestPath = Join-Path $itManifestDirectory 'mysql-resources.json'
$itManifest = [ordered]@{
    runId = $itSuffix; sshAlias = $SshAlias; mysqlContainer = $itMysqlContainer
    database = $itDatabase; username = $itUsername; status = 'PLANNED'; testClass = $TestClass; testResult = 'NOT_STARTED'
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
}
$itManifest | ConvertTo-Json | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
}

try {
    if (-not $CleanupManifest) {
    $itCreateTemplate = @'
set -eu
printf '%s\n' "CREATE DATABASE __DB__ CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '__USER__'@'%' IDENTIFIED BY '__PASS__'; GRANT ALL PRIVILEGES ON __DB__.* TO '__USER__'@'%';" | docker exec -i __CONTAINER__ sh -lc 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" mysql -uroot'
'@
    $itCreateScript = $itCreateTemplate.Replace('__DB__', $itDatabase).Replace('__USER__', $itUsername).Replace('__PASS__', $itPassword).Replace('__CONTAINER__', $itMysqlContainer)
    $itCleanupRequired = $true
    $itManifest.status = 'PROVISIONING'
    $itManifest | ConvertTo-Json | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
    Invoke-ItRemoteBash $itCreateScript | Out-Null

    $itIpTemplate = @'
docker inspect -f '{{with index .NetworkSettings.Networks "__NETWORK__"}}{{.IPAddress}}{{end}}' __CONTAINER__
'@
    $itIpScript = $itIpTemplate.Replace('__NETWORK__', $MysqlNetwork).Replace('__CONTAINER__', $itMysqlContainer)
    $itMysqlIp = (Invoke-ItRemoteBash $itIpScript | Select-Object -First 1).Trim()
    if ($itMysqlIp -notmatch '^\d{1,3}(\.\d{1,3}){3}$') {
        throw "MySQL container has no IPv4 address on $MysqlNetwork"
    }

    $itListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $itListener.Start()
    $itLocalPort = ([System.Net.IPEndPoint]$itListener.LocalEndpoint).Port
    $itListener.Stop()

    $itTunnel = Start-Process -FilePath 'ssh' -ArgumentList @(
        '-o', 'BatchMode=yes',
        '-o', 'ExitOnForwardFailure=yes',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', 'ConnectTimeout=10',
        '-N',
        '-L', "127.0.0.1:${itLocalPort}:${itMysqlIp}:3306",
        $SshAlias
    ) -WindowStyle Hidden -PassThru

    $itReady = $false
    for ($itAttempt = 0; $itAttempt -lt 30; $itAttempt++) {
        if ($itTunnel.HasExited) {
            throw "SSH tunnel exited with code $($itTunnel.ExitCode)"
        }
        $itClient = [System.Net.Sockets.TcpClient]::new()
        try {
            $itConnect = $itClient.ConnectAsync('127.0.0.1', $itLocalPort)
            if ($itConnect.Wait(500) -and $itClient.Connected) {
                $itReady = $true
                break
            }
        }
        catch {
            # Retry until the bounded readiness loop expires.
        }
        finally {
            $itClient.Dispose()
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $itReady) {
        throw 'SSH MySQL tunnel did not become ready'
    }

    $env:AI_CHAT_MYSQL_IT_ENABLED = 'true'
    $env:AI_CHAT_MYSQL_IT_HOST = '127.0.0.1'
    $env:AI_CHAT_MYSQL_IT_PORT = [string]$itLocalPort
    $env:AI_CHAT_MYSQL_IT_DATABASE = $itDatabase
    $env:AI_CHAT_MYSQL_IT_USERNAME = $itUsername
    $env:AI_CHAT_MYSQL_IT_PASSWORD = $itPassword

    Write-Output "Running $TestClass in isolated database $itDatabase"
    $itManifest.status = 'TESTING'
    $itManifest.testResult = 'RUNNING'
    $itManifest | ConvertTo-Json | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
    Push-Location $itRepositoryRoot
    try {
        Invoke-ItMonitoredMaven -TestClass $TestClass -HealthCheck {
            Invoke-ItRemoteBash $itContainerDiscovery | Out-Null
        }
    }
    finally {
        Pop-Location
    }
    Write-Output 'MYSQL_INTEGRATION_RESULT=PASS'
    $itManifest.testResult = 'PASS'
    }
}
catch {
    $itManifest.testResult = 'FAILED'
    $itManifest | ConvertTo-Json | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
    throw
}
finally {
    if ($itTunnel -and -not $itTunnel.HasExited) {
        Stop-Process -Id $itTunnel.Id -Force -ErrorAction SilentlyContinue
        $itTunnel.WaitForExit()
    }

    Remove-Item Env:AI_CHAT_MYSQL_IT_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:AI_CHAT_MYSQL_IT_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:AI_CHAT_MYSQL_IT_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:AI_CHAT_MYSQL_IT_DATABASE -ErrorAction SilentlyContinue
    Remove-Item Env:AI_CHAT_MYSQL_IT_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AI_CHAT_MYSQL_IT_PASSWORD -ErrorAction SilentlyContinue

    if ($itCleanupRequired) {
        $itManifest.status = 'CLEANUP_REQUIRED'
        $itManifest | ConvertTo-Json | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
        if ($itDatabase -cne "ai_chat_it_$itSuffix" -or $itUsername -cne "ait_$itSuffix" -or $itSuffix -notmatch '^\d{14}_[a-f0-9]{8}$') {
            throw 'Cleanup identity does not match this run manifest'
        }
        $itDropTemplate = @'
set -eu
printf '%s\n' "DROP DATABASE IF EXISTS __DB__; DROP USER IF EXISTS '__USER__'@'%';" | docker exec -i __CONTAINER__ sh -lc 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" mysql -uroot'
'@
        $itDropScript = $itDropTemplate.Replace('__DB__', $itDatabase).Replace('__USER__', $itUsername).Replace('__CONTAINER__', $itMysqlContainer)
        Invoke-ItRemoteBash $itDropScript | Out-Null
        $itVerifyTemplate = @'
set -eu
remaining="$(printf '%s\n' "SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '__DB__') + (SELECT COUNT(*) FROM mysql.user WHERE user = '__USER__');" | docker exec -i __CONTAINER__ sh -lc 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" mysql -N -B -uroot')"
test "$remaining" = 0
test "$(docker inspect -f '{{.State.Health.Status}}' __CONTAINER__)" = healthy
'@
        Invoke-ItRemoteBash ($itVerifyTemplate.Replace('__DB__', $itDatabase).Replace('__USER__', $itUsername).Replace('__CONTAINER__', $itMysqlContainer)) | Out-Null
        $itManifest.status = 'CLEANED_VERIFIED'
        $itManifest | ConvertTo-Json | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
        Write-Output "MYSQL_RESOURCE_MANIFEST=$itManifestPath"
        Write-Output "Removed isolated database $itDatabase and its temporary account"
    }
}
