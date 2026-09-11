[CmdletBinding()]
param(
    [ValidateSet('home-server-frp', 'home-server-lan')]
    [string]$SshAlias = 'home-server-frp',
    [string]$CleanupManifest
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/monitored-maven.ps1"
$itRepositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$itSuffix = (Get-Date -Format 'yyyyMMddHHmmss') + '_' + ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$itDatabase = "ai_chat_it_$itSuffix"
$itUsername = "ait_$itSuffix"
$itRedisPrefix = "aichat:it:$itSuffix`:"
$itRabbitVhost = "/ai_chat_it_$itSuffix"
$itRabbitExchange = "ai_chat_it_$itSuffix"
$itElasticIndex = "ai-chat-it-$($itSuffix.ToLowerInvariant())"
$itElasticRole = "ait_role_$itSuffix"
$itPassword = ([Guid]::NewGuid().ToString('N')) + 'Aa9'
$itManifestDirectory = Join-Path $itRepositoryRoot "artifacts/reliability/$itSuffix"
$itManifestPath = Join-Path $itManifestDirectory 'stack-resources.json'
$itTunnel = $null
$itProvisionAttempted = $false

$itManifest = [ordered]@{
    runId = $itSuffix
    sshAlias = $SshAlias
    resources = [ordered]@{
        mysqlDatabase = $itDatabase
        mysqlUser = $itUsername
        redisUser = $itUsername
        redisPrefix = $itRedisPrefix
        rabbitUser = $itUsername
        rabbitVhost = $itRabbitVhost
        elasticsearchIndex = $itElasticIndex
        elasticsearchUser = $itUsername
        elasticsearchRole = $itElasticRole
    }
    status = 'PLANNED'
    testResult = 'NOT_STARTED'
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
}
if ($CleanupManifest) {
    $itManifestPath = (Resolve-Path -LiteralPath $CleanupManifest).Path
    $itManifest = Get-Content -LiteralPath $itManifestPath -Raw | ConvertFrom-Json -AsHashtable
    $itSuffix = $itManifest.runId
    if ($itSuffix -notmatch '^\d{14}_[a-f0-9]{8}$' -or $itManifest.sshAlias -cne $SshAlias) {
        throw 'Cleanup manifest identity or SSH alias is invalid'
    }
    $itDatabase = $itManifest.resources.mysqlDatabase
    $itUsername = $itManifest.resources.mysqlUser
    $itRedisPrefix = $itManifest.resources.redisPrefix
    $itRabbitVhost = $itManifest.resources.rabbitVhost
    $itElasticIndex = $itManifest.resources.elasticsearchIndex
    $itElasticRole = $itManifest.resources.elasticsearchRole
    $itContainers = @($itManifest.containers)
    if ($itContainers.Count -ne 4 -or ($itContainers | Where-Object { $_ -notmatch '^[A-Za-z0-9_.-]+$' }).Count -gt 0 `
        -or $itManifest.resources.redisUser -cne $itUsername -or $itManifest.resources.rabbitUser -cne $itUsername `
        -or $itManifest.resources.elasticsearchUser -cne $itUsername) {
        throw 'Cleanup manifest lacks verified container identities or has inconsistent users'
    }
    $itProvisionAttempted = $true
}
else {
    [void](New-Item -ItemType Directory -Path $itManifestDirectory -Force)
    $itManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
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
    foreach ($itArgument in @(
        '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10',
        $SshAlias, 'base64 -d | bash'
    )) {
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
        throw "Remote command failed with exit code $($itProcess.ExitCode); output withheld to protect credentials"
    }
    return $itOutput
}

function Get-ItFreePort {
    $itListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $itListener.Start()
    try {
        return ([System.Net.IPEndPoint]$itListener.LocalEndpoint).Port
    }
    finally {
        $itListener.Stop()
    }
}

function Wait-ItTunnelPort {
    param([Parameter(Mandatory)][int]$Port)
    for ($itAttempt = 0; $itAttempt -lt 30; $itAttempt++) {
        if ($itTunnel.HasExited) {
            throw "SSH tunnel exited with code $($itTunnel.ExitCode)"
        }
        $itClient = [System.Net.Sockets.TcpClient]::new()
        try {
            $itConnect = $itClient.ConnectAsync('127.0.0.1', $Port)
            if ($itConnect.Wait(500) -and $itClient.Connected) {
                return
            }
        }
        catch {
            # The bounded loop handles startup races.
        }
        finally {
            $itClient.Dispose()
        }
        Start-Sleep -Milliseconds 200
    }
    throw "SSH tunnel port $Port did not become ready"
}

function Clear-ItEnvironment {
    Get-ChildItem Env:AI_CHAT_STACK_* -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item -LiteralPath $_.PSPath -ErrorAction SilentlyContinue
    }
}

