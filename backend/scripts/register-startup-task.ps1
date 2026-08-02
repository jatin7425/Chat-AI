# Run this in an elevated PowerShell (Run as Administrator).
# Registers a task that starts the backend automatically at boot, regardless of who's logged in,
# and restarts it automatically if it crashes.
#
# Reachable over Wi-Fi at http://<this machine's LAN IP>:8787 -- no public tunnel is used
# (localtunnel's free subdomains aren't stable across reconnects, so it was dropped).

$backendAction = New-ScheduledTaskAction -Execute "cmd.exe" `
    -Argument '/c "F:\weekend minis\something\backend\scripts\run-backend-loop.bat"' `
    -WorkingDirectory "F:\weekend minis\something\backend"
$backendTrigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask -TaskName "SoulAI-Backend" `
    -Action $backendAction -Trigger $backendTrigger -Principal $principal -Settings $settings `
    -Description "Story Simulator backend -- starts at boot regardless of login, restarts on crash." `
    -Force

Write-Host "Registered. Test now with: Start-ScheduledTask -TaskName 'SoulAI-Backend'"
