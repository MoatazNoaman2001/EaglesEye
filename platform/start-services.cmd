@echo off
rem EaglesEye — start core + pipeline in their own windows (dev mode).
rem Prereq: the docker stack is up (cd infra\dev && docker compose up -d)
start "EaglesEye core (8080)" cmd /k "cd /d "%~dp0core" && call mvn quarkus:dev -Dquarkus.console.enabled=false"
start "EaglesEye pipeline (8082)" cmd /k "cd /d "%~dp0pipeline" && call mvn quarkus:dev -Dquarkus.console.enabled=false"
echo Two windows opened: core (8080) and pipeline (8082). Close a window to stop that service.
