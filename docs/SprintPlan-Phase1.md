# Sprint Plan — Phase 1
## **EaglesEye** — Fleet Telematics Platform

| Field | Value |
|---|---|
| Document | Sprint Plan — Phase 1 |
| Version | 0.1 |
| Date | 04 August 2026 |
| Cadence | 9 sprints × 2 weeks = 18 weeks (Sun–Thu work week) |
| Related | BRD-Phase1-Fleet-Telematics.md · SolutionArchitecture-Phase1.md |

**Sprint 1 starts Sunday 09 Aug 2026.** Milestone exits (M0–M5) are marked where they land; they don't always align to sprint boundaries and that's fine — the sprint goal is what the team commits to.

Task IDs are `T-<sprint><seq>` (e.g. T-304 = Sprint 3, task 4). 🏢 = business/non-engineering task. References point at BRD requirements and architecture ADRs/risks.

---

## Timeline at a glance

| Sprint | Dates (2026) | Goal | Milestone exit |
|---|---|---|---|
| S1 | Aug 09 – Aug 20 | Hardware lab live, project foundations | **M0** |
| S2 | Aug 23 – Sep 03 | Ingestion spine: Kafka, SPI, first decoder | |
| S3 | Sep 06 – Sep 17 | Three protocols decoding, 2k msg/s proven | **M1** |
| S4 | Sep 20 – Oct 01 | Tenancy, auth, device & vehicle management | |
| S5 | Oct 04 – Oct 15 | Live map in production quality | **M2** |
| S6 | Oct 18 – Oct 29 | Trips, map matching, geofences | |
| S7 | Nov 01 – Nov 12 | Rules, alerts, notifications | **M3** (odometer gate) |
| S8 | Nov 15 – Nov 26 | Device health console, reports | **M4** |
| S9 | Nov 29 – Dec 10 | API, Arabic hardening, pilot #1 live | **M5** |

> **Standing risk watch:** RK-A6 (Arabic PDF) is deliberately scheduled in S4, far before reports in S8. The M3 odometer gate needs 30 days of real-vehicle data, so capture starts in S4 (T-408) — if that slips, M3 slips.

---

## Sprint 1 — Hardware Lab & Foundations *(Aug 09 – Aug 20)* → **M0**

**Sprint goal:** live byte streams from 3 device models captured and documented; repo, CI, and dev infrastructure exist; business dependencies chased.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-101 | 🏢 Procure 3–5 GPS devices (Teltonika FMB-series, Concox/GT06 family, third per pilot inventory) + activate SIMs | DP-01, OQ-02 | Urgent |
| T-102 | 🏢 Confirm pilot customers and device models; signed pilot agreements | OQ-01, OQ-02, DP-03 | Urgent |
| T-103 | 🏢 Open installer/workshop partner conversations | DP-02, RK-07 | High |
| T-104 | Build raw traffic capture listener (dumb TCP logger); capture and document live byte streams per device model | M0 exit, RK-06 | Urgent |
| T-105 | Scaffold Quarkus multi-module monorepo: `decoder-api`, `gateway`, `pipeline`, `core`, decoder plugin modules; ArchUnit + Maven enforcer boundary rules in CI | ADR-1, RK-A10 | High |
| T-106 | Dev environment: Docker Compose with Kafka (KRaft), PostgreSQL+Timescale+PostGIS, Valkey, Keycloak | DP-04 | High |
| T-107 | 🏢 Provision cloud VMs for staging (region per OQ-05 default) | DP-04, OA-1 | High |
| T-108 | 🏢 Compile open-source component + licence register; send for legal review | DP-06, RK-08, RK-A9 | Normal |
| T-109 | CI/CD pipeline: build, test, container images, deploy to staging | — | Normal |

**Exit:** M0 — live byte streams from 3 device models captured and documented.

---

## Sprint 2 — Ingestion Spine Core *(Aug 23 – Sep 03)*

