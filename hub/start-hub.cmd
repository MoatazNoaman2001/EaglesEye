@echo off
title EaglesEye Hub
cd /d "%~dp0"
start "" http://localhost:4600
node server.mjs
