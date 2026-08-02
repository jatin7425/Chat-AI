@echo off
cd /d "F:\weekend minis\something\backend"
:loop
node dist\index.js >> backend.log 2>&1
echo [%date% %time%] backend exited, restarting >> backend.log
timeout /t 3 /nobreak >nul
goto loop
