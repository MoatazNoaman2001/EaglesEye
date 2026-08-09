@echo off
title EaglesEye Dev Stack
cd /d "%~dp0"
echo Starting EaglesEye development stack...
docker compose up -d
if errorlevel 1 (
  echo.
  echo Failed to start. Is the Docker engine running?
  echo See docs\Troubleshooting-Docker.md
  pause
  exit /b 1
)
echo.
docker compose ps
echo.
echo Keycloak:  http://localhost:8180  ^(admin/admin^)
echo Kafka:     localhost:9092
echo Postgres:  localhost:5432  ^(eagleseye/eagleseye^)
echo Valkey:    localhost:6379
echo Mosquitto: localhost:1883
echo.
pause
