@echo off
powershell -NoExit -Command "Start-Process powershell -ArgumentList '-NoExit', '-Command', 'cd ''T:\Java\javacode\Ai_Agent''; ccr start' -Verb RunAs"