**Sprint goal:** a real Teltonika device sends data end-to-end: decode → Kafka → TimescaleDB, with ACK-after-persist semantics.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-201 | Kafka topic design + provisioning (`telemetry.raw/decoded/rejected`, `events.domain`, `alerts.outbound`, `registry.devices` compacted) | Arch §4, AO-03 | High |
| T-202 | Decoder SPI (`decoder-api`): `ProtocolDecoder`, `TelemetryRecord`, `SessionContext`, framing contributor | BR-02, AO-04 | Urgent |
| T-203 | Ingestion gateway: Netty TCP listener framework, per-port protocol binding, connection lifecycle, IMEI auth against registry cache | FR-ING-05/06 | Urgent |
| T-204 | **Teltonika Codec 8/8E decoder plugin** (TCP+UDP) with ACK per spec; validate against physical unit | FR-ING-01/11 | Urgent |
| T-205 | ACK-after-durable-persist: produce raw+decoded with `acks=all` before device ACK | NFR-04, Arch §3.3 | Urgent |
| T-206 | Timeseries writer: batched inserts to positions hypertable, `ON CONFLICT DO NOTHING` dedup | FR-ING-08, NFR-01 | High |
| T-207 | Positions hypertable DDL: Timescale + compression policy + `(device_id, time)` index; Flyway baseline | NFR-06/07 | High |
| T-208 | Device registry: minimal CRUD + compacted-topic publication + gateway cache with miss-fallback lookup | FR-DEV-01, RK-A5 | High |
| T-209 | Decoder regression harness: replay captured raw frames from S1 as unit fixtures | RK-06 | Normal |

---

## Sprint 3 — Three Protocols + Load Proof *(Sep 06 – Sep 17)* → **M1**

**Sprint goal:** M1 exit — 3 protocols decoding, 2,000 msg/s sustained, backlog replay correct.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-301 | **Concox/GT06-family decoder plugin** with ACK; validate against physical unit | FR-ING-02 | Urgent |
| T-302 | **Third protocol decoder** (Queclink or Ruptela per pilot inventory) | FR-ING-03, OA-5 | High |
| T-303 | **Generic MQTT/JSON ingestion**: Mosquitto bridge + published telemetry schema | FR-ING-04, OA-3 | High |
| T-304 | Out-of-order/backlog handling: device-timestamp placement + dirty vehicle-day marking | FR-ING-07, BR-10, P-7 | Urgent |
| T-305 | Device simulator: N virtual devices per protocol, configurable rates, backlog-burst mode | NFR-01, RK-03 | Urgent |
| T-306 | **Load test: 2,000 msg/s sustained, 5,000 burst, 10× reconnect storm** — with concurrent query load; capture evidence | SM-07, NFR-01, RK-A4 | Urgent |
| T-307 | Raw payload archive: `telemetry.raw` → object storage after retention window | FR-ING-09 | Normal |
| T-308 | Observability v1: Prometheus/Grafana/Loki, ingestion dashboards, consumer-lag alerting | NFR-10, RK-A1 | High |
| T-309 | Write decoder developer guide (the "new protocol in 2 weeks" doc, drafted while memory is fresh) | BG-03, Acceptance #10 | Normal |

**Exit:** M1 — 3 protocols decoding; 2,000 msg/s sustained; backlog replay correct.

---

## Sprint 4 — Tenancy, Auth & Fleet Management *(Sep 20 – Oct 01)*

**Sprint goal:** multi-tenant platform skeleton: users log in, manage devices/vehicles, tenant isolation enforced and CI-tested.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-401 | Tenant model + hierarchy (parent/child) + branding fields; **RLS on all tenant tables**, migration lint enforcing it | FR-SEC-01/02/08, AO-01/02/05 | Urgent |
| T-402 | **Cross-tenant leak test in CI**: two tenants, assert zero mutual visibility | RK-A2 | Urgent |
| T-403 | Keycloak integration: OIDC, roles (Platform Admin, Tenant Admin, Manager, Viewer, Installer), session/password policy, 2FA | FR-SEC-03/05/06 | High |
| T-404 | Vehicle/asset CRUD + groups + group-scoped visibility | FR-DEV-03/04, FR-SEC-04 | High |
| T-405 | Device lifecycle: register, assign to vehicle (history kept), unassign, decommission without data loss | FR-DEV-02/06 | High |
| T-406 | Bulk import vehicles + devices via spreadsheet | FR-DEV-05 | Normal |
| T-407 | Audit log for administrative actions | FR-SEC-07 | Normal |
| T-408 | 🏢 **Fit validation vehicle & start 30-day odometer capture** (drives M3 gate) | RK-01, SM-02 | Urgent |
| T-409 | **Arabic PDF rendering spike**: pick engine, render a real Arabic report, native-speaker review | RK-A6, FR-LOC-04, OA-4 | High |
| T-410 | Frontend scaffold: React+TS+Vite, i18next AR/EN with RTL, auth flow, app shell (Arabic-first) | FR-LOC-01/02 | High |

---

## Sprint 5 — Live Tracking Console *(Oct 04 – Oct 15)* → **M2**

