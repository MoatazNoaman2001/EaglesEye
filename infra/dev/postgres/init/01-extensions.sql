-- EaglesEye dev database bootstrap.
-- Runs once, on first container start, against the eagleseye database.

-- Time-series positions (hypertables, compression, retention) — Arch §5.1
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Spatial types and indexes for geofences — FR-GEO, Arch §5.1
CREATE EXTENSION IF NOT EXISTS postgis;

-- Keycloak keeps its own schema in its own database (Arch §7 / ADR-5)
CREATE DATABASE keycloak OWNER eagleseye;
