function Invoke-ItMonitoredMaven {
    param([Parameter(Mandatory)][string]$TestClass, [Parameter(Mandatory)][scriptblock]$HealthCheck)
    if ($TestClass -notmatch '^[A-Za-z0-9_,]+$') { throw 'Invalid test class selector' }
    & $HealthCheck
    $itInfo = [System.Diagnostics.ProcessStartInfo]::new($env:ComSpec)
    $itInfo.UseShellExecute = $false
    $itInfo.CreateNoWindow = $true
    $itInfo.RedirectStandardOutput = $true
    $itInfo.RedirectStandardError = $true
    foreach ($itArgument in @('/d', '/c', "mvn -q -pl ai-chat-app -am -Dtest=$TestClass -Dsurefire.failIfNoSpecifiedTests=false test")) {
        [void]$itInfo.ArgumentList.Add($itArgument)
    }
    $itProcess = [System.Diagnostics.Process]::new()
    $itProcess.StartInfo = $itInfo
    try {
        [void]$itProcess.Start()
        $itOutput = $itProcess.StandardOutput.ReadToEndAsync()
        $itErrorOutput = $itProcess.StandardError.ReadToEndAsync()
        while (-not $itProcess.WaitForExit(10000)) {
            # Stop the local test tree if SSH or the resource guard becomes unavailable.
            & $HealthCheck
        }
        Write-Output $itOutput.GetAwaiter().GetResult()
        Write-Output $itErrorOutput.GetAwaiter().GetResult()
        if ($itProcess.ExitCode -ne 0) { throw "Integration test exited with code $($itProcess.ExitCode)" }
    }
    finally {
        if ($itProcess.Id -and -not $itProcess.HasExited) {
            $itProcess.Kill($true)
            $itProcess.WaitForExit()
        }
        $itProcess.Dispose()
    }
}