**Sprint goal:** M2 exit — a test fleet is visible and correct on the live map.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-501 | Enricher: IMEI→device→vehicle→tenant resolution, status derivation (moving/idle/stopped/offline/no-fix), Valkey live state | FR-TRK-03, Arch §6 | Urgent |
| T-502 | Live map: MapLibre, all-vehicle view, clustering, status colouring | FR-TRK-01/07 | Urgent |
| T-503 | WebSocket/SSE live push with per-session tenant+group filtering, 1 update/vehicle/sec throttle | FR-TRK-02, NFR-02, RK-A8 | Urgent |
| T-504 | Vehicle detail panel: speed, heading, last update, ignition, voltage, address | FR-TRK-04 | High |
| T-505 | Self-hosted geo stack: tile server (regional extract), Photon reverse geocoding + Valkey geohash cache | FR-TRK-05, AS-06, ADR-6 | High |
| T-506 | Map search/filter by group, status, plate, driver; follow-vehicle mode | FR-TRK-06/08 | Normal |
| T-507 | Installer flow: registration + live "device is reporting valid GPS" confirmation screen | FR-DEV-08 | High |
| T-508 | Device-to-map latency measurement in Grafana (SM-03 continuously measured) | NFR-02 | High |
| T-509 | 🏢 Installer partner signed; installation checklist + standard device config profiles per model | DP-02, RK-04 | High |

**Exit:** M2 — test fleet visible and correct on the live map.

---

## Sprint 6 — Trips, Map Matching & Geofences *(Oct 18 – Oct 29)*

**Sprint goal:** trustworthy trips: segmentation, OSRM matching, history replay, geofence events.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-601 | Trip engine: ignition+movement segmentation with hysteresis, per-tenant thresholds; trip/stop persistence | FR-TRP-01/03/04/08 | Urgent |
| T-602 | OSRM deployment + `/match` integration; matched distance as reported distance; confidence flagging fallback | FR-TRP-02, RK-A3 | Urgent |
| T-603 | Dirty vehicle-day recompute: rate-limited queue, idempotent, live-edge priority | FR-TRP-07, RK-A7 | High |
| T-604 | History replay UI: date-range path on map with playback controls; daily summary per vehicle | FR-TRP-05/06 | High |
| T-605 | Geofence CRUD: draw circle/polygon, categories/colours, assignment to vehicles/groups; PostGIS storage | FR-GEO-01/02/03 | High |
| T-606 | Geofence evaluator: enter/exit with two-fix hysteresis, dwell time, time-window rules | FR-GEO-04/05/06 | Urgent |
| T-607 | Compare running odometer-validation data vs computed distance; tune matching (mid-gate check) | RK-01, SM-02 | Urgent |
| T-608 | GeoJSON geofence import | FR-GEO-07 | Low |

---

## Sprint 7 — Insight Engine: Rules, Alerts & Digest *(Nov 01 – Nov 12)* → **M3**

> Reframed 10 Aug 2026 per the product thesis (docs/business/Competitive-Study-and-Product-Thesis):
> the alerts machinery gains the **Daily Digest** — one verdict message per morning, exceptions only,
> framed in money. WhatsApp is the owner surface (OQ-06 answered); transport starts as email/in-app.

**Sprint goal:** all Must alert types firing with mandatory dedup; the Daily Digest loop live; M3 odometer gate passes.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-711 | **Daily Digest generator**: nightly per-tenant rollup of events+trips into one verdict message | Thesis | Urgent |
| T-712 | Normal-profile onboarding: working hours, home zones, speed policy (10-minute setup) | Thesis, FR-GEO-06 | High |
| T-713 | Money model: fuel price + idle-burn settings; currency framing in digest and reports | Thesis | High |
| T-714 | Digest delivery: email + in-app in WhatsApp-ready template; WhatsApp Business API application started early | Thesis, OQ-06 | High |

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-701 | Rules engine: all Must event types (speeding, idle, geofence in/out, ignition, unauthorised movement, power disconnect, low battery, SOS, offline) | FR-ALT-01 | Urgent |
| T-702 | Rule configuration per vehicle/group: thresholds, active time windows | FR-ALT-02 | High |
| T-703 | **Cooldown + dedup via Valkey — mandatory, on by default** | FR-ALT-06, RK-05 | Urgent |
| T-704 | Notification outbox + delivery: email (SMTP) + in-app notification centre | FR-ALT-03 | High |
| T-705 | Webhook delivery: retry, exponential backoff, dead-letter status visible to tenant | FR-ALT-04, FR-API-03 | High |
| T-706 | Alert history: searchable, acknowledgement status; per-user alert subscriptions | FR-ALT-07/08 | High |
| T-707 | `NotificationChannel` interface + WhatsApp/SMS decision spike | FR-ALT-05, OQ-06 | Normal |
| T-708 | **M3 gate: 30-day odometer validation review — distance within 2%** (data from T-408) | SM-02, Acceptance #3 | Urgent |
| T-709 | 🏢 Tune alert thresholds with pilot users (week-one plan per RK-05) | RK-05 | Normal |

