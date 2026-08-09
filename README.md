<p align="center">
  <img src="https://raw.githubusercontent.com/MoatazNoaman2001/EaglesEye/phase1/brand/eagleseye-icon.svg" width="120" alt="EaglesEye">
</p>

<h1 align="center">EaglesEye</h1>
<p align="center"><i>We see you — wherever you are · عين النسر — نراك أينما كنت</i></p>

---

**EaglesEye** is a hardware-agnostic fleet telematics platform for small and mid-sized fleets in Egypt, the Gulf, and the wider MENA region.

**Bring your own hardware.** Fleets keep the GPS devices they already own — Teltonika, Concox/GT06, Queclink and more — and get live tracking, trustworthy trip history, geofencing, smart alerting, device health diagnostics, and a real public API. Multi-tenant, Arabic-first, built to scale from 10 to 10,000 vehicles.

## Status

Phase 1 (core tracking platform) is in active development — see the [`phase1`](../../tree/phase1) branch.

Working today: multi-protocol ingestion through a Traccar bridge, generic MQTT/JSON device lane,
Kafka event backbone, telemetry enrichment with live vehicle state, device/vehicle registry with
bridge auto-sync, runtime settings service, and a live map.

## Branches

| Branch | Purpose |
|---|---|
| `main` | This overview |
| `phase1` | Phase 1 development — all code, docs, and project tooling |
| `dev` | Integration branch |
| `deploy` | Deployment configuration (coming later) |

## Stack

Java 21 / Quarkus · Apache Kafka · TimescaleDB + PostGIS · Valkey · Keycloak · Traccar (protocol bridge) · Mosquitto MQTT · MapLibre

## Documentation

On the [`phase1`](../../tree/phase1) branch: `docs/` holds the BRD, solution architecture (with ADRs and risk register), sprint plan, and a hands-on stack study guide.