$itPreflight = @'
set -eu
available_kib="$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"
test "$available_kib" -ge 2097152
disk_percent="$(df -P / | awk 'NR == 2 {gsub(/%/, "", $5); print $5}')"
test "$disk_percent" -le 80
inode_percent="$(df -Pi / | awk 'NR == 2 {gsub(/%/, "", $5); print $5}')"
test "$inode_percent" -le 80
test -z "$(docker ps --filter health=unhealthy -q)"
mysql="$(docker ps --filter label=com.docker.compose.project=aichat-home-middleware --filter label=com.docker.compose.service=mysql --format '{{.Names}}')"
redis="$(docker ps --filter label=com.docker.compose.project=aichat-home-middleware --filter label=com.docker.compose.service=redis --format '{{.Names}}')"
rabbit="$(docker ps --filter label=com.docker.compose.project=aichat-home-middleware --filter label=com.docker.compose.service=rabbitmq --format '{{.Names}}')"
elastic="$(docker ps --filter name='^/elasticsearch-v9$' --format '{{.Names}}')"
for name in "$mysql" "$redis" "$rabbit" "$elastic"; do
  test -n "$name"
  test "$(printf '%s\n' "$name" | wc -l)" -eq 1
  test "$(docker inspect -f '{{.State.Health.Status}}' "$name")" = healthy
done
printf '%s|%s|%s|%s\n' "$mysql" "$redis" "$rabbit" "$elastic"
printf '%s|%s|%s|%s\n' \
  "$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$mysql")" \
  "$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$redis")" \
  "$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$rabbit")" \
  "$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$elastic")"
'@