**Exit:** M3 — distance for real test vehicle within 2% of odometer over 30 days.

---

## Sprint 8 — Device Health & Reports *(Nov 15 – Nov 26)* → **M4**

**Sprint goal:** M4 exit — health console diagnosing seeded failures; report suite complete.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-801 | Health dashboard: all devices by staleness; per-device GSM/GPS/voltage/battery/firmware/protocol/profile | FR-HLT-01/02 | Urgent |
| T-802 | **Non-reporting cause classifier**: no network / no GPS fix / power disconnected / device silent | FR-HLT-03, BR-06 | Urgent |
| T-803 | Internal alerting on non-reporting devices; connection history per device (connects, disconnects, decode errors) | FR-HLT-04/05 | High |
| T-804 | Cross-tenant health view for internal support; config-drift flagging | FR-HLT-06/07 | High |
| T-805 | **Seeded-failure test**: pull SIM, antenna, power on lab devices — verify classifier reaches ≥80% correct cause | Acceptance #6 | Urgent |
| T-806 | Report suite: trips, distance/utilisation, stops/idle, speeding, geofence activity, inactivity | FR-RPT-01…06 | High |
| T-807 | Excel + PDF export (Arabic-correct, engine from T-409); scheduled email delivery | FR-RPT-07/08, FR-LOC-04 | High |
| T-808 | Voltage-drain monitoring alerts (battery protection) | RK-04 | Normal |

**Exit:** M4 — all Must alert types firing correctly; health console diagnosing seeded failures.

---

## Sprint 9 — API, Hardening & Pilot Launch *(Nov 29 – Dec 10)* → **M5**

**Sprint goal:** M5 exit — pilot fleet #1 live in production.

| ID | Task | Refs | Priority |
|---|---|---|---|
| T-901 | Public REST API v1: vehicles, devices, positions, trips, geofences, events; OpenAPI published | FR-API-01/04 | Urgent |
| T-902 | API tokens: tenant-scoped, hashed, revocable; per-tenant rate limiting (Bucket4j) | FR-API-02/05 | High |
| T-903 | 🏢 One external integration consuming API+webhooks successfully (pilot's IT or our own demo ERP bridge) | Acceptance #7, OQ-08 | High |
| T-904 | **Arabic end-to-end verification by native-speaking pilot user, incl. exported reports**; fix pass | Acceptance #8 | Urgent |
| T-905 | Backup/restore: pgBackRest + WAL archiving; **restore rehearsal executed** | NFR-11, Acceptance #9 | Urgent |
| T-906 | Production deployment in target region; TLS, secrets management, hardening pass | NFR-08/09, OA-1/2 | Urgent |
| T-907 | 🏢 Pilot #1 onboarding: tenant provisioning, device registration, installer scheduling, ops training | SM-01 | Urgent |
| T-908 | Availability monitoring + on-call runbooks (ingestion 99.5% measurement live) | NFR-03, SM-04 | High |
| T-909 | **Protocol-decoder acceptance test: new developer adds a decoder from docs alone, < 2 weeks** (start now, finishes post-M5) | BG-03, Acceptance #10 | Normal |
| T-910 | Bulk historical export endpoint | FR-API-06 | Low |

**Exit:** M5 — pilot fleet #1 live in production. Pilot #2 onboarding continues post-sprint toward full Phase 1 acceptance.

---

## Business-dependency tracker (owner: Moataz / business side)

These block engineering if late — reviewed at every sprint boundary:

| Item | Needed by | Status |
|---|---|---|
| Devices + SIMs in lab (DP-01) | Sprint 1 | ☐ |
| Pilot agreements signed (OQ-01/DP-03) | Sprint 2 | ☐ |
| Pilot device models confirmed (OQ-02) → third protocol choice | Sprint 2 | ☐ |
| Launch market / region (OQ-05) → staging region | Sprint 4 | ☐ |
| Installer partner engaged (DP-02) | Sprint 5 | ☐ |
| WhatsApp-at-pilot decision (OQ-06) | Sprint 7 | ☐ |
| Commercial model (OQ-04) | Sprint 9 | ☐ |
| ERP-during-pilot decision (OQ-08) | Sprint 9 | ☐ |

---

## Revision History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1 | 04 Aug 2026 | Moataz + Claude | Initial sprint plan |