try {
    if (-not $CleanupManifest) {
    $itPreflightLines = (Invoke-ItRemoteBash $itPreflight).Trim() -split "`n"
    if ($itPreflightLines.Count -ne 2) {
        throw 'Middleware preflight returned an unexpected shape'
    }
    $itContainers = $itPreflightLines[0].Trim() -split '\|'
    $itIps = $itPreflightLines[1].Trim() -split '\|'
    if ($itContainers.Count -ne 4 -or $itIps.Count -ne 4 `
            -or ($itContainers | Where-Object { $_ -notmatch '^[A-Za-z0-9_.-]+$' }).Count -gt 0 `
            -or ($itIps | Where-Object { $_ -notmatch '^\d{1,3}(\.\d{1,3}){3}$' }).Count -gt 0) {
        throw 'Middleware discovery returned unsafe container names or addresses'
    }

    $itCreateTemplate = @'
set -eu
printf '%s\n' "CREATE DATABASE __DB__ CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '__USER__'@'%' IDENTIFIED BY '__PASS__'; GRANT ALL PRIVILEGES ON __DB__.* TO '__USER__'@'%';" | docker exec -i __MYSQL__ sh -lc 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" mysql -uroot'
docker exec __REDIS__ sh -lc 'redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" ACL SETUSER __USER__ reset on ">__PASS__" "~__PREFIX__*" +get +set +del +unlink +exists +expire +pexpire +ttl +pttl +mget +mset +incr +decr +ping >/dev/null'
docker exec __RABBIT__ rabbitmqctl add_vhost '__VHOST__' >/dev/null
docker exec __RABBIT__ rabbitmqctl add_user '__USER__' '__PASS__' >/dev/null
docker exec __RABBIT__ rabbitmqctl set_permissions -p '__VHOST__' '__USER__' '.*' '.*' '.*' >/dev/null
docker exec __ELASTIC__ sh -lc 'auth="elastic:$(cat /run/secrets/elastic.password)"; code=$(curl -sS -u "$auth" -o /dev/null -w "%{http_code}" -X PUT -H "Content-Type: application/json" --data-binary "{\"indices\":[{\"names\":[\"__INDEX__\"],\"privileges\":[\"all\"]}]}" http://127.0.0.1:9200/_security/role/__ROLE__); test "$code" = 200; code=$(curl -sS -u "$auth" -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" --data-binary "{\"password\":\"__PASS__\",\"roles\":[\"__ROLE__\"]}" http://127.0.0.1:9200/_security/user/__USER__); test "$code" = 200; code=$(curl -sS -u "$auth" -o /dev/null -w "%{http_code}" -X PUT -H "Content-Type: application/json" --data-binary "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}" http://127.0.0.1:9200/__INDEX__); test "$code" = 200'
'@
    $itCreateScript = $itCreateTemplate.Replace('__DB__', $itDatabase).Replace('__USER__', $itUsername).Replace('__PASS__', $itPassword).Replace('__PREFIX__', $itRedisPrefix).Replace('__VHOST__', $itRabbitVhost).Replace('__INDEX__', $itElasticIndex).Replace('__ROLE__', $itElasticRole).Replace('__MYSQL__', $itContainers[0]).Replace('__REDIS__', $itContainers[1]).Replace('__RABBIT__', $itContainers[2]).Replace('__ELASTIC__', $itContainers[3])
    $itProvisionAttempted = $true
    $itManifest.containers = $itContainers
    $itManifest.status = 'PROVISIONING'
    $itManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
    Invoke-ItRemoteBash $itCreateScript | Out-Null

    $itPorts = @((Get-ItFreePort), (Get-ItFreePort), (Get-ItFreePort), (Get-ItFreePort))
    $itTunnelArguments = @(
        '-o', 'BatchMode=yes', '-o', 'ExitOnForwardFailure=yes', '-o', 'StrictHostKeyChecking=yes',
        '-o', 'ConnectTimeout=10', '-N',
        '-L', "127.0.0.1:$($itPorts[0]):$($itIps[0]):3306",
        '-L', "127.0.0.1:$($itPorts[1]):$($itIps[1]):6379",
        '-L', "127.0.0.1:$($itPorts[2]):$($itIps[2]):5672",
        '-L', "127.0.0.1:$($itPorts[3]):$($itIps[3]):9200",
        $SshAlias
    )
    $itTunnel = Start-Process -FilePath 'ssh' -ArgumentList $itTunnelArguments -WindowStyle Hidden -PassThru
    foreach ($itPort in $itPorts) {
        Wait-ItTunnelPort -Port $itPort
    }

    $env:AI_CHAT_STACK_IT_ENABLED = 'true'
    $env:AI_CHAT_STACK_MYSQL_HOST = '127.0.0.1'
    $env:AI_CHAT_STACK_MYSQL_PORT = [string]$itPorts[0]
    $env:AI_CHAT_STACK_MYSQL_DATABASE = $itDatabase
    $env:AI_CHAT_STACK_MYSQL_USERNAME = $itUsername
    $env:AI_CHAT_STACK_MYSQL_PASSWORD = $itPassword
    $env:AI_CHAT_STACK_REDIS_HOST = '127.0.0.1'
    $env:AI_CHAT_STACK_REDIS_PORT = [string]$itPorts[1]
    $env:AI_CHAT_STACK_REDIS_USERNAME = $itUsername
    $env:AI_CHAT_STACK_REDIS_PASSWORD = $itPassword
    $env:AI_CHAT_STACK_REDIS_PREFIX = $itRedisPrefix
    $env:AI_CHAT_STACK_RABBIT_HOST = '127.0.0.1'
    $env:AI_CHAT_STACK_RABBIT_PORT = [string]$itPorts[2]
    $env:AI_CHAT_STACK_RABBIT_USERNAME = $itUsername
    $env:AI_CHAT_STACK_RABBIT_PASSWORD = $itPassword
    $env:AI_CHAT_STACK_RABBIT_VHOST = $itRabbitVhost
    $env:AI_CHAT_STACK_RABBIT_EXCHANGE = $itRabbitExchange
    $env:AI_CHAT_STACK_ES_HOST = '127.0.0.1'
    $env:AI_CHAT_STACK_ES_PORT = [string]$itPorts[3]
    $env:AI_CHAT_STACK_ES_USERNAME = $itUsername
    $env:AI_CHAT_STACK_ES_PASSWORD = $itPassword
    $env:AI_CHAT_STACK_ES_INDEX = $itElasticIndex

    $itManifest.status = 'TESTING'
    $itManifest.testResult = 'RUNNING'
    $itManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
    Push-Location $itRepositoryRoot
    try {
        Invoke-ItMonitoredMaven -TestClass ReliabilityStackIntegrationTest -HealthCheck {
            Invoke-ItRemoteBash $itPreflight | Out-Null
        }
    }
    finally {
        Pop-Location
    }
    $itManifest.testResult = 'PASS'
    Write-Output 'RELIABILITY_STACK_RESULT=PASS'
    }
}
catch {
    $itManifest.testResult = 'FAILED'
    if (-not $itProvisionAttempted) { $itManifest.status = 'NOT_PROVISIONED' }
    $itManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
    throw
}
finally {
    if ($itTunnel -and -not $itTunnel.HasExited) {
        Stop-Process -Id $itTunnel.Id -Force -ErrorAction SilentlyContinue
        $itTunnel.WaitForExit()
    }
    Clear-ItEnvironment

    if ($itProvisionAttempted) {
        $itManifest.status = 'CLEANUP_REQUIRED'
        $itManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
        if ($itDatabase -cne "ai_chat_it_$itSuffix" -or $itUsername -cne "ait_$itSuffix" `
                -or $itRedisPrefix -cne "aichat:it:$itSuffix`:" `
                -or $itRabbitVhost -cne "/ai_chat_it_$itSuffix" `
                -or $itElasticIndex -cne "ai-chat-it-$($itSuffix.ToLowerInvariant())" `
                -or $itElasticRole -cne "ait_role_$itSuffix" `
                -or $itSuffix -notmatch '^\d{14}_[a-f0-9]{8}$') {
            throw 'Cleanup identity does not match this run manifest'
        }
        $itCleanupTemplate = @'
set -eu
printf '%s\n' "DROP DATABASE IF EXISTS __DB__; DROP USER IF EXISTS '__USER__'@'%';" | docker exec -i __MYSQL__ sh -lc 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" mysql -uroot'
docker exec __REDIS__ sh -lc 'redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" --scan --pattern "__PREFIX__*" | while IFS= read -r key; do case "$key" in __PREFIX__*) redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" UNLINK "$key" >/dev/null ;; *) exit 1 ;; esac; done; redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" ACL DELUSER __USER__ >/dev/null || true'
docker exec __RABBIT__ rabbitmqctl delete_vhost '__VHOST__' >/dev/null 2>&1 || true
docker exec __RABBIT__ rabbitmqctl delete_user '__USER__' >/dev/null 2>&1 || true
docker exec __ELASTIC__ sh -lc 'auth="elastic:$(cat /run/secrets/elastic.password)"; code=$(curl -sS -u "$auth" -o /dev/null -w "%{http_code}" -X DELETE http://127.0.0.1:9200/__INDEX__); test "$code" = 200 -o "$code" = 404; code=$(curl -sS -u "$auth" -o /dev/null -w "%{http_code}" -X DELETE http://127.0.0.1:9200/_security/user/__USER__); test "$code" = 200 -o "$code" = 404; code=$(curl -sS -u "$auth" -o /dev/null -w "%{http_code}" -X DELETE http://127.0.0.1:9200/_security/role/__ROLE__); test "$code" = 200 -o "$code" = 404'
'@
        $itCleanupScript = $itCleanupTemplate.Replace('__DB__', $itDatabase).Replace('__USER__', $itUsername).Replace('__PREFIX__', $itRedisPrefix).Replace('__VHOST__', $itRabbitVhost).Replace('__INDEX__', $itElasticIndex).Replace('__ROLE__', $itElasticRole).Replace('__MYSQL__', $itContainers[0]).Replace('__REDIS__', $itContainers[1]).Replace('__RABBIT__', $itContainers[2]).Replace('__ELASTIC__', $itContainers[3])
        Invoke-ItRemoteBash $itCleanupScript | Out-Null

        $itVerifyTemplate = @'
set -eu
remaining="$(printf '%s\n' "SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '__DB__') + (SELECT COUNT(*) FROM mysql.user WHERE user = '__USER__');" | docker exec -i __MYSQL__ sh -lc 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" mysql -N -B -uroot')"
test "$remaining" = 0
test -z "$(docker exec __REDIS__ sh -lc 'redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" ACL LIST | grep "user __USER__ " || true')"
test -z "$(docker exec __REDIS__ sh -lc 'redis-cli --no-auth-warning -a "$(cat /run/secrets/redis_password)" --scan --pattern "__PREFIX__*"')"
test -z "$(docker exec __RABBIT__ rabbitmqctl list_vhosts -q | grep -Fx '__VHOST__' || true)"
test -z "$(docker exec __RABBIT__ rabbitmqctl list_users -q | awk '{print $1}' | grep -Fx '__USER__' || true)"
code="$(docker exec __ELASTIC__ sh -lc 'curl -sS -u "elastic:$(cat /run/secrets/elastic.password)" -o /dev/null -w "%{http_code}" http://127.0.0.1:9200/__INDEX__')"
test "$code" = 404
user_code="$(docker exec __ELASTIC__ sh -lc 'curl -sS -u "elastic:$(cat /run/secrets/elastic.password)" -o /dev/null -w "%{http_code}" http://127.0.0.1:9200/_security/user/__USER__')"
role_code="$(docker exec __ELASTIC__ sh -lc 'curl -sS -u "elastic:$(cat /run/secrets/elastic.password)" -o /dev/null -w "%{http_code}" http://127.0.0.1:9200/_security/role/__ROLE__')"
test "$user_code" = 404; test "$role_code" = 404
for name in __MYSQL__ __REDIS__ __RABBIT__ __ELASTIC__; do test "$(docker inspect -f '{{.State.Health.Status}}' "$name")" = healthy; done
test -z "$(docker ps --filter health=unhealthy -q)"
available_kib="$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"; test "$available_kib" -ge 2097152
disk_percent="$(df -P / | awk 'NR == 2 {gsub(/%/, "", $5); print $5}')"; test "$disk_percent" -le 80
'@
        $itVerifyScript = $itVerifyTemplate.Replace('__PREFIX__', $itRedisPrefix).Replace('__DB__', $itDatabase).Replace('__USER__', $itUsername).Replace('__VHOST__', $itRabbitVhost).Replace('__INDEX__', $itElasticIndex).Replace('__ROLE__', $itElasticRole).Replace('__MYSQL__', $itContainers[0]).Replace('__REDIS__', $itContainers[1]).Replace('__RABBIT__', $itContainers[2]).Replace('__ELASTIC__', $itContainers[3])
        Invoke-ItRemoteBash $itVerifyScript | Out-Null
        $itManifest.status = 'CLEANED_VERIFIED'
        $itManifest.cleanedAt = (Get-Date).ToUniversalTime().ToString('o')
        $itManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $itManifestPath -Encoding utf8NoBOM
        Write-Output "RELIABILITY_STACK_MANIFEST=$itManifestPath"
    }
}
